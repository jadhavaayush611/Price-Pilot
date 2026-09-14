package com.pricepilot.intelligence.discovery.intent;

import com.pricepilot.intelligence.discovery.dto.DiscoverySearchResponseDTO;

/**
 * Service contract for natural-language product discovery.
 *
 * Translates natural-language shopping requests into validated ShoppingQueryIntent,
 * converts intent into structured filters + semantic query text, and orchestrates
 * candidate discovery through the verified hybrid search engine.
 */
public interface NaturalLanguageDiscoveryService {

    /**
     * Executes end-to-end natural-language search:
     * Natural Language Query -> Intent -> Validation -> Hybrid Search -> Ranking -> Results.
     *
     * @param request Natural-language search request
     * @return Fully ranked discovery response with enriched provenance and intelligence
     */
    DiscoverySearchResponseDTO search(NaturalLanguageSearchRequest request);

    /**
     * Interprets a query string into a ShoppingQueryIntent without executing retrieval.
     * Useful for previewing intent extraction in UI or API clients.
     *
     * @param rawQuery Raw user query text
     * @return Interpreted shopping query intent
     */
    ShoppingQueryIntent interpretQuery(String rawQuery);
}
