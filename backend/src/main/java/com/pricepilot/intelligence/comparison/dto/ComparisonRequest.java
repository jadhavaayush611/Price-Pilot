package com.pricepilot.intelligence.comparison.dto;

import java.util.List;
import java.util.UUID;

/**
 * Data Transfer Object for requesting a product comparison session or saving a comparison matrix.
 */
public class ComparisonRequest {

    private List<UUID> productIds;
    private String category;
    private List<String> criteria;
    private UUID userId;
    private String sessionToken;
    private String name;
    private String notes;

    public ComparisonRequest() {
    }

    public ComparisonRequest(List<UUID> productIds, String category, List<String> criteria, UUID userId, String sessionToken) {
        this.productIds = productIds;
        this.category = category;
        this.criteria = criteria;
        this.userId = userId;
        this.sessionToken = sessionToken;
    }

    public ComparisonRequest(List<UUID> productIds, String category, List<String> criteria, UUID userId, String sessionToken, String name, String notes) {
        this.productIds = productIds;
        this.category = category;
        this.criteria = criteria;
        this.userId = userId;
        this.sessionToken = sessionToken;
        this.name = name;
        this.notes = notes;
    }

    public List<UUID> getProductIds() {
        return productIds;
    }

    public void setProductIds(List<UUID> productIds) {
        this.productIds = productIds;
    }

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
    }

    public List<String> getCriteria() {
        return criteria;
    }

    public void setCriteria(List<String> criteria) {
        this.criteria = criteria;
    }

    public UUID getUserId() {
        return userId;
    }

    public void setUserId(UUID userId) {
        this.userId = userId;
    }

    public String getSessionToken() {
        return sessionToken;
    }

    public void setSessionToken(String sessionToken) {
        this.sessionToken = sessionToken;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getNotes() {
        return notes;
    }

    public void setNotes(String notes) {
        this.notes = notes;
    }
}
