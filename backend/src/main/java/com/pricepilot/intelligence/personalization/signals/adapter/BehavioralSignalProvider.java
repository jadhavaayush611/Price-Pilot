package com.pricepilot.intelligence.personalization.signals.adapter;

import com.pricepilot.intelligence.personalization.context.PersonalizationContext;

import java.util.UUID;

/**
 * Contract for retrieving normalized behavioral personalization context for a user.
 * Decouples the personalization intelligence subsystem from behavioral signal extraction internals.
 */
public interface BehavioralSignalProvider {

    /**
     * Retrieves the normalized behavioral {@link PersonalizationContext} for the specified user.
     *
     * @param userId the unique identifier of the user
     * @return the normalized, immutable PersonalizationContext containing behavioral signals
     */
    PersonalizationContext getBehavioralSignalContext(UUID userId);
}
