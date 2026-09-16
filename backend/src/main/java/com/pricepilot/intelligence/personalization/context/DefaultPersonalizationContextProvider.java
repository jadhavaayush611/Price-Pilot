package com.pricepilot.intelligence.personalization.context;

import com.pricepilot.intelligence.personalization.preference.adapter.ExplicitPreferenceAdapter;
import com.pricepilot.intelligence.personalization.signals.adapter.BehavioralSignalAdapter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Primary;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Component;

import java.util.Objects;
import java.util.UUID;

/**
 * Primary production implementation of {@link PersonalizationContextProvider}.
 * Aggregates explicit preferences and behavioral signals into a unified, immutable {@link PersonalizationContext}.
 */
@Component
@Primary
public class DefaultPersonalizationContextProvider implements PersonalizationContextProvider {

    private static final Logger log = LoggerFactory.getLogger(DefaultPersonalizationContextProvider.class);

    private final ExplicitPreferenceAdapter explicitPreferenceAdapter;
    private final BehavioralSignalAdapter behavioralSignalAdapter;

    public DefaultPersonalizationContextProvider(
            ExplicitPreferenceAdapter explicitPreferenceAdapter,
            BehavioralSignalAdapter behavioralSignalAdapter) {
        this.explicitPreferenceAdapter = Objects.requireNonNull(explicitPreferenceAdapter, "ExplicitPreferenceAdapter cannot be null");
        this.behavioralSignalAdapter = Objects.requireNonNull(behavioralSignalAdapter, "BehavioralSignalAdapter cannot be null");
    }

    @Override
    public PersonalizationContext getPersonalizationContext(UUID userId) {
        if (userId == null) {
            return PersonalizationContext.empty(null);
        }

        PersonalizationContext explicitContext;
        try {
            explicitContext = explicitPreferenceAdapter.getExplicitPreferenceContext(userId);
        } catch (AccessDeniedException | SecurityException e) {
            throw e;
        } catch (Exception e) {
            log.warn("Explicit preference retrieval failed for user: {}. Continuing with empty explicit context. Cause: {}",
                    userId, e.getMessage());
            explicitContext = PersonalizationContext.empty(userId);
        }

        PersonalizationContext behavioralContext;
        try {
            behavioralContext = behavioralSignalAdapter.getBehavioralSignalContext(userId);
        } catch (AccessDeniedException | SecurityException e) {
            throw e;
        } catch (Exception e) {
            log.warn("Behavioral signal retrieval failed for user: {}. Continuing with empty behavioral context. Cause: {}",
                    userId, e.getMessage());
            behavioralContext = PersonalizationContext.empty(userId);
        }

        return PersonalizationContext.builder(userId)
                .addSignals(explicitContext.getSignals())
                .addSignals(behavioralContext.getSignals())
                .build();
    }
}
