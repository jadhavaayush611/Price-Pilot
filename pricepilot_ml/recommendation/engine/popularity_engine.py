from typing import List, Dict, Any, Optional
import pandas as pd
from pricepilot_ml.recommendation.engine.base import BaseRecommendationEngine
from pricepilot_ml.recommendation.explainability.explanations import RecommendationExplainer
from pricepilot_ml.recommendation.persistence.model_store import ModelStore

class PopularityRecommendationEngine(BaseRecommendationEngine):
    """Popularity-Based Recommendation Engine."""

    def __init__(
        self,
        model_store: ModelStore,
        df_user_features: Optional[pd.DataFrame] = None,
        df_product_features: Optional[pd.DataFrame] = None
    ) -> None:
        self.model_store = model_store
        self.explainer = RecommendationExplainer(df_user_features, df_product_features)
        self.df_user_features = df_user_features
        self.df_product_features = df_product_features
        self._model = None

    def _get_model(self, df_products: pd.DataFrame):
        if self._model is not None:
            return self._model
        try:
            self._model = self.model_store.load_model(self.get_algorithm_name())
        except Exception:
            # Fallback inline fitting
            from pricepilot_ml.recommendation.models.popularity import PopularityRecommender
            self._model = PopularityRecommender()
            self._model.fit(df_products)
        return self._model

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

        model = self._get_model(df_products)
        predictions = model.predict(user_id, df_products, limit=limit)

        recommendations = []
        for pid, score in predictions:
            # Find product info
            p_match = df_products[df_products["id"].astype(str) == pid] if "id" in df_products.columns else df_products[df_products["productId"].astype(str) == pid]
            p_info = p_match.iloc[0].to_dict() if not p_match.empty else {}
            
            explanation = self.explainer.explain(
                user_id=user_id,
                product_id=pid,
                score=score,
                algorithm=self.get_algorithm_name(),
                product_info=p_info
            )
            recommendations.append(explanation)

        return recommendations

    def get_algorithm_name(self) -> str:
        return "Popularity"
