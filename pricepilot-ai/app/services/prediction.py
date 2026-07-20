import pandas as pd
import numpy as np
from typing import List, Dict, Any, Tuple
from app.schemas.recommendation import PredictRequest, PredictResponse, ScoredRecommendation, SimilarRequest, SimilarResponse
from app.loaders.model_registry import model_registry
from app.explainability.explainer import explainability_service
from app.utils.logger import log_structured
import logging

class PredictionService:
    def predict_recommendations(self, request: PredictRequest) -> PredictResponse:
        user_id = str(request.userId)
        algorithm = request.algorithm.lower().strip()
        limit = request.limit

        # 1. Convert candidates to Pandas DataFrame for models
        candidate_data = []
        for c in request.candidates:
            c_dict = c.model_dump(by_alias=True)
            # Add mapping for fields that might be missing or differently named
            c_dict["id"] = c.id
            c_dict["productId"] = c.id
            # Calculate discount ratio if not present
            c_dict["discount_ratio"] = float(c.discountPercentage or 0) / 100.0
            candidate_data.append(c_dict)
            
        df_candidates = pd.DataFrame(candidate_data)

        # 2. Retrieve model
        # If requested algorithm is collaborative/content/hybrid, but model is not loaded, fall back
        model = model_registry.get_model(algorithm)
        active_algorithm = request.algorithm

        if model is None:
            log_structured(logging.WARNING, "requested_model_missing_fallback", {
                "requested_algorithm": request.algorithm,
                "fallback_algorithm": "Popularity"
            })
            model = model_registry.get_model("popularity")
            active_algorithm = "Popularity"
            
        if model is None:
            # Absolute fallback if even Popularity model is not loaded (e.g. brand new deploy with no models)
            # We will use simple inline popularity rules
            log_structured(logging.ERROR, "no_models_available_inline_fallback")
            predictions = self._inline_popularity_predict(df_candidates, limit)
            active_algorithm = "Popularity (Fallback)"
        else:
            # 3. Dynamic injection of interactions for online personalization
            if request.interactions and hasattr(model, "user_history_"):
                # For ContentBasedRecommender, inject the current user history dynamically
                interacted_pids = [str(i.productId) for i in request.interactions]
                model.user_history_[user_id] = interacted_pids
                log_structured(logging.INFO, "dynamic_user_history_injected", {
                    "userId": user_id,
                    "interaction_count": len(interacted_pids)
                })

            # Check if collaborative model can score this user
            if algorithm == "collaborative" and hasattr(model, "user_to_idx_"):
                if user_id not in model.user_to_idx_:
                    log_structured(logging.WARNING, "collaborative_cold_start_fallback", {
                        "userId": user_id,
                        "fallback": "Popularity"
                    })
                    # Fall back to Popularity model
                    model = model_registry.get_model("popularity")
                    active_algorithm = "Popularity"

            # Check if hybrid model can score collaborative for this user
            if algorithm == "hybrid" and hasattr(model, "collaborative_model"):
                collab = model.collaborative_model
                if hasattr(collab, "user_to_idx_") and user_id not in collab.user_to_idx_:
                    # Collaborative part of hybrid has cold-start, hybrid model itself handles this
                    # by redistributing weights to Content and Popularity.
                    pass

            try:
                # 4. Generate predictions
                predictions = model.predict(user_id, df_candidates, limit=len(df_candidates))
            except Exception as e:
                log_structured(logging.ERROR, "prediction_error", {"error": str(e), "algorithm": active_algorithm})
                # Fall back to inline popularity if predictions crash
                predictions = self._inline_popularity_predict(df_candidates, limit)
                active_algorithm = "Popularity (Fallback)"

        # 5. Build scored recommendations and explain them
        recommendations = []
        user_info = request.userProfile.model_dump() if request.userProfile else None
        
        # Parse interactions for history in explainer
        interaction_history = [i.model_dump() for i in request.interactions] if request.interactions else []

        # Pre-build candidate map for O(1) lookup instead of O(N) DataFrame filtering inside loop
        candidate_map = {str(c["id"]): c for c in candidate_data}

        max_score = 0.0
        for pid, score in predictions:
            # Find candidate info O(1)
            product_info = candidate_map.get(str(pid))
            if not product_info:
                continue
            
            reasons = explainability_service.explain(
                user_id=user_id,
                product_id=str(pid),
                score=score,
                algorithm=active_algorithm,
                product_info=product_info,
                user_info=user_info,
                interaction_history=interaction_history
            )
            
            recommendations.append(ScoredRecommendation(
                productId=str(pid),
                score=float(score),
                reasons=reasons
            ))
            if score > max_score:
                max_score = score

        # Sort and limit
        recommendations.sort(key=lambda x: x.score, reverse=True)
        recommendations = recommendations[:limit]

        return PredictResponse(
            algorithm=active_algorithm,
            score=float(max_score),
            recommendations=recommendations
        )

    def predict_similar_products(self, request: SimilarRequest) -> SimilarResponse:
        target_pid = request.targetProductId
        target_product = request.targetProduct
        limit = request.limit

        scored_candidates = []
        target_price = target_product.currentMinPrice or 0.0

        for c in request.candidates:
            if c.id == target_pid:
                continue

            score = 0.0
            reasons = []

            # A. Category Match
            if c.category == target_product.category:
                score += 50.0
                reasons.append(f"Same category: {c.category}")

            # B. Brand Match
            if c.brand and target_product.brand and c.brand != "Unknown" and c.brand == target_product.brand:
                score += 30.0
                reasons.append(f"Same brand: {c.brand}")

            # C. Price Similarity
            cand_price = c.currentMinPrice or 0.0
            if target_price > 0:
                pct_diff = abs(cand_price - target_price) / target_price
                if pct_diff <= 0.1:
                    score += 20.0
                    reasons.append("Very similar price range")
                elif pct_diff <= 0.2:
                    score += 10.0
                    reasons.append("Similar price range")
                elif pct_diff <= 0.3:
                    score += 5.0
                    reasons.append("Slightly similar price range")

            # D. Trending Score (Analytics)
            if c.trendingScore:
                score += c.trendingScore * 0.05

            # E. Discount
            if c.discountPercentage:
                score += c.discountPercentage * 0.3
                if c.discountPercentage > 15:
                    reasons.append(f"Offers a high discount of {int(c.discountPercentage)}%")

            # Ensure we have at least one explanation reason
            if not reasons:
                reasons.append("Alternative product suggestion")

            scored_candidates.append(ScoredRecommendation(
                productId=c.id,
                score=float(score),
                reasons=reasons
            ))

        # Sort and limit
        scored_candidates.sort(key=lambda x: x.score, reverse=True)
        scored_candidates = scored_candidates[:limit]

        return SimilarResponse(
            targetProductId=target_pid,
            similarProducts=scored_candidates
        )

    def _inline_popularity_predict(self, df_candidates: pd.DataFrame, limit: int) -> List[Tuple[str, float]]:
        """In-memory popularity fallback scoring when no models are loaded."""
        predictions = []
        for _, row in df_candidates.iterrows():
            pid = str(row.get("id"))
            views = float(row.get("viewCount", 0.0))
            saves = float(row.get("saveCount", 0.0))
            watchlists = float(row.get("watchlistCount", 0.0))
            trending = float(row.get("trendingScore", 0.0))
            
            # Simple weighted sum
            score = views * 1.0 + saves * 5.0 + watchlists * 10.0 + trending * 2.0
            predictions.append((pid, score))
            
        predictions.sort(key=lambda x: x[1], reverse=True)
        return predictions[:limit]

prediction_service = PredictionService()
