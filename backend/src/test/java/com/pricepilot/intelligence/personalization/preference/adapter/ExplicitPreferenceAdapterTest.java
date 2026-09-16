package com.pricepilot.intelligence.personalization.preference.adapter;

import com.pricepilot.intelligence.personalization.context.*;
import com.pricepilot.intelligence.personalization.preference.AvailabilityPreference;
import com.pricepilot.intelligence.personalization.preference.DealSensitivity;
import com.pricepilot.intelligence.personalization.preference.PriceSensitivity;
import com.pricepilot.intelligence.personalization.preference.UserShoppingPreferenceEntity;
import com.pricepilot.intelligence.personalization.preference.UserShoppingPreferenceService;
import com.pricepilot.intelligence.personalization.preference.dto.UserShoppingPreferenceDTO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.security.access.AccessDeniedException;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("ExplicitPreferenceAdapter Unit Tests")
class ExplicitPreferenceAdapterTest {

    @Mock
    private UserShoppingPreferenceService preferenceService;

    private ExplicitPreferenceAdapter adapter;
    private UUID testUserId;

    @BeforeEach
    void setUp() {
        adapter = new ExplicitPreferenceAdapter(preferenceService);
        testUserId = UUID.randomUUID();
    }

    @Test
    @DisplayName("A. Fully populated preference record maps completely to PersonalizationContext")
    void testFullyPopulatedPreferenceMapping() {
        UserShoppingPreferenceDTO dto = UserShoppingPreferenceDTO.builder()
                .id(UUID.randomUUID())
                .userId(testUserId)
                .preferredCategories(Set.of("Smartphones", "Laptops"))
                .preferredBrands(Set.of("Apple", "Sony"))
                .minBudget(BigDecimal.valueOf(300))
                .maxBudget(BigDecimal.valueOf(1200))
                .minRating(4.5)
                .dealSensitivity(DealSensitivity.HIGH)
                .priceSensitivity(PriceSensitivity.LOW)
                .availabilityPreference(AvailabilityPreference.IN_STOCK_ONLY)
                .build();

        when(preferenceService.getPreferences(testUserId)).thenReturn(dto);

        PersonalizationContext context = adapter.getExplicitPreferenceContext(testUserId);

        assertNotNull(context);
        assertEquals(testUserId, context.getUserId());
        assertFalse(context.isEmpty());
        assertEquals(9, context.signalCount());

        // Categories & Brands
        assertTrue(context.getPreferredCategories().contains("smartphones"));
        assertTrue(context.getPreferredCategories().contains("laptops"));
        assertTrue(context.getPreferredBrands().contains("apple"));
        assertTrue(context.getPreferredBrands().contains("sony"));

        // Numerical & Enum Preferences
        assertEquals(BigDecimal.valueOf(300), context.getMinBudget().orElse(null));
        assertEquals(BigDecimal.valueOf(1200), context.getMaxBudget().orElse(null));
        assertEquals(4.5, context.getMinRating().orElse(0.0));
        assertEquals(DealSensitivity.HIGH, context.getDealSensitivity().orElse(null));
        assertEquals(PriceSensitivity.LOW, context.getPriceSensitivity().orElse(null));
        assertEquals(AvailabilityPreference.IN_STOCK_ONLY, context.getAvailabilityPreference().orElse(null));
    }

    @Test
    @DisplayName("B. Every generated signal has EXPLICIT_PREFERENCE provenance")
    void testSourceCorrectnessAllExplicit() {
        UserShoppingPreferenceDTO dto = UserShoppingPreferenceDTO.builder()
                .id(UUID.randomUUID())
                .userId(testUserId)
                .preferredCategories(Set.of("Audio"))
                .preferredBrands(Set.of("Bose"))
                .dealSensitivity(DealSensitivity.HIGH)
                .build();

        when(preferenceService.getPreferences(testUserId)).thenReturn(dto);

        PersonalizationContext context = adapter.getExplicitPreferenceContext(testUserId);

        for (PersonalizationSignal signal : context.getSignals()) {
            assertEquals(PersonalizationSource.EXPLICIT_PREFERENCE, signal.source());
        }
        assertEquals(context.getSignals().size(), context.getExplicitSignals().size());
        assertTrue(context.getBehavioralSignals().isEmpty());
    }

