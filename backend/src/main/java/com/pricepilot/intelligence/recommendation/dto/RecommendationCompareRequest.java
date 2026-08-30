package com.pricepilot.intelligence.recommendation.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.UUID;

/**
 * Request payload for comparison-based product recommendations.
 */
public class RecommendationCompareRequest {

    @NotEmpty(message = "Product IDs list cannot be empty")
    @Size(min = 2, max = 5, message = "Comparison recommendations require between 2 and 5 product IDs")
    private List<UUID> productIds;

    private String recommendationType;

    public RecommendationCompareRequest() {
    }

    public RecommendationCompareRequest(List<UUID> productIds, String recommendationType) {
        this.productIds = productIds;
        this.recommendationType = recommendationType;
    }

    public List<UUID> getProductIds() {
        return productIds;
    }

    public void setProductIds(List<UUID> productIds) {
        this.productIds = productIds;
    }

    public String getRecommendationType() {
        return recommendationType;
    }

    public void setRecommendationType(String recommendationType) {
        this.recommendationType = recommendationType;
    }
}
