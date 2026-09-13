package com.pricepilot.intelligence.discovery.hybrid;

import com.pricepilot.product.ProductEntity;

import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

/**
 * Value object encapsulating a product candidate discovered during hybrid retrieval.
 * Holds retrieval ranks, semantic similarity score, provenance, and fused score.
 */
public final class HybridCandidate implements Serializable, Comparable<HybridCandidate> {

    private static final long serialVersionUID = 1L;

    private final ProductEntity product;
    private final CandidateProvenance provenance;
    private final Integer structuredRank;
    private final Integer semanticRank;
    private final Double semanticSimilarityScore;
    private final double fusedScore;

    public HybridCandidate(
            ProductEntity product,
            CandidateProvenance provenance,
            Integer structuredRank,
            Integer semanticRank,
            Double semanticSimilarityScore,
            double fusedScore) {
        this.product = Objects.requireNonNull(product, "product cannot be null");
        this.provenance = Objects.requireNonNull(provenance, "provenance cannot be null");
        this.structuredRank = structuredRank;
        this.semanticRank = semanticRank;
        this.semanticSimilarityScore = semanticSimilarityScore;
        this.fusedScore = fusedScore;
    }

    public ProductEntity getProduct() {
        return product;
    }

    public UUID getProductId() {
        return product.getId();
    }

    public CandidateProvenance getProvenance() {
        return provenance;
    }

    public Integer getStructuredRank() {
        return structuredRank;
    }

    public Integer getSemanticRank() {
        return semanticRank;
    }

    public Double getSemanticSimilarityScore() {
        return semanticSimilarityScore;
    }

    public double getFusedScore() {
        return fusedScore;
    }

    /**
     * Orders descending by fused score, with deterministic tie-breaker on product UUID.
     */
    @Override
    public int compareTo(HybridCandidate other) {
        if (other == null) return -1;
        int scoreComp = Double.compare(other.fusedScore, this.fusedScore);
        if (scoreComp != 0) {
            return scoreComp;
        }
        String id1 = this.getProductId() != null ? this.getProductId().toString() : "";
        String id2 = other.getProductId() != null ? other.getProductId().toString() : "";
        return id1.compareTo(id2);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        HybridCandidate that = (HybridCandidate) o;
        return Objects.equals(getProductId(), that.getProductId());
    }

    @Override
    public int hashCode() {
        return Objects.hash(getProductId());
    }

    @Override
    public String toString() {
        return "HybridCandidate{" +
                "productId=" + getProductId() +
                ", provenance=" + provenance +
                ", fusedScore=" + fusedScore +
                ", semanticSimilarityScore=" + semanticSimilarityScore +
                '}';
    }
}
