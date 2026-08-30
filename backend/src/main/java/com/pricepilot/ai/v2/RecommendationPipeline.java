package com.pricepilot.ai.v2;

import com.pricepilot.intelligence.recommendation.dto.RecommendationResponse;
import com.pricepilot.intelligence.recommendation.dto.RecommendationType;
import com.pricepilot.product.dto.ProductResponseDTO;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Pipeline contract for orchestrating candidate retrieval, strategy scoring,
 * evidence extraction, confidence calculation, and explanation generation in Engine v2.
 */
public interface RecommendationPipeline {

    /**
     * Executes the v2 recommendation pipeline for a given product or user context.
     *
     * @param targetProductId Optional target product ID.
     * @param userId Optional authenticated user ID.
     * @param limit Maximum recommendations to return.
     * @param context Additional pipeline parameters (e.g. recommendationType).
     * @return Structured RecommendationResponse with explainable details.
     */
    RecommendationResponse executePipeline(UUID targetProductId, UUID userId, int limit, Map<String, Object> context);

    /**
     * Executes comparison-based recommendation pipeline directly for candidate products.
     *
     * @param candidates List of candidate product DTOs.
     * @param recommendationType Strategy goal (e.g. Best Overall, Best Value, etc.).
     * @param userId Optional authenticated user ID.
     * @param context Additional pipeline parameters.
     * @return Structured RecommendationResponse with explainable details.
     */
    RecommendationResponse executeComparisonPipeline(
            List<ProductResponseDTO> candidates,
            RecommendationType recommendationType,
            UUID userId,
            Map<String, Object> context
    );
}
