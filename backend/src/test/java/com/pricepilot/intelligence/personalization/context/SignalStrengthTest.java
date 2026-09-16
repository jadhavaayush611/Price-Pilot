package com.pricepilot.intelligence.personalization.context;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("SignalStrength Domain Value Object Tests")
class SignalStrengthTest {

    @Test
    @DisplayName("Valid bounded strength construction")
    void testValidStrengthConstruction() {
        SignalStrength s0 = SignalStrength.of(0.0);
        SignalStrength sHalf = SignalStrength.of(0.5);
        SignalStrength s1 = SignalStrength.of(1.0);

        assertEquals(0.0, s0.value());
        assertEquals(0.5, sHalf.value());
        assertEquals(1.0, s1.value());
        assertEquals(SignalStrength.MIN, s0);
        assertEquals(SignalStrength.MAX, s1);
    }

    @ParameterizedTest
    @ValueSource(doubles = {-0.01, -1.0, -100.0, 1.01, 2.0, 100.0})
    @DisplayName("Rejects out of bounds strength values")
    void testOutOfBoundsStrengthRejected(double invalidVal) {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> SignalStrength.of(invalidVal));
        assertTrue(ex.getMessage().contains("must be bounded between 0.0 and 1.0"));
    }

    @Test
    @DisplayName("Rejects NaN strength")
    void testNanStrengthRejected() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> SignalStrength.of(Double.NaN));
        assertTrue(ex.getMessage().contains("cannot be NaN"));
    }

    @Test
    @DisplayName("Rejects positive and negative infinity")
    void testInfiniteStrengthRejected() {
        IllegalArgumentException exPos = assertThrows(IllegalArgumentException.class, () -> SignalStrength.of(Double.POSITIVE_INFINITY));
        assertTrue(exPos.getMessage().contains("cannot be infinite"));

        IllegalArgumentException exNeg = assertThrows(IllegalArgumentException.class, () -> SignalStrength.of(Double.NEGATIVE_INFINITY));
        assertTrue(exNeg.getMessage().contains("cannot be infinite"));
    }

    @Test
    @DisplayName("Implements Comparable correctly")
    void testComparable() {
        SignalStrength low = SignalStrength.of(0.2);
        SignalStrength high = SignalStrength.of(0.8);

        assertTrue(low.compareTo(high) < 0);
        assertTrue(high.compareTo(low) > 0);
        assertEquals(0, low.compareTo(SignalStrength.of(0.2)));
        assertEquals(1, low.compareTo(null));
    }

    @Test
    @DisplayName("Equality and hashCode contract")
    void testEqualsAndHashCode() {
        SignalStrength a = SignalStrength.of(0.75);
        SignalStrength b = SignalStrength.of(0.75);
        SignalStrength c = SignalStrength.of(0.80);

        assertEquals(a, b);
        assertEquals(a.hashCode(), b.hashCode());
        assertNotEquals(a, c);
    }
}
