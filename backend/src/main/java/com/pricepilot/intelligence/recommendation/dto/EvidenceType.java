package com.pricepilot.intelligence.recommendation.dto;

/**
 * Structured evidence classification types for grounded recommendations and trade-offs.
 */
public enum EvidenceType {
    LOWEST_PRICE,
    PRICE_BELOW_AVERAGE,
    HIGHEST_RATING,
    HIGHEST_DISCOUNT,
    HIGH_SELLER_AVAILABILITY,
    BETTER_SPECIFICATION,
    LOWER_SCORE,
    HIGHER_PRICE,
    LIMITED_AVAILABILITY
}
