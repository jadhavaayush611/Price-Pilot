package com.pricepilot.intelligence.personalization.evidence;

import com.pricepilot.intelligence.recommendation.dto.EvidenceItem;
import lombok.Builder;

import java.io.Serializable;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Immutable value object representing grounded personalized evidence and trade-offs
 * generated to explain a candidate's {@link com.pricepilot.intelligence.personalization.scoring.PersonalizedScore}.
 * <p>
 * Core Invariant:
 * Evidence explains the score; evidence never creates, modifies, or recalculates the score.
 */
public final class PersonalizedEvidence implements Serializable {

    private final UUID productId;
    private final List<EvidenceItem> positiveEvidence;
    private final List<EvidenceItem> tradeOffs;
    private final double netAdjustment;

    @Builder
    public PersonalizedEvidence(
            UUID productId,
            List<EvidenceItem> positiveEvidence,
            List<EvidenceItem> tradeOffs,
            Double netAdjustment) {

        this.productId = productId;
        this.positiveEvidence = positiveEvidence != null ? List.copyOf(positiveEvidence) : Collections.emptyList();
        this.tradeOffs = tradeOffs != null ? List.copyOf(tradeOffs) : Collections.emptyList();
        this.netAdjustment = netAdjustment != null && !Double.isNaN(netAdjustment) && !Double.isInfinite(netAdjustment)
                ? netAdjustment : 0.0;
    }

    public static PersonalizedEvidence empty(UUID productId) {
        return new PersonalizedEvidence(productId, Collections.emptyList(), Collections.emptyList(), 0.0);
    }

    public static PersonalizedEvidence empty() {
        return empty(null);
    }

    public UUID getProductId() {
        return productId;
    }

    public List<EvidenceItem> getPositiveEvidence() {
        return positiveEvidence;
    }

    public List<EvidenceItem> getTradeOffs() {
        return tradeOffs;
    }

    public double getNetAdjustment() {
        return netAdjustment;
    }

    public boolean hasPersonalization() {
        return !positiveEvidence.isEmpty() || !tradeOffs.isEmpty();
    }

    public boolean isEmpty() {
        return positiveEvidence.isEmpty() && tradeOffs.isEmpty();
    }

    public int totalEvidenceCount() {
        return positiveEvidence.size() + tradeOffs.size();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof PersonalizedEvidence that)) return false;
        return Double.compare(that.netAdjustment, netAdjustment) == 0 &&
                Objects.equals(productId, that.productId) &&
                Objects.equals(positiveEvidence, that.positiveEvidence) &&
                Objects.equals(tradeOffs, that.tradeOffs);
    }

    @Override
    public int hashCode() {
        return Objects.hash(productId, positiveEvidence, tradeOffs, netAdjustment);
    }

    @Override
    public String toString() {
        return "PersonalizedEvidence{" +
                "productId=" + productId +
                ", positiveCount=" + positiveEvidence.size() +
                ", tradeOffCount=" + tradeOffs.size() +
                ", netAdjustment=" + netAdjustment +
                '}';
    }
}
