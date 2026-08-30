package com.pricepilot.intelligence.analytics.model;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Immutable decoupled representation of a raw historical price observation.
 */
public record RawPriceObservation(
        BigDecimal price,
        LocalDateTime observedAt,
        UUID sellerId,
        String sellerName
) {
}