    @Test
    @DisplayName("C. Signal types match Phase 6.1 definitions")
    void testSignalTypeCorrectness() {
        UserShoppingPreferenceDTO dto = UserShoppingPreferenceDTO.builder()
                .id(UUID.randomUUID())
                .userId(testUserId)
                .preferredCategories(Set.of("Audio"))
                .preferredBrands(Set.of("Bose"))
                .minBudget(BigDecimal.valueOf(50))
                .minRating(4.0)
                .dealSensitivity(DealSensitivity.MEDIUM)
                .priceSensitivity(PriceSensitivity.HIGH)
                .availabilityPreference(AvailabilityPreference.IN_STOCK_ONLY)
                .build();

        when(preferenceService.getPreferences(testUserId)).thenReturn(dto);

        PersonalizationContext context = adapter.getExplicitPreferenceContext(testUserId);

        List<PersonalizationSignalType> types = context.getSignals().stream()
                .map(PersonalizationSignal::signalType)
                .toList();

        assertTrue(types.contains(PersonalizationSignalType.CATEGORY_PREFERENCE));
        assertTrue(types.contains(PersonalizationSignalType.BRAND_PREFERENCE));
        assertTrue(types.contains(PersonalizationSignalType.BUDGET_PREFERENCE));
        assertTrue(types.contains(PersonalizationSignalType.RATING_PREFERENCE));
        assertTrue(types.contains(PersonalizationSignalType.DEAL_SENSITIVITY));
        assertTrue(types.contains(PersonalizationSignalType.PRICE_SENSITIVITY));
        assertTrue(types.contains(PersonalizationSignalType.AVAILABILITY_PREFERENCE));
    }

    @Test
    @DisplayName("D. User without preference record produces valid empty explicit context")
    void testMissingPreferenceRecordProducesEmptyContext() {
        // Cold-start default DTO has id == null
        UserShoppingPreferenceDTO coldStartDto = UserShoppingPreferenceDTO.builder()
                .userId(testUserId)
                .preferredCategories(Collections.emptySet())
                .preferredBrands(Collections.emptySet())
                .dealSensitivity(DealSensitivity.MEDIUM)
                .priceSensitivity(PriceSensitivity.MEDIUM)
                .availabilityPreference(AvailabilityPreference.ALL)
                .build();

        when(preferenceService.getPreferences(testUserId)).thenReturn(coldStartDto);

        PersonalizationContext context = adapter.getExplicitPreferenceContext(testUserId);

        assertNotNull(context);
        assertEquals(testUserId, context.getUserId());
        assertTrue(context.isEmpty());
        assertEquals(0, context.signalCount());
        assertTrue(context.getPreferredCategories().isEmpty());
        assertTrue(context.getPreferredBrands().isEmpty());
        assertTrue(context.getMinBudget().isEmpty());
        assertTrue(context.getMaxBudget().isEmpty());
        assertTrue(context.getMinRating().isEmpty());
        assertTrue(context.getDealSensitivity().isEmpty());
        assertTrue(context.getPriceSensitivity().isEmpty());
        assertTrue(context.getAvailabilityPreference().isEmpty());
    }

    @Test
    @DisplayName("E. Partial preferences do not manufacture fabricated signals")
    void testPartialPreferences() {
        UserShoppingPreferenceDTO dto = UserShoppingPreferenceDTO.builder()
                .id(UUID.randomUUID())
                .userId(testUserId)
                .preferredBrands(Set.of("Apple"))
                .dealSensitivity(null)
                .priceSensitivity(null)
                .availabilityPreference(null)
                .build();

        when(preferenceService.getPreferences(testUserId)).thenReturn(dto);

        PersonalizationContext context = adapter.getExplicitPreferenceContext(testUserId);

        assertEquals(1, context.signalCount());
        assertTrue(context.getPreferredBrands().contains("apple"));
        assertTrue(context.getPreferredCategories().isEmpty());
        assertTrue(context.getMinBudget().isEmpty());
        assertTrue(context.getMinRating().isEmpty());
        assertTrue(context.getDealSensitivity().isEmpty());
    }

