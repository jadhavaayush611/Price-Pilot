package com.pricepilot.intelligence.semantic.exception;

/**
 * Base unchecked exception for semantic intelligence domain operations.
 */
public class SemanticIntelligenceException extends RuntimeException {

    public SemanticIntelligenceException(String message) {
        super(message);
    }

    public SemanticIntelligenceException(String message, Throwable cause) {
        super(message, cause);
    }
}
