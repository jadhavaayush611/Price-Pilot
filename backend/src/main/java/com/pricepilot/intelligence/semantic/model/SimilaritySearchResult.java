package com.pricepilot.intelligence.semantic.model;

import java.io.Serializable;
import java.util.Objects;

/**
 * Result record from a vector similarity search execution.
 */
public final class SimilaritySearchResult implements Serializable, Comparable<SimilaritySearchResult> {

    private static final long serialVersionUID = 1L;

    private final String entityType;
    private final String entityId;
    private final double score;
    private final EmbeddingMetadata metadata;
    private final EmbeddingVector vector;

    public SimilaritySearchResult(
            String entityType,
            String entityId,
            double score,
            EmbeddingMetadata metadata,
            EmbeddingVector vector) {
        this.entityType = Objects.requireNonNull(entityType, "entityType cannot be null");
        this.entityId = Objects.requireNonNull(entityId, "entityId cannot be null");
        this.score = score;
        this.metadata = metadata;
        this.vector = vector;
    }

    public String getEntityType() {
        return entityType;
    }

    public String getEntityId() {
        return entityId;
    }

    public double getScore() {
        return score;
    }

    public EmbeddingMetadata getMetadata() {
        return metadata;
    }

    public EmbeddingVector getVector() {
        return vector;
    }

    /**
     * Orders descending by score, with deterministic tie-breaker on entityId.
     */
    @Override
    public int compareTo(SimilaritySearchResult other) {
        if (other == null) return -1;
        int scoreComp = Double.compare(other.score, this.score);
        if (scoreComp != 0) {
            return scoreComp;
        }
        return this.entityId.compareTo(other.entityId);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        SimilaritySearchResult that = (SimilaritySearchResult) o;
        return Double.compare(that.score, score) == 0 &&
                Objects.equals(entityType, that.entityType) &&
                Objects.equals(entityId, that.entityId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(entityType, entityId, score);
    }

    @Override
    public String toString() {
        return "SimilaritySearchResult{" +
                "entityType='" + entityType + '\'' +
                ", entityId='" + entityId + '\'' +
                ", score=" + score +
                '}';
    }
}
