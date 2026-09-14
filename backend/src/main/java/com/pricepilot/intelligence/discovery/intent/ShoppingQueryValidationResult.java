package com.pricepilot.intelligence.discovery.intent;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.ToString;

import java.io.Serializable;
import java.util.Collections;
import java.util.List;

/**
 * Result object produced after validating a ShoppingQueryIntent.
 */
@Getter
@ToString
@Builder
@AllArgsConstructor
public final class ShoppingQueryValidationResult implements Serializable {

    private static final long serialVersionUID = 1L;

    private final boolean valid;
    private final boolean hasConflicts;
    private final List<String> errors;
    private final ShoppingQueryIntent normalizedIntent;

    public static ShoppingQueryValidationResult success(ShoppingQueryIntent intent) {
        return ShoppingQueryValidationResult.builder()
                .valid(true)
                .hasConflicts(false)
                .errors(Collections.emptyList())
                .normalizedIntent(intent)
                .build();
    }

    public static ShoppingQueryValidationResult conflict(ShoppingQueryIntent intent, List<String> conflictErrors) {
        return ShoppingQueryValidationResult.builder()
                .valid(false)
                .hasConflicts(true)
                .errors(conflictErrors != null ? conflictErrors : Collections.emptyList())
                .normalizedIntent(intent)
                .build();
    }

    public static ShoppingQueryValidationResult invalid(ShoppingQueryIntent intent, List<String> errors) {
        return ShoppingQueryValidationResult.builder()
                .valid(false)
                .hasConflicts(false)
                .errors(errors != null ? errors : Collections.emptyList())
                .normalizedIntent(intent)
                .build();
    }
}
