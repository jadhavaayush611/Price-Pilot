package com.pricepilot.intelligence.discovery.hybrid;

import com.pricepilot.intelligence.discovery.dto.DiscoverySearchRequestDTO;
import com.pricepilot.intelligence.discovery.dto.DiscoverySearchResponseDTO;

/**
 * Service contract for hybrid search orchestrating structured lexical retrieval,
 * semantic vector retrieval, candidate set fusion, and deterministic ranking.
 */
public interface HybridSearchService {

    /**
     * Executes hybrid retrieval combining structured and semantic candidate discovery.
     *
     * @param request Hybrid search request parameters
     * @return Ranked and enriched discovery search response
     */
    DiscoverySearchResponseDTO search(HybridSearchRequest request);

    /**
     * Executes hybrid retrieval using standard DiscoverySearchRequestDTO.
     */
    DiscoverySearchResponseDTO search(DiscoverySearchRequestDTO request);
}
