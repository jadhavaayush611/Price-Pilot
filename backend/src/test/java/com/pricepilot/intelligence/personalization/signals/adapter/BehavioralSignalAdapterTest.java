package com.pricepilot.intelligence.personalization.signals.adapter;

import com.pricepilot.intelligence.personalization.context.*;
import com.pricepilot.intelligence.personalization.signals.BehavioralSignalService;
import com.pricepilot.intelligence.personalization.signals.UserShoppingSignals;
import com.pricepilot.interaction.InteractionType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.security.access.AccessDeniedException;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("BehavioralSignalAdapter Unit Tests")
class BehavioralSignalAdapterTest {

    @Mock
    private BehavioralSignalService behavioralSignalService;

    private BehavioralSignalAdapter adapter;
    private UUID testUserId;

    @BeforeEach
    void setUp() {
        adapter = new BehavioralSignalAdapter(behavioralSignalService);
        testUserId = UUID.randomUUID();
    }

    @Test
    @DisplayName("A. Complete behavioral mapping of categories, brands, and product interactions")
    void testCompleteBehavioralMapping() {
        UUID prod1 = UUID.randomUUID();
        UUID prod2 = UUID.randomUUID();

        UserShoppingSignals signals = UserShoppingSignals.builder()
                .userId(testUserId)
                .categoryAffinity(Map.of("smartphones", 0.9, "laptops", 0.75))
                .brandAffinity(Map.of("apple", 0.85, "sony", 0.6))
                .recentInteractedProductIds(Set.of(prod1, prod2))
                .interactionCountsByType(Map.of(InteractionType.PRODUCT_VIEW, 5, InteractionType.PRODUCT_SAVE, 2))
                .totalInteractions(7)
                .build();

        when(behavioralSignalService.extractSignals(testUserId)).thenReturn(signals);

        PersonalizationContext context = adapter.getBehavioralSignalContext(testUserId);

        assertNotNull(context);
        assertEquals(testUserId, context.getUserId());
        assertFalse(context.isEmpty());
        assertEquals(6, context.signalCount()); // 2 categories + 2 brands + 2 product interactions

        // Verify affinity queries
        assertEquals(0.9, context.getCategoryAffinity("smartphones"));
        assertEquals(0.9, context.getCategoryAffinity("SMARTPHONES"));
        assertEquals(0.75, context.getCategoryAffinity("laptops"));
        assertEquals(0.85, context.getBrandAffinity("apple"));
        assertEquals(0.6, context.getBrandAffinity("sony"));
        assertEquals(0.0, context.getBrandAffinity("dell"));
    }

    @Test
    @DisplayName("B. Every generated signal has BEHAVIORAL_SIGNAL provenance")
    void testSourceCorrectnessAllBehavioral() {
        UserShoppingSignals signals = UserShoppingSignals.builder()
                .userId(testUserId)
                .categoryAffinity(Map.of("audio", 0.8))
                .brandAffinity(Map.of("bose", 0.9))
                .build();

        when(behavioralSignalService.extractSignals(testUserId)).thenReturn(signals);

        PersonalizationContext context = adapter.getBehavioralSignalContext(testUserId);

        for (PersonalizationSignal signal : context.getSignals()) {
            assertEquals(PersonalizationSource.BEHAVIORAL_SIGNAL, signal.source());
        }
        assertEquals(context.getSignals().size(), context.getBehavioralSignals().size());
        assertTrue(context.getExplicitSignals().isEmpty());
    }

    @Test
    @DisplayName("C. Explicit and behavioral signals on same entity coexist without overwriting")
    void testExplicitAndBehavioralCoexistence() {
        // Explicit preference from 6.2
        PersonalizationSignal explicitSignal = PersonalizationSignal.explicit(
                PersonalizationSignalType.CATEGORY_PREFERENCE,
                "laptops",
                "Laptops"
        );

        // Behavioral signal from 6.3
        UserShoppingSignals shoppingSignals = UserShoppingSignals.builder()
                .userId(testUserId)
                .categoryAffinity(Map.of("laptops", 0.85))
                .build();

        PersonalizationContext behavioralContext = adapter.mapToContext(testUserId, shoppingSignals);

        // Compose both into a PersonalizationContext
        PersonalizationContext composed = PersonalizationContext.builder(testUserId)
                .addSignal(explicitSignal)
                .addSignals(behavioralContext.getSignals())
                .build();

        assertEquals(2, composed.signalCount());
        assertEquals(1, composed.getExplicitSignals().size());
        assertEquals(1, composed.getBehavioralSignals().size());

        assertTrue(composed.getPreferredCategories().contains("laptops"));
        assertEquals(0.85, composed.getCategoryAffinity("laptops"));

        PersonalizationSignal explicit = composed.getExplicitSignals().get(0);
        PersonalizationSignal behavioral = composed.getBehavioralSignals().get(0);

        assertEquals(PersonalizationSource.EXPLICIT_PREFERENCE, explicit.source());
        assertEquals(PersonalizationSource.BEHAVIORAL_SIGNAL, behavioral.source());
        assertEquals(PersonalizationSignalType.CATEGORY_PREFERENCE, explicit.signalType());
        assertEquals(PersonalizationSignalType.CATEGORY_AFFINITY, behavioral.signalType());
    }

