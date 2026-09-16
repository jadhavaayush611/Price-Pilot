package com.pricepilot.intelligence.personalization.context;

import java.io.Serializable;

/**
 * Bounded domain value object representing the strength or confidence of a personalization signal.
 * Strictly enforces a normalized range of [0.0, 1.0] to prevent unbounded personalization influence.
 */
public record SignalStrength(double value) implements Comparable<SignalStrength>, Serializable {

    public static final SignalStrength MIN = new SignalStrength(0.0);
    public static final SignalStrength MAX = new SignalStrength(1.0);
    public static final SignalStrength DEFAULT_EXPLICIT = new SignalStrength(1.0);
    public static final SignalStrength NEUTRAL = new SignalStrength(0.5);

    public SignalStrength {
        if (Double.isNaN(value)) {
            throw new IllegalArgumentException("Signal strength cannot be NaN");
        }
        if (Double.isInfinite(value)) {
            throw new IllegalArgumentException("Signal strength cannot be infinite: " + value);
        }
        if (value < 0.0 || value > 1.0) {
            throw new IllegalArgumentException("Signal strength must be bounded between 0.0 and 1.0, got: " + value);
        }
    }

    public static SignalStrength of(double value) {
        return new SignalStrength(value);
    }

    @Override
    public int compareTo(SignalStrength other) {
        if (other == null) {
            return 1;
        }
        return Double.compare(this.value, other.value);
    }
}
