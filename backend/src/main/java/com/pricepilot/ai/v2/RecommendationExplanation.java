package com.pricepilot.ai.v2;

import java.util.List;
import java.util.UUID;

/**
 * Value object representing an AI-generated explanation for product recommendations.
 */
public record RecommendationExplanation(
        UUID productId,
        String summaryExplanation,
        List<String> keyDecisionDrivers,
        double confidenceScore,
        String modelName
) {}
