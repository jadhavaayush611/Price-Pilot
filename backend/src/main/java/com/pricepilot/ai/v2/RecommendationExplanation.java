package com.pricepilot.ai.v2;

import java.util.Collections;
import java.util.List;
import java.util.UUID;

/**
 * Value object representing an AI-generated or deterministic explanation for product recommendations.
 */
public record RecommendationExplanation(
        UUID productId,
        String summaryExplanation,
        List<String> keyDecisionDrivers,
        List<String> tradeOffs,
        double confidenceScore,
        String modelName,
        String explanationStrategy
) {
    public RecommendationExplanation(UUID productId, String summaryExplanation, List<String> keyDecisionDrivers, double confidenceScore, String modelName) {
        this(productId, summaryExplanation, keyDecisionDrivers, Collections.emptyList(), confidenceScore, modelName, "DETERMINISTIC_RULE_BASED");
    }
}