    @Test
    @DisplayName("F. Category and brand normalization follows Phase 6.1 rules")
    void testNormalization() {
        UserShoppingPreferenceDTO dto = UserShoppingPreferenceDTO.builder()
                .id(UUID.randomUUID())
                .userId(testUserId)
                .preferredCategories(Set.of("  Laptops  ", "SMARTPHONES"))
                .preferredBrands(Set.of(" Apple ", "sony"))
                .build();

        when(preferenceService.getPreferences(testUserId)).thenReturn(dto);

        PersonalizationContext context = adapter.getExplicitPreferenceContext(testUserId);

        assertTrue(context.getPreferredCategories().contains("laptops"));
        assertTrue(context.getPreferredCategories().contains("smartphones"));
        assertTrue(context.getPreferredBrands().contains("apple"));
        assertTrue(context.getPreferredBrands().contains("sony"));
    }

    @Test
    @DisplayName("G. Determinism: identical inputs produce equal contexts and identical hash codes")
    void testDeterminism() {
        UserShoppingPreferenceDTO dto = UserShoppingPreferenceDTO.builder()
                .id(UUID.randomUUID())
                .userId(testUserId)
                .preferredCategories(Set.of("Monitors", "Audio"))
                .preferredBrands(Set.of("Dell", "Sony"))
                .minBudget(BigDecimal.valueOf(100))
                .maxBudget(BigDecimal.valueOf(500))
                .minRating(4.0)
                .build();

        when(preferenceService.getPreferences(testUserId)).thenReturn(dto);

        PersonalizationContext c1 = adapter.getExplicitPreferenceContext(testUserId);
        PersonalizationContext c2 = adapter.getExplicitPreferenceContext(testUserId);

        assertEquals(c1, c2);
        assertEquals(c1.hashCode(), c2.hashCode());
        assertEquals(c1.getSignals(), c2.getSignals());
    }

    @Test
    @DisplayName("H. Immutability of context returned by adapter")
    void testImmutability() {
        UserShoppingPreferenceDTO dto = UserShoppingPreferenceDTO.builder()
                .id(UUID.randomUUID())
                .userId(testUserId)
                .preferredCategories(Set.of("Shoes"))
                .build();

        when(preferenceService.getPreferences(testUserId)).thenReturn(dto);

        PersonalizationContext context = adapter.getExplicitPreferenceContext(testUserId);

        assertThrows(UnsupportedOperationException.class, () ->
                context.getSignals().add(PersonalizationSignal.explicit(PersonalizationSignalType.BRAND_PREFERENCE, "nike", "Nike"))
        );
        assertThrows(UnsupportedOperationException.class, () ->
                context.getPreferredCategories().add("Watches")
        );
    }

    @Test
    @DisplayName("J. Signal strengths are bounded within [0, 1]")
    void testStrengthBounds() {
        UserShoppingPreferenceDTO dto = UserShoppingPreferenceDTO.builder()
                .id(UUID.randomUUID())
                .userId(testUserId)
                .preferredCategories(Set.of("Audio"))
                .dealSensitivity(DealSensitivity.HIGH)
                .build();

        when(preferenceService.getPreferences(testUserId)).thenReturn(dto);

        PersonalizationContext context = adapter.getExplicitPreferenceContext(testUserId);

        for (PersonalizationSignal signal : context.getSignals()) {
            double val = signal.strength().value();
            assertTrue(val >= 0.0 && val <= 1.0);
            assertFalse(Double.isNaN(val));
            assertFalse(Double.isInfinite(val));
        }
    }

