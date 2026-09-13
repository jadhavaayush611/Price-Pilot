package com.pricepilot.intelligence.semantic.service;

import com.pricepilot.intelligence.semantic.model.SimilaritySearchRequest;
import com.pricepilot.intelligence.semantic.model.SimilaritySearchResult;

import java.util.List;
import java.util.Map;

/**
 * Service for performing similarity and semantic vector search queries.
 */
public interface VectorSearchService {

    /**
     * Executes similarity search with a pre-computed vector.
     */
    List<SimilaritySearchResult> searchByVector(SimilaritySearchRequest request);

    /**
     * Embeds a query string and executes similarity search against stored embeddings.
     */
    List<SimilaritySearchResult> searchByText(String queryText, String entityType, int topK, double minScore);

    /**
     * Embeds a query string and executes similarity search with metadata attribute filters.
     */
    List<SimilaritySearchResult> searchByTextWithFilters(
            String queryText,
            String entityType,
            int topK,
            double minScore,
            Map<String, String> filterAttributes
    );
}
