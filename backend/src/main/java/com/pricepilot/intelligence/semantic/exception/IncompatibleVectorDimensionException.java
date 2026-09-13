package com.pricepilot.intelligence.semantic.exception;

/**
 * Thrown when attempting an operation on vectors with mismatched dimensions.
 */
public class IncompatibleVectorDimensionException extends SemanticIntelligenceException {

    public IncompatibleVectorDimensionException(String message) {
        super(message);
    }

    public IncompatibleVectorDimensionException(int expected, int actual) {
        super(String.format("Incompatible vector dimension: expected %d, but got %d", expected, actual));
    }
}
