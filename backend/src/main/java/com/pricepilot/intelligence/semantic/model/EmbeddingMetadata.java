package com.pricepilot.intelligence.semantic.model;

import java.io.Serializable;
import java.time.Instant;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Metadata descriptor associated with a stored semantic embedding vector.
 * Encapsulates entity provenance, model versioning, and dimension specifications.
 */
public final class EmbeddingMetadata implements Serializable {

    private static final long serialVersionUID = 1L;

    private final String entityType;
    private final String entityId;
    private final String modelName;
    private final String modelVersion;
    private final int dimension;
    private final Map<String, String> attributes;
    private final Instant createdAt;
    private final Instant updatedAt;

    public EmbeddingMetadata(
            String entityType,
            String entityId,
            String modelName,
            String modelVersion,
            int dimension,
            Map<String, String> attributes,
            Instant createdAt,
            Instant updatedAt) {
        this.entityType = Objects.requireNonNull(entityType, "entityType cannot be null").trim().toUpperCase();
        this.entityId = Objects.requireNonNull(entityId, "entityId cannot be null").trim();
        this.modelName = Objects.requireNonNull(modelName, "modelName cannot be null").trim();
        this.modelVersion = Objects.requireNonNull(modelVersion, "modelVersion cannot be null").trim();
        if (dimension <= 0) {
            throw new IllegalArgumentException("dimension must be positive: " + dimension);
        }
        this.dimension = dimension;
        this.attributes = attributes != null ? Collections.unmodifiableMap(new HashMap<>(attributes)) : Collections.emptyMap();
        this.createdAt = createdAt != null ? createdAt : Instant.now();
        this.updatedAt = updatedAt != null ? updatedAt : this.createdAt;
    }

    public static Builder builder() {
        return new Builder();
    }

    public String getEntityType() {
        return entityType;
    }

    public String getEntityId() {
        return entityId;
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

    public Map<String, String> getAttributes() {
        return attributes;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        EmbeddingMetadata that = (EmbeddingMetadata) o;
        return dimension == that.dimension &&
                Objects.equals(entityType, that.entityType) &&
                Objects.equals(entityId, that.entityId) &&
                Objects.equals(modelName, that.modelName) &&
                Objects.equals(modelVersion, that.modelVersion) &&
                Objects.equals(attributes, that.attributes);
    }

    @Override
    public int hashCode() {
        return Objects.hash(entityType, entityId, modelName, modelVersion, dimension, attributes);
    }

    @Override
    public String toString() {
        return "EmbeddingMetadata{" +
                "entityType='" + entityType + '\'' +
                ", entityId='" + entityId + '\'' +
                ", modelName='" + modelName + '\'' +
                ", modelVersion='" + modelVersion + '\'' +
                ", dimension=" + dimension +
                ", attributesCount=" + attributes.size() +
                ", createdAt=" + createdAt +
                ", updatedAt=" + updatedAt +
                '}';
    }

    public static final class Builder {
        private String entityType;
        private String entityId;
        private String modelName;
        private String modelVersion;
        private int dimension;
        private Map<String, String> attributes = new HashMap<>();
        private Instant createdAt;
        private Instant updatedAt;

        public Builder entityType(String entityType) {
            this.entityType = entityType;
            return this;
        }

        public Builder entityId(String entityId) {
            this.entityId = entityId;
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

        public Builder dimension(int dimension) {
            this.dimension = dimension;
            return this;
        }

        public Builder attributes(Map<String, String> attributes) {
            this.attributes = attributes != null ? new HashMap<>(attributes) : new HashMap<>();
            return this;
        }

        public Builder addAttribute(String key, String value) {
            if (this.attributes == null) {
                this.attributes = new HashMap<>();
            }
            this.attributes.put(key, value);
            return this;
        }

        public Builder createdAt(Instant createdAt) {
            this.createdAt = createdAt;
            return this;
        }

        public Builder updatedAt(Instant updatedAt) {
            this.updatedAt = updatedAt;
            return this;
        }

        public EmbeddingMetadata build() {
            return new EmbeddingMetadata(entityType, entityId, modelName, modelVersion, dimension, attributes, createdAt, updatedAt);
        }
    }
}
