package com.pricepilot.intelligence.personalization.preference.adapter;

import com.pricepilot.intelligence.personalization.context.PersonalizationContext;
import com.pricepilot.intelligence.personalization.context.PersonalizationContextBuilder;
import com.pricepilot.intelligence.personalization.context.PersonalizationContextProvider;
import com.pricepilot.intelligence.personalization.preference.UserShoppingPreferenceEntity;
import com.pricepilot.intelligence.personalization.preference.UserShoppingPreferenceService;
import com.pricepilot.intelligence.personalization.preference.dto.UserShoppingPreferenceDTO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Adapter bridging PricePilot's v1.1 explicit shopping preference infrastructure
 * to the v1.2 {@link PersonalizationContext} domain foundation.
 * <p>
 * Reuses the existing cached {@link UserShoppingPreferenceService} without introducing duplicate persistence.
 */
@Component
public class ExplicitPreferenceAdapter implements UserPreferenceProvider, PersonalizationContextProvider {

    private static final Logger log = LoggerFactory.getLogger(ExplicitPreferenceAdapter.class);

    private final UserShoppingPreferenceService preferenceService;

    public ExplicitPreferenceAdapter(UserShoppingPreferenceService preferenceService) {
        this.preferenceService = preferenceService;
    }

    @Override
    public PersonalizationContext getExplicitPreferenceContext(UUID userId) {
        if (userId == null) {
            return PersonalizationContext.empty(null);
        }

        try {
            UserShoppingPreferenceDTO dto = preferenceService.getPreferences(userId);
            return mapToContext(userId, dto);
        } catch (AccessDeniedException | SecurityException e) {
            // Re-throw authorization/security exceptions without masking
            throw e;
        } catch (RuntimeException e) {
            log.warn("Failed to retrieve explicit preferences for user: {}. Falling back to empty context. Cause: {}",
                    userId, e.getMessage());
            return PersonalizationContext.empty(userId);
        }
    }

    @Override
    public PersonalizationContext getPersonalizationContext(UUID userId) {
        return getExplicitPreferenceContext(userId);
    }

    /**
     * Maps an existing {@link UserShoppingPreferenceDTO} to an immutable {@link PersonalizationContext}.
     *
     * @param userId the user identifier
     * @param dto the preference DTO
     * @return the normalized PersonalizationContext
     */
    public PersonalizationContext mapToContext(UUID userId, UserShoppingPreferenceDTO dto) {
        if (userId == null) {
            return PersonalizationContext.empty(null);
        }
        if (dto == null || dto.getId() == null) {
            // Cold start / default DTO: no explicit preferences were persisted by user
            return PersonalizationContext.empty(userId);
        }

        PersonalizationContextBuilder builder = PersonalizationContext.builder(userId);

        if (dto.getPreferredCategories() != null && !dto.getPreferredCategories().isEmpty()) {
            builder.addPreferredCategories(dto.getPreferredCategories());
        }

        if (dto.getPreferredBrands() != null && !dto.getPreferredBrands().isEmpty()) {
            builder.addPreferredBrands(dto.getPreferredBrands());
        }

        if (dto.getMinBudget() != null) {
            builder.minBudget(dto.getMinBudget());
        }

        if (dto.getMaxBudget() != null) {
            builder.maxBudget(dto.getMaxBudget());
        }

        if (dto.getMinRating() != null) {
            builder.minRating(dto.getMinRating());
        }

        if (dto.getDealSensitivity() != null) {
            builder.dealSensitivity(dto.getDealSensitivity());
        }

        if (dto.getPriceSensitivity() != null) {
            builder.priceSensitivity(dto.getPriceSensitivity());
        }

        if (dto.getAvailabilityPreference() != null) {
            builder.availabilityPreference(dto.getAvailabilityPreference());
        }

        return builder.build();
    }

    /**
     * Maps an existing {@link UserShoppingPreferenceEntity} directly to an immutable {@link PersonalizationContext}.
     *
     * @param entity the preference entity
     * @return the normalized PersonalizationContext
     */
    public PersonalizationContext mapToContext(UserShoppingPreferenceEntity entity) {
        if (entity == null || entity.getUserId() == null) {
            return PersonalizationContext.empty(null);
        }

        PersonalizationContextBuilder builder = PersonalizationContext.builder(entity.getUserId());

        if (entity.getPreferredCategories() != null && !entity.getPreferredCategories().isEmpty()) {
            builder.addPreferredCategories(entity.getPreferredCategories());
        }

        if (entity.getPreferredBrands() != null && !entity.getPreferredBrands().isEmpty()) {
            builder.addPreferredBrands(entity.getPreferredBrands());
        }

        if (entity.getMinBudget() != null) {
            builder.minBudget(entity.getMinBudget());
        }

        if (entity.getMaxBudget() != null) {
            builder.maxBudget(entity.getMaxBudget());
        }

        if (entity.getMinRating() != null) {
            builder.minRating(entity.getMinRating());
        }

        if (entity.getDealSensitivity() != null) {
            builder.dealSensitivity(entity.getDealSensitivity());
        }

        if (entity.getPriceSensitivity() != null) {
            builder.priceSensitivity(entity.getPriceSensitivity());
        }

        if (entity.getAvailabilityPreference() != null) {
            builder.availabilityPreference(entity.getAvailabilityPreference());
        }

        return builder.build();
    }
}
