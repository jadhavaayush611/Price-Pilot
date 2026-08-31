package com.pricepilot.intelligence.personalization.preference;

import com.pricepilot.intelligence.personalization.preference.dto.UpdateShoppingPreferenceRequest;
import com.pricepilot.intelligence.personalization.preference.dto.UserShoppingPreferenceDTO;
import com.pricepilot.user.UserRepository;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class UserShoppingPreferenceServiceImpl implements UserShoppingPreferenceService {

    private final UserShoppingPreferenceRepository preferenceRepository;
    private final UserRepository userRepository;

    public UserShoppingPreferenceServiceImpl(
            UserShoppingPreferenceRepository preferenceRepository,
            UserRepository userRepository) {
        this.preferenceRepository = preferenceRepository;
        this.userRepository = userRepository;
    }

    @Override
    @Transactional(readOnly = true)
    @Cacheable(value = "user-preferences", key = "#userId", condition = "#userId != null")
    public UserShoppingPreferenceDTO getPreferences(UUID userId) {
        if (userId == null) {
            throw new IllegalArgumentException("User ID cannot be null");
        }

        return preferenceRepository.findByUserId(userId)
                .map(this::toDTO)
                .orElseGet(() -> defaultDTO(userId));
    }

    @Override
    @Transactional
    @CacheEvict(value = {"user-preferences", "user-recommendations"}, key = "#userId")
    public UserShoppingPreferenceDTO updatePreferences(UUID userId, UpdateShoppingPreferenceRequest request) {
        if (userId == null) {
            throw new IllegalArgumentException("User ID cannot be null");
        }
        if (request == null) {
            throw new IllegalArgumentException("Preference update request cannot be null");
        }

        // Validate budget ranges
        if (request.getMinBudget() != null && request.getMaxBudget() != null) {
            if (request.getMinBudget().compareTo(request.getMaxBudget()) > 0) {
                throw new IllegalArgumentException("Minimum budget cannot exceed maximum budget");
            }
        }

        UserShoppingPreferenceEntity entity = preferenceRepository.findByUserId(userId)
                .orElseGet(() -> UserShoppingPreferenceEntity.builder()
                        .userId(userId)
                        .build());

        if (request.getPreferredCategories() != null) {
            entity.setPreferredCategories(request.getPreferredCategories().stream()
                    .filter(c -> c != null && !c.trim().isEmpty())
                    .map(String::trim)
                    .collect(Collectors.toSet()));
        }

        if (request.getPreferredBrands() != null) {
            entity.setPreferredBrands(request.getPreferredBrands().stream()
                    .filter(b -> b != null && !b.trim().isEmpty())
                    .map(String::trim)
                    .collect(Collectors.toSet()));
        }

        if (request.getMinBudget() != null) {
            entity.setMinBudget(request.getMinBudget());
        }
        if (request.getMaxBudget() != null) {
            entity.setMaxBudget(request.getMaxBudget());
        }
        if (request.getMinRating() != null) {
            entity.setMinRating(request.getMinRating());
        }
        if (request.getDealSensitivity() != null) {
            entity.setDealSensitivity(request.getDealSensitivity());
        }
        if (request.getPriceSensitivity() != null) {
            entity.setPriceSensitivity(request.getPriceSensitivity());
        }
        if (request.getAvailabilityPreference() != null) {
            entity.setAvailabilityPreference(request.getAvailabilityPreference());
        }

        UserShoppingPreferenceEntity saved = preferenceRepository.save(entity);
        return toDTO(saved);
    }

    @Override
    @Transactional
    @CacheEvict(value = {"user-preferences", "user-recommendations"}, key = "#userId")
    public void resetPreferences(UUID userId) {
        if (userId == null) {
            throw new IllegalArgumentException("User ID cannot be null");
        }
        preferenceRepository.deleteByUserId(userId);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<UserShoppingPreferenceEntity> getPreferenceEntity(UUID userId) {
        if (userId == null) {
            return Optional.empty();
        }
        return preferenceRepository.findByUserId(userId);
    }

    private UserShoppingPreferenceDTO toDTO(UserShoppingPreferenceEntity entity) {
        return UserShoppingPreferenceDTO.builder()
                .id(entity.getId())
                .userId(entity.getUserId())
                .preferredCategories(entity.getPreferredCategories() != null ? new HashSet<>(entity.getPreferredCategories()) : new HashSet<>())
                .preferredBrands(entity.getPreferredBrands() != null ? new HashSet<>(entity.getPreferredBrands()) : new HashSet<>())
                .minBudget(entity.getMinBudget())
                .maxBudget(entity.getMaxBudget())
                .minRating(entity.getMinRating())
                .dealSensitivity(entity.getDealSensitivity() != null ? entity.getDealSensitivity() : DealSensitivity.MEDIUM)
                .priceSensitivity(entity.getPriceSensitivity() != null ? entity.getPriceSensitivity() : PriceSensitivity.MEDIUM)
                .availabilityPreference(entity.getAvailabilityPreference() != null ? entity.getAvailabilityPreference() : AvailabilityPreference.ALL)
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .build();
    }

    private UserShoppingPreferenceDTO defaultDTO(UUID userId) {
        return UserShoppingPreferenceDTO.builder()
                .userId(userId)
                .preferredCategories(new HashSet<>())
                .preferredBrands(new HashSet<>())
                .dealSensitivity(DealSensitivity.MEDIUM)
                .priceSensitivity(PriceSensitivity.MEDIUM)
                .availabilityPreference(AvailabilityPreference.ALL)
                .build();
    }
}
