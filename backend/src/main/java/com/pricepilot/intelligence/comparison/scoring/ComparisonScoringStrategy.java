package com.pricepilot.intelligence.comparison.scoring;

import com.pricepilot.intelligence.recommendation.dto.ProductScore;
import com.pricepilot.product.dto.ProductResponseDTO;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Modular interface for calculating multi-factor ProductScore breakdown in product matrix comparisons.
 */
public interface ComparisonScoringStrategy {

    /**
     * Calculates modular scoring breakdown for a list of compared products.
     *
     * @param products List of product response DTOs
     * @return Map of product ID to ProductScore
     */
    Map<UUID, ProductScore> calculateScores(List<ProductResponseDTO> products);
}
