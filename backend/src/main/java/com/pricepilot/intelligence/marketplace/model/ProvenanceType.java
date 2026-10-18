package com.pricepilot.intelligence.marketplace.model;

/**
 * Origin classification of offer data.
 * Used to distinguish live verified feeds from cached, stale, demo/seed, or user-submitted data.
 */
public enum ProvenanceType {
    LIVE,
    CACHED,
    STALE,
    DEMO,
    USER_SUBMITTED,
    UNKNOWN
}
