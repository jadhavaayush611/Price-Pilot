package com.pricepilot.intelligence.personalization.preference;

public enum DealSensitivity {
    LOW,
    MEDIUM,
    HIGH;

    public static DealSensitivity fromString(String value) {
        if (value == null || value.trim().isEmpty()) {
            return MEDIUM;
        }
        try {
            return DealSensitivity.valueOf(value.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return MEDIUM;
        }
    }
}
