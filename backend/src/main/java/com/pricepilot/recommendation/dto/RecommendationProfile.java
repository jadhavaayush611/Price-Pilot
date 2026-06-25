package com.pricepilot.recommendation.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RecommendationProfile {
    private Map<String, Double> preferredCategories;
    private Map<String, Double> preferredBrands;
    private Map<String, Double> preferredSellers; // key: seller name/id
    private BigDecimal minPrice;
    private BigDecimal maxPrice;
    private Map<String, Long> interactionFrequency;
    private long totalInteractions;
}
