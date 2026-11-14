package com.pricepilot.ai.stream;

/**
 * Thrown when the dedicated assistant streaming executor queue is saturated.
 * Results in HTTP 503 Service Unavailable with a Retry-After header.
 */
public class StreamCapacityExceededException extends RuntimeException {

    public StreamCapacityExceededException(String message) {
        super(message);
    }

    public StreamCapacityExceededException(String message, Throwable cause) {
        super(message, cause);
    }
}
