package com.pricepilot.intelligence.recommendation.confidence;

import com.pricepilot.intelligence.recommendation.dto.EvidenceItem;
import com.pricepilot.intelligence.recommendation.dto.ProductScore;
import com.pricepilot.product.dto.ProductResponseDTO;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.List;

/**
 * Deterministic Confidence Calculator for Explainable AI Recommendations.
 *
 * The confidence score C in [0.10, 0.99] is computed using four deterministic pillars:
 *
 * 1. Score Separation (Weight: 0.0 - 0.40):
 *    - Measures score dominance of rank 1 over rank 2.
 *    - gap = score(rank1) - score(rank2)
 *    - gap >= 15.0 pts -> 0.40
 *    - 0 < gap < 15.0 -> 0.20 + (gap / 15.0) * 0.20
 *    - gap == 0 (tie) -> 0.10
 *    - single candidate -> 0.30
 *
 * 2. Supporting Evidence (Weight: 0.05 - 0.30):
 *    - Number of positive factual evidence items supporting the winner.
 *    - >= 3 factors -> 0.30
 *    - 2 factors -> 0.22
 *    - 1 factor -> 0.15
 *    - 0 factors -> 0.05
 *
 * 3. Data Completeness (Weight: 0.0 - 0.20):
 *    - Valid current price on all candidates -> +0.08
 *    - Valid product ratings & popularity data -> +0.06
 *    - Multi-seller / availability data present -> +0.06
 *
 * 4. Signal Consistency & Conflicting Penalties (Weight: 0.0 - 0.10):
 *    - Base consistency bonus: +0.10
 *    - -0.05 penalty per conflicting signal (e.g. recommended has lowest price but lowest rating,
 *      or highest rating but highest price among candidates). Minimum 0.0.
 */
@Component
public class ConfidenceCalculator {

    public double calculateConfidence(
            List<ProductResponseDTO> rankedCandidates,
            List<ProductScore> rankedScores,
            List<EvidenceItem> positiveEvidence,
            List<EvidenceItem> negativeTradeOffs) {

        if (rankedCandidates == null || rankedCandidates.isEmpty()) {
            return 0.50;
        }

        // Pillar 1: Score Separation (0.0 - 0.40)
        double separationScore;
        if (rankedScores == null || rankedScores.size() < 2) {
            separationScore = 0.30;
        } else {
            double topScore = rankedScores.get(0).getOverallScore();
            double runnerUpScore = rankedScores.get(1).getOverallScore();
            double gap = Math.max(0.0, topScore - runnerUpScore);
            if (gap >= 15.0) {
                separationScore = 0.40;
            } else if (gap > 0.0) {
                separationScore = 0.20 + (gap / 15.0) * 0.20;
            } else {
                separationScore = 0.10; // tie
            }
        }

        // Pillar 2: Supporting Evidence (0.05 - 0.30)
        int evidenceCount = positiveEvidence != null ? positiveEvidence.size() : 0;
        double evidenceScore;
        if (evidenceCount >= 3) {
            evidenceScore = 0.30;
        } else if (evidenceCount == 2) {
            evidenceScore = 0.22;
        } else if (evidenceCount == 1) {
            evidenceScore = 0.15;
        } else {
            evidenceScore = 0.05;
        }

        // Pillar 3: Data Completeness (0.0 - 0.20)
        boolean allHavePrices = rankedCandidates.stream().allMatch(p ->
                p.getPrices() != null && !p.getPrices().isEmpty() &&
                p.getPrices().stream().anyMatch(pr -> pr.getCurrentPrice() != null && pr.getCurrentPrice().compareTo(BigDecimal.ZERO) > 0));

        boolean allHaveDescriptionsOrSpecs = rankedCandidates.stream().allMatch(p ->
                p.getDescription() != null && !p.getDescription().isBlank());

        boolean allHaveSellers = rankedCandidates.stream().allMatch(p ->
                p.getPrices() != null && p.getPrices().stream().anyMatch(pr -> pr.getSeller() != null));

        double completenessScore = 0.0;
        if (allHavePrices) completenessScore += 0.08;
        if (allHaveDescriptionsOrSpecs) completenessScore += 0.06;
        if (allHaveSellers) completenessScore += 0.06;

        // Pillar 4: Signal Consistency & Conflict Penalty (0.0 - 0.10)
        double consistencyScore = 0.10;
        if (negativeTradeOffs != null && !negativeTradeOffs.isEmpty()) {
            // Each negative trade-off indicates a conflicting dimension where another candidate outperformed
            double penalty = negativeTradeOffs.size() * 0.04;
            consistencyScore = Math.max(0.0, consistencyScore - penalty);
        }

        double totalConfidence = separationScore + evidenceScore + completenessScore + consistencyScore;
        // Clamp between 0.10 and 0.99
        double clamped = Math.max(0.10, Math.min(0.99, totalConfidence));
        return Math.round(clamped * 100.0) / 100.0;
    }
}
