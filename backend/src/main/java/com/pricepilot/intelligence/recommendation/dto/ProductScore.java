package com.pricepilot.intelligence.recommendation.dto;

import java.util.Map;
import java.util.UUID;

/**
 * Data Transfer Object for storing multidimensional scoring of a product within Shopping Intelligence.
 */
public class ProductScore {

    private UUID productId;
    private String productName;
    private double overallScore;
    private double baseScore;
    private double personalizationContribution;
    private double priceValueScore;
    private double featureScore;
    private double popularityScore;
    private Map<String, Double> breakdown;
    private Map<String, Double> personalizationBreakdown;
    private String recommendationBadge;

    public ProductScore() {
    }

    public ProductScore(UUID productId, String productName, double overallScore, double priceValueScore, double featureScore, double popularityScore, Map<String, Double> breakdown, String recommendationBadge) {
        this.productId = productId;
        this.productName = productName;
        this.overallScore = overallScore;
        this.baseScore = overallScore;
        this.personalizationContribution = 0.0;
        this.priceValueScore = priceValueScore;
        this.featureScore = featureScore;
        this.popularityScore = popularityScore;
        this.breakdown = breakdown;
        this.recommendationBadge = recommendationBadge;
    }

    public UUID getProductId() {
        return productId;
    }

    public void setProductId(UUID productId) {
        this.productId = productId;
    }

    public String getProductName() {
        return productName;
    }

    public void setProductName(String productName) {
        this.productName = productName;
    }

    public double getOverallScore() {
        return overallScore;
    }

    public void setOverallScore(double overallScore) {
        this.overallScore = overallScore;
    }

    public double getPriceValueScore() {
        return priceValueScore;
    }

    public void setPriceValueScore(double priceValueScore) {
        this.priceValueScore = priceValueScore;
    }

    public double getFeatureScore() {
        return featureScore;
    }

    public void setFeatureScore(double featureScore) {
        this.featureScore = featureScore;
    }

    public double getPopularityScore() {
        return popularityScore;
    }

    public void setPopularityScore(double popularityScore) {
        this.popularityScore = popularityScore;
    }

    public Map<String, Double> getBreakdown() {
        return breakdown;
    }

    public void setBreakdown(Map<String, Double> breakdown) {
        this.breakdown = breakdown;
    }

    public String getRecommendationBadge() {
        return recommendationBadge;
    }

    public void setRecommendationBadge(String recommendationBadge) {
        this.recommendationBadge = recommendationBadge;
    }

    public double getBaseScore() {
        return baseScore;
    }

    public void setBaseScore(double baseScore) {
        this.baseScore = baseScore;
    }

    public double getPersonalizationContribution() {
        return personalizationContribution;
    }

    public void setPersonalizationContribution(double personalizationContribution) {
        this.personalizationContribution = personalizationContribution;
    }

    public Map<String, Double> getPersonalizationBreakdown() {
        return personalizationBreakdown;
    }

    public void setPersonalizationBreakdown(Map<String, Double> personalizationBreakdown) {
        this.personalizationBreakdown = personalizationBreakdown;
    }
}
