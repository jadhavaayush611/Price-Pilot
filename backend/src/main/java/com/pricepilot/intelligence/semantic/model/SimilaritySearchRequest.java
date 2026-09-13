package com.pricepilot.intelligence.semantic.model;

import java.io.Serializable;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Request parameter object for executing vector similarity searches.
 */
public final class SimilaritySearchRequest implements Serializable {

    private static final long serialVersionUID = 1L;
    private static final int DEFAULT_TOP_K = 10;
    private static final int MAX_TOP_K = 200;

    private final EmbeddingVector queryVector;
    private final String entityType;
    private final String modelName;
    private final String modelVersion;
    private final int topK;
    private final double minScore;
    private final Map<String, String> filterAttributes;

    public SimilaritySearchRequest(
            EmbeddingVector queryVector,
            String entityType,
            String modelName,
            String modelVersion,
            int topK,
            double minScore,
            Map<String, String> filterAttributes) {
        this.queryVector = Objects.requireNonNull(queryVector, "Query vector cannot be null");
        this.entityType = entityType != null ? entityType.trim().toUpperCase() : null;
        this.modelName = Objects.requireNonNull(modelName, "Model name cannot be null").trim();
        this.modelVersion = Objects.requireNonNull(modelVersion, "Model version cannot be null").trim();
        this.topK = Math.max(1, Math.min(topK > 0 ? topK : DEFAULT_TOP_K, MAX_TOP_K));
        this.minScore = Math.max(-1.0, Math.min(1.0, minScore));
        this.filterAttributes = filterAttributes != null ? Collections.unmodifiableMap(new HashMap<>(filterAttributes)) : Collections.emptyMap();
    }

    public static Builder builder() {
        return new Builder();
    }

    public EmbeddingVector getQueryVector() {
        return queryVector;
    }

    public String getEntityType() {
        return entityType;
    }

    public String getModelName() {
        return modelName;
    }

    public String getModelVersion() {
        return modelVersion;
    }

    public int getTopK() {
        return topK;
    }

    public double getMinScore() {
        return minScore;
    }

    public Map<String, String> getFilterAttributes() {
        return filterAttributes;
    }

    public static final class Builder {
        private EmbeddingVector queryVector;
        private String entityType;
        private String modelName;
        private String modelVersion;
        private int topK = DEFAULT_TOP_K;
        private double minScore = 0.0;
        private Map<String, String> filterAttributes = new HashMap<>();

        public Builder queryVector(EmbeddingVector queryVector) {
            this.queryVector = queryVector;
            return this;
        }

        public Builder entityType(String entityType) {
            this.entityType = entityType;
            return this;
        }

        public Builder modelName(String modelName) {
            this.modelName = modelName;
            return this;
        }

        public Builder modelVersion(String modelVersion) {
            this.modelVersion = modelVersion;
            return this;
        }

        public Builder topK(int topK) {
            this.topK = topK;
            return this;
        }

        public Builder minScore(double minScore) {
            this.minScore = minScore;
            return this;
        }

        public Builder filterAttributes(Map<String, String> filterAttributes) {
            this.filterAttributes = filterAttributes != null ? new HashMap<>(filterAttributes) : new HashMap<>();
            return this;
        }

        public Builder addFilter(String key, String value) {
            if (this.filterAttributes == null) {
                this.filterAttributes = new HashMap<>();
            }
            this.filterAttributes.put(key, value);
            return this;
        }

        public SimilaritySearchRequest build() {
            return new SimilaritySearchRequest(queryVector, entityType, modelName, modelVersion, topK, minScore, filterAttributes);
        }
    }
}
