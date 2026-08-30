package com.pricepilot.intelligence.analytics.model;

/**
 * Objective historical price position classification.
 * Independent of seller-promoted discount banners.
 */
public enum DealQuality {
    EXCELLENT_DEAL,
    GOOD_DEAL,
    FAIR_PRICE,
    ABOVE_AVERAGE,
    HIGH_PRICE,
    INSUFFICIENT_DATA
}
