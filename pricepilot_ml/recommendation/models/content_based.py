import numpy as np
import pandas as pd
from typing import List, Dict, Any, Tuple, Optional

class ContentBasedRecommender:
    """Computes similarity of candidate products to a user's product interaction history."""
    
    def __init__(self) -> None:
        self.product_features_ = None
        self.product_ids_ = []
        self.product_to_idx_ = {}
        self.category_cols_ = []
        self.brand_cols_ = []
        self.num_cols_ = []
        self.user_history_ = {}

    def fit(self, df_products: pd.DataFrame, df_interactions: Optional[pd.DataFrame] = None) -> None:
        """Extracts and normalizes product attributes to fit content features and builds user profiles."""
        if df_products.empty:
            return
            
        df = df_products.copy()
        prod_col = "id" if "id" in df.columns else "productId"
        self.product_ids_ = df[prod_col].astype(str).tolist()
        self.product_to_idx_ = {pid: i for i, pid in enumerate(self.product_ids_)}
        
        # Safe dummy coding for category and brand
        df_encoded = pd.get_dummies(df, columns=["category", "brand"], dummy_na=False, dtype=float)
        
        self.category_cols_ = [c for c in df_encoded.columns if c.startswith("category_")]
        self.brand_cols_ = [c for c in df_encoded.columns if c.startswith("brand_")]
        
        # Numeric features (including popularity metrics and ratings)
        self.num_cols_ = ["currentMinPrice", "discount_ratio", "averageSellerRating", "trendingScore"]
        self.num_cols_ = [c for c in self.num_cols_ if c in df_encoded.columns]
        
        # Normalize numeric features using min-max scaling
        for col in self.num_cols_:
            c_min = df_encoded[col].min()
            c_max = df_encoded[col].max()
            diff = c_max - c_min
            df_encoded[col] = (df_encoded[col] - c_min) / (diff if diff != 0 else 1.0)
            
        feature_cols = self.category_cols_ + self.brand_cols_ + self.num_cols_
        self.product_features_ = df_encoded[feature_cols].fillna(0.0).values
        
        # Build user interaction history profiles
        self.user_history_ = {}
        if df_interactions is not None and not df_interactions.empty:
            user_groups = df_interactions.groupby("userId")
            for user_id, group in user_groups:
                interacted_pids = group["productId"].astype(str).tolist()
                self.user_history_[str(user_id)] = [pid for pid in interacted_pids if pid in self.product_to_idx_]

    def predict(self, user_id: str, df_products: pd.DataFrame, limit: int = 10) -> List[Tuple[str, float]]:
        """Calculates cosine similarity between user's profile and candidate products."""
        user_id = str(user_id)
        if user_id not in self.user_history_ or len(self.user_history_[user_id]) == 0 or self.product_features_ is None:
            # Fallback: if user is cold-start (no interactions), return empty
            return []
            
        # Build user preference vector by taking the mean of all products they have interacted with
        interacted_indices = [self.product_to_idx_[pid] for pid in self.user_history_[user_id] if pid in self.product_to_idx_]
        if not interacted_indices:
            return []
            
        user_vector = np.mean(self.product_features_[interacted_indices], axis=0)
        
        # Cosine similarity calculation using NumPy
        prod_norms = np.linalg.norm(self.product_features_, axis=1)
        prod_norms[prod_norms == 0] = 1e-9
        user_norm = np.linalg.norm(user_vector)
        if user_norm == 0:
            user_norm = 1e-9
            
        scores = np.dot(self.product_features_, user_vector) / (prod_norms * user_norm)
        
        recommendations = []
        for pid, idx in self.product_to_idx_.items():
            # Exclude items user already interacted with
            if pid not in self.user_history_[user_id]:
                recommendations.append((pid, float(scores[idx])))
                
        recommendations.sort(key=lambda x: x[1], reverse=True)
        return recommendations[:limit]
