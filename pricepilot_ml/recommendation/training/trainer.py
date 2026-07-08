import os
import json
from datetime import datetime, timezone
from typing import Dict, Any, Tuple
import pandas as pd

from pricepilot_ml.recommendation.training.dataset_builder import DatasetBuilder
from pricepilot_ml.recommendation.persistence.model_store import ModelStore
from pricepilot_ml.recommendation.evaluation.offline import OfflineEvaluator
from pricepilot_ml.recommendation.models.popularity import PopularityRecommender
from pricepilot_ml.recommendation.models.content_based import ContentBasedRecommender
from pricepilot_ml.recommendation.models.collaborative import CollaborativeFilteringRecommender
from pricepilot_ml.recommendation.models.hybrid import HybridRecommender
from pricepilot_ml.recommendation.engine.popularity_engine import PopularityRecommendationEngine
from pricepilot_ml.recommendation.engine.content_engine import ContentBasedRecommendationEngine
from pricepilot_ml.recommendation.engine.collaborative_engine import CollaborativeFilteringEngine
from pricepilot_ml.recommendation.engine.hybrid_engine import HybridRecommendationEngine

class Trainer:
    """Coordinates dataset preparation, model training, evaluation, and persistence."""

    def __init__(self, base_dir: str = ".") -> None:
        self.base_dir = os.path.abspath(base_dir)
        self.dataset_builder = DatasetBuilder(base_dir=self.base_dir)
        self.model_store = ModelStore(base_dir=self.base_dir)

    def train_and_evaluate(self, dataset_version: str = "1.0.0", k: int = 10) -> Dict[str, Any]:
        """Runs the entire training, evaluation, and persistence pipeline."""
        # 1. Build/load datasets
        df_prod, df_user, df_int, df_rec = self.dataset_builder.build(dataset_version=dataset_version)

        # 2. Setup train/test splits for evaluation
        evaluator = OfflineEvaluator(df_products=df_prod, df_interactions=df_int, test_ratio=0.2)
        train_df, test_ground_truth = evaluator.train_test_split()

        # 3. Instantiate base models
        pop_model = PopularityRecommender()
        content_model = ContentBasedRecommender()
        collab_model = CollaborativeFilteringRecommender()
        
        # Fit on full data for persistence
        pop_model.fit(df_prod)
        content_model.fit(df_prod, df_int)
        collab_model.fit(df_int)
        
        hybrid_weights = {"popularity": 0.20, "content": 0.35, "collaborative": 0.45}
        hybrid_model = HybridRecommender(pop_model, content_model, collab_model, weights=hybrid_weights)

        # 4. Instantiate engines for evaluation
        pop_engine = PopularityRecommendationEngine(self.model_store, df_user, df_prod)
        pop_engine._model = pop_model
        
        content_engine = ContentBasedRecommendationEngine(self.model_store, df_user, df_prod)
        content_engine._model = content_model
        
        collab_engine = CollaborativeFilteringEngine(self.model_store, df_user, df_prod)
        collab_engine._model = collab_model
        
        hybrid_engine = HybridRecommendationEngine(self.model_store, df_user, df_prod, weights=hybrid_weights)
        hybrid_engine._model = hybrid_model

        # 5. Evaluate engines using the train-test split
        eval_reports = {}
        eval_reports["popularity"] = evaluator.evaluate(pop_engine, train_df, test_ground_truth, k=k)
        eval_reports["content"] = evaluator.evaluate(content_engine, train_df, test_ground_truth, k=k)
        eval_reports["collaborative"] = evaluator.evaluate(collab_engine, train_df, test_ground_truth, k=k)
        eval_reports["hybrid"] = evaluator.evaluate(hybrid_engine, train_df, test_ground_truth, k=k)

        # 6. Persist models and their evaluation reports
        self.model_store.persist(
            pop_model,
            algorithm="Popularity",
            dataset_version=dataset_version,
            feature_version="1.0.0",
            evaluation_metrics=eval_reports["popularity"],
            configuration={}
        )

        self.model_store.persist(
            content_model,
            algorithm="Content",
            dataset_version=dataset_version,
            feature_version="1.0.0",
            evaluation_metrics=eval_reports["content"],
            configuration={}
        )

        self.model_store.persist(
            collab_model,
            algorithm="Collaborative",
            dataset_version=dataset_version,
            feature_version="1.0.0",
            evaluation_metrics=eval_reports["collaborative"],
            configuration={"weights": collab_model.weights}
        )

        self.model_store.persist(
            hybrid_model,
            algorithm="Hybrid",
            dataset_version=dataset_version,
            feature_version="1.0.0",
            evaluation_metrics=eval_reports["hybrid"],
            configuration={"weights": hybrid_weights}
        )

        # 7. Generate aggregate report
        timestamp = datetime.now(timezone.utc).isoformat().replace("+00:00", "Z")
        final_report = {
            "datasetVersion": dataset_version,
            "trainedAt": timestamp,
            "metrics": eval_reports
        }
        
        report_path = os.path.join(self.base_dir, "models", "recommendation", "training_report.json")
        with open(report_path, "w") as f:
            json.dump(final_report, f, indent=4)
            
        return final_report
