package com.pricepilot.intelligence.personalization.preference;

import com.pricepilot.intelligence.personalization.preference.dto.UpdateShoppingPreferenceRequest;
import com.pricepilot.intelligence.personalization.preference.dto.UserShoppingPreferenceDTO;

import java.util.Optional;
import java.util.UUID;

public interface UserShoppingPreferenceService {
    UserShoppingPreferenceDTO getPreferences(UUID userId);
    UserShoppingPreferenceDTO updatePreferences(UUID userId, UpdateShoppingPreferenceRequest request);
    void resetPreferences(UUID userId);
    Optional<UserShoppingPreferenceEntity> getPreferenceEntity(UUID userId);
}
