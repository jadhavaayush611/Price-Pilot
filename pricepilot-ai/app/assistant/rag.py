import re
import logging
from typing import Dict, Any, List, Optional
from app.assistant.tools import (
    search_products,
    get_product_details,
    get_similar_products,
    get_recommendations,
    get_dashboard,
    get_trending_products,
    get_product_analytics,
    get_price_history,
    get_watchlists,
    get_saved_products
)

logger = logging.getLogger("pricepilot.assistant.rag")

class RetrievalPipeline:
    """Retrieves relevant domain context from PricePilot services to ground LLM reasoning."""

    def retrieve_context(self, query: str, token: Optional[str] = None, active_product_id: Optional[str] = None) -> Dict[str, Any]:
        """Analyzes the user query and retrieves context from appropriate PricePilot APIs."""
        logger.info(f"Retrieving context for query: '{query}' | Active Product ID: {active_product_id}")
        context = {
            "products": [],
            "analytics": {},
            "price_history": [],
            "dashboard": {},
            "saved_products": [],
            "watchlists": [],
            "trending_products": [],
            "recommendations": []
        }

        query_lower = query.lower()

        # 1. Product Search and Specific Product details
        search_keyword = None
        # Try to extract search keywords
        if "gaming" in query_lower or "laptop" in query_lower or "phone" in query_lower or "iphone" in query_lower or "macbook" in query_lower or "dell" in query_lower or "xps" in query_lower:
            # Try to grab a chunk of words
            words = query.split()
            # Simple keyword extraction: strip common question words
            cleaned_words = [w for w in words if w.lower() not in ["show", "me", "the", "has", "dropped", "in", "price", "is", "now", "a", "good", "time", "to", "buy", "under", "which", "seller", "currently", "best", "deal"]]
            search_keyword = " ".join(cleaned_words)
            if not search_keyword:
                search_keyword = query
        elif "show me" in query_lower or "search" in query_lower or "find" in query_lower or "compare" in query_lower:
            search_keyword = query_lower.replace("show me", "").replace("search for", "").replace("find", "").replace("compare", "").strip()

        # Search for products if keywords are found
        searched_product_ids = []
        if search_keyword:
            logger.info(f"RAG: Executing product search for '{search_keyword}'")
            search_res = search_products(keyword=search_keyword, token=token)
            products_list = search_res.get("content", [])
            context["products"].extend(products_list)
            searched_product_ids = [p.get("id") for p in products_list if p.get("id")]

        # Determine target product ID for details, analytics, and history
        target_product_id = active_product_id
        if not target_product_id and searched_product_ids:
            target_product_id = searched_product_ids[0]

        # Extract product UUID if direct query contains standard UUID format
        uuid_match = re.search(r"[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}", query_lower)
        if uuid_match:
            target_product_id = uuid_match.group(0)

        # 2. Retrieve Specific Product Context (Details, Analytics, History)
        if target_product_id:
            logger.info(f"RAG: Retrieving detailed context for product: {target_product_id}")
            # If search didn't already fetch details, fetch details
            if not any(p.get("id") == target_product_id for p in context["products"]):
                details = get_product_details(target_product_id, token=token)
                if details:
                    context["products"].append(details)
            
            # Fetch analytics
            analytics_res = get_product_analytics(target_product_id, token=token)
            if analytics_res:
                context["analytics"][target_product_id] = analytics_res
                
            # Fetch price history
            history_res = get_price_history(target_product_id, token=token)
            if history_res:
                context["price_history"] = history_res

        # 3. Recommendations & Similar Products
        if "recommend" in query_lower or "similar" in query_lower or "like" in query_lower or "matches" in query_lower:
            if target_product_id:
                similar_res = get_similar_products(target_product_id, token=token)
                context["recommendations"].extend(similar_res)
            else:
                recs_res = get_recommendations(token=token)
                context["recommendations"].extend(recs_res.get("content", []))

        # 4. Trending/Popular Products
        if "trending" in query_lower or "popular" in query_lower or "deal" in query_lower or "movement" in query_lower:
            trending_res = get_trending_products(token=token)
            context["trending_products"].extend(trending_res)

        # 5. Dashboard Context
        if "dashboard" in query_lower or "my activity" in query_lower or "summary" in query_lower:
            dashboard_res = get_dashboard(token=token)
            context["dashboard"] = dashboard_res

        # 6. Saved Products
        if "saved" in query_lower or "my saves" in query_lower or "profile" in query_lower or "recommend something similar to my" in query_lower:
            saved_res = get_saved_products(token=token)
            context["saved_products"].extend(saved_res)

        # 7. Watchlists
        if "watchlist" in query_lower or "watchlists" in query_lower or "tracking" in query_lower:
            watchlists_res = get_watchlists(token=token)
            context["watchlists"].extend(watchlists_res)

        return context

    def format_context_as_text(self, context: Dict[str, Any]) -> str:
        """Formats the retrieved JSON data into structured Markdown for injection into LLM prompts."""
        lines = ["Here is the retrieved context from the PricePilot database:"]

        # Products
        if context["products"]:
            lines.append("\n### Products:")
            for p in context["products"]:
                price_info = ""
                prices = p.get("productPrices", []) or []
                if prices:
                    price_info = ", ".join([f"Seller {pr.get('sellerName', 'Unknown')}: current price {pr.get('currentPrice')}, original price {pr.get('originalPrice')}, discount {pr.get('discountPercentage')}%" for pr in prices])
                else:
                    price_info = f"Min Price: {p.get('currentMinPrice')}, Max Discount: {p.get('discountPercentage')}%"
                
                lines.append(f"- Product: '{p.get('name')}' | ID: {p.get('id')} | Brand: {p.get('brand')} | Category: {p.get('category')} | Price details: [{price_info}]")

        # Analytics
        if context["analytics"]:
            lines.append("\n### Product Analytics:")
            for pid, anal in context["analytics"].items():
                lines.append(f"- Product ID {pid} Analytics: View Count: {anal.get('viewCount')}, Save Count: {anal.get('saveCount')}, Watchlist Count: {anal.get('watchlistCount')}, Price Change Count: {anal.get('priceChangeCount')}")

        # Price History
        if context["price_history"]:
            lines.append("\n### Price History:")
            for ph in context["price_history"]:
                lines.append(f"- Date: {ph.get('recordedAt') or ph.get('timestamp') or 'N/A'} | Price: {ph.get('price')} | Seller ID: {ph.get('sellerId') or 'N/A'}")

        # Saved Products
        if context["saved_products"]:
            lines.append("\n### User Saved Products:")
            for s in context["saved_products"]:
                prod = s.get("product") or s
                lines.append(f"- Saved Product: '{prod.get('name')}' | ID: {prod.get('id')} | Price: {prod.get('currentMinPrice') or prod.get('price')}")

        # Watchlists
        if context["watchlists"]:
            lines.append("\n### User Watchlists:")
            for w in context["watchlists"]:
                prod = w.get("product") or w
                lines.append(f"- Watchlist ID: {w.get('id')} | Target Price: {w.get('targetPrice')} | Product: '{prod.get('name')}' | Current Min Price: {prod.get('currentMinPrice')}")

        # Trending
        if context["trending_products"]:
            lines.append("\n### Trending Products:")
            for tp in context["trending_products"]:
                lines.append(f"- Product: '{tp.get('name')}' | ID: {tp.get('id')} | Category: {tp.get('category')} | Current Price: {tp.get('currentMinPrice')}")

        # Recommendations
        if context["recommendations"]:
            lines.append("\n### Recommendations:")
            for r in context["recommendations"]:
                lines.append(f"- Product: '{r.get('name')}' | ID: {r.get('id')} | Current Price: {r.get('currentMinPrice')}")

        # Dashboard
        if context["dashboard"]:
            db = context["dashboard"]
            lines.append(f"\n### User Dashboard Summary:")
            lines.append(f"- Total Saved Products: {db.get('totalSavedProducts', 0)}")
            lines.append(f"- Active Watchlists: {db.get('activeWatchlists', 0)}")
            lines.append(f"- Price Drop Alerts: {db.get('priceDropAlerts', 0)}")
            if db.get("recentSavings"):
                lines.append(f"- Recent Savings: {db.get('recentSavings')}")

        if len(lines) == 1:
            lines.append("No relevant context found in database for this query.")

        return "\n".join(lines)

rag_pipeline = RetrievalPipeline()
