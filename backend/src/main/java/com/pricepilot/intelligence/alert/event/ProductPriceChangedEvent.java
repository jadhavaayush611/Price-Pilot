package com.pricepilot.intelligence.alert.event;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Event published when a product price changes or availability shifts.
 */
public record ProductPriceChangedEvent(
        UUID productId,
        UUID sellerId,
        BigDecimal oldPrice,
        BigDecimal newPrice,
        boolean isBackInStock,
        LocalDateTime timestamp
) {
}
