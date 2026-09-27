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

    @Test
    @DisplayName("Update preferences can clear minBudget, maxBudget, and minRating by setting them to null")
    void testUpdatePreferencesClearsBudgetAndRating() {
        UserShoppingPreferenceEntity existing = UserShoppingPreferenceEntity.builder()
                .userId(testUserId)
                .minBudget(BigDecimal.valueOf(500))
                .maxBudget(BigDecimal.valueOf(1500))
                .minRating(4.5)
                .build();
        existing.setId(UUID.randomUUID());

        when(preferenceRepository.findByUserId(testUserId)).thenReturn(Optional.of(existing));
        when(preferenceRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        UpdateShoppingPreferenceRequest request = UpdateShoppingPreferenceRequest.builder()
                .minBudget(null)
                .maxBudget(null)
                .minRating(null)
                .currency(com.pricepilot.currency.CurrencyCode.USD)
                .build();

        UserShoppingPreferenceDTO result = preferenceService.updatePreferences(testUserId, request);

        assertNotNull(result);
        assertNull(result.getMinBudget());
        assertNull(result.getMaxBudget());
        assertNull(result.getMinRating());
        assertEquals(com.pricepilot.currency.CurrencyCode.USD, result.getCurrency());
    }

    @Test
    @DisplayName("Update preferences invokes RecommendationCacheHelper when available")
    void testUpdatePreferencesEvictsUserCaches() {
        com.pricepilot.recommendation.RecommendationCacheHelper mockCacheHelper = mock(com.pricepilot.recommendation.RecommendationCacheHelper.class);
        org.springframework.beans.factory.ObjectProvider<com.pricepilot.recommendation.RecommendationCacheHelper> provider = mock(org.springframework.beans.factory.ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(mockCacheHelper);

        UserShoppingPreferenceServiceImpl serviceWithCache = new UserShoppingPreferenceServiceImpl(
                preferenceRepository, userRepository, provider);

        when(preferenceRepository.findByUserId(testUserId)).thenReturn(Optional.empty());
        when(preferenceRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        UpdateShoppingPreferenceRequest request = UpdateShoppingPreferenceRequest.builder()
                .currency(com.pricepilot.currency.CurrencyCode.INR)
                .build();

        serviceWithCache.updatePreferences(testUserId, request);

        verify(mockCacheHelper, times(1)).evictUserCaches(testUserId);
    }

    @Test
    @DisplayName("Reset preferences invokes RecommendationCacheHelper when available")
    void testResetPreferencesEvictsUserCaches() {
        com.pricepilot.recommendation.RecommendationCacheHelper mockCacheHelper = mock(com.pricepilot.recommendation.RecommendationCacheHelper.class);
        org.springframework.beans.factory.ObjectProvider<com.pricepilot.recommendation.RecommendationCacheHelper> provider = mock(org.springframework.beans.factory.ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(mockCacheHelper);

        UserShoppingPreferenceServiceImpl serviceWithCache = new UserShoppingPreferenceServiceImpl(
                preferenceRepository, userRepository, provider);

        serviceWithCache.resetPreferences(testUserId);

        verify(preferenceRepository, times(1)).deleteByUserId(testUserId);
        verify(mockCacheHelper, times(1)).evictUserCaches(testUserId);
    }

    @Test
    @DisplayName("Update preferences persists INR with ₹5000 budget correctly")
    void testUpdatePreferencesINR() {
        when(preferenceRepository.findByUserId(testUserId)).thenReturn(Optional.empty());
        when(preferenceRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        UpdateShoppingPreferenceRequest request = UpdateShoppingPreferenceRequest.builder()
                .preferredCategories(Set.of("Headphones"))
                .preferredBrands(Set.of("Sony"))
                .minBudget(BigDecimal.valueOf(1000))
                .maxBudget(BigDecimal.valueOf(5000))
                .minRating(4.0)
                .currency(com.pricepilot.currency.CurrencyCode.INR)
                .dealSensitivity(DealSensitivity.HIGH)
                .priceSensitivity(PriceSensitivity.MEDIUM)
                .availabilityPreference(AvailabilityPreference.IN_STOCK_ONLY)
                .build();

        UserShoppingPreferenceDTO result = preferenceService.updatePreferences(testUserId, request);

        assertNotNull(result);
        assertEquals(com.pricepilot.currency.CurrencyCode.INR, result.getCurrency());
        assertEquals(BigDecimal.valueOf(5000), result.getMaxBudget());
        assertEquals(BigDecimal.valueOf(1000), result.getMinBudget());
        assertEquals(4.0, result.getMinRating());
        assertEquals(DealSensitivity.HIGH, result.getDealSensitivity());
        assertEquals(AvailabilityPreference.IN_STOCK_ONLY, result.getAvailabilityPreference());
    }

    @Test
    @DisplayName("Null userId or request throws IllegalArgumentException")
    void testNullValidation() {
        assertThrows(IllegalArgumentException.class, () -> preferenceService.getPreferences(null));
        assertThrows(IllegalArgumentException.class, () -> preferenceService.updatePreferences(null, new UpdateShoppingPreferenceRequest()));
        assertThrows(IllegalArgumentException.class, () -> preferenceService.updatePreferences(testUserId, null));
        assertThrows(IllegalArgumentException.class, () -> preferenceService.resetPreferences(null));
    }
}
