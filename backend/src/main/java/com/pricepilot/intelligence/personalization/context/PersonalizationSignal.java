package com.pricepilot.intelligence.personalization.context;

import java.io.Serializable;
import java.util.Collections;
import java.util.Comparator;
import java.util.Map;
import java.util.Objects;

/**
 * Immutable value object representing a single normalized personalization signal.
 * Encapsulates the signal type, provenance source, target key, normalized string representation,
 * bounded strength, and optional metadata.
 */
public record PersonalizationSignal(
        PersonalizationSignalType signalType,
        PersonalizationSource source,
        String targetKey,
        String normalizedValue,
        SignalStrength strength,
        Map<String, String> metadata
) implements Comparable<PersonalizationSignal>, Serializable {

    private static final Comparator<PersonalizationSignal> COMPARATOR = Comparator
            .comparing(PersonalizationSignal::signalType)
            .thenComparing(PersonalizationSignal::source)
            .thenComparing(PersonalizationSignal::targetKey, Comparator.nullsFirst(String::compareTo))
            .thenComparing(PersonalizationSignal::normalizedValue, Comparator.nullsFirst(String::compareTo))
            .thenComparing(PersonalizationSignal::strength);

    public PersonalizationSignal {
        Objects.requireNonNull(signalType, "Signal type cannot be null");
        Objects.requireNonNull(source, "Signal source cannot be null");
        Objects.requireNonNull(normalizedValue, "Normalized value cannot be null");

        String trimmedValue = normalizedValue.trim();
        if (trimmedValue.isEmpty()) {
            throw new IllegalArgumentException("Normalized value cannot be blank");
        }
        normalizedValue = trimmedValue;

        if (targetKey != null) {
            String trimmedKey = targetKey.trim();
            targetKey = trimmedKey.isEmpty() ? null : trimmedKey.toLowerCase();
        }

        strength = strength != null ? strength : SignalStrength.DEFAULT_EXPLICIT;
        metadata = metadata != null ? Map.copyOf(metadata) : Collections.emptyMap();
    }

    public static PersonalizationSignal of(
            PersonalizationSignalType signalType,
            PersonalizationSource source,
            String targetKey,
            String normalizedValue,
            SignalStrength strength) {
        return new PersonalizationSignal(signalType, source, targetKey, normalizedValue, strength, Collections.emptyMap());
    }

    public static PersonalizationSignal of(
            PersonalizationSignalType signalType,
            PersonalizationSource source,
            String targetKey,
            String normalizedValue,
            double strengthValue) {
        return new PersonalizationSignal(signalType, source, targetKey, normalizedValue, SignalStrength.of(strengthValue), Collections.emptyMap());
    }

    public static PersonalizationSignal explicit(
            PersonalizationSignalType signalType,
            String targetKey,
            String normalizedValue) {
        return new PersonalizationSignal(signalType, PersonalizationSource.EXPLICIT_PREFERENCE, targetKey, normalizedValue, SignalStrength.DEFAULT_EXPLICIT, Collections.emptyMap());
    }

    public static PersonalizationSignal behavioral(
            PersonalizationSignalType signalType,
            String targetKey,
            String normalizedValue,
            double strengthValue) {
        return new PersonalizationSignal(signalType, PersonalizationSource.BEHAVIORAL_SIGNAL, targetKey, normalizedValue, SignalStrength.of(strengthValue), Collections.emptyMap());
    }

    @Override
    public int compareTo(PersonalizationSignal other) {
        if (other == null) {
            return 1;
        }
        return COMPARATOR.compare(this, other);
    }
}
