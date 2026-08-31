package com.pricepilot.intelligence.personalization.preference;

public enum PriceSensitivity {
    LOW,
    MEDIUM,
    HIGH;

    public static PriceSensitivity fromString(String value) {
        if (value == null || value.trim().isEmpty()) {
            return MEDIUM;
        }
        try {
            return PriceSensitivity.valueOf(value.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return MEDIUM;
        }
    }
}
