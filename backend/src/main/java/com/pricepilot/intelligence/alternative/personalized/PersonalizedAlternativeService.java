package com.pricepilot.intelligence.alternative.personalized;

import com.pricepilot.intelligence.alternative.model.AlternativeRequest;
import com.pricepilot.intelligence.alternative.model.AlternativeResponseDTO;
import com.pricepilot.intelligence.alternative.model.AlternativeType;
import com.pricepilot.intelligence.personalization.context.PersonalizationContext;

import java.util.UUID;

/**
 * Service contract for discovering, filtering, and scoring personalized product alternatives.
 * Preserves deterministic generic qualification as authoritative, while ranking valid alternatives
 * based on user personalization context and grounded evidence generation.
 */
public interface PersonalizedAlternativeService {

    /**
     * Finds personalized alternatives for a specific baseline product given an authenticated user ID.
     */
    AlternativeResponseDTO findPersonalizedAlternativesForProduct(UUID productId, AlternativeRequest request, UUID userId);

    /**
     * Finds personalized alternatives for a specific baseline product given an explicit PersonalizationContext.
     */
    AlternativeResponseDTO findPersonalizedAlternativesForProduct(UUID productId, AlternativeRequest request, PersonalizationContext context);

    /**
     * Finds personalized alternatives for a structured/interpreted shopping query given an authenticated user ID.
     */
    AlternativeResponseDTO findPersonalizedAlternativesForQuery(AlternativeRequest request, UUID userId);

    /**
     * Finds personalized alternatives for a structured/interpreted shopping query given an explicit PersonalizationContext.
     */
    AlternativeResponseDTO findPersonalizedAlternativesForQuery(AlternativeRequest request, PersonalizationContext context);

    /**
     * Finds personalized alternatives for a natural language request given an authenticated user ID.
     */
    AlternativeResponseDTO findPersonalizedAlternativesForNaturalLanguage(String query, AlternativeType type, int limit, UUID userId);

    /**
     * Finds personalized alternatives for a natural language request given an explicit PersonalizationContext.
     */
    AlternativeResponseDTO findPersonalizedAlternativesForNaturalLanguage(String query, AlternativeType type, int limit, PersonalizationContext context);
}
