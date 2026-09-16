package com.pricepilot.intelligence.personalization.signals.adapter;

import com.pricepilot.intelligence.personalization.context.PersonalizationContext;
import com.pricepilot.intelligence.personalization.context.PersonalizationContextBuilder;
import com.pricepilot.intelligence.personalization.context.PersonalizationContextProvider;
import com.pricepilot.intelligence.personalization.context.PersonalizationSignal;
import com.pricepilot.intelligence.personalization.context.PersonalizationSignalType;
import com.pricepilot.intelligence.personalization.signals.BehavioralSignalService;
import com.pricepilot.intelligence.personalization.signals.UserShoppingSignals;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.UUID;

/**
 * Adapter bridging PricePilot's v1.1 behavioral signal extraction infrastructure
 * to the v1.2 {@link PersonalizationContext} domain foundation.
 * <p>
 * Reuses the existing {@link BehavioralSignalService} and its deduplication/satiation safeguards
 * without creating duplicate event processing or repository queries.
 */
@Component
public class BehavioralSignalAdapter implements BehavioralSignalProvider, PersonalizationContextProvider {

    private static final Logger log = LoggerFactory.getLogger(BehavioralSignalAdapter.class);

    private final BehavioralSignalService behavioralSignalService;

    public BehavioralSignalAdapter(BehavioralSignalService behavioralSignalService) {
        this.behavioralSignalService = behavioralSignalService;
    }

    @Override
    public PersonalizationContext getBehavioralSignalContext(UUID userId) {
        if (userId == null) {
            return PersonalizationContext.empty(null);
        }

        try {
            UserShoppingSignals shoppingSignals = behavioralSignalService.extractSignals(userId);
            return mapToContext(userId, shoppingSignals);
        } catch (AccessDeniedException | SecurityException e) {
            // Re-throw authorization/security exceptions without masking
            throw e;
        } catch (RuntimeException e) {
            log.warn("Failed to retrieve behavioral signals for user: {}. Falling back to empty context. Cause: {}",
                    userId, e.getMessage());
            return PersonalizationContext.empty(userId);
        }
    }

    @Override
    public PersonalizationContext getPersonalizationContext(UUID userId) {
        return getBehavioralSignalContext(userId);
    }

    /**
     * Maps an aggregated {@link UserShoppingSignals} instance into an immutable {@link PersonalizationContext}.
     *
     * @param userId the user identifier
     * @param signals the extracted user shopping signals
     * @return the normalized PersonalizationContext containing behavioral signals
     */
    public PersonalizationContext mapToContext(UUID userId, UserShoppingSignals signals) {
        if (userId == null) {
            return PersonalizationContext.empty(null);
        }
        if (signals == null) {
            return PersonalizationContext.empty(userId);
        }

        PersonalizationContextBuilder builder = PersonalizationContext.builder(userId);

        // Map Category Affinities
        if (signals.getCategoryAffinity() != null && !signals.getCategoryAffinity().isEmpty()) {
            for (Map.Entry<String, Double> entry : signals.getCategoryAffinity().entrySet()) {
                String rawCategory = entry.getKey();
                Double rawAffinity = entry.getValue();

                if (rawCategory != null && !rawCategory.trim().isEmpty() && rawAffinity != null) {
                    double boundedAffinity = sanitizeAffinity(rawAffinity);
                    if (boundedAffinity > 0.0) {
                        builder.addCategoryAffinity(rawCategory.trim(), boundedAffinity);
                    }
                }
            }
        }

        // Map Brand Affinities
        if (signals.getBrandAffinity() != null && !signals.getBrandAffinity().isEmpty()) {
            for (Map.Entry<String, Double> entry : signals.getBrandAffinity().entrySet()) {
                String rawBrand = entry.getKey();
                Double rawAffinity = entry.getValue();

                if (rawBrand != null && !rawBrand.trim().isEmpty() && rawAffinity != null) {
                    double boundedAffinity = sanitizeAffinity(rawAffinity);
                    if (boundedAffinity > 0.0) {
                        builder.addBrandAffinity(rawBrand.trim(), boundedAffinity);
                    }
                }
            }
        }

        // Map Recent Interacted Product IDs as INTERACTION_AFFINITY signals
        if (signals.getRecentInteractedProductIds() != null && !signals.getRecentInteractedProductIds().isEmpty()) {
            for (UUID productId : signals.getRecentInteractedProductIds()) {
                if (productId != null) {
                    builder.addSignal(PersonalizationSignal.behavioral(
                            PersonalizationSignalType.INTERACTION_AFFINITY,
                            productId.toString(),
                            productId.toString(),
                            1.0
                    ));
                }
            }
        }

        return builder.build();
    }

    private double sanitizeAffinity(Double affinity) {
        if (affinity == null || Double.isNaN(affinity) || Double.isInfinite(affinity)) {
            return 0.0;
        }
        return Math.max(0.0, Math.min(1.0, affinity));
    }
}
