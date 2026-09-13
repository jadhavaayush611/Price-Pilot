package com.pricepilot.intelligence.semantic.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Externalized configuration properties for the PricePilot Semantic Intelligence Foundation.
 */
@Component
@ConfigurationProperties(prefix = "pricepilot.intelligence.semantic")
public class SemanticIntelligenceProperties {

    /**
     * Master toggle enabling or disabling semantic intelligence features.
     */
    private boolean enabled = true;

    /**
     * Embedding provider identifier (e.g. "local-deterministic", "local", "fastapi-ai").
     */
    private String provider = "local-deterministic";

    /**
     * Embedding model identifier name.
     */
    private String modelName = "local-hash-embedding";

    /**
     * Embedding model version string.
     */
    private String modelVersion = "v1";

    /**
     * Dimensionality of generated vector embeddings.
     */
    private int dimension = 64;

    /**
     * Maximum timeout in milliseconds for vector embedding generation.
     */
    private long timeoutMs = 5000;

    /**
     * Maximum allowable batch size for embedding operations.
     */
    private int batchSize = 32;

    /**
     * Default minimum cosine similarity score threshold (0.0 to 1.0).
     */
    private double minSimilarityScore = 0.0;

    /**
     * Maximum character length allowed for untrusted input text.
     */
    private int maxInputLength = 4096;

    public SemanticIntelligenceProperties() {
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getProvider() {
        return provider;
    }

    public void setProvider(String provider) {
        this.provider = provider;
    }

    public String getModelName() {
        return modelName;
    }

    public void setModelName(String modelName) {
        this.modelName = modelName;
    }

    public String getModelVersion() {
        return modelVersion;
    }

    public void setModelVersion(String modelVersion) {
        this.modelVersion = modelVersion;
    }

    public int getDimension() {
        return dimension;
    }

    public void setDimension(int dimension) {
        this.dimension = dimension;
    }

    public long getTimeoutMs() {
        return timeoutMs;
    }

    public void setTimeoutMs(long timeoutMs) {
        this.timeoutMs = timeoutMs;
    }

    public int getBatchSize() {
        return batchSize;
    }

    public void setBatchSize(int batchSize) {
        this.batchSize = batchSize;
    }

    public double getMinSimilarityScore() {
        return minSimilarityScore;
    }

    public void setMinSimilarityScore(double minSimilarityScore) {
        this.minSimilarityScore = minSimilarityScore;
    }

    public int getMaxInputLength() {
        return maxInputLength;
    }

    public void setMaxInputLength(int maxInputLength) {
        this.maxInputLength = maxInputLength;
    }
}
