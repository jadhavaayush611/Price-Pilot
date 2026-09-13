package com.pricepilot.product.event;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Domain event published when a product entity is permanently deleted.
 */
public record ProductDeletedEvent(
        UUID productId,
        LocalDateTime timestamp
) implements Serializable {
    public ProductDeletedEvent(UUID productId) {
        this(productId, LocalDateTime.now());
    }
}
