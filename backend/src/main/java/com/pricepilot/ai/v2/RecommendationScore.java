package com.pricepilot.ai.v2;

import java.util.Map;
import java.util.UUID;

/**
 * Value object representing detailed score calculation results from Recommendation Engine v2.
 */
public record RecommendationScore(
        UUID productId,
        double totalScore,
        double priceScore,
        double relevanceScore,
        double reliabilityScore,
        Map<String, Double> factorWeights
) {}
