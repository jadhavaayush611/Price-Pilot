from typing import List, Dict, Any, Tuple, Optional
import pandas as pd

class HybridRecommender:
    """Combines Popularity, Content-Based, and Collaborative Filtering recommendations."""
    
    def __init__(
        self,
        popularity_model: Any,
        content_model: Any,
        collaborative_model: Any,
        weights: Optional[Dict[str, float]] = None
    ) -> None:
        self.popularity_model = popularity_model
        self.content_model = content_model
        self.collaborative_model = collaborative_model
        self.weights = weights or {
            "popularity": 0.20,
            "content": 0.35,
            "collaborative": 0.45
        }

    def predict(self, user_id: str, df_products: pd.DataFrame, limit: int = 10) -> List[Tuple[str, float]]:
        """Averages scores from popularity, content, and collaborative sub-models."""
        user_id = str(user_id)
        
        # 1. Fetch sub-model predictions
        pop_res = self.popularity_model.predict(user_id, df_products, limit=len(df_products))
        content_res = self.content_model.predict(user_id, df_products, limit=len(df_products))
        collab_res = self.collaborative_model.predict(user_id, df_products, limit=len(df_products))
        
        # 2. Normalize prediction outputs for consistent scoring scales
        def normalize_scores(res_list):
            if not res_list:
                return {}
            scores = [r[1] for r in res_list]
            min_s, max_s = min(scores), max(scores)
            diff = max_s - min_s
            if diff == 0:
                return {r[0]: 1.0 for r in res_list}
            return {r[0]: (r[1] - min_s) / diff for r in res_list}
            
        pop_scores = normalize_scores(pop_res)
        content_scores = normalize_scores(content_res)
        collab_scores = normalize_scores(collab_res)
        
        # If collaborative result is empty (cold start), we redistribute its weight to Content & Popularity
        has_collab = len(collab_res) > 0
        w_pop = self.weights.get("popularity", 0.20)
        w_content = self.weights.get("content", 0.35)
        w_collab = self.weights.get("collaborative", 0.45)
        
        if not has_collab:
            total_remaining = w_pop + w_content
            if total_remaining > 0:
                w_pop = w_pop / total_remaining
                w_content = w_content / total_remaining
                w_collab = 0.0
        
        hybrid_scores = []
        for _, row in df_products.iterrows():
            pid = str(row.get("id") or row.get("productId"))
            
            s_pop = pop_scores.get(pid, 0.0)
            s_content = content_scores.get(pid, 0.0)
            s_collab = collab_scores.get(pid, 0.0) if has_collab else 0.0
            
            score = w_pop * s_pop + w_content * s_content + w_collab * s_collab
            hybrid_scores.append((pid, score))
            
        hybrid_scores.sort(key=lambda x: x[1], reverse=True)
        return hybrid_scores[:limit]