    @ParameterizedTest
    @EnumSource(DealSensitivity.class)
    @DisplayName("K. Deal sensitivity maps every enum level deterministically")
    void testDealSensitivityMapping(DealSensitivity sensitivity) {
        UserShoppingPreferenceDTO dto = UserShoppingPreferenceDTO.builder()
                .id(UUID.randomUUID())
                .userId(testUserId)
                .dealSensitivity(sensitivity)
                .build();

        when(preferenceService.getPreferences(testUserId)).thenReturn(dto);

        PersonalizationContext context = adapter.getExplicitPreferenceContext(testUserId);
        assertEquals(sensitivity, context.getDealSensitivity().orElse(null));
    }

    @ParameterizedTest
    @EnumSource(PriceSensitivity.class)
    @DisplayName("K. Price sensitivity maps every enum level deterministically")
    void testPriceSensitivityMapping(PriceSensitivity sensitivity) {
        UserShoppingPreferenceDTO dto = UserShoppingPreferenceDTO.builder()
                .id(UUID.randomUUID())
                .userId(testUserId)
                .priceSensitivity(sensitivity)
                .build();

        when(preferenceService.getPreferences(testUserId)).thenReturn(dto);

        PersonalizationContext context = adapter.getExplicitPreferenceContext(testUserId);
        assertEquals(sensitivity, context.getPriceSensitivity().orElse(null));
    }

    @ParameterizedTest
    @EnumSource(AvailabilityPreference.class)
    @DisplayName("L. Availability preference maps every enum level deterministically")
    void testAvailabilityPreferenceMapping(AvailabilityPreference availability) {
        UserShoppingPreferenceDTO dto = UserShoppingPreferenceDTO.builder()
                .id(UUID.randomUUID())
                .userId(testUserId)
                .availabilityPreference(availability)
                .build();

        when(preferenceService.getPreferences(testUserId)).thenReturn(dto);

        PersonalizationContext context = adapter.getExplicitPreferenceContext(testUserId);
        assertEquals(availability, context.getAvailabilityPreference().orElse(null));
    }

    @Test
    @DisplayName("M. Budget boundaries: min only, max only, both, equal")
    void testBudgetBoundaries() {
        // Min only
        UserShoppingPreferenceDTO minOnly = UserShoppingPreferenceDTO.builder()
                .id(UUID.randomUUID())
                .userId(testUserId)
                .minBudget(BigDecimal.valueOf(100))
                .build();
        when(preferenceService.getPreferences(testUserId)).thenReturn(minOnly);
        PersonalizationContext cMin = adapter.getExplicitPreferenceContext(testUserId);
        assertEquals(BigDecimal.valueOf(100), cMin.getMinBudget().orElse(null));
        assertTrue(cMin.getMaxBudget().isEmpty());

        // Max only
        UserShoppingPreferenceDTO maxOnly = UserShoppingPreferenceDTO.builder()
                .id(UUID.randomUUID())
                .userId(testUserId)
                .maxBudget(BigDecimal.valueOf(500))
                .build();
        when(preferenceService.getPreferences(testUserId)).thenReturn(maxOnly);
        PersonalizationContext cMax = adapter.getExplicitPreferenceContext(testUserId);
        assertTrue(cMax.getMinBudget().isEmpty());
        assertEquals(BigDecimal.valueOf(500), cMax.getMaxBudget().orElse(null));

        // Equal min/max
        UserShoppingPreferenceDTO equalBudget = UserShoppingPreferenceDTO.builder()
                .id(UUID.randomUUID())
                .userId(testUserId)
                .minBudget(BigDecimal.valueOf(300))
                .maxBudget(BigDecimal.valueOf(300))
                .build();
        when(preferenceService.getPreferences(testUserId)).thenReturn(equalBudget);
        PersonalizationContext cEq = adapter.getExplicitPreferenceContext(testUserId);
        assertEquals(BigDecimal.valueOf(300), cEq.getMinBudget().orElse(null));
        assertEquals(BigDecimal.valueOf(300), cEq.getMaxBudget().orElse(null));
    }

