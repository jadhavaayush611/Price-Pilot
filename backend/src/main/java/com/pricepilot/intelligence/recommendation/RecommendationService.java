package com.pricepilot.intelligence.recommendation;

import com.pricepilot.intelligence.recommendation.dto.RecommendationResponse;

import java.util.UUID;

/**
 * Service interface for Shopping Intelligence Recommendation Engine v2.
 */
public interface RecommendationService {

    /**
     * Retrieves AI recommendation matrix for a specific target product.
     *
     * @param productId Target product ID.
     * @param limit Max recommendations.
     * @return Recommendation response dto.
     */
    RecommendationResponse getRecommendationsForProduct(UUID productId, int limit);

    /**
     * Retrieves personalized AI recommendations for an authenticated user.
     *
     * @param userId Authenticated user ID.
     * @param limit Max recommendations.
     * @return Recommendation response dto.
     */
    RecommendationResponse getPersonalizedRecommendations(UUID userId, int limit);
}
