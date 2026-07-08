from typing import List, Dict, Any, Optional
import pandas as pd

class RecommendationExplainer:
    """Generates human-readable explanations for product recommendations independent of the ranking score."""
    
    def __init__(self, df_user_features: Optional[pd.DataFrame] = None, df_product_features: Optional[pd.DataFrame] = None) -> None:
        self.df_user_features = df_user_features
        self.df_product_features = df_product_features

    def explain(
        self,
        user_id: str,
        product_id: str,
        score: float,
        algorithm: str,
        interaction_history: Optional[List[Dict[str, Any]]] = None,
        product_info: Optional[Dict[str, Any]] = None,
        user_info: Optional[Dict[str, Any]] = None
    ) -> Dict[str, Any]:
        """Generates reasons for a recommendation."""
        reasons = []
        user_id = str(user_id)
        product_id = str(product_id)
        
        # 1. Fetch user features/preferences if not passed directly
        user_pref = user_info or {}
        if not user_pref and self.df_user_features is not None and not self.df_user_features.empty:
            match = self.df_user_features[self.df_user_features["userId"].astype(str) == user_id]
            if not match.empty:
                user_pref = match.iloc[0].to_dict()
                
        # 2. Fetch product features if not passed directly
        prod_features = product_info or {}
        if not prod_features and self.df_product_features is not None and not self.df_product_features.empty:
            pid_col = "productId" if "productId" in self.df_product_features.columns else "id"
            match = self.df_product_features[self.df_product_features[pid_col].astype(str) == product_id]
            if not match.empty:
                prod_features = match.iloc[0].to_dict()
                    
        # A. Category matching
        user_cat = user_pref.get("preferredCategory") or user_pref.get("preferred_category")
        prod_cat = prod_features.get("category") or prod_features.get("productCategory")
        if user_cat and prod_cat and user_cat != "Unknown" and user_cat == prod_cat:
            reasons.append(f"Matches your preferred category: {user_cat}")
            
        # B. Brand matching
        user_brand = user_pref.get("preferredBrand") or user_pref.get("preferred_brand")
        prod_brand = prod_features.get("brand") or prod_features.get("productBrand")
        if user_brand and prod_brand and user_brand != "Unknown" and user_brand == prod_brand:
            reasons.append(f"Matches your preferred brand: {user_brand}")
            
        # C. Price range matching
        price = prod_features.get("currentMinPrice") or prod_features.get("current_price") or prod_features.get("price")
        min_p = user_pref.get("minPriceViewed") or user_pref.get("min_price_viewed")
        max_p = user_pref.get("maxPriceViewed") or user_pref.get("max_price_viewed")
        if price is not None and min_p is not None and max_p is not None:
            try:
                price_f, min_pf, max_pf = float(price), float(min_p), float(max_p)
                if min_pf <= price_f <= max_pf and max_pf > 0:
                    reasons.append("Within your preferred price range")
            except (ValueError, TypeError):
                pass
                
        # D. Discount check
        discount = prod_features.get("discount_ratio") or prod_features.get("discountPercentage") or prod_features.get("discount_percentage")
        if discount is not None:
            try:
                discount_f = float(discount)
                if discount_f > 0.15:
                    pct = int(discount_f * 100) if discount_f <= 1.0 else int(discount_f)
                    reasons.append(f"Offers a significant discount of {pct}%")
            except (ValueError, TypeError):
                pass

        # E. Popularity-specific reasons
        views = prod_features.get("viewCount") or prod_features.get("views")
        if views is not None:
            try:
                if float(views) > 100:
                    reasons.append("Highly popular with many views recently")
            except (ValueError, TypeError):
                pass
                
        # F. Interaction history checking
        if interaction_history:
            has_viewed = any(str(item.get("productId")) == product_id and item.get("interactionType") == "PRODUCT_VIEW" for item in interaction_history)
            if has_viewed:
                reasons.append("Based on your recent product views")
            user_interacted_cats = set(item.get("category") for item in interaction_history if item.get("category"))
            if prod_cat in user_interacted_cats:
                reasons.append("Similar to other items you have explored")
                
        # Fallbacks to ensure reasons is never empty
        if not reasons:
            if algorithm.lower() == "popularity":
                reasons.append("Trending product popular among other users")
            elif algorithm.lower() == "collaborative":
                reasons.append("Recommended based on users with similar interests")
            elif algorithm.lower() == "content":
                reasons.append("Matches characteristics of items you like")
            else:
                reasons.append("Recommended product you might like")

        return {
            "productId": product_id,
            "score": round(float(score), 4),
            "algorithm": algorithm,
            "reasons": reasons
        }
