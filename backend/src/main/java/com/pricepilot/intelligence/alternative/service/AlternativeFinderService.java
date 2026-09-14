package com.pricepilot.intelligence.alternative.service;

import com.pricepilot.intelligence.alternative.model.AlternativeRequest;
import com.pricepilot.intelligence.alternative.model.AlternativeResponseDTO;
import com.pricepilot.intelligence.alternative.model.AlternativeType;

import java.util.UUID;

/**
 * Service contract for discovering, filtering, and scoring product alternatives
 * across both product-driven and query-driven entry modes.
 */
public interface AlternativeFinderService {

    /**
     * Finds alternatives for an existing product baseline.
     */
    AlternativeResponseDTO findAlternativesForProduct(UUID productId, AlternativeRequest request);

    /**
     * Finds alternatives for a structured or interpreted shopping query.
     */
    AlternativeResponseDTO findAlternativesForQuery(AlternativeRequest request);

    /**
     * Finds alternatives from a free-form natural language request.
     */
    AlternativeResponseDTO findAlternativesForNaturalLanguage(String query, AlternativeType type, int limit);
}
