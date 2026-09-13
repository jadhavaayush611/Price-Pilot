package com.pricepilot.product.event;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Domain event published when a product entity is updated.
 * Holds both previous and updated states to enable zero-overhead semantic diffing.
 */
public record ProductUpdatedEvent(
        UUID productId,
        String oldName,
        String oldBrand,
        String oldCategory,
        String oldDescription,
        boolean oldArchived,
        String newName,
        String newBrand,
        String newCategory,
        String newDescription,
        boolean newArchived,
        LocalDateTime timestamp
) implements Serializable {
    public ProductUpdatedEvent(
            UUID productId,
            String oldName,
            String oldBrand,
            String oldCategory,
            String oldDescription,
            boolean oldArchived,
            String newName,
            String newBrand,
            String newCategory,
            String newDescription,
            boolean newArchived) {
        this(productId, oldName, oldBrand, oldCategory, oldDescription, oldArchived,
                newName, newBrand, newCategory, newDescription, newArchived, LocalDateTime.now());
    }
}
