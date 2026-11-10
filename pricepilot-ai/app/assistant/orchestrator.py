import os
import uuid
import time
import logging
from typing import Dict, Any, List, Optional

from app.config.settings import settings
from app.assistant.memory import memory_manager
from app.assistant.rag import rag_pipeline
from app.assistant.response_formatter import response_formatter
from app.assistant.prompts import (
    SYSTEM_PROMPT,
    RECOMMENDATION_PROMPT,
    PRICE_ANALYSIS_PROMPT,
    COMPARISON_PROMPT,
    SHOPPING_ADVICE_PROMPT
)
from app.assistant.providers import (
    LlmProvider,
    ProviderFactory,
    LlmRole,
    LlmMessage,
    LlmGenerationConfig,
    LlmCompletionRequest,
    ProviderError
)

logger = logging.getLogger("pricepilot.assistant.orchestrator")

class AssistantOrchestrator:
    """Orchestrates RAG, memory, prompt assembly, LLM call, and response formatting."""

    def __init__(self, provider: Optional[LlmProvider] = None) -> None:
        self._provider = provider

    @property
    def provider(self) -> LlmProvider:
        if self._provider is None:
            self._provider = ProviderFactory.get_provider()
        return self._provider

    def set_provider(self, provider: LlmProvider) -> None:
        self._provider = provider

    def chat(self, message: str, conversation_id: Optional[str] = None, token: Optional[str] = None) -> Dict[str, Any]:
        start_time = time.time()
        
        # 1. Manage Conversation ID & memory (temporary compatibility shim for AI-02)
        if not conversation_id:
            conversation_id = str(uuid.uuid4())
            
        memory_manager.add_message(conversation_id, "user", message)
        active_product_id = memory_manager.get_active_product(conversation_id)

        # 2. Retrieve Context (RAG)
        retrieval_start = time.time()
        context = rag_pipeline.retrieve_context(message, token=token, active_product_id=active_product_id)
        retrieval_latency = time.time() - retrieval_start

        # Update memory state based on retrieved context
        if context.get("products"):
            new_active_id = context["products"][0].get("id")
            memory_manager.set_active_product(conversation_id, new_active_id)
            memory_manager.add_previous_search(conversation_id, message)

        context_str = rag_pipeline.format_context_as_text(context)

        # 3. Construct Prompts & Messages
        history = memory_manager.get_messages(conversation_id)

        # Select prompt modifiers based on query intent
        intent_prompt = ""
        query_lower = message.lower()
        if "recommend" in query_lower or "similar" in query_lower:
            intent_prompt = RECOMMENDATION_PROMPT
        elif "buy now" in query_lower or "should i buy" in query_lower or "is now a good time" in query_lower:
            intent_prompt = PRICE_ANALYSIS_PROMPT
        elif "compare" in query_lower:
            intent_prompt = COMPARISON_PROMPT
        else:
            intent_prompt = SHOPPING_ADVICE_PROMPT

        # Validate system prompt assembly
        if not SYSTEM_PROMPT:
            raise ValueError("System prompt is empty or not configured")

        system_instruction = f"{SYSTEM_PROMPT.strip()}\n\n{intent_prompt.strip()}"

        # Build stateless conversation messages
        # Prior history
        llm_messages: List[LlmMessage] = []
        for msg in history[:-1]:
            role = LlmRole.USER if msg.get("role") == "user" else LlmRole.ASSISTANT
            llm_messages.append(LlmMessage(role=role, content=msg.get("content", "")))

        # Current user turn containing grounding context and query
        user_turn_content = (
            f"<retrieved_context>\n"
            f"{context_str}\n"
            f"</retrieved_context>\n\n"
            f"<user_query>\n"
            f"{message}\n"
            f"</user_query>"
        )
        llm_messages.append(LlmMessage(role=LlmRole.USER, content=user_turn_content))

        # 4. Invoke LLM Provider with configured budget and 0 retries
        llm_start = time.time()
        completion_request = LlmCompletionRequest(
            system_instruction=system_instruction,
            messages=llm_messages,
            generation_config=LlmGenerationConfig(
                temperature=settings.llm_temperature,
                top_p=settings.llm_top_p,
                max_output_tokens=settings.llm_max_tokens
            ),
            metadata={"conversation_id": conversation_id}
        )

        logger.info(
            "Invoking LLM provider '%s' (model=%s, timeout=%.2fs)",
            self.provider.provider_name,
            self.provider.model_name,
            settings.llm_timeout_seconds
        )
        llm_response = self.provider.generate_text(completion_request)
        raw_response = llm_response.content
        llm_latency = llm_response.latency_seconds

        latencies = {
            "retrieval": retrieval_latency,
            "llm": llm_latency,
            "total": time.time() - start_time
        }

        # Save to memory (temporary compatibility shim)
        memory_manager.add_message(conversation_id, "assistant", raw_response)

        # 5. Format Structured Response
        structured_res = response_formatter.format_response(raw_response, context, message)
        structured_res["conversationId"] = conversation_id
        structured_res["latency"] = latencies
        structured_res["model"] = llm_response.model_name
        structured_res["provider"] = llm_response.provider_name

        return structured_res

    def _run_local_reasoning(self, query: str, context: Dict[str, Any], active_product_id: Optional[str]) -> str:
        """Local reasoning engine helper used for standalone comparison and fallback scenarios."""
        query_lower = query.lower()
        products = context.get("products", [])

        # 1. Price Insight Engine (Is now a good time to buy?)
        if "buy now" in query_lower or "should i buy" in query_lower or "is now a good time" in query_lower:
            if not products:
                return (
                    "I searched the PricePilot catalog, but I couldn't find any products in focus to analyze. "
                    "Please search for a product first, and I will be happy to analyze if now is a good time to buy."
                )
            
            p = products[0]
            history = context.get("price_history", [])
            
            if not history:
                return (
                    f"### Price Insight: {p.get('name')}\n"
                    f"I see that **{p.get('name')}** is currently listed at ₹{p.get('currentMinPrice', 0):,.2f}. "
                    "However, we don't have enough historical price points stored yet to run a full trend evaluation.\n\n"
                    "[BUY_CONFIDENCE]\n"
                    "Score: 70%\n"
                    "Reason: Insufficient price history, but pricing is stable. Default confidence rating applied.\n"
                    "[/BUY_CONFIDENCE]"
                )
            
            prices = [float(h.get("price", 0)) for h in history if h.get("price") is not None]
            avg_price = sum(prices) / len(prices)
            current_price = float(p.get("currentMinPrice") or prices[-1])
            min_price = min(prices)
            discount = float(p.get("discountPercentage") or 0.0)

            if current_price <= min_price:
                score = 95
                reason = f"Current price of ₹{current_price:,.2f} is at its lowest historical point (minimum price seen was ₹{min_price:,.2f}). This is an exceptional buying window!"
            elif current_price < avg_price:
                pct = ((avg_price - current_price) / avg_price) * 100
                score = 80 + int(pct)
                score = min(score, 94)
                reason = f"Current price is {pct:.1f}% below the historical average (₹{avg_price:,.2f}) with stable pricing."
            else:
                pct = ((current_price - avg_price) / avg_price) * 100
                score = max(50 - int(pct), 20)
                reason = f"Current price is {pct:.1f}% above the historical average (₹{avg_price:,.2f}). We recommend waiting for a price drop."

            return (
                f"### Price Insight: {p.get('name')}\n\n"
                f"• **Current Price**: ₹{current_price:,.2f}\n"
                f"• **Historical Average**: ₹{avg_price:,.2f}\n"
                f"• **Historical Minimum**: ₹{min_price:,.2f}\n"
                f"• **Active Discount**: {discount:.1f}%\n\n"
                f"**Recommendation**: {reason}\n\n"
                f"[BUY_CONFIDENCE]\n"
                f"Score: {score}%\n"
                f"Reason: {reason}\n"
                f"[/BUY_CONFIDENCE]"
            )

        # 2. Comparison Engine (Compare MacBook vs Dell etc.)
        if "compare" in query_lower:
            if len(products) < 2:
                return (
                    "I see you want to compare products, but I couldn't find multiple items in the context. "
                    "Could you specify the names or search keywords for the products you'd like to compare?"
                )
            
            p1, p2 = products[0], products[1]
            p1_prices = p1.get("productPrices") or []
            p2_prices = p2.get("productPrices") or []
            p1_price = p1.get("currentMinPrice") or (p1_prices[0].get("currentPrice") if p1_prices else 0.0)
            p2_price = p2.get("currentMinPrice") or (p2_prices[0].get("currentPrice") if p2_prices else 0.0)
            p1_disc = p1.get("discountPercentage") or (p1_prices[0].get("discountPercentage") if p1_prices else 0.0)
            p2_disc = p2.get("discountPercentage") or (p2_prices[0].get("discountPercentage") if p2_prices else 0.0)

            # Map details
            summary = (
                f"### Comparison: {p1.get('name')} vs {p2.get('name')}\n\n"
                f"| Feature | {p1.get('name')} | {p2.get('name')} |\n"
                f"| --- | --- | --- |\n"
                f"| **Price** | ₹{p1_price:,.2f} | ₹{p2_price:,.2f} |\n"
                f"| **Brand** | {p1.get('brand')} | {p2.get('brand')} |\n"
                f"| **Category** | {p1.get('category')} | {p2.get('category')} |\n"
                f"| **Discount** | {p1_disc}% | {p2_disc}% |\n"
                f"| **Sellers** | {len(p1_prices)} available | {len(p2_prices)} available |\n\n"
                f"**Verdict**: "
            )
            if p1_price < p2_price:
                summary += f"**{p1.get('name')}** is more budget-friendly by ₹{p2_price - p1_price:,.2f}."
            else:
                summary += f"**{p2.get('name')}** is more budget-friendly by ₹{p1_price - p2_price:,.2f}."

            card_json = {
                "products": [
                    {
                        "id": p1.get("id"),
                        "name": p1.get("name"),
                        "price": float(p1_price),
                        "discount": float(p1_disc),
                        "sellersCount": len(p1_prices),
                        "brand": p1.get("brand")
                    },
                    {
                        "id": p2.get("id"),
                        "name": p2.get("name"),
                        "price": float(p2_price),
                        "discount": float(p2_disc),
                        "sellersCount": len(p2_prices),
                        "brand": p2.get("brand")
                    }
                ],
                "summary": f"Comparing {p1.get('name')} and {p2.get('name')}."
            }

            import json
            return f"{summary}\n\n[COMPARISON]\n{json.dumps(card_json)}\n[/COMPARISON]"

        # 3. Recommendations & Similar Products
        if "recommend" in query_lower or "similar" in query_lower or "suggest" in query_lower:
            recs = context.get("recommendations") or context.get("saved_products") or context.get("products")
            if not recs:
                return (
                    "I'd love to make recommendations, but your profile has no saved products or active preferences yet. "
                    "Search for some products first or add them to your saved products, and I will recommend matching options!"
                )
            
            lines = ["Here are my explainable recommendations for you:\n"]
            for r in recs[:3]:
                p = r.get("product") or r
                name = p.get("name")
                price = p.get("currentMinPrice") or p.get("price") or 0.0
                disc = p.get("discountPercentage") or 0.0

                lines.append(f"**{name}** (₹{price:,.2f})")
                lines.append("Recommended because:")
                lines.append(f"• Matches your preferred brand {p.get('brand', 'Unknown')}.")
                if disc > 5.0:
                    lines.append(f"• Recently experienced a significant price drop of {disc}%.")
                else:
                    lines.append("• Fits within your general category preferences.")
                lines.append("")

            return "\n".join(lines)

        # 4. Trending Products
        if "trending" in query_lower or "popular" in query_lower:
            trending = context.get("trending_products") or products
            if not trending:
                return "There are no trending products registered in PricePilot today. Check back later!"
            
            lines = ["### Trending Products on PricePilot:\n"]
            for p in trending[:5]:
                lines.append(f"- **{p.get('name')}** | Brand: {p.get('brand')} | Current Price: ₹{p.get('currentMinPrice', 0):,.2f}")
            return "\n".join(lines)

        # 5. General Search or Catalog details
        if products:
            lines = ["I found the following products matching your request:\n"]
            for p in products[:5]:
                prices = p.get("productPrices") or []
                min_price = p.get("currentMinPrice") or (prices[0].get("currentPrice") if prices else 0.0)
                lines.append(f"- **{p.get('name')}** (₹{min_price:,.2f}) - Category: {p.get('category')} | Brand: {p.get('brand')}")
            lines.append("\nWould you like me to compare them, analyze their price history, or check for similar options?")
            return "\n".join(lines)

        # 6. Fallback general statement
        return (
            "Hello! I am the PricePilot AI Shopping Assistant. "
            "I can help you search the product catalog, check price histories, analyze buy confidences, or generate comparisons. "
            "For example, you could ask me:\n"
            "- *'Is now a good time to buy?'*\n"
            "- *'Compare gaming laptops under 80,000'* \n"
            "- *'Show me trending products'*"
        )

orchestrator = AssistantOrchestrator()
