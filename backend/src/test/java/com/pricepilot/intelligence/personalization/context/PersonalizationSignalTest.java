package com.pricepilot.intelligence.personalization.context;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("PersonalizationSignal Domain Value Object Tests")
class PersonalizationSignalTest {

    @Test
    @DisplayName("Valid explicit signal construction")
    void testValidExplicitSignalConstruction() {
        PersonalizationSignal signal = PersonalizationSignal.explicit(
                PersonalizationSignalType.CATEGORY_PREFERENCE,
                "Electronics",
                "Electronics"
        );

        assertEquals(PersonalizationSignalType.CATEGORY_PREFERENCE, signal.signalType());
        assertEquals(PersonalizationSource.EXPLICIT_PREFERENCE, signal.source());
        assertEquals("electronics", signal.targetKey());
        assertEquals("Electronics", signal.normalizedValue());
        assertEquals(SignalStrength.DEFAULT_EXPLICIT, signal.strength());
        assertTrue(signal.metadata().isEmpty());
    }

    @Test
    @DisplayName("Valid behavioral signal construction")
    void testValidBehavioralSignalConstruction() {
        PersonalizationSignal signal = PersonalizationSignal.behavioral(
                PersonalizationSignalType.BRAND_AFFINITY,
                "Apple",
                "Apple",
                0.85
        );

        assertEquals(PersonalizationSignalType.BRAND_AFFINITY, signal.signalType());
        assertEquals(PersonalizationSource.BEHAVIORAL_SIGNAL, signal.source());
        assertEquals("apple", signal.targetKey());
        assertEquals("Apple", signal.normalizedValue());
        assertEquals(0.85, signal.strength().value());
        assertTrue(signal.metadata().isEmpty());
    }

    @Test
    @DisplayName("Rejects null signal type")
    void testNullSignalTypeRejected() {
        NullPointerException ex = assertThrows(NullPointerException.class, () ->
                new PersonalizationSignal(null, PersonalizationSource.EXPLICIT_PREFERENCE, "key", "val", SignalStrength.MAX, null)
        );
        assertTrue(ex.getMessage().contains("Signal type cannot be null"));
    }

    @Test
    @DisplayName("Rejects null source")
    void testNullSourceRejected() {
        NullPointerException ex = assertThrows(NullPointerException.class, () ->
                new PersonalizationSignal(PersonalizationSignalType.BRAND_PREFERENCE, null, "key", "val", SignalStrength.MAX, null)
        );
        assertTrue(ex.getMessage().contains("Signal source cannot be null"));
    }

    @Test
    @DisplayName("Rejects null normalized value")
    void testNullNormalizedValueRejected() {
        NullPointerException ex = assertThrows(NullPointerException.class, () ->
                new PersonalizationSignal(PersonalizationSignalType.BRAND_PREFERENCE, PersonalizationSource.EXPLICIT_PREFERENCE, "key", null, SignalStrength.MAX, null)
        );
        assertTrue(ex.getMessage().contains("Normalized value cannot be null"));
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   ", "\t\n"})
    @DisplayName("Rejects blank or whitespace normalized value")
    void testBlankNormalizedValueRejected(String blankVal) {
        if (blankVal == null) {
            assertThrows(NullPointerException.class, () ->
                    new PersonalizationSignal(PersonalizationSignalType.BRAND_PREFERENCE, PersonalizationSource.EXPLICIT_PREFERENCE, "key", null, SignalStrength.MAX, null)
            );
        } else {
            IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () ->
                    new PersonalizationSignal(PersonalizationSignalType.BRAND_PREFERENCE, PersonalizationSource.EXPLICIT_PREFERENCE, "key", blankVal, SignalStrength.MAX, null)
            );
            assertTrue(ex.getMessage().contains("Normalized value cannot be blank"));
        }
    }

    @Test
    @DisplayName("Normalizes targetKey to lower-case and trims whitespace")
    void testTargetKeyNormalization() {
        PersonalizationSignal signal = PersonalizationSignal.explicit(
                PersonalizationSignalType.BRAND_PREFERENCE,
                "  Sony  ",
                "Sony"
        );
        assertEquals("sony", signal.targetKey());

        PersonalizationSignal blankKeySignal = PersonalizationSignal.explicit(
                PersonalizationSignalType.DEAL_SENSITIVITY,
                "   ",
                "HIGH"
        );
        assertNull(blankKeySignal.targetKey());
    }

    @Test
    @DisplayName("Metadata is defensively copied and unmodifiable")
    void testMetadataImmutability() {
        Map<String, String> meta = Map.of("key1", "val1");
        PersonalizationSignal signal = new PersonalizationSignal(
                PersonalizationSignalType.CATEGORY_PREFERENCE,
                PersonalizationSource.EXPLICIT_PREFERENCE,
                "category",
                "Shoes",
                SignalStrength.MAX,
                meta
        );

        assertThrows(UnsupportedOperationException.class, () -> signal.metadata().put("key2", "val2"));
    }

    @Test
    @DisplayName("Preserves explicit vs behavioral provenance distinction")
    void testProvenancePreservation() {
        PersonalizationSignal explicit = PersonalizationSignal.explicit(
                PersonalizationSignalType.CATEGORY_PREFERENCE,
                "laptops",
                "Laptops"
        );
        PersonalizationSignal behavioral = PersonalizationSignal.behavioral(
                PersonalizationSignalType.CATEGORY_AFFINITY,
                "laptops",
                "Laptops",
                0.90
        );

        assertNotEquals(explicit.source(), behavioral.source());
        assertEquals(PersonalizationSource.EXPLICIT_PREFERENCE, explicit.source());
        assertEquals(PersonalizationSource.BEHAVIORAL_SIGNAL, behavioral.source());
        assertNotEquals(explicit, behavioral);
    }

    @Test
    @DisplayName("Deterministic comparison contract")
    void testComparisonContract() {
        PersonalizationSignal sigCategory = PersonalizationSignal.explicit(PersonalizationSignalType.CATEGORY_PREFERENCE, "laptops", "Laptops");
        PersonalizationSignal sigBrand = PersonalizationSignal.explicit(PersonalizationSignalType.BRAND_PREFERENCE, "apple", "Apple");

        assertTrue(sigCategory.compareTo(sigBrand) < 0);
        assertTrue(sigBrand.compareTo(sigCategory) > 0);
        assertEquals(0, sigCategory.compareTo(sigCategory));
        assertEquals(1, sigCategory.compareTo(null));
    }
}
