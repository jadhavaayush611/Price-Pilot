package com.pricepilot.intelligence.personalization.preference;

import com.pricepilot.intelligence.personalization.preference.dto.UpdateShoppingPreferenceRequest;
import com.pricepilot.intelligence.personalization.preference.dto.UserShoppingPreferenceDTO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.cache.CacheManager;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserShoppingPreferenceServiceTest {

    @Mock
    private UserShoppingPreferenceRepository preferenceRepository;

    @Mock
    private com.pricepilot.user.UserRepository userRepository;

    @Mock
    private CacheManager cacheManager;

    private UserShoppingPreferenceServiceImpl preferenceService;
    private UUID testUserId;

    @BeforeEach
    void setUp() {
        preferenceService = new UserShoppingPreferenceServiceImpl(preferenceRepository, userRepository);
        testUserId = UUID.randomUUID();
    }

    @Test
    @DisplayName("Returns existing user preferences mapped to DTO")
    void testGetPreferencesExisting() {
        UserShoppingPreferenceEntity entity = UserShoppingPreferenceEntity.builder()
                .userId(testUserId)
                .preferredCategories(Set.of("Smartphone", "Laptop"))
                .preferredBrands(Set.of("Apple", "Sony"))
                .minBudget(BigDecimal.valueOf(500))
                .maxBudget(BigDecimal.valueOf(1500))
                .minRating(4.5)
                .dealSensitivity(DealSensitivity.HIGH)
                .priceSensitivity(PriceSensitivity.HIGH)
                .availabilityPreference(AvailabilityPreference.IN_STOCK_ONLY)
                .build();
        entity.setId(UUID.randomUUID());

        when(preferenceRepository.findByUserId(testUserId)).thenReturn(Optional.of(entity));

        UserShoppingPreferenceDTO dto = preferenceService.getPreferences(testUserId);

        assertNotNull(dto);
        assertEquals(testUserId, dto.getUserId());
        assertTrue(dto.getPreferredCategories().contains("Smartphone"));
        assertTrue(dto.getPreferredBrands().contains("Apple"));
        assertEquals(BigDecimal.valueOf(500), dto.getMinBudget());
        assertEquals(BigDecimal.valueOf(1500), dto.getMaxBudget());
        assertEquals(4.5, dto.getMinRating());
        assertEquals(DealSensitivity.HIGH, dto.getDealSensitivity());
        assertEquals(PriceSensitivity.HIGH, dto.getPriceSensitivity());
        assertEquals(AvailabilityPreference.IN_STOCK_ONLY, dto.getAvailabilityPreference());
    }

    @Test
    @DisplayName("Returns default preferences on cold start when no preference entity exists")
    void testGetPreferencesDefaultOnColdStart() {
        when(preferenceRepository.findByUserId(testUserId)).thenReturn(Optional.empty());

        UserShoppingPreferenceDTO dto = preferenceService.getPreferences(testUserId);

        assertNotNull(dto);
        assertEquals(testUserId, dto.getUserId());
        assertTrue(dto.getPreferredCategories().isEmpty());
        assertTrue(dto.getPreferredBrands().isEmpty());
        assertNull(dto.getMinBudget());
        assertNull(dto.getMaxBudget());
        assertEquals(DealSensitivity.MEDIUM, dto.getDealSensitivity());
        assertEquals(PriceSensitivity.MEDIUM, dto.getPriceSensitivity());
        assertEquals(AvailabilityPreference.ALL, dto.getAvailabilityPreference());
    }

    @Test
    @DisplayName("Rejects budget range when minBudget exceeds maxBudget")
    void testUpdatePreferencesInvalidBudget() {
        UpdateShoppingPreferenceRequest request = UpdateShoppingPreferenceRequest.builder()
                .minBudget(BigDecimal.valueOf(1000))
                .maxBudget(BigDecimal.valueOf(500))
                .build();

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () ->
                preferenceService.updatePreferences(testUserId, request)
        );
        assertTrue(ex.getMessage().contains("Minimum budget cannot exceed maximum budget"));
        verify(preferenceRepository, never()).save(any());
    }

    @Test
    @DisplayName("Successfully creates new preferences when none exist")
    void testUpdatePreferencesCreateNew() {
        when(preferenceRepository.findByUserId(testUserId)).thenReturn(Optional.empty());
        when(preferenceRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        UpdateShoppingPreferenceRequest request = UpdateShoppingPreferenceRequest.builder()
                .preferredCategories(Set.of("Audio", "Monitors"))
                .preferredBrands(Set.of("Bose", "LG"))
                .minBudget(BigDecimal.valueOf(100))
                .maxBudget(BigDecimal.valueOf(600))
                .minRating(4.0)
                .dealSensitivity(DealSensitivity.HIGH)
                .priceSensitivity(PriceSensitivity.MEDIUM)
                .availabilityPreference(AvailabilityPreference.IN_STOCK_ONLY)
                .build();

        UserShoppingPreferenceDTO result = preferenceService.updatePreferences(testUserId, request);

        assertNotNull(result);
        assertEquals(testUserId, result.getUserId());
        assertTrue(result.getPreferredCategories().contains("Audio"));
        assertTrue(result.getPreferredBrands().contains("Bose"));
        assertEquals(BigDecimal.valueOf(100), result.getMinBudget());
        assertEquals(BigDecimal.valueOf(600), result.getMaxBudget());
        assertEquals(4.0, result.getMinRating());
        assertEquals(DealSensitivity.HIGH, result.getDealSensitivity());
    }

    @Test
    @DisplayName("Reset preferences deletes entity and returns default values")
    void testResetPreferences() {
        preferenceService.resetPreferences(testUserId);
        verify(preferenceRepository, times(1)).deleteByUserId(testUserId);
    }
}
