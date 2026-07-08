import os
import json
import pandas as pd
import numpy as np
from datetime import datetime, timezone
from typing import Dict, Any, List, Set, Tuple
from pricepilot_ml.recommendation.evaluation.metrics import OfflineMetrics

class OfflineEvaluator:
    """Orchestrates the offline evaluation of recommendation engines using train-test splits."""

    def __init__(self, df_products: pd.DataFrame, df_interactions: pd.DataFrame, test_ratio: float = 0.2) -> None:
        self.df_products = df_products
        self.df_interactions = df_interactions
        self.test_ratio = test_ratio
        self.catalog = set(df_products["id"].astype(str).tolist() if "id" in df_products.columns else df_products["productId"].astype(str).tolist())
        self.global_popularity = df_interactions["productId"].astype(str).value_counts().to_dict()
        self.total_users = df_interactions["userId"].nunique()

    def train_test_split(self) -> Tuple[pd.DataFrame, Dict[str, Set[str]]]:
        """Splits interaction history into training DataFrame and testing ground truth mapping."""
        if self.df_interactions.empty:
            return pd.DataFrame(), {}
            
        # Chronological split if timestamp is available
        if "createdAt" in self.df_interactions.columns:
            df = self.df_interactions.sort_values("createdAt")
            split_idx = int(len(df) * (1 - self.test_ratio))
            train_df = df.iloc[:split_idx].copy()
            test_df = df.iloc[split_idx:].copy()
        else:
            # Random split
            df = self.df_interactions.sample(frac=1.0, random_state=42)
            split_idx = int(len(df) * (1 - self.test_ratio))
            train_df = df.iloc[:split_idx].copy()
            test_df = df.iloc[split_idx:].copy()

        # Build testing ground truth dictionary: userId -> Set of productIds they interacted with
        test_ground_truth = {}
        for _, row in test_df.iterrows():
            uid = str(row["userId"])
            pid = str(row["productId"])
            if uid not in test_ground_truth:
                test_ground_truth[uid] = set()
            test_ground_truth[uid].add(pid)

        return train_df, test_ground_truth

    def evaluate(self, engine: Any, train_df: pd.DataFrame, test_ground_truth: Dict[str, Set[str]], k: int = 10) -> Dict[str, Any]:
        """Evaluates a recommendation engine using OfflineMetrics."""
        # 1. Fit engine on training interactions
        if hasattr(engine, "_model") and engine._model is not None:
            if hasattr(engine._model, "fit"):
                # Re-fit with training split
                if "Collaborative" in engine.get_algorithm_name():
                    engine._model.fit(train_df)
                elif "Content" in engine.get_algorithm_name():
                    engine._model.fit(self.df_products, train_df)
                elif "Hybrid" in engine.get_algorithm_name():
                    engine._model.popularity_model.fit(self.df_products)
                    engine._model.content_model.fit(self.df_products, train_df)
                    engine._model.collaborative_model.fit(train_df)
                    
        # 2. Generate recommendations for test users
        precisions = []
        recalls = []
        maps = []
        ndcgs = []
        diversities = []
        novelties = []
        pop_biases = []
        all_recs = []

        test_users = list(test_ground_truth.keys())
        for user_id in test_users:
            ground_truth = test_ground_truth[user_id]
            
            # Recommend
            recs_explained = engine.recommend(user_id, self.df_products, limit=k, df_interactions=train_df)
            recommended = [rec["productId"] for rec in recs_explained]
            all_recs.append(recommended)

            precisions.append(OfflineMetrics.precision_at_k(recommended, ground_truth, k))
            recalls.append(OfflineMetrics.recall_at_k(recommended, ground_truth, k))
            maps.append(OfflineMetrics.map_at_k(recommended, ground_truth, k))
            ndcgs.append(OfflineMetrics.ndcg_at_k(recommended, ground_truth, k))
            
            if recommended:
                diversities.append(OfflineMetrics.diversity(recommended, self.df_products))
                novelties.append(OfflineMetrics.novelty(recommended, self.global_popularity, self.total_users))
                pop_biases.append(OfflineMetrics.popularity_bias(recommended, self.global_popularity))

        # 3. Calculate aggregate statistics
        report = {
            "algorithm": engine.get_algorithm_name(),
            "k": k,
            "precisionAtK": float(np.mean(precisions)) if precisions else 0.0,
            "recallAtK": float(np.mean(recalls)) if recalls else 0.0,
            "mapAtK": float(np.mean(maps)) if maps else 0.0,
            "ndcgAtK": float(np.mean(ndcgs)) if ndcgs else 0.0,
            "coverage": float(OfflineMetrics.coverage(all_recs, self.catalog)),
            "diversity": float(np.mean(diversities)) if diversities else 0.0,
            "novelty": float(np.mean(novelties)) if novelties else 0.0,
            "popularityBias": float(np.mean(pop_biases)) if pop_biases else 0.0,
            "timestamp": datetime.now(timezone.utc).isoformat().replace("+00:00", "Z")
        }
        return report
