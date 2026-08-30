package com.pricepilot.intelligence.recommendation.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Entity for storing audit and inference history of Explainable AI Recommendations.
 * Does not store raw prompts or sensitive user data.
 */
@Entity
@Table(name = "recommendation_history_events")
public class RecommendationHistoryEventEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "user_id")
    private UUID userId;

    @Column(name = "session_id", length = 100)
    private String sessionId;

    @Column(name = "target_product_id")
    private UUID targetProductId;

    @Column(name = "recommended_product_id", nullable = false)
    private UUID recommendedProductId;

    @Column(name = "product_ids", nullable = false, columnDefinition = "TEXT")
    private String productIds;

    @Column(name = "recommendation_type", nullable = false, length = 50)
    private String recommendationType;

    @Column(name = "scoring_strategy", nullable = false, length = 100)
    private String scoringStrategy;

    @Column(name = "explanation_strategy", nullable = false, length = 100)
    private String explanationStrategy;

    @Column(name = "score", nullable = false)
    private Double score;

    @Column(name = "confidence", nullable = false)
    private Double confidence;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    public RecommendationHistoryEventEntity() {
    }

    public RecommendationHistoryEventEntity(UUID id, UUID userId, String sessionId, UUID targetProductId,
                                          UUID recommendedProductId, String productIds, String recommendationType,
                                          String scoringStrategy, String explanationStrategy, Double score,
                                          Double confidence, LocalDateTime createdAt) {
        this.id = id;
        this.userId = userId;
        this.sessionId = sessionId;
        this.targetProductId = targetProductId;
        this.recommendedProductId = recommendedProductId;
        this.productIds = productIds;
        this.recommendationType = recommendationType;
        this.scoringStrategy = scoringStrategy;
        this.explanationStrategy = explanationStrategy;
        this.score = score;
        this.confidence = confidence;
        this.createdAt = createdAt;
    }

    @PrePersist
    protected void onCreate() {
        if (this.createdAt == null) {
            this.createdAt = LocalDateTime.now();
        }
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

    public String getSessionId() {
        return sessionId;
    }

    public void setSessionId(String sessionId) {
        this.sessionId = sessionId;
    }

    public UUID getTargetProductId() {
        return targetProductId;
    }

    public void setTargetProductId(UUID targetProductId) {
        this.targetProductId = targetProductId;
    }

    public UUID getRecommendedProductId() {
        return recommendedProductId;
    }

    public void setRecommendedProductId(UUID recommendedProductId) {
        this.recommendedProductId = recommendedProductId;
    }

    public String getProductIds() {
        return productIds;
    }

    public void setProductIds(String productIds) {
        this.productIds = productIds;
    }

    public String getRecommendationType() {
        return recommendationType;
    }

    public void setRecommendationType(String recommendationType) {
        this.recommendationType = recommendationType;
    }

    public String getScoringStrategy() {
        return scoringStrategy;
    }

    public void setScoringStrategy(String scoringStrategy) {
        this.scoringStrategy = scoringStrategy;
    }

    public String getExplanationStrategy() {
        return explanationStrategy;
    }

    public void setExplanationStrategy(String explanationStrategy) {
        this.explanationStrategy = explanationStrategy;
    }

    public Double getScore() {
        return score;
    }

    public void setScore(Double score) {
        this.score = score;
    }

    public Double getConfidence() {
        return confidence;
    }

    public void setConfidence(Double confidence) {
        this.confidence = confidence;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
