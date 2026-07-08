import numpy as np
import pandas as pd
from typing import List, Dict, Any, Tuple, Optional

class CollaborativeFilteringRecommender:
    """Collaborative Filtering Recommender using a User-Product Interaction Matrix."""
    
    def __init__(self, interaction_weights: Optional[Dict[str, float]] = None) -> None:
        self.weights = interaction_weights or {
            "PRODUCT_VIEW": 1.0,
            "SELLER_CLICK": 2.0,
            "PRODUCT_SAVE": 3.0,
            "WATCHLIST_ADD": 4.0
        }
        self.user_item_matrix_ = None
        self.user_similarity_ = None
        self.user_to_idx_ = {}
        self.idx_to_user_ = {}
        self.item_to_idx_ = {}
        self.idx_to_item_ = {}
        self.raw_matrix_ = None

    def fit(self, df_interactions: pd.DataFrame) -> None:
        """Constructs user-product matrix and calculates cosine similarities between users."""
        if df_interactions.empty:
            return
            
        df = df_interactions.copy()
        df["score"] = df["interactionType"].map(self.weights).fillna(1.0)
        
        # Aggregate weights per user-product
        df_agg = df.groupby(["userId", "productId"])["score"].sum().reset_index()
        
        unique_users = df_agg["userId"].unique()
        unique_items = df_agg["productId"].unique()
        
        self.user_to_idx_ = {str(u): i for i, u in enumerate(unique_users)}
        self.idx_to_user_ = {i: str(u) for i, u in enumerate(unique_users)}
        self.item_to_idx_ = {str(item): i for i, item in enumerate(unique_items)}
        self.idx_to_item_ = {i: str(item) for i, item in enumerate(unique_items)}
        
        n_users = len(unique_users)
        n_items = len(unique_items)
        R = np.zeros((n_users, n_items))
        
        for _, row in df_agg.iterrows():
            u_idx = self.user_to_idx_[str(row["userId"])]
            i_idx = self.item_to_idx_[str(row["productId"])]
            R[u_idx, i_idx] = row["score"]
            
        self.raw_matrix_ = R
        
        # Compute user similarity using Cosine Similarity
        norms = np.linalg.norm(R, axis=1, keepdims=True)
        norms[norms == 0] = 1e-9
        R_norm = R / norms
        self.user_similarity_ = np.dot(R_norm, R_norm.T)

    def predict(self, user_id: str, df_products: pd.DataFrame, limit: int = 10) -> List[Tuple[str, float]]:
        """Predicts recommendation scores for products using user similarity scores."""
        user_id = str(user_id)
        if user_id not in self.user_to_idx_ or self.user_similarity_ is None:
            return []
            
        u_idx = self.user_to_idx_[user_id]
        
        sim_scores = self.user_similarity_[u_idx].copy()
        sim_scores[u_idx] = 0.0  # Zero out self-similarity
        
        # Multiply similarities by user interactions
        pred_scores = np.dot(sim_scores, self.raw_matrix_)
        
        sum_sim = np.sum(np.abs(sim_scores))
        if sum_sim > 0:
            pred_scores /= sum_sim
            
        recommendations = []
        for i_idx, score in enumerate(pred_scores):
            item_id = self.idx_to_item_[i_idx]
            recommendations.append((item_id, float(score)))
            
        # Filter to products present in df_products
        valid_product_ids = set(df_products["id"].astype(str) if "id" in df_products.columns else df_products["productId"].astype(str))
        recommendations = [r for r in recommendations if r[0] in valid_product_ids]
        
        # Sort by predicted score
        recommendations.sort(key=lambda x: x[1], reverse=True)
        return recommendations[:limit]
