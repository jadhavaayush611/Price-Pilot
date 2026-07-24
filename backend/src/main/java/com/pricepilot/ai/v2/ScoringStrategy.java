package com.pricepilot.ai.v2;

import com.pricepilot.product.ProductEntity;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Strategy interface for scoring candidate products in Recommendation Engine v2.
 * Implementations in Phase 2 will provide hybrid vector-search and ML model scoring.
 */
public interface ScoringStrategy {

    /**
     * Identifies the strategy implementation name.
     */
    String getStrategyName();

    /**
     * Computes recommendation scores for a set of candidate products against a context target.
     *
     * @param targetProductId The target product ID or null if user-centric.
     * @param candidates List of candidate product entities to score.
     * @param context Additional contextual parameters (user preferences, budget, filters).
     * @return Map of Product ID to RecommendationScore.
     */
    Map<UUID, RecommendationScore> calculateScores(UUID targetProductId, List<ProductEntity> candidates, Map<String, Object> context);
}
