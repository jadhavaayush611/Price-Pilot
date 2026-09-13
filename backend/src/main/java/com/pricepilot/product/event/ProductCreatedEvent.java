package com.pricepilot.product.event;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Domain event published when a new product is created.
 */
public record ProductCreatedEvent(
        UUID productId,
        String name,
        String brand,
        String category,
        String description,
        boolean archived,
        LocalDateTime timestamp
) implements Serializable {
    public ProductCreatedEvent(UUID productId, String name, String brand, String category, String description, boolean archived) {
        this(productId, name, brand, category, description, archived, LocalDateTime.now());
    }
}
