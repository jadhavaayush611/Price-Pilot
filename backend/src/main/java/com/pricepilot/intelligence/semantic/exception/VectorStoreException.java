package com.pricepilot.intelligence.semantic.exception;

/**
 * Thrown when vector storage operations (upsert, delete, similarity search) encounter an error.
 */
public class VectorStoreException extends SemanticIntelligenceException {

    public VectorStoreException(String message) {
        super(message);
    }

    public VectorStoreException(String message, Throwable cause) {
        super(message, cause);
    }
}
