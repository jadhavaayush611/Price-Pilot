import re
import json
import logging
from typing import Dict, Any, List, Optional

logger = logging.getLogger("pricepilot.assistant.response_formatter")

class ResponseFormatter:
    """Formats and structure AI Assistant answers into typed frontend models."""

    def format_response(self, raw_content: str, retrieved_context: Dict[str, Any], query: str) -> Dict[str, Any]:
        """Parses the raw LLM output and combines it with retrieved context to form a structured API response."""
        
        # 1. Extract Buy Confidence if present
        buy_confidence = None
        buy_conf_match = re.search(r"\[BUY_CONFIDENCE\](.*?)(?:\[/BUY_CONFIDENCE\]|$)", raw_content, re.DOTALL)
        if buy_conf_match:
            content = buy_conf_match.group(1).strip()
            score_match = re.search(r"Score:\s*(\d+)%", content)
            reason_match = re.search(r"Reason:\s*(.*)", content, re.DOTALL)
            
            score = int(score_match.group(1)) if score_match else 70
            reason = reason_match.group(1).strip() if reason_match else "Based on historical average prices."
            buy_confidence = {
                "score": score,
                "reason": reason
            }
        elif "buy now" in query.lower() or "should i buy" in query.lower():
            # Fallback buy confidence generation from historical data in context
            buy_confidence = self._generate_fallback_buy_confidence(retrieved_context)

        # 2. Extract Comparison if present
        comparisons = None
        comp_match = re.search(r"\[COMPARISON\](.*?)(?:\[/COMPARISON\]|$)", raw_content, re.DOTALL)
        if comp_match:
            try:
                comp_text = comp_match.group(1).strip()
                comparisons = json.loads(comp_text)
            except Exception as e:
                logger.warning(f"Failed to parse comparison JSON block: {e}")
                
        if not comparisons and "compare" in query.lower():
            # Fallback comparison generation if comparison was requested but not formatted correctly
            comparisons = self._generate_fallback_comparison(retrieved_context)

        # 3. Strip structural tags from user-facing response text
        clean_text = raw_content
        clean_text = re.sub(r"\[BUY_CONFIDENCE\].*?\[/BUY_CONFIDENCE\]", "", clean_text, flags=re.DOTALL)
        clean_text = re.sub(r"\[COMPARISON\].*?\[/COMPARISON\]", "", clean_text, flags=re.DOTALL)
        clean_text = clean_text.strip()

        # 4. Identify active product ID
        active_product_id = None
        if retrieved_context.get("products"):
            active_product_id = retrieved_context["products"][0].get("id")

        # 5. Extract product cards (if any products were retrieved or suggested)
        products_list = []
        for p in retrieved_context.get("products", []):
            # Map products to the structure needed by the frontend card
            products_list.append(self._map_product_to_card(p))

        # 6. Generate suggested follow-up prompts
        suggested_prompts = self._generate_suggested_prompts(query, retrieved_context, active_product_id)

        return {
            "response": clean_text,
            "activeProductId": active_product_id,
            "suggestedPrompts": suggested_prompts,
            "products": products_list,
            "comparisons": comparisons,
            "buyConfidence": buy_confidence
        }

    def _map_product_to_card(self, product: Dict[str, Any]) -> Dict[str, Any]:
        """Maps raw product model to a clean product card presentation."""
        prices = product.get("productPrices") or []
        discount = 0.0
        current_price = product.get("currentMinPrice") or 0.0
        original_price = product.get("originalMinPrice") or current_price

        if prices:
            current_price = min((p.get("currentPrice") for p in prices if p.get("currentPrice") is not None), default=current_price)
            original_price = min((p.get("originalPrice") for p in prices if p.get("originalPrice") is not None), default=original_price)
            discount = max((p.get("discountPercentage") for p in prices if p.get("discountPercentage") is not None), default=0.0)
        else:
            discount = product.get("discountPercentage", 0.0)

        # Normalize types
        current_price = float(current_price) if current_price else 0.0
        original_price = float(original_price) if original_price else 0.0
        discount = float(discount) if discount else 0.0

        return {
            "id": product.get("id"),
            "name": product.get("name"),
            "brand": product.get("brand") or "Unknown",
            "category": product.get("category"),
            "price": current_price,
            "originalPrice": original_price,
            "discount": discount,
            "sellersCount": len(prices) if prices else 1,
            "imageUrl": product.get("imageUrl") or "/placeholder.png"
        }

    def _generate_fallback_buy_confidence(self, context: Dict[str, Any]) -> Dict[str, Any]:
        """Generates deterministic buy confidence score based on actual price history context."""
        history = context.get("price_history", [])
        products = context.get("products", [])

        if not history or not products:
            return {
                "score": 75,
                "reason": "Insufficient price history to calculate confidence. However, the item is currently available at the listed price."
            }

        prices = [float(h.get("price", 0)) for h in history if h.get("price") is not None]
        if not prices:
            return {
                "score": 70,
                "reason": "No numeric price history points found to evaluate."
            }

        avg_price = sum(prices) / len(prices)
        current_price = float(products[0].get("currentMinPrice") or prices[-1])
        min_price = min(prices)

        price_diff_percent = ((avg_price - current_price) / avg_price) * 100 if avg_price > 0 else 0

        if current_price <= min_price:
            score = 95
            reason = f"Current price of ₹{current_price:,.2f} is at its historical minimum. This is an excellent time to buy!"
        elif current_price < avg_price:
            score = 80 + int(price_diff_percent)
            score = min(score, 94)
            reason = f"Current price is {price_diff_percent:.1f}% below the historical average (₹{avg_price:,.2f}) with stable pricing."
        else:
            score = max(50 - int((current_price - avg_price) / avg_price * 100), 20)
            reason = f"Current price is above the historical average (₹{avg_price:,.2f}). We recommend waiting for a price drop."

        return {
            "score": score,
            "reason": reason
        }

    def _generate_fallback_comparison(self, context: Dict[str, Any]) -> Dict[str, Any]:
        """Generates side-by-side comparison object dynamically if LLM did not structure it."""
        products = context.get("products", [])
        if len(products) < 2:
            return None

        p_cards = [self._map_product_to_card(p) for p in products[:2]]
        
        # Build comparison grid
        comp_data = {
            "products": p_cards,
            "summary": f"Comparing {p_cards[0]['name']} and {p_cards[1]['name']}. {p_cards[0]['name']} is priced at ₹{p_cards[0]['price']:,.2f} compared to {p_cards[1]['name']} at ₹{p_cards[1]['price']:,.2f}."
        }
        return comp_data

    def _generate_suggested_prompts(self, query: str, context: Dict[str, Any], active_product_id: Optional[str]) -> List[str]:
        """Generates helpful dynamic suggested prompts based on current interaction state."""
        prompts = []
        query_lower = query.lower()

        # If a specific product is in focus
        if active_product_id and context.get("products"):
            prod = context["products"][0]
            name = prod.get("name", "this product")
            short_name = name[:15] + "..." if len(name) > 15 else name
            
            if "price" not in query_lower and "should i buy" not in query_lower:
                prompts.append(f"Is now a good time to buy {short_name}?")
            if "similar" not in query_lower and "recommend" not in query_lower:
                prompts.append(f"Recommend items similar to {short_name}")
            if "compare" not in query_lower:
                prompts.append(f"Compare {short_name} with other options")
        
        # General fallback prompts
        if not prompts:
            prompts.append("Is now a good time to buy?")
            prompts.append("Recommend something similar to my saved products.")
            prompts.append("Which products are trending?")
            
        return prompts[:3]

response_formatter = ResponseFormatter()
