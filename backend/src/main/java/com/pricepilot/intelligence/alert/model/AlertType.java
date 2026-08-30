package com.pricepilot.intelligence.alert.model;

/**
 * Deterministic alert classification types for smart watchlists.
 */
public enum AlertType {
    PRICE_DROP,
    PRICE_TARGET_REACHED,
    HISTORICAL_LOW_REACHED,
    GOOD_DEAL_DETECTED,
    PRICE_INCREASE,
    PRICE_RECOVERY,
    BACK_IN_STOCK
}
