from typing import List, Dict, Any, Optional
import pandas as pd
from pricepilot_ml.recommendation.engine.base import BaseRecommendationEngine
from pricepilot_ml.recommendation.explainability.explanations import RecommendationExplainer
from pricepilot_ml.recommendation.persistence.model_store import ModelStore

class HybridRecommendationEngine(BaseRecommendationEngine):
    """Hybrid Recommendation Engine that leverages Pop, Content, and Collab filtering."""

    def __init__(
        self,
        model_store: ModelStore,
        df_user_features: Optional[pd.DataFrame] = None,
        df_product_features: Optional[pd.DataFrame] = None,
        weights: Optional[Dict[str, float]] = None
    ) -> None:
        self.model_store = model_store
        self.explainer = RecommendationExplainer(df_user_features, df_product_features)
        self.df_user_features = df_user_features
        self.df_product_features = df_product_features
        self.weights = weights
        self._model = None

    def _get_model(self, df_products: pd.DataFrame, df_interactions: Optional[pd.DataFrame] = None):
        if self._model is not None:
            return self._model
        try:
            self._model = self.model_store.load_model(self.get_algorithm_name())
        except Exception:
            # Fallback: recreate submodels and build hybrid recommender
            from pricepilot_ml.recommendation.models.popularity import PopularityRecommender
            from pricepilot_ml.recommendation.models.content_based import ContentBasedRecommender
            from pricepilot_ml.recommendation.models.collaborative import CollaborativeFilteringRecommender
            from pricepilot_ml.recommendation.models.hybrid import HybridRecommender
            
            pop = PopularityRecommender()
            content = ContentBasedRecommender()
            collab = CollaborativeFilteringRecommender()
            
            pop.fit(df_products)
            content.fit(df_products, df_interactions)
            if df_interactions is not None:
                collab.fit(df_interactions)
                
            self._model = HybridRecommender(pop, content, collab, self.weights)
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

        model = self._get_model(df_products, df_interactions)
        predictions = model.predict(user_id, df_products, limit=limit)

        recommendations = []
        for pid, score in predictions:
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
        return "Hybrid"
