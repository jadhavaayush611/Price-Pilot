package com.pricepilot.intelligence.analytics.model;

/**
 * Statistical price volatility classification based on coefficient of variation (CV = sigma / mu).
 */
public enum PriceVolatility {
    LOW,
    MEDIUM,
    HIGH,
    INSUFFICIENT_DATA
}
