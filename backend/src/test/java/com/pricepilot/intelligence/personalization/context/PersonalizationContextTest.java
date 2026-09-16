package com.pricepilot.intelligence.personalization.context;

import com.pricepilot.intelligence.personalization.preference.AvailabilityPreference;
import com.pricepilot.intelligence.personalization.preference.DealSensitivity;
import com.pricepilot.intelligence.personalization.preference.PriceSensitivity;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("PersonalizationContext Domain Model Tests")
class PersonalizationContextTest {

    @Test
    @DisplayName("Empty context initialization")
    void testEmptyContext() {
        UUID userId = UUID.randomUUID();
        PersonalizationContext context = PersonalizationContext.empty(userId);

        assertEquals(userId, context.getUserId());
        assertTrue(context.isEmpty());
        assertFalse(context.hasSignals());
        assertEquals(0, context.signalCount());
        assertTrue(context.getSignals().isEmpty());
        assertTrue(context.getExplicitSignals().isEmpty());
        assertTrue(context.getBehavioralSignals().isEmpty());
        assertTrue(context.getPreferredCategories().isEmpty());
        assertTrue(context.getPreferredBrands().isEmpty());
        assertTrue(context.getMinBudget().isEmpty());
        assertTrue(context.getMaxBudget().isEmpty());
        assertTrue(context.getMinRating().isEmpty());
        assertTrue(context.getDealSensitivity().isEmpty());
        assertTrue(context.getPriceSensitivity().isEmpty());
        assertTrue(context.getAvailabilityPreference().isEmpty());
        assertEquals(0.0, context.getCategoryAffinity("electronics"));
        assertEquals(0.0, context.getBrandAffinity("apple"));
    }

    @Test
    @DisplayName("Populated context resolves explicit preferences and behavioral affinities")
    void testPopulatedContext() {
        UUID userId = UUID.randomUUID();

        List<PersonalizationSignal> signals = List.of(
                PersonalizationSignal.explicit(PersonalizationSignalType.CATEGORY_PREFERENCE, "smartphones", "Smartphones"),
                PersonalizationSignal.explicit(PersonalizationSignalType.CATEGORY_PREFERENCE, "laptops", "Laptops"),
                PersonalizationSignal.explicit(PersonalizationSignalType.BRAND_PREFERENCE, "apple", "Apple"),
                PersonalizationSignal.explicit(PersonalizationSignalType.BUDGET_PREFERENCE, "budget", "500.00:1500.00"),
                PersonalizationSignal.explicit(PersonalizationSignalType.RATING_PREFERENCE, "min_rating", "4.5"),
                PersonalizationSignal.explicit(PersonalizationSignalType.DEAL_SENSITIVITY, "deal_sensitivity", "HIGH"),
                PersonalizationSignal.explicit(PersonalizationSignalType.PRICE_SENSITIVITY, "price_sensitivity", "MEDIUM"),
                PersonalizationSignal.explicit(PersonalizationSignalType.AVAILABILITY_PREFERENCE, "availability_preference", "IN_STOCK_ONLY"),
                PersonalizationSignal.behavioral(PersonalizationSignalType.CATEGORY_AFFINITY, "audio", "Audio", 0.75),
                PersonalizationSignal.behavioral(PersonalizationSignalType.BRAND_AFFINITY, "sony", "Sony", 0.80)
        );

        PersonalizationContext context = new PersonalizationContext(userId, signals);

        assertEquals(userId, context.getUserId());
        assertFalse(context.isEmpty());
        assertTrue(context.hasSignals());
        assertEquals(10, context.signalCount());

        assertEquals(8, context.getExplicitSignals().size());
        assertEquals(2, context.getBehavioralSignals().size());

        assertTrue(context.getPreferredCategories().contains("smartphones"));
        assertTrue(context.getPreferredCategories().contains("laptops"));
        assertTrue(context.getPreferredBrands().contains("apple"));

        assertEquals(Optional.of(new BigDecimal("500.00")), context.getMinBudget().map(b -> b.setScale(2)));
        assertEquals(Optional.of(new BigDecimal("1500.00")), context.getMaxBudget().map(b -> b.setScale(2)));
        assertEquals(4.5, context.getMinRating().orElse(0.0));
        assertEquals(DealSensitivity.HIGH, context.getDealSensitivity().orElse(null));
        assertEquals(PriceSensitivity.MEDIUM, context.getPriceSensitivity().orElse(null));
        assertEquals(AvailabilityPreference.IN_STOCK_ONLY, context.getAvailabilityPreference().orElse(null));

        assertEquals(0.75, context.getCategoryAffinity("audio"));
        assertEquals(0.75, context.getCategoryAffinity("AUDIO"));
        assertEquals(0.80, context.getBrandAffinity("sony"));
        assertEquals(0.0, context.getBrandAffinity("samsung"));
    }

    @Test
    @DisplayName("Context collections are strictly immutable")
    void testImmutabilityOfReturnedCollections() {
        UUID userId = UUID.randomUUID();
        PersonalizationContext context = PersonalizationContext.builder(userId)
                .addPreferredCategory("Electronics")
                .addPreferredBrand("Apple")
                .addCategoryAffinity("Shoes", 0.5)
                .build();

        assertThrows(UnsupportedOperationException.class, () -> context.getSignals().add(
                PersonalizationSignal.explicit(PersonalizationSignalType.BRAND_PREFERENCE, "bose", "Bose")
        ));
        assertThrows(UnsupportedOperationException.class, () -> context.getPreferredCategories().add("Books"));
        assertThrows(UnsupportedOperationException.class, () -> context.getPreferredBrands().add("Sony"));
        assertThrows(UnsupportedOperationException.class, () -> context.getCategoryAffinities().put("gaming", 0.9));
        assertThrows(UnsupportedOperationException.class, () -> context.getBrandAffinities().put("dell", 0.8));
    }

    @Test
    @DisplayName("Context equality and deterministic ordering")
    void testEqualityAndDeterministicOrdering() {
        UUID userId = UUID.randomUUID();

        PersonalizationSignal s1 = PersonalizationSignal.explicit(PersonalizationSignalType.CATEGORY_PREFERENCE, "smartphones", "Smartphones");
        PersonalizationSignal s2 = PersonalizationSignal.explicit(PersonalizationSignalType.BRAND_PREFERENCE, "apple", "Apple");
        PersonalizationSignal s3 = PersonalizationSignal.behavioral(PersonalizationSignalType.BRAND_AFFINITY, "sony", "Sony", 0.9);

        // Context constructed with order (s1, s2, s3)
        PersonalizationContext c1 = new PersonalizationContext(userId, List.of(s1, s2, s3));

        // Context constructed with reversed order (s3, s2, s1)
        PersonalizationContext c2 = new PersonalizationContext(userId, List.of(s3, s2, s1));

        assertEquals(c1, c2);
        assertEquals(c1.hashCode(), c2.hashCode());
        assertEquals(c1.getSignals(), c2.getSignals());
    }

    @Test
    @DisplayName("Different users produce non-equal contexts even with same signals")
    void testUserScoping() {
        UUID userA = UUID.randomUUID();
        UUID userB = UUID.randomUUID();

        PersonalizationSignal signal = PersonalizationSignal.explicit(PersonalizationSignalType.BRAND_PREFERENCE, "apple", "Apple");

        PersonalizationContext cA = new PersonalizationContext(userA, List.of(signal));
        PersonalizationContext cB = new PersonalizationContext(userB, List.of(signal));

        assertNotEquals(cA, cB);
        assertNotEquals(cA.hashCode(), cB.hashCode());
    }
}
