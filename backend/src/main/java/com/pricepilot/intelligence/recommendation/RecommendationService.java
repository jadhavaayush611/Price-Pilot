package com.pricepilot.intelligence.recommendation;

import com.pricepilot.intelligence.recommendation.dto.RecommendationCompareRequest;
import com.pricepilot.intelligence.recommendation.dto.RecommendationResponse;
import com.pricepilot.intelligence.recommendation.dto.RecommendationType;

import java.util.UUID;

/**
 * Service interface for Shopping Intelligence Recommendation Engine v2.
 */
public interface RecommendationService {

    /**
     * Retrieves AI recommendation matrix for a specific target product with default strategy.
     *
     * @param productId Target product ID.
     * @param limit Max recommendations.
     * @return Recommendation response dto.
     */
    default RecommendationResponse getRecommendationsForProduct(UUID productId, int limit) {
        return getRecommendationsForProduct(productId, limit, RecommendationType.BEST_OVERALL);
    }

    /**
     * Retrieves AI recommendation matrix for a specific target product with custom strategy.
     *
     * @param productId Target product ID.
     * @param limit Max recommendations.
     * @param type Recommendation strategy type.
     * @return Recommendation response dto.
     */
    RecommendationResponse getRecommendationsForProduct(UUID productId, int limit, RecommendationType type);

    /**
     * Compares candidate products and returns explainable recommendations.
     *
     * @param request Comparison request containing 2-5 product IDs and optional strategy.
     * @param userId Optional authenticated user ID.
     * @return Recommendation response dto.
     */
    RecommendationResponse compareAndRecommend(RecommendationCompareRequest request, UUID userId);

    /**
     * Retrieves personalized AI recommendations for an authenticated user.
     *
     * @param userId Authenticated user ID.
     * @param limit Max recommendations.
     * @return Recommendation response dto.
     */
    RecommendationResponse getPersonalizedRecommendations(UUID userId, int limit);
}
