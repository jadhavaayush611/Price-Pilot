import numpy as np
import pandas as pd
from typing import List, Dict, Any, Set

class OfflineMetrics:
    """Computes ranking and recommendation list quality metrics for offline evaluation."""
    
    @staticmethod
    def precision_at_k(recommended: List[str], ground_truth: Set[str], k: int) -> float:
        if not recommended or not ground_truth:
            return 0.0
        rec_k = recommended[:k]
        hits = sum(1 for item in rec_k if item in ground_truth)
        return hits / k

    @staticmethod
    def recall_at_k(recommended: List[str], ground_truth: Set[str], k: int) -> float:
        if not recommended or not ground_truth:
            return 0.0
        rec_k = recommended[:k]
        hits = sum(1 for item in rec_k if item in ground_truth)
        return hits / len(ground_truth)

    @staticmethod
    def map_at_k(recommended: List[str], ground_truth: Set[str], k: int) -> float:
        if not recommended or not ground_truth:
            return 0.0
        rec_k = recommended[:k]
        sum_precisions = 0.0
        num_hits = 0
        for i, item in enumerate(rec_k):
            if item in ground_truth:
                num_hits += 1
                sum_precisions += num_hits / (i + 1)
        if num_hits == 0:
            return 0.0
        return sum_precisions / min(len(ground_truth), k)

    @staticmethod
    def ndcg_at_k(recommended: List[str], ground_truth: Set[str], k: int) -> float:
        if not recommended or not ground_truth:
            return 0.0
        rec_k = recommended[:k]
        dcg = 0.0
        for i, item in enumerate(rec_k):
            if item in ground_truth:
                dcg += 1.0 / np.log2(i + 2)
                
        idcg = sum(1.0 / np.log2(i + 2) for i in range(min(len(ground_truth), k)))
        if idcg == 0.0:
            return 0.0
        return dcg / idcg

    @staticmethod
    def coverage(all_recommendations: List[List[str]], catalog: Set[str]) -> float:
        if not catalog:
            return 0.0
        recommended_set = set()
        for rec_list in all_recommendations:
            recommended_set.update(rec_list)
        return len(recommended_set.intersection(catalog)) / len(catalog)

    @staticmethod
    def diversity(recommended: List[str], df_products: pd.DataFrame) -> float:
        """Calculates categorical diversity (1 - average pairwise category match)."""
        if len(recommended) <= 1 or df_products.empty:
            return 1.0
            
        prod_col = "id" if "id" in df_products.columns else "productId"
        p_subset = df_products[df_products[prod_col].astype(str).isin(recommended)]
        
        categories = p_subset["category"].tolist()
        if len(categories) <= 1:
            return 0.0
            
        n = len(categories)
        matches = 0
        total_pairs = 0
        for i in range(n):
            for j in range(i + 1, n):
                total_pairs += 1
                if categories[i] == categories[j]:
                    matches += 1
                    
        return 1.0 - (matches / total_pairs)

    @staticmethod
    def novelty(recommended: List[str], global_popularity: Dict[str, int], total_users: int) -> float:
        """Calculates average novelty: -log2(popularity / total_users)."""
        if not recommended or total_users == 0:
            return 0.0
            
        novelties = []
        for item in recommended:
            pop = global_popularity.get(item, 0)
            # Add small smoothing constant to avoid log(0)
            ratio = max(pop, 1) / total_users
            novelties.append(-np.log2(ratio))
            
        return float(np.mean(novelties))

    @staticmethod
    def popularity_bias(recommended: List[str], global_popularity: Dict[str, int]) -> float:
        """Calculates average global popularity count of recommended items."""
        if not recommended:
            return 0.0
        return float(np.mean([global_popularity.get(item, 0) for item in recommended]))
