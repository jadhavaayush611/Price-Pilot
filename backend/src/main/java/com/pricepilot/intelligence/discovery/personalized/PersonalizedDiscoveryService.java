package com.pricepilot.intelligence.discovery.personalized;

import com.pricepilot.intelligence.discovery.dto.DiscoverySearchRequestDTO;
import com.pricepilot.intelligence.discovery.dto.DiscoverySearchResponseDTO;
import com.pricepilot.intelligence.personalization.context.PersonalizationContext;

import java.util.UUID;

/**
 * Service contract orchestrating personalized product discovery.
 * <p>
 * Fundamental Invariant:
 * Personalization may change the ranking of eligible candidates,
 * but it must never override hard discovery constraints or modify product facts.
 */
public interface PersonalizedDiscoveryService {

    /**
     * Executes personalized product discovery for an authenticated user.
     *
     * @param request the discovery search request parameters and hard filters
     * @param userId the authenticated user identifier
     * @return the personalized discovery response containing ranked candidates and grounded evidence
     */
    DiscoverySearchResponseDTO discover(DiscoverySearchRequestDTO request, UUID userId);

    /**
     * Executes personalized product discovery using an explicit personalization context.
     *
     * @param request the discovery search request parameters and hard filters
     * @param context the user personalization context
     * @return the personalized discovery response containing ranked candidates and grounded evidence
     */
    DiscoverySearchResponseDTO discover(DiscoverySearchRequestDTO request, PersonalizationContext context);
}
