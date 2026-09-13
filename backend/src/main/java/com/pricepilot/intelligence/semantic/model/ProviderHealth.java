package com.pricepilot.intelligence.semantic.model;

import java.io.Serializable;
import java.time.Instant;
import java.util.Objects;

/**
 * Health check report for embedding providers.
 */
public final class ProviderHealth implements Serializable {

    private static final long serialVersionUID = 1L;

    private final boolean healthy;
    private final String providerName;
    private final String modelName;
    private final String modelVersion;
    private final int dimension;
    private final String message;
    private final Instant checkedAt;

    public ProviderHealth(
            boolean healthy,
            String providerName,
            String modelName,
            String modelVersion,
            int dimension,
            String message,
            Instant checkedAt) {
        this.healthy = healthy;
        this.providerName = Objects.requireNonNull(providerName, "providerName cannot be null");
        this.modelName = modelName;
        this.modelVersion = modelVersion;
        this.dimension = dimension;
        this.message = message;
        this.checkedAt = checkedAt != null ? checkedAt : Instant.now();
    }

    public static ProviderHealth up(String providerName, String modelName, String modelVersion, int dimension) {
        return new ProviderHealth(true, providerName, modelName, modelVersion, dimension, "Provider is operational", Instant.now());
    }

    public static ProviderHealth down(String providerName, String message) {
        return new ProviderHealth(false, providerName, null, null, 0, message, Instant.now());
    }

    public boolean isHealthy() {
        return healthy;
    }

    public String getProviderName() {
        return providerName;
    }

    public String getModelName() {
        return modelName;
    }

    public String getModelVersion() {
        return modelVersion;
    }

    public int getDimension() {
        return dimension;
    }

    public String getMessage() {
        return message;
    }

    public Instant getCheckedAt() {
        return checkedAt;
    }

    @Override
    public String toString() {
        return "ProviderHealth{" +
                "healthy=" + healthy +
                ", providerName='" + providerName + '\'' +
                ", modelName='" + modelName + '\'' +
                ", modelVersion='" + modelVersion + '\'' +
                ", dimension=" + dimension +
                ", message='" + message + '\'' +
                ", checkedAt=" + checkedAt +
                '}';
    }
}