    @Test
    @DisplayName("N. User isolation: preference data for user A does not appear in user B's context")
    void testUserIsolation() {
        UUID userA = UUID.randomUUID();
        UUID userB = UUID.randomUUID();

        UserShoppingPreferenceDTO dtoA = UserShoppingPreferenceDTO.builder()
                .id(UUID.randomUUID())
                .userId(userA)
                .preferredBrands(Set.of("Apple"))
                .build();

        UserShoppingPreferenceDTO dtoB = UserShoppingPreferenceDTO.builder()
                .id(UUID.randomUUID())
                .userId(userB)
                .preferredBrands(Set.of("Samsung"))
                .build();

        when(preferenceService.getPreferences(userA)).thenReturn(dtoA);
        when(preferenceService.getPreferences(userB)).thenReturn(dtoB);

        PersonalizationContext cA = adapter.getExplicitPreferenceContext(userA);
        PersonalizationContext cB = adapter.getExplicitPreferenceContext(userB);

        assertEquals(userA, cA.getUserId());
        assertEquals(userB, cB.getUserId());
        assertTrue(cA.getPreferredBrands().contains("apple"));
        assertFalse(cA.getPreferredBrands().contains("samsung"));
        assertTrue(cB.getPreferredBrands().contains("samsung"));
        assertFalse(cB.getPreferredBrands().contains("apple"));
    }

    @Test
    @DisplayName("O. Failure isolation: DataAccess failure falls back to empty context")
    void testFailureIsolationDataAccessException() {
        when(preferenceService.getPreferences(testUserId))
                .thenThrow(new DataAccessResourceFailureException("Database unreachable"));

        PersonalizationContext context = adapter.getExplicitPreferenceContext(testUserId);

        assertNotNull(context);
        assertEquals(testUserId, context.getUserId());
        assertTrue(context.isEmpty());
    }

    @Test
    @DisplayName("O. Failure isolation: Security / AccessDenied exceptions are NOT swallowed")
    void testFailureIsolationSecurityExceptionRethrown() {
        when(preferenceService.getPreferences(testUserId))
                .thenThrow(new AccessDeniedException("Access denied to user preferences"));

        assertThrows(AccessDeniedException.class, () -> adapter.getExplicitPreferenceContext(testUserId));
    }

    @Test
    @DisplayName("P. Zero behavioral contamination in explicit preference adapter")
    void testZeroBehavioralContamination() {
        UserShoppingPreferenceDTO dto = UserShoppingPreferenceDTO.builder()
                .id(UUID.randomUUID())
                .userId(testUserId)
                .preferredCategories(Set.of("Gaming"))
                .preferredBrands(Set.of("Razer"))
                .build();

        when(preferenceService.getPreferences(testUserId)).thenReturn(dto);

        PersonalizationContext context = adapter.getExplicitPreferenceContext(testUserId);

        assertTrue(context.getBehavioralSignals().isEmpty());
        assertEquals(0, context.getCategoryAffinities().size());
        assertEquals(0, context.getBrandAffinities().size());
    }

    @Test
    @DisplayName("Direct Entity mapping method")
    void testDirectEntityMapping() {
        UserShoppingPreferenceEntity entity = UserShoppingPreferenceEntity.builder()
                .userId(testUserId)
                .preferredCategories(Set.of("Fashion"))
                .preferredBrands(Set.of("Nike"))
                .minBudget(BigDecimal.valueOf(50))
                .maxBudget(BigDecimal.valueOf(250))
                .dealSensitivity(DealSensitivity.HIGH)
                .build();
        entity.setId(UUID.randomUUID());

        PersonalizationContext context = adapter.mapToContext(entity);

        assertNotNull(context);
        assertEquals(testUserId, context.getUserId());
        assertTrue(context.getPreferredCategories().contains("fashion"));
        assertTrue(context.getPreferredBrands().contains("nike"));
        assertEquals(BigDecimal.valueOf(50), context.getMinBudget().orElse(null));
        assertEquals(BigDecimal.valueOf(250), context.getMaxBudget().orElse(null));
    }

    @Test
    @DisplayName("Null userId gracefully returns empty context with null user")
    void testNullUserIdHandling() {
        PersonalizationContext context = adapter.getExplicitPreferenceContext(null);
        assertNotNull(context);
        assertNull(context.getUserId());
        assertTrue(context.isEmpty());
    }
}
