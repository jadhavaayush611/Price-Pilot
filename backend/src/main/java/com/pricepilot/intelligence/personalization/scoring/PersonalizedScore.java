package com.pricepilot.intelligence.personalization.scoring;

import com.pricepilot.intelligence.recommendation.dto.EvidenceItem;
import lombok.Builder;

import java.io.Serializable;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Immutable domain representation of a product candidate's personalized scoring result.
 * Contains base score, bounded personalization adjustment, final clamped score, explainability breakdown, and evidence.
 * <p>
 * Invariant: When personalization context is empty, adjustment == 0.0 and finalScore == clamped(baseScore).
 * Personalization never alters underlying product facts or candidate eligibility.
 */
public final class PersonalizedScore implements Serializable {

    private final double baseScore;
    private final double personalizationAdjustment;
    private final double finalScore;
    private final Map<String, Double> breakdown;
    private final List<EvidenceItem> positiveEvidence;
    private final List<EvidenceItem> tradeOffs;
    private final boolean eligible;

    @Builder
    public PersonalizedScore(
            double baseScore,
            double personalizationAdjustment,
            double finalScore,
            Map<String, Double> breakdown,
            List<EvidenceItem> positiveEvidence,
            List<EvidenceItem> tradeOffs,
            Boolean eligible) {

        this.baseScore = sanitizeNumber(baseScore, 75.0);
        this.personalizationAdjustment = sanitizeNumber(personalizationAdjustment, 0.0);
        this.finalScore = sanitizeNumber(finalScore, this.baseScore);
        this.breakdown = breakdown != null ? Map.copyOf(breakdown) : Collections.emptyMap();
        this.positiveEvidence = positiveEvidence != null ? List.copyOf(positiveEvidence) : Collections.emptyList();
        this.tradeOffs = tradeOffs != null ? List.copyOf(tradeOffs) : Collections.emptyList();
        this.eligible = eligible != null ? eligible : true;
    }

    /**
     * Creates a neutral personalized score with zero adjustment and identical base/final scores.
     *
     * @param baseScore the original deterministic base score
     * @return an immutable PersonalizedScore with zero personalization contribution
     */
    public static PersonalizedScore neutral(double baseScore) {
        double sanitized = sanitizeNumber(baseScore, 75.0);
        double clamped = Math.max(0.0, Math.min(100.0, sanitized));
        return new PersonalizedScore(
                clamped,
                0.0,
                clamped,
                Collections.emptyMap(),
                Collections.emptyList(),
                Collections.emptyList(),
                true
        );
    }

    private static double sanitizeNumber(double val, double fallback) {
        if (Double.isNaN(val) || Double.isInfinite(val)) {
            return fallback;
        }
        return val;
    }

    public double getBaseScore() {
        return baseScore;
    }

    public double getPersonalizationAdjustment() {
        return personalizationAdjustment;
    }

    public double getPersonalizationContribution() {
        return personalizationAdjustment;
    }

    public double getFinalScore() {
        return finalScore;
    }

    public Map<String, Double> getBreakdown() {
        return breakdown;
    }

    public Map<String, Double> getPersonalizationBreakdown() {
        return breakdown;
    }

    public List<EvidenceItem> getPositiveEvidence() {
        return positiveEvidence;
    }

    public List<EvidenceItem> getPersonalizationEvidence() {
        return positiveEvidence;
    }

    public List<EvidenceItem> getTradeOffs() {
        return tradeOffs;
    }

    public List<EvidenceItem> getPersonalizationTradeOffs() {
        return tradeOffs;
    }

    public boolean isEligible() {
        return eligible;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof PersonalizedScore that)) return false;
        return Double.compare(that.baseScore, baseScore) == 0 &&
                Double.compare(that.personalizationAdjustment, personalizationAdjustment) == 0 &&
                Double.compare(that.finalScore, finalScore) == 0 &&
                eligible == that.eligible &&
                Objects.equals(breakdown, that.breakdown) &&
                Objects.equals(positiveEvidence, that.positiveEvidence) &&
                Objects.equals(tradeOffs, that.tradeOffs);
    }

    @Override
    public int hashCode() {
        return Objects.hash(baseScore, personalizationAdjustment, finalScore, breakdown, positiveEvidence, tradeOffs, eligible);
    }

    @Override
    public String toString() {
        return "PersonalizedScore{" +
                "baseScore=" + baseScore +
                ", adjustment=" + personalizationAdjustment +
                ", finalScore=" + finalScore +
                ", eligible=" + eligible +
                ", breakdown=" + breakdown +
                '}';
    }
}
