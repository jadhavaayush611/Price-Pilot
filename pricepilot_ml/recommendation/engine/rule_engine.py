from typing import List, Dict, Any, Optional
import pandas as pd
from pricepilot_ml.recommendation.engine.base import BaseRecommendationEngine
from pricepilot_ml.recommendation.explainability.explanations import RecommendationExplainer

class RuleBasedRecommendationEngine(BaseRecommendationEngine):
    """Rule-Based Recommendation Engine mimicking the production fallback logic."""

    def __init__(self, df_user_features: Optional[pd.DataFrame] = None, df_product_features: Optional[pd.DataFrame] = None) -> None:
        self.explainer = RecommendationExplainer(df_user_features, df_product_features)
        self.df_user_features = df_user_features
        self.df_product_features = df_product_features

    def recommend(
        self,
        user_id: str,
        df_products: pd.DataFrame,
        limit: int = 10,
        df_interactions: Optional[pd.DataFrame] = None,
        **kwargs
    ) -> List[Dict[str, Any]]:
        user_id = str(user_id)
        if df_products.empty:
            return []

        # Find user preference
        user_pref = {}
        if self.df_user_features is not None and not self.df_user_features.empty:
            match = self.df_user_features[self.df_user_features["userId"].astype(str) == user_id]
            if not match.empty:
                user_pref = match.iloc[0].to_dict()

        # Score products
        scored_products = []
        for _, row in df_products.iterrows():
            pid = str(row.get("id") or row.get("productId"))
            score = 0.0
            
            # A. Category Match
            user_cat = user_pref.get("preferredCategory")
            prod_cat = row.get("category")
            if user_cat and prod_cat and user_cat != "Unknown" and user_cat == prod_cat:
                score += 10.0
                
            # B. Brand Match
            user_brand = user_pref.get("preferredBrand")
            prod_brand = row.get("brand")
            if user_brand and prod_brand and user_brand != "Unknown" and user_brand == prod_brand:
                score += 8.0
                
            # C. Price range match
            price = row.get("currentMinPrice") or row.get("price")
            min_p = user_pref.get("minPriceViewed")
            max_p = user_pref.get("maxPriceViewed")
            if price is not None and min_p is not None and max_p is not None:
                try:
                    price_f = float(price)
                    min_pf = float(min_p)
                    max_pf = float(max_p)
                    if min_pf <= price_f <= max_pf:
                        score += 15.0
                except (ValueError, TypeError):
                    pass

            # D. Trending Score
            trending = float(row.get("trendingScore", 0.0))
            score += trending * 0.1
            
            # E. View Count
            views = float(row.get("viewCount", 0.0))
            score += views * 0.05
            
            # F. Discount Percentage
            discount = float(row.get("discount_ratio", 0.0))
            score += discount * 50.0

            scored_products.append((pid, score, row.to_dict()))

        # Sort and limit
        scored_products.sort(key=lambda x: x[1], reverse=True)
        top_products = scored_products[:limit]

        # Generate explanations
        recommendations = []
        for pid, score, p_info in top_products:
            explanation = self.explainer.explain(
                user_id=user_id,
                product_id=pid,
                score=score,
                algorithm=self.get_algorithm_name(),
                product_info=p_info,
                user_info=user_pref
            )
            recommendations.append(explanation)

        return recommendations

    def get_algorithm_name(self) -> str:
        return "RuleBased"
