package com.pricepilot.intelligence.personalization.preference.adapter;

import com.pricepilot.intelligence.personalization.context.PersonalizationContext;

import java.util.UUID;

/**
 * Contract for retrieving normalized explicit preference context for a user.
 * Decouples the personalization intelligence subsystem from preference persistence details.
 */
public interface UserPreferenceProvider {

    /**
     * Retrieves the normalized explicit {@link PersonalizationContext} for the specified user.
     *
     * @param userId the unique identifier of the user
     * @return the normalized, immutable PersonalizationContext containing explicit preferences
     */
    PersonalizationContext getExplicitPreferenceContext(UUID userId);
}
