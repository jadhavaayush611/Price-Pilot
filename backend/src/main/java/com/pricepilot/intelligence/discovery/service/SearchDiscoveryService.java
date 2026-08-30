package com.pricepilot.intelligence.discovery.service;

import com.pricepilot.intelligence.discovery.dto.DiscoverySearchRequestDTO;
import com.pricepilot.intelligence.discovery.dto.DiscoverySearchResponseDTO;
import com.pricepilot.intelligence.discovery.dto.SearchSuggestionDTO;

import java.util.List;

/**
 * Service interface for intelligent search and product discovery.
 */
public interface SearchDiscoveryService {

    /**
     * Executes intelligent, relevance-scored, deal-aware discovery search.
     *
     * @param request search and filtering criteria
     * @return structured discovery response with explainable relevance and intelligence signals
     */
    DiscoverySearchResponseDTO searchAndDiscover(DiscoverySearchRequestDTO request);

    /**
     * Returns bounded autocomplete suggestions from catalog data.
     *
     * @param query prefix or partial query
     * @param limit maximum number of suggestions
     * @return list of suggestions
     */
    List<SearchSuggestionDTO> getSuggestions(String query, int limit);
}
