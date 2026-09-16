package com.pricepilot.intelligence.personalization.context;

import java.util.UUID;

/**
 * Functional contract for retrieving a normalized, immutable {@link PersonalizationContext} for a given user.
 * Enables decoupling of domain intelligence from persistence and aggregation logic.
 */
@FunctionalInterface
public interface PersonalizationContextProvider {

    /**
     * Retrieves the personalization context for the specified user identifier.
     *
     * @param userId the user identifier
     * @return the normalized, immutable PersonalizationContext
     */
    PersonalizationContext getPersonalizationContext(UUID userId);
}
