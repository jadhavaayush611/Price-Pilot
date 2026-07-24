package com.pricepilot.intelligence.comparison.dto;

import com.pricepilot.intelligence.recommendation.dto.ProductScore;
import com.pricepilot.product.dto.ProductResponseDTO;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Data Transfer Object containing full product matrix comparison results.
 */
public class ComparisonResponse {

    private UUID comparisonId;
    private List<ProductResponseDTO> products;
    private List<ComparisonRow> rows;
    private Map<UUID, ProductScore> scores;
    private String summary;
    private LocalDateTime createdAt;

    public ComparisonResponse() {
    }

    public ComparisonResponse(UUID comparisonId, List<ProductResponseDTO> products, List<ComparisonRow> rows, Map<UUID, ProductScore> scores, String summary, LocalDateTime createdAt) {
        this.comparisonId = comparisonId;
        this.products = products;
        this.rows = rows;
        this.scores = scores;
        this.summary = summary;
        this.createdAt = createdAt;
    }

    public UUID getComparisonId() {
        return comparisonId;
    }

    public void setComparisonId(UUID comparisonId) {
        this.comparisonId = comparisonId;
    }

    public List<ProductResponseDTO> getProducts() {
        return products;
    }

    public void setProducts(List<ProductResponseDTO> products) {
        this.products = products;
    }

    public List<ComparisonRow> getRows() {
        return rows;
    }

    public void setRows(List<ComparisonRow> rows) {
        this.rows = rows;
    }

    public Map<UUID, ProductScore> getScores() {
        return scores;
    }

    public void setScores(Map<UUID, ProductScore> scores) {
        this.scores = scores;
    }

    public String getSummary() {
        return summary;
    }

    public void setSummary(String summary) {
        this.summary = summary;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
