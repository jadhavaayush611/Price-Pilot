package com.pricepilot.intelligence.semantic.model;

import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

/**
 * Immutable domain record representing an entity embedding combined with its vector and metadata.
 */
public final class EmbeddingRecord implements Serializable {

    private static final long serialVersionUID = 1L;

    private final UUID id;
    private final EmbeddingMetadata metadata;
    private final EmbeddingVector vector;

    public EmbeddingRecord(UUID id, EmbeddingMetadata metadata, EmbeddingVector vector) {
        this.id = id != null ? id : UUID.randomUUID();
        this.metadata = Objects.requireNonNull(metadata, "Embedding metadata cannot be null");
        this.vector = Objects.requireNonNull(vector, "Embedding vector cannot be null");

        if (metadata.getDimension() != vector.dimension()) {
            throw new IllegalArgumentException(String.format(
                    "Metadata dimension (%d) does not match vector dimension (%d)",
                    metadata.getDimension(), vector.dimension()
            ));
        }
    }

    public static Builder builder() {
        return new Builder();
    }

    public UUID getId() {
        return id;
    }

    public EmbeddingMetadata getMetadata() {
        return metadata;
    }

    public EmbeddingVector getVector() {
        return vector;
    }

    public String getEntityType() {
        return metadata.getEntityType();
    }

    public String getEntityId() {
        return metadata.getEntityId();
    }

    public String getModelName() {
        return metadata.getModelName();
    }

    public String getModelVersion() {
        return metadata.getModelVersion();
    }

    public int getDimension() {
        return metadata.getDimension();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        EmbeddingRecord that = (EmbeddingRecord) o;
        return Objects.equals(id, that.id) &&
                Objects.equals(metadata, that.metadata) &&
                Objects.equals(vector, that.vector);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, metadata, vector);
    }

    @Override
    public String toString() {
        return "EmbeddingRecord{" +
                "id=" + id +
                ", metadata=" + metadata +
                ", vector=" + vector +
                '}';
    }

    public static final class Builder {
        private UUID id;
        private EmbeddingMetadata metadata;
        private EmbeddingVector vector;

        public Builder id(UUID id) {
            this.id = id;
            return this;
        }

        public Builder metadata(EmbeddingMetadata metadata) {
            this.metadata = metadata;
            return this;
        }

        public Builder vector(EmbeddingVector vector) {
            this.vector = vector;
            return this;
        }

        public EmbeddingRecord build() {
            return new EmbeddingRecord(id, metadata, vector);
        }
    }
}
