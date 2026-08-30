package com.pricepilot.intelligence.analytics.model;

/**
 * Conservative deterministic purchasing signal derived from verifiable historical data.
 */
public enum PurchaseSignal {
    BUY_NOW,
    GOOD_TIME,
    WAIT,
    NEUTRAL,
    INSUFFICIENT_DATA
}
