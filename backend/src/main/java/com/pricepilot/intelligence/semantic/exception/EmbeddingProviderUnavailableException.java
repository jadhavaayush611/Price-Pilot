package com.pricepilot.intelligence.semantic.exception;

/**
 * Thrown when the configured embedding provider is offline, disabled, or unreachable.
 */
public class EmbeddingProviderUnavailableException extends EmbeddingProviderException {

    public EmbeddingProviderUnavailableException(String message) {
        super(message);
    }

    public EmbeddingProviderUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
