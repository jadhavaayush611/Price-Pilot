package com.pricepilot.intelligence.personalization.context;

import com.pricepilot.intelligence.personalization.preference.AvailabilityPreference;
import com.pricepilot.intelligence.personalization.preference.DealSensitivity;
import com.pricepilot.intelligence.personalization.preference.PriceSensitivity;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("PersonalizationContextBuilder Tests")
class PersonalizationContextBuilderTest {

    @Test
    @DisplayName("Builds complete context from builder methods")
    void testBuildCompleteContext() {
        UUID userId = UUID.randomUUID();

        PersonalizationContext context = PersonalizationContext.builder(userId)
                .addPreferredCategory("  Laptops ")
                .addPreferredCategories(List.of("Audio", " Monitors "))
                .addPreferredBrand(" Sony ")
                .addPreferredBrands(List.of(" Apple ", "Dell"))
                .minBudget(BigDecimal.valueOf(200))
                .maxBudget(BigDecimal.valueOf(1200))
                .minRating(4.2)
                .dealSensitivity(DealSensitivity.HIGH)
                .priceSensitivity(PriceSensitivity.LOW)
                .availabilityPreference(AvailabilityPreference.IN_STOCK_ONLY)
                .addCategoryAffinity("Laptops", 0.95)
                .addBrandAffinity("Apple", 0.85)
                .build();

        assertEquals(userId, context.getUserId());
        assertTrue(context.getPreferredCategories().contains("laptops"));
        assertTrue(context.getPreferredCategories().contains("audio"));
        assertTrue(context.getPreferredCategories().contains("monitors"));
        assertTrue(context.getPreferredBrands().contains("sony"));
        assertTrue(context.getPreferredBrands().contains("apple"));
        assertTrue(context.getPreferredBrands().contains("dell"));

        assertEquals(BigDecimal.valueOf(200), context.getMinBudget().orElse(null));
        assertEquals(BigDecimal.valueOf(1200), context.getMaxBudget().orElse(null));
        assertEquals(4.2, context.getMinRating().orElse(0.0));
        assertEquals(DealSensitivity.HIGH, context.getDealSensitivity().orElse(null));
        assertEquals(PriceSensitivity.LOW, context.getPriceSensitivity().orElse(null));
        assertEquals(AvailabilityPreference.IN_STOCK_ONLY, context.getAvailabilityPreference().orElse(null));

        assertEquals(0.95, context.getCategoryAffinity("laptops"));
        assertEquals(0.85, context.getBrandAffinity("apple"));
    }

    @Test
    @DisplayName("Builder handles blank and whitespace entries gracefully")
    void testBlankHandling() {
        UUID userId = UUID.randomUUID();
        PersonalizationContext context = PersonalizationContext.builder(userId)
                .addPreferredCategory("  ")
                .addPreferredCategory(null)
                .addPreferredBrand("   ")
                .addPreferredBrand(null)
                .addCategoryAffinity("   ", 0.5)
                .addCategoryAffinity(null, 0.5)
                .addBrandAffinity("   ", 0.5)
                .addBrandAffinity(null, 0.5)
                .build();

        assertTrue(context.isEmpty());
        assertEquals(0, context.signalCount());
    }

    @Test
    @DisplayName("Builder rejects minBudget exceeding maxBudget")
    void testInvalidBudgetRangeRejected() {
        UUID userId = UUID.randomUUID();
        PersonalizationContextBuilder builder = PersonalizationContext.builder(userId)
                .minBudget(BigDecimal.valueOf(1000))
                .maxBudget(BigDecimal.valueOf(500));

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, builder::build);
        assertTrue(ex.getMessage().contains("cannot exceed maximum budget"));
    }

    @ParameterizedTest
    @ValueSource(doubles = {-0.1, 5.1, 10.0, -5.0})
    @DisplayName("Builder rejects invalid minRating values outside 0.0-5.0")
    void testInvalidMinRatingRejected(double invalidRating) {
        PersonalizationContextBuilder builder = new PersonalizationContextBuilder();
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> builder.minRating(invalidRating));
        assertTrue(ex.getMessage().contains("Min rating must be between 0.0 and 5.0"));
    }

    @Test
    @DisplayName("Builder rejects NaN and infinite minRating")
    void testNanMinRatingRejected() {
        PersonalizationContextBuilder builder = new PersonalizationContextBuilder();
        assertThrows(IllegalArgumentException.class, () -> builder.minRating(Double.NaN));
        assertThrows(IllegalArgumentException.class, () -> builder.minRating(Double.POSITIVE_INFINITY));
        assertThrows(IllegalArgumentException.class, () -> builder.minRating(Double.NEGATIVE_INFINITY));
    }

    @Test
    @DisplayName("Deduplication replaces lower strength duplicate signal with higher strength")
    void testDeduplicationKeepsHigherStrength() {
        UUID userId = UUID.randomUUID();

        PersonalizationSignal lower = PersonalizationSignal.behavioral(
                PersonalizationSignalType.BRAND_AFFINITY,
                "apple",
                "Apple",
                0.40
        );
        PersonalizationSignal higher = PersonalizationSignal.behavioral(
                PersonalizationSignalType.BRAND_AFFINITY,
                "apple",
                "Apple",
                0.85
        );

        PersonalizationContext context = PersonalizationContext.builder(userId)
                .addSignal(lower)
                .addSignal(higher)
                .build();

        assertEquals(1, context.signalCount());
        assertEquals(0.85, context.getBrandAffinity("apple"));
    }

    @Test
    @DisplayName("Construction is deterministic regardless of builder invocation sequence")
    void testDeterministicConstruction() {
        UUID userId = UUID.randomUUID();

        PersonalizationContext c1 = PersonalizationContext.builder(userId)
                .addPreferredCategory("Audio")
                .addPreferredBrand("Sony")
                .minBudget(BigDecimal.valueOf(100))
                .maxBudget(BigDecimal.valueOf(500))
                .addBrandAffinity("Apple", 0.7)
                .build();

        PersonalizationContext c2 = PersonalizationContext.builder(userId)
                .addBrandAffinity("Apple", 0.7)
                .maxBudget(BigDecimal.valueOf(500))
                .addPreferredBrand("Sony")
                .minBudget(BigDecimal.valueOf(100))
                .addPreferredCategory("Audio")
                .build();

        assertEquals(c1, c2);
        assertEquals(c1.hashCode(), c2.hashCode());
        assertEquals(c1.getSignals(), c2.getSignals());
    }
}