    @Test
    @DisplayName("D. No behavioral contamination of explicit preference signals")
    void testNoBehavioralContamination() {
        UserShoppingSignals signals = UserShoppingSignals.builder()
                .userId(testUserId)
                .categoryAffinity(Map.of("monitors", 0.7))
                .build();

        when(behavioralSignalService.extractSignals(testUserId)).thenReturn(signals);

        PersonalizationContext context = adapter.getBehavioralSignalContext(testUserId);

        assertTrue(context.getExplicitSignals().isEmpty());
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
    @DisplayName("E. Strength values are bounded in [0.0, 1.0] and sanitized")
    void testStrengthSanitization() {
        Map<String, Double> categories = new HashMap<>();
        categories.put("audio", 0.85);
        categories.put("shoes", 1.5); // should be clamped to 1.0
        categories.put("invalid_neg", -0.5); // should be skipped or 0.0
        categories.put("invalid_nan", Double.NaN); // should be skipped or 0.0
        categories.put("invalid_inf", Double.POSITIVE_INFINITY); // should be skipped or 0.0

        UserShoppingSignals signals = UserShoppingSignals.builder()
                .userId(testUserId)
                .categoryAffinity(categories)
                .build();

        PersonalizationContext context = adapter.mapToContext(testUserId, signals);

        for (PersonalizationSignal signal : context.getSignals()) {
            double val = signal.strength().value();
            assertTrue(val >= 0.0 && val <= 1.0);
            assertFalse(Double.isNaN(val));
            assertFalse(Double.isInfinite(val));
        }

        assertEquals(0.85, context.getCategoryAffinity("audio"));
        assertEquals(1.0, context.getCategoryAffinity("shoes"));
        assertEquals(0.0, context.getCategoryAffinity("invalid_neg"));
    }

    @Test
    @DisplayName("H. Missing behavioral history returns empty context with zero signals")
    void testMissingBehavioralHistory() {
        UserShoppingSignals emptySignals = UserShoppingSignals.neutral();
        emptySignals.setUserId(testUserId);

        when(behavioralSignalService.extractSignals(testUserId)).thenReturn(emptySignals);

        PersonalizationContext context = adapter.getBehavioralSignalContext(testUserId);

        assertNotNull(context);
        assertEquals(testUserId, context.getUserId());
        assertTrue(context.isEmpty());
        assertEquals(0, context.signalCount());
        assertTrue(context.getCategoryAffinities().isEmpty());
        assertTrue(context.getBrandAffinities().isEmpty());
    }

    @Test
    @DisplayName("I. Partial behavioral data maps only available signals")
    void testPartialBehavioralData() {
        UserShoppingSignals signals = UserShoppingSignals.builder()
                .userId(testUserId)
                .brandAffinity(Map.of("sony", 0.65))
                .build();

        when(behavioralSignalService.extractSignals(testUserId)).thenReturn(signals);

        PersonalizationContext context = adapter.getBehavioralSignalContext(testUserId);

        assertEquals(1, context.signalCount());
        assertEquals(0.65, context.getBrandAffinity("sony"));
        assertTrue(context.getCategoryAffinities().isEmpty());
    }

    @Test
    @DisplayName("J. Category and brand normalization trims whitespace and lower-cases keys")
    void testNormalization() {
        UserShoppingSignals signals = UserShoppingSignals.builder()
                .userId(testUserId)
                .categoryAffinity(Map.of("  SmartPhones  ", 0.9))
                .brandAffinity(Map.of("  Apple  ", 0.8))
                .build();

        when(behavioralSignalService.extractSignals(testUserId)).thenReturn(signals);

        PersonalizationContext context = adapter.getBehavioralSignalContext(testUserId);

        assertEquals(0.9, context.getCategoryAffinity("smartphones"));
        assertEquals(0.9, context.getCategoryAffinity("SmartPhones"));
        assertEquals(0.8, context.getBrandAffinity("apple"));
        assertEquals(0.8, context.getBrandAffinity("Apple"));
    }

    @Test
    @DisplayName("K. Determinism: identical behavioral signals produce equal contexts and identical hash codes")
    void testDeterminism() {
        UserShoppingSignals signals = UserShoppingSignals.builder()
                .userId(testUserId)
                .categoryAffinity(Map.of("audio", 0.7, "monitors", 0.8))
                .brandAffinity(Map.of("sony", 0.9, "dell", 0.6))
                .build();

        when(behavioralSignalService.extractSignals(testUserId)).thenReturn(signals);

        PersonalizationContext c1 = adapter.getBehavioralSignalContext(testUserId);
        PersonalizationContext c2 = adapter.getBehavioralSignalContext(testUserId);

        assertEquals(c1, c2);
        assertEquals(c1.hashCode(), c2.hashCode());
        assertEquals(c1.getSignals(), c2.getSignals());
    }

    @Test
    @DisplayName("L. User isolation: signals from user A never appear in user B's context")
    void testUserIsolation() {
        UUID userA = UUID.randomUUID();
        UUID userB = UUID.randomUUID();

        UserShoppingSignals signalsA = UserShoppingSignals.builder()
                .userId(userA)
                .brandAffinity(Map.of("apple", 0.9))
                .build();

        UserShoppingSignals signalsB = UserShoppingSignals.builder()
                .userId(userB)
                .brandAffinity(Map.of("samsung", 0.8))
                .build();

        when(behavioralSignalService.extractSignals(userA)).thenReturn(signalsA);
        when(behavioralSignalService.extractSignals(userB)).thenReturn(signalsB);

        PersonalizationContext cA = adapter.getBehavioralSignalContext(userA);
        PersonalizationContext cB = adapter.getBehavioralSignalContext(userB);

        assertEquals(userA, cA.getUserId());
        assertEquals(userB, cB.getUserId());
        assertEquals(0.9, cA.getBrandAffinity("apple"));
        assertEquals(0.0, cA.getBrandAffinity("samsung"));
        assertEquals(0.8, cB.getBrandAffinity("samsung"));
        assertEquals(0.0, cB.getBrandAffinity("apple"));
    }

    @Test
    @DisplayName("M. Failure handling: DataAccess / Runtime exception gracefully falls back to empty context")
    void testFailureHandlingDataAccessException() {
        when(behavioralSignalService.extractSignals(testUserId))
                .thenThrow(new DataAccessResourceFailureException("Database unavailable"));

        PersonalizationContext context = adapter.getBehavioralSignalContext(testUserId);

        assertNotNull(context);
        assertEquals(testUserId, context.getUserId());
        assertTrue(context.isEmpty());
    }

    @Test
    @DisplayName("M. Failure handling: Security / AccessDenied exceptions are NOT swallowed")
    void testFailureHandlingSecurityExceptionRethrown() {
        when(behavioralSignalService.extractSignals(testUserId))
                .thenThrow(new AccessDeniedException("Access denied to user behavioral signals"));

        assertThrows(AccessDeniedException.class, () -> adapter.getBehavioralSignalContext(testUserId));
    }

    @Test
    @DisplayName("N. Immutability of returned collections")
    void testImmutability() {
        UserShoppingSignals signals = UserShoppingSignals.builder()
                .userId(testUserId)
                .categoryAffinity(Map.of("audio", 0.8))
                .build();

        when(behavioralSignalService.extractSignals(testUserId)).thenReturn(signals);

        PersonalizationContext context = adapter.getBehavioralSignalContext(testUserId);

        assertThrows(UnsupportedOperationException.class, () ->
                context.getSignals().add(PersonalizationSignal.behavioral(PersonalizationSignalType.BRAND_AFFINITY, "sony", "Sony", 0.5))
        );
        assertThrows(UnsupportedOperationException.class, () ->
                context.getCategoryAffinities().put("shoes", 0.5)
        );
        assertThrows(UnsupportedOperationException.class, () ->
                context.getBrandAffinities().put("apple", 0.5)
        );
    }

    @Test
    @DisplayName("O. Privacy: raw interaction counts and event history are NOT exposed through context")
    void testPrivacyNoRawEventsInContext() {
        UserShoppingSignals signals = UserShoppingSignals.builder()
                .userId(testUserId)
                .categoryAffinity(Map.of("smartphones", 0.9))
                .interactionCountsByType(Map.of(InteractionType.SEARCH, 20, InteractionType.PRODUCT_VIEW, 50))
                .totalInteractions(70)
                .build();

        when(behavioralSignalService.extractSignals(testUserId)).thenReturn(signals);

        PersonalizationContext context = adapter.getBehavioralSignalContext(testUserId);

        // Context contains only normalized affinity signals, not raw counters or timestamps
        assertEquals(1, context.signalCount());
        assertEquals(0.9, context.getCategoryAffinity("smartphones"));
        assertEquals("PersonalizationContext{userId=" + testUserId + ", signalCount=1, explicitCount=0, behavioralCount=1}", context.toString());
    }

    @Test
    @DisplayName("P. Null userId gracefully returns empty context with null user")
    void testNullUserIdHandling() {
        PersonalizationContext context = adapter.getBehavioralSignalContext(null);
        assertNotNull(context);
        assertNull(context.getUserId());
        assertTrue(context.isEmpty());
    }
}
