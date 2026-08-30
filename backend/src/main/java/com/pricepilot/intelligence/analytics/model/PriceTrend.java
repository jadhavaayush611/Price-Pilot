package com.pricepilot.intelligence.analytics.model;

/**
 * Deterministic price trajectory direction over historical observation windows.
 */
public enum PriceTrend {
    RISING,
    FALLING,
    STABLE,
    INSUFFICIENT_DATA
}
