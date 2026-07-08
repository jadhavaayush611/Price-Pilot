import pandas as pd
from typing import List, Dict, Any, Tuple, Optional

class PopularityRecommender:
    """Ranks products based on aggregate popularity metrics: view count, save count, watchlist count, trending score."""
    
    def __init__(
        self,
        view_weight: float = 1.0,
        save_weight: float = 5.0,
        watchlist_weight: float = 10.0,
        trending_weight: float = 2.0
    ) -> None:
        self.view_weight = view_weight
        self.save_weight = save_weight
        self.watchlist_weight = watchlist_weight
        self.trending_weight = trending_weight
        self.product_scores_ = {}

    def fit(self, df_products: pd.DataFrame) -> None:
        """Computes popularity scores for all products in the database."""
        self.product_scores_ = {}
        if df_products.empty:
            return

        for _, row in df_products.iterrows():
            pid = str(row.get("id") or row.get("productId"))
            views = float(row.get("viewCount", 0.0))
            saves = float(row.get("saveCount", 0.0))
            watchlists = float(row.get("watchlistCount", 0.0))
            trending = float(row.get("trendingScore", 0.0))
            
            score = (views * self.view_weight +
                     saves * self.save_weight +
                     watchlists * self.watchlist_weight +
                     trending * self.trending_weight)
            self.product_scores_[pid] = score

    def predict(self, user_id: str, df_products: pd.DataFrame, limit: int = 10) -> List[Tuple[str, float]]:
        """Predicts popularity scores for items. Identical recommendations across all users."""
        scored = []
        for _, row in df_products.iterrows():
            pid = str(row.get("id") or row.get("productId"))
            score = self.product_scores_.get(pid, 0.0)
            scored.append((pid, score))
            
        scored.sort(key=lambda x: x[1], reverse=True)
        return scored[:limit]
