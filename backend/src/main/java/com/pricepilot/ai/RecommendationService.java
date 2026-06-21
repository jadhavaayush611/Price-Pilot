package com.pricepilot.ai;

import com.pricepilot.product.dto.ProductResponseDTO;
import java.util.List;
import java.util.UUID;

/**
 * Interface for the future Recommendation Engine.
 * Intended to be implemented using collaborative filtering / hybrid recommender pipelines,
 * integrating with downstream Python/FastAPI ML services.
 */
public interface RecommendationService {
    
    /**
     * Gets personalized recommendations for a user.
     *
     * @param userId The ID of the user.
     * @param limit The maximum number of recommendations to return.
     * @return A list of recommended products.
     */
    List<ProductResponseDTO> getPersonalizedRecommendations(UUID userId, int limit);

    /**
     * Gets similar products based on co-occurrence, characteristics, or vector distance.
     *
     * @param productId The ID of the target product.
     * @param limit The maximum number of similar products to return.
     * @return A list of similar products.
     */
    List<ProductResponseDTO> getSimilarProducts(UUID productId, int limit);
}
