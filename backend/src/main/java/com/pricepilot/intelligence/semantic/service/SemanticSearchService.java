package com.pricepilot.intelligence.semantic.service;

import com.pricepilot.intelligence.semantic.model.SimilaritySearchResult;

import java.util.List;

/**
 * Domain-level abstraction for semantic search capabilities across PricePilot entities.
 * Serves as the foundation entrypoint for future natural language search, hybrid discovery,
 * and contextual recommendation ranking.
 */
public interface SemanticSearchService {

    /**
     * Executes semantic similarity search against indexed entities.
     *
     * @param query Raw natural-language or semantic search query
     * @param entityType Target entity domain (e.g. "PRODUCT", "CATEGORY")
     * @param topK Maximum number of results to retrieve
     * @param minScore Minimum semantic similarity threshold
     * @return Ranked list of matched entities
     */
    List<SimilaritySearchResult> search(String query, String entityType, int topK, double minScore);
}
