package com.pricepilot.intelligence.discovery.ranking;

import com.pricepilot.intelligence.discovery.interpretation.InterpretedQuery;

import java.util.Comparator;
import java.util.List;

/**
 * Strategy interface for scoring product candidates against an interpreted user query.
 */
public interface SearchRelevanceStrategy {

    /**
     * Scores a candidate against the interpreted query.
     *
     * @param candidate product candidate
     * @param query interpreted search query
     */
    void scoreCandidate(ScoredProductCandidate candidate, InterpretedQuery query);

    /**
     * Provides deterministic comparator for tie-breaking.
     */
    Comparator<ScoredProductCandidate> getDeterministicComparator();
}
