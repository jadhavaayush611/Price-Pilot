package com.pricepilot.intelligence.recommendation.dto;

import com.pricepilot.product.dto.ProductResponseDTO;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Data Transfer Object for explainable recommendation engine v2 output response.
 */
public class RecommendationResponse {

    private UUID targetProductId;
    private UUID userId;
    private List<ProductResponseDTO> recommendedProducts = new ArrayList<>();
    private ProductResponseDTO recommendedProduct;
    private String recommendationType;
    private Double score;
    private Double confidence;
    private String explanation;
    private List<String> supportingFactors = new ArrayList<>();
    private List<String> tradeOffs = new ArrayList<>();
    private List<EvidenceItem> evidence = new ArrayList<>();
    private List<EvidenceItem> personalizationEvidence = new ArrayList<>();
    private Double baseScore;
    private Double personalizationContribution;
    private List<ProductScore> scores = new ArrayList<>();
    private String scoringStrategy;
    private String explanationStrategy;
    private String strategyUsed;
    private LocalDateTime generatedAt;

    public RecommendationResponse() {
    }

    public RecommendationResponse(UUID targetProductId, UUID userId, List<ProductResponseDTO> recommendedProducts,
                                  List<ProductScore> scores, String explanation, String strategyUsed,
                                  LocalDateTime generatedAt) {
        this.targetProductId = targetProductId;
        this.userId = userId;
        this.recommendedProducts = recommendedProducts;
        this.scores = scores;
        this.explanation = explanation;
        this.strategyUsed = strategyUsed;
        this.scoringStrategy = strategyUsed;
        this.explanationStrategy = "DETERMINISTIC_RULE_BASED";
        this.generatedAt = generatedAt;
        if (recommendedProducts != null && !recommendedProducts.isEmpty()) {
            this.recommendedProduct = recommendedProducts.get(0);
        }
        if (scores != null && !scores.isEmpty()) {
            this.score = scores.get(0).getOverallScore();
        }
        this.recommendationType = "BEST_OVERALL";
        this.confidence = 0.85;
    }

    public RecommendationResponse(UUID targetProductId, UUID userId, List<ProductResponseDTO> recommendedProducts,
                                  ProductResponseDTO recommendedProduct, String recommendationType, Double score,
                                  Double confidence, String explanation, List<String> supportingFactors,
                                  List<String> tradeOffs, List<EvidenceItem> evidence, List<ProductScore> scores,
                                  String scoringStrategy, String explanationStrategy, LocalDateTime generatedAt) {
        this.targetProductId = targetProductId;
        this.userId = userId;
        this.recommendedProducts = recommendedProducts != null ? recommendedProducts : new ArrayList<>();
        this.recommendedProduct = recommendedProduct;
        this.recommendationType = recommendationType;
        this.score = score;
        this.confidence = confidence;
        this.explanation = explanation;
        this.supportingFactors = supportingFactors != null ? supportingFactors : new ArrayList<>();
        this.tradeOffs = tradeOffs != null ? tradeOffs : new ArrayList<>();
        this.evidence = evidence != null ? evidence : new ArrayList<>();
        this.scores = scores != null ? scores : new ArrayList<>();
        this.scoringStrategy = scoringStrategy;
        this.explanationStrategy = explanationStrategy;
        this.strategyUsed = scoringStrategy;
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

    public ProductResponseDTO getRecommendedProduct() {
        return recommendedProduct;
    }

    public void setRecommendedProduct(ProductResponseDTO recommendedProduct) {
        this.recommendedProduct = recommendedProduct;
    }

    public String getRecommendationType() {
        return recommendationType;
    }

    public void setRecommendationType(String recommendationType) {
        this.recommendationType = recommendationType;
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

    public List<String> getSupportingFactors() {
        return supportingFactors;
    }

    public void setSupportingFactors(List<String> supportingFactors) {
        this.supportingFactors = supportingFactors;
    }

    public List<String> getTradeOffs() {
        return tradeOffs;
    }

    public void setTradeOffs(List<String> tradeOffs) {
        this.tradeOffs = tradeOffs;
    }

    public List<EvidenceItem> getEvidence() {
        return evidence;
    }

    public void setEvidence(List<EvidenceItem> evidence) {
        this.evidence = evidence;
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

    public List<EvidenceItem> getPersonalizationEvidence() {
        return personalizationEvidence;
    }

    public void setPersonalizationEvidence(List<EvidenceItem> personalizationEvidence) {
        this.personalizationEvidence = personalizationEvidence != null ? personalizationEvidence : new ArrayList<>();
    }

    public Double getBaseScore() {
        return baseScore;
    }

    public void setBaseScore(Double baseScore) {
        this.baseScore = baseScore;
    }

    public Double getPersonalizationContribution() {
        return personalizationContribution;
    }

    public void setPersonalizationContribution(Double personalizationContribution) {
        this.personalizationContribution = personalizationContribution;
    }
}
