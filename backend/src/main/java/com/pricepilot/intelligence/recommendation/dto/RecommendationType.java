package com.pricepilot.intelligence.recommendation.dto;

/**
 * Supported recommendation strategy types for explainable product recommendations.
 */
public enum RecommendationType {
    BEST_OVERALL("Best Overall"),
    BEST_VALUE("Best Price / Value"),
    HIGHEST_RATED("Highest Rated"),
    BEST_DISCOUNT("Best Discount");

    private final String displayName;

    RecommendationType(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }

    public static RecommendationType fromString(String value) {
        if (value == null || value.trim().isEmpty()) {
            return BEST_OVERALL;
        }
        String clean = value.trim().toUpperCase().replace("-", "_").replace(" ", "_");
        for (RecommendationType type : values()) {
            if (type.name().equals(clean)) {
                return type;
            }
        }
        if (clean.contains("VALUE") || clean.contains("PRICE")) {
            return BEST_VALUE;
        }
        if (clean.contains("RATE") || clean.contains("RATING")) {
            return HIGHEST_RATED;
        }
        if (clean.contains("DISCOUNT") || clean.contains("DEAL") || clean.contains("SALE")) {
            return BEST_DISCOUNT;
        }
        return BEST_OVERALL;
    }
}
