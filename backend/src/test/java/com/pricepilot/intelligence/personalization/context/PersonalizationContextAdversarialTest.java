package com.pricepilot.intelligence.personalization.context;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("PersonalizationContext Adversarial & Resilience Tests")
class PersonalizationContextAdversarialTest {

    @ParameterizedTest
    @ValueSource(doubles = {Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY, -0.0001, 1.0001, -100.0, 100.0})
    @DisplayName("Rejects malformed and out-of-bounds signal strengths in signal factory")
    void testAdversarialSignalStrengths(double adversarialStrength) {
        assertThrows(IllegalArgumentException.class, () ->
                PersonalizationSignal.behavioral(PersonalizationSignalType.BRAND_AFFINITY, "brand", "Brand", adversarialStrength)
        );
    }

    @Test
    @DisplayName("External mutations to input list do not affect constructed PersonalizationContext")
    void testInputListMutationResilience() {
        UUID userId = UUID.randomUUID();
        List<PersonalizationSignal> mutableList = new ArrayList<>();
        mutableList.add(PersonalizationSignal.explicit(PersonalizationSignalType.CATEGORY_PREFERENCE, "audio", "Audio"));

        PersonalizationContext context = new PersonalizationContext(userId, mutableList);
        assertEquals(1, context.signalCount());

        // Mutate external list
        mutableList.add(PersonalizationSignal.explicit(PersonalizationSignalType.BRAND_PREFERENCE, "bose", "Bose"));
        mutableList.clear();

        // Context must remain unchanged
        assertEquals(1, context.signalCount());
        assertTrue(context.getPreferredCategories().contains("audio"));
    }

    @Test
    @DisplayName("Randomly shuffled signals produce strictly identical contexts")
    void testShuffledInputsProduceIdenticalContext() {
        UUID userId = UUID.randomUUID();

        List<PersonalizationSignal> originalSignals = List.of(
                PersonalizationSignal.explicit(PersonalizationSignalType.CATEGORY_PREFERENCE, "phones", "Phones"),
                PersonalizationSignal.explicit(PersonalizationSignalType.BRAND_PREFERENCE, "apple", "Apple"),
                PersonalizationSignal.explicit(PersonalizationSignalType.DEAL_SENSITIVITY, "deal_sensitivity", "HIGH"),
                PersonalizationSignal.behavioral(PersonalizationSignalType.CATEGORY_AFFINITY, "laptops", "Laptops", 0.8),
                PersonalizationSignal.behavioral(PersonalizationSignalType.BRAND_AFFINITY, "dell", "Dell", 0.6)
        );

        PersonalizationContext baseline = new PersonalizationContext(userId, originalSignals);

        for (int i = 0; i < 20; i++) {
            List<PersonalizationSignal> shuffled = new ArrayList<>(originalSignals);
            Collections.shuffle(shuffled, new Random(i * 42L));

            PersonalizationContext shuffledContext = new PersonalizationContext(userId, shuffled);

            assertEquals(baseline, shuffledContext, "Context failed equality check on iteration " + i);
            assertEquals(baseline.hashCode(), shuffledContext.hashCode(), "HashCode failed on iteration " + i);
            assertEquals(baseline.getSignals(), shuffledContext.getSignals(), "Signals ordering failed on iteration " + i);
        }
    }

    @Test
    @DisplayName("Explicit and behavioral signals on same entity do NOT collapse into one source")
    void testExplicitAndBehavioralCoexistWithoutCollapsing() {
        UUID userId = UUID.randomUUID();

        // User explicitly stated Apple is preferred, and also has high behavioral interaction with Apple
        PersonalizationSignal explicitSignal = PersonalizationSignal.explicit(
                PersonalizationSignalType.BRAND_PREFERENCE,
                "apple",
                "Apple"
        );
        PersonalizationSignal behavioralSignal = PersonalizationSignal.behavioral(
                PersonalizationSignalType.BRAND_AFFINITY,
                "apple",
                "Apple",
                0.95
        );

        PersonalizationContext context = PersonalizationContext.builder(userId)
                .addSignal(explicitSignal)
                .addSignal(behavioralSignal)
                .build();

        // Both signals must coexist with distinct provenance
        assertEquals(2, context.signalCount());
        assertEquals(1, context.getExplicitSignals().size());
        assertEquals(1, context.getBehavioralSignals().size());

        assertTrue(context.getPreferredBrands().contains("apple"));
        assertEquals(0.95, context.getBrandAffinity("apple"));

        PersonalizationSignal storedExplicit = context.getExplicitSignals().get(0);
        PersonalizationSignal storedBehavioral = context.getBehavioralSignals().get(0);

        assertEquals(PersonalizationSource.EXPLICIT_PREFERENCE, storedExplicit.source());
        assertEquals(PersonalizationSource.BEHAVIORAL_SIGNAL, storedBehavioral.source());
    }

    @Test
    @DisplayName("Null userId represents anonymous or system context without failing")
    void testNullUserContext() {
        PersonalizationContext context = PersonalizationContext.empty(null);
        assertNull(context.getUserId());
        assertTrue(context.isEmpty());
        assertNotNull(context.getSignals());
    }
}
