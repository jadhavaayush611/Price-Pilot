package com.pricepilot.ai.v2;

import com.pricepilot.product.ProductEntity;

import java.util.UUID;

/**
 * Interface for generating natural language explanations for product recommendations.
 * Implementations in Phase 2 will interface with LLMs and prompt engines.
 */
public interface ExplanationGenerator {

    /**
     * Generates explanation for a recommendation score.
     *
     * @param targetProduct Target product.
     * @param recommendedProduct Recommended product.
     * @param score Score details calculated by pipeline.
     * @return AI explanation result.
     */
    RecommendationExplanation generateExplanation(ProductEntity targetProduct, ProductEntity recommendedProduct, RecommendationScore score);
}
