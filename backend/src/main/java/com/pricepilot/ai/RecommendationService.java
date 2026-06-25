package com.pricepilot.ai;

import com.pricepilot.product.dto.ProductResponseDTO;
import com.pricepilot.product.dto.PageResponse;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * Interface for the Recommendation Engine.
 * Designed so future ML services can replace it without changing controllers.
 */
public interface RecommendationService {
    
    /**
     * Gets personalized recommendations for a user (simple limit-based).
     *
     * @param userId The ID of the user.
     * @param limit The maximum number of recommendations to return.
     * @return A list of recommended products.
     */
    List<ProductResponseDTO> getPersonalizedRecommendations(UUID userId, int limit);

    /**
     * Gets personalized recommendations for a user with pagination, filtering, and sorting.
     */
    PageResponse<ProductResponseDTO> getPersonalizedRecommendations(
            UUID userId,
            String category,
            String brand,
            BigDecimal minPrice,
            BigDecimal maxPrice,
            String sort,
            int page,
            int size);

    /**
     * Gets similar products based on co-occurrence, characteristics, or vector distance.
     *
     * @param productId The ID of the target product.
     * @param limit The maximum number of similar products to return.
     * @return A list of similar products.
     */
    List<ProductResponseDTO> getSimilarProducts(UUID productId, int limit);
}
