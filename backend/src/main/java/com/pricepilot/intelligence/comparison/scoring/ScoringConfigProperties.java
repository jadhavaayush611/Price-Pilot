package com.pricepilot.intelligence.comparison.scoring;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Externalized configuration properties for Shopping Intelligence comparison scoring weights.
 */
@Component
@ConfigurationProperties(prefix = "pricepilot.intelligence.scoring.weights")
public class ScoringConfigProperties {

    /**
     * Weight for Price Competitiveness factor (0.0 to 1.0)
     */
    private double priceCompetitiveness = 0.30;

    /**
     * Weight for Discount Percentage factor (0.0 to 1.0)
     */
    private double discountPercentage = 0.20;

    /**
     * Weight for Product Rating factor (0.0 to 1.0)
     */
    private double productRating = 0.20;

    /**
     * Weight for Review Count / Popularity factor (0.0 to 1.0)
     */
    private double popularity = 0.10;

    /**
     * Weight for Seller Reputation factor (0.0 to 1.0)
     */
    private double sellerReputation = 0.10;

    /**
     * Weight for Availability factor (0.0 to 1.0)
     */
    private double availability = 0.10;

    public ScoringConfigProperties() {
    }

    public double getPriceCompetitiveness() {
        return priceCompetitiveness;
    }

    public void setPriceCompetitiveness(double priceCompetitiveness) {
        this.priceCompetitiveness = priceCompetitiveness;
    }

    public double getDiscountPercentage() {
        return discountPercentage;
    }

    public void setDiscountPercentage(double discountPercentage) {
        this.discountPercentage = discountPercentage;
    }

    public double getProductRating() {
        return productRating;
    }

    public void setProductRating(double productRating) {
        this.productRating = productRating;
    }

    public double getPopularity() {
        return popularity;
    }

    public void setPopularity(double popularity) {
        this.popularity = popularity;
    }

    public double getSellerReputation() {
        return sellerReputation;
    }

    public void setSellerReputation(double sellerReputation) {
        this.sellerReputation = sellerReputation;
    }

    public double getAvailability() {
        return availability;
    }

    public void setAvailability(double availability) {
        this.availability = availability;
    }
}
