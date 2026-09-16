package com.pricepilot.intelligence.personalization.context;

/**
 * Enumerates the supported dimensions and types of personalization signals in PricePilot.
 * Directly corresponds to established explicit preference fields and behavioral affinity dimensions.
 */
public enum PersonalizationSignalType {
    // Explicit preference signals
    CATEGORY_PREFERENCE,
    BRAND_PREFERENCE,
    BUDGET_PREFERENCE,
    RATING_PREFERENCE,
    DEAL_SENSITIVITY,
    PRICE_SENSITIVITY,
    AVAILABILITY_PREFERENCE,

    // Behavioral affinity signals
    CATEGORY_AFFINITY,
    BRAND_AFFINITY,
    BUDGET_AFFINITY,
    DEAL_AFFINITY,
    RATING_AFFINITY,
    INTERACTION_AFFINITY
}
