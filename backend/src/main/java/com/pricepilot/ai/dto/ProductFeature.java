package com.pricepilot.ai.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProductFeature {
    private String productId;
    private String category;
    private String brand;
    private double currentMinPrice;
    private double originalMinPrice;
    private double averageSellerRating;
    private double viewCount;
    private double saveCount;
    private double watchlistCount;
    private double trendingScore;
    private double discountPercentage;
}
