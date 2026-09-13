package com.pricepilot.intelligence.semantic.exception;

/**
 * Thrown when an embedding provider fails during vector generation.
 */
public class EmbeddingProviderException extends SemanticIntelligenceException {

    public EmbeddingProviderException(String message) {
        super(message);
    }

    public EmbeddingProviderException(String message, Throwable cause) {
        super(message, cause);
    }
}
