package com.pricepilot.intelligence.semantic.exception;

/**
 * Thrown when embedding generation exceeds the configured timeout threshold.
 */
public class EmbeddingTimeoutException extends EmbeddingProviderException {

    public EmbeddingTimeoutException(String message) {
        super(message);
    }

    public EmbeddingTimeoutException(String message, Throwable cause) {
        super(message, cause);
    }
}
