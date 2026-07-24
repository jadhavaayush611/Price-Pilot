package com.pricepilot.intelligence.comparison.dto;

import com.pricepilot.product.dto.ProductResponseDTO;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Data Transfer Object for saved comparison records.
 */
public class SavedComparisonResponseDTO {

    private UUID id;
    private UUID userId;
    private UUID sessionId;
    private String name;
    private List<UUID> productIds;
    private String notes;
    private LocalDateTime createdAt;
    private List<ProductResponseDTO> products;

    public SavedComparisonResponseDTO() {
    }

    public SavedComparisonResponseDTO(UUID id, UUID userId, UUID sessionId, String name, List<UUID> productIds, String notes, LocalDateTime createdAt, List<ProductResponseDTO> products) {
        this.id = id;
        this.userId = userId;
        this.sessionId = sessionId;
        this.name = name;
        this.productIds = productIds;
        this.notes = notes;
        this.createdAt = createdAt;
        this.products = products;
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public UUID getUserId() {
        return userId;
    }

    public void setUserId(UUID userId) {
        this.userId = userId;
    }

    public UUID getSessionId() {
        return sessionId;
    }

    public void setSessionId(UUID sessionId) {
        this.sessionId = sessionId;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public List<UUID> getProductIds() {
        return productIds;
    }

    public void setProductIds(List<UUID> productIds) {
        this.productIds = productIds;
    }

    public String getNotes() {
        return notes;
    }

    public void setNotes(String notes) {
        this.notes = notes;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public List<ProductResponseDTO> getProducts() {
        return products;
    }

    public void setProducts(List<ProductResponseDTO> products) {
        this.products = products;
    }
}
