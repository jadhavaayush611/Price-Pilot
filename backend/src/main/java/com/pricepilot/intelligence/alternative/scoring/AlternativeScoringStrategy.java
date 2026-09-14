package com.pricepilot.intelligence.alternative.scoring;

import com.pricepilot.intelligence.alternative.model.AlternativeCandidate;
import com.pricepilot.intelligence.alternative.model.AlternativeType;
import com.pricepilot.intelligence.alternative.model.SourceProductContextDTO;

import java.util.Comparator;

/**
 * Strategy interface for scoring alternative candidates, checking eligibility,
 * synthesizing factual evidence, and providing deterministic tie-breaking.
 */
public interface AlternativeScoringStrategy {

    /**
     * Evaluates eligibility of a candidate for a given alternative type and source context.
     */
    boolean isEligible(AlternativeCandidate candidate, SourceProductContextDTO sourceContext, AlternativeType type);

    /**
     * Scores the candidate, assigns reason codes, builds structured evidence, and creates primary explanation.
     */
    void scoreCandidate(AlternativeCandidate candidate, SourceProductContextDTO sourceContext, AlternativeType type);

    /**
     * Deterministic comparator enforcing strict ranking order:
     * alternativeScore DESC -> semanticSimilarity DESC -> dealQuality DESC -> rating DESC -> price ASC -> productId ASC
     */
    Comparator<AlternativeCandidate> getDeterministicComparator();
}
