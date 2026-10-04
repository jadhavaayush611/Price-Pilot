package com.pricepilot.intelligence.assistant.dto;

/**
 * Deterministic classification for shopping assistant product discovery responses.
 */
public enum AssistantMatchClassification {
    EXACT_MATCH,
    CLOSE_MATCHES,
    NO_MATCH,
    CATEGORY_RESULTS
}
