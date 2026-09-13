package com.pricepilot.intelligence.semantic.exception;

/**
 * Thrown when embedding input text is invalid, empty, or exceeds safety boundaries.
 */
public class InvalidEmbeddingInputException extends SemanticIntelligenceException {

    public InvalidEmbeddingInputException(String message) {
        super(message);
    }

    public InvalidEmbeddingInputException(String message, Throwable cause) {
        super(message, cause);
    }
}
