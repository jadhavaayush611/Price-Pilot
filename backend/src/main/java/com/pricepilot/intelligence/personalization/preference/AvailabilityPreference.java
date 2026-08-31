package com.pricepilot.intelligence.personalization.preference;

public enum AvailabilityPreference {
    ALL,
    IN_STOCK_ONLY;

    public static AvailabilityPreference fromString(String value) {
        if (value == null || value.trim().isEmpty()) {
            return ALL;
        }
        try {
            return AvailabilityPreference.valueOf(value.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return ALL;
        }
    }
}
