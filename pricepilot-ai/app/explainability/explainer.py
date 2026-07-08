import os
import sys
from typing import List, Dict, Any, Optional
from app.utils.logger import log_structured
import logging

# Ensure workspace root is in sys.path
workspace_root = os.path.abspath(os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "..", ".."))
if workspace_root not in sys.path:
    sys.path.insert(0, workspace_root)

try:
    from pricepilot_ml.recommendation.explainability.explanations import RecommendationExplainer
    logger_msg = "Successfully imported RecommendationExplainer from pricepilot_ml"
    log_structured(logging.INFO, "import_explainer_success", {"message": logger_msg})
except ImportError:
    RecommendationExplainer = None
    logger_msg = "Could not import RecommendationExplainer from pricepilot_ml, using local fallback"
    log_structured(logging.WARNING, "import_explainer_fallback", {"message": logger_msg})

class ExplainabilityService:
    """Explains recommendation scores using predefined business reasoning rules."""

    def __init__(self):
        # Instantiate the imported explainer if available
        if RecommendationExplainer is not None:
            self._explainer = RecommendationExplainer()
        else:
            self._explainer = None

    def explain(
        self,
        user_id: str,
        product_id: str,
        score: float,
        algorithm: str,
        product_info: Dict[str, Any],
        user_info: Optional[Dict[str, Any]] = None,
        interaction_history: Optional[List[Dict[str, Any]]] = None
    ) -> List[str]:
        """Generates a list of reasons explaining why this product was recommended."""
        # 1. Try to use pricepilot_ml's explainer
        if self._explainer is not None:
            try:
                # Map keys to make them compatible with explanations.py
                prod_mapped = {**product_info}
                # explanations.py looks for currentMinPrice, discount_ratio, viewCount, etc.
                if "discountPercentage" in prod_mapped and "discount_ratio" not in prod_mapped:
                    prod_mapped["discount_ratio"] = float(prod_mapped["discountPercentage"]) / 100.0
                
                # Format user info if provided
                user_mapped = {}
                if user_info:
                    user_mapped = {
                        "minPriceViewed": user_info.get("minPrice"),
                        "maxPriceViewed": user_info.get("maxPrice"),
                        "preferredCategory": list(user_info.get("preferredCategories", {}).keys())[0] if user_info.get("preferredCategories") else "Unknown",
                        "preferredBrand": list(user_info.get("preferredBrands", {}).keys())[0] if user_info.get("preferredBrands") else "Unknown"
                    }

                result = self._explainer.explain(
                    user_id=user_id,
                    product_id=product_id,
                    score=score,
                    algorithm=algorithm,
                    interaction_history=interaction_history,
                    product_info=prod_mapped,
                    user_info=user_mapped
                )
                return result.get("reasons", [])
            except Exception as e:
                log_structured(logging.ERROR, "ml_explain_error", {"error": str(e)})

        # 2. Local fallback implementation if import failed or threw an exception
        reasons = []
        prod_cat = product_info.get("category")
        prod_brand = product_info.get("brand")
        price = product_info.get("currentMinPrice") or product_info.get("price", 0)
        discount = product_info.get("discountPercentage", 0)

        if user_info:
            pref_cats = user_info.get("preferredCategories", {})
            pref_brands = user_info.get("preferredBrands", {})
            min_p = user_info.get("minPrice")
            max_p = user_info.get("maxPrice")

            if prod_cat and prod_cat in pref_cats:
                reasons.append(f"Matches your preferred category: {prod_cat}")
            if prod_brand and prod_brand in pref_brands:
                reasons.append(f"Matches your preferred brand: {prod_brand}")
            if min_p is not None and max_p is not None:
                if min_p <= price <= max_p:
                    reasons.append("Within your preferred price range")

        if discount > 15.0:
            reasons.append(f"Offers a significant discount of {int(discount)}%")

        views = product_info.get("viewCount", 0)
        if views > 100:
            reasons.append("Highly popular with many views recently")

        # Fallback default reasons
        if not reasons:
            algo_lower = algorithm.lower()
            if "popularity" in algo_lower:
                reasons.append("Trending product popular among other users")
            elif "collaborative" in algo_lower:
                reasons.append("Recommended based on users with similar interests")
            elif "content" in algo_lower:
                reasons.append("Matches characteristics of items you like")
            else:
                reasons.append("Recommended product you might like")

        return reasons

explainability_service = ExplainabilityService()
