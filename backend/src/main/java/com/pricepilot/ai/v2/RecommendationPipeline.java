package com.pricepilot.ai.v2;

import com.pricepilot.product.dto.ProductResponseDTO;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Pipeline contract for orchestrating candidate retrieval, strategy scoring, and explanation generation in Engine v2.
 */
public interface RecommendationPipeline {

    /**
     * Executes the v2 recommendation pipeline for a given product or user context.
     *
     * @param targetProductId Optional target product ID.
     * @param userId Optional authenticated user ID.
     * @param limit Maximum recommendations to return.
     * @param context Additional pipeline parameters.
     * @return Ordered list of recommended products with embedded metadata.
     */
    List<ProductResponseDTO> executePipeline(UUID targetProductId, UUID userId, int limit, Map<String, Object> context);
}
