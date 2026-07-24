package com.pricepilot.intelligence.recommendation.dto;

import com.pricepilot.product.dto.ProductResponseDTO;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Data Transfer Object for recommendation engine v2 output response.
 */
public class RecommendationResponse {

    private UUID targetProductId;
    private UUID userId;
    private List<ProductResponseDTO> recommendedProducts;
    private List<ProductScore> scores;
    private String explanation;
    private String strategyUsed;
    private LocalDateTime generatedAt;

    public RecommendationResponse() {
    }

    public RecommendationResponse(UUID targetProductId, UUID userId, List<ProductResponseDTO> recommendedProducts, List<ProductScore> scores, String explanation, String strategyUsed, LocalDateTime generatedAt) {
        this.targetProductId = targetProductId;
        this.userId = userId;
        this.recommendedProducts = recommendedProducts;
        this.scores = scores;
        this.explanation = explanation;
        this.strategyUsed = strategyUsed;
        this.generatedAt = generatedAt;
    }

    public UUID getTargetProductId() {
        return targetProductId;
    }

    public void setTargetProductId(UUID targetProductId) {
        this.targetProductId = targetProductId;
    }

    public UUID getUserId() {
        return userId;
    }

    public void setUserId(UUID userId) {
        this.userId = userId;
    }

    public List<ProductResponseDTO> getRecommendedProducts() {
        return recommendedProducts;
    }

    public void setRecommendedProducts(List<ProductResponseDTO> recommendedProducts) {
        this.recommendedProducts = recommendedProducts;
    }

    public List<ProductScore> getScores() {
        return scores;
    }

    public void setScores(List<ProductScore> scores) {
        this.scores = scores;
    }

    public String getExplanation() {
        return explanation;
    }

    public void setExplanation(String explanation) {
        this.explanation = explanation;
    }

    public String getStrategyUsed() {
        return strategyUsed;
    }

    public void setStrategyUsed(String strategyUsed) {
        this.strategyUsed = strategyUsed;
    }

    public LocalDateTime getGeneratedAt() {
        return generatedAt;
    }

    public void setGeneratedAt(LocalDateTime generatedAt) {
        this.generatedAt = generatedAt;
    }
}
