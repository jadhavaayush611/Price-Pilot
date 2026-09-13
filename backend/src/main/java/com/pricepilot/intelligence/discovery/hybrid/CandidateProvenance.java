package com.pricepilot.intelligence.discovery.hybrid;

/**
 * Enumeration representing the retrieval provenance of a product candidate in hybrid search.
 */
public enum CandidateProvenance {
    /**
     * Retrieved exclusively via structured SQL/specification search.
     */
    STRUCTURED,

    /**
     * Retrieved exclusively via semantic vector similarity search.
     */
    SEMANTIC,

    /**
     * Retrieved by both structured search and semantic vector search.
     */
    BOTH
}
