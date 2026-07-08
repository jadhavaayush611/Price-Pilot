package com.pricepilot.ai.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.util.Map;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserProfile {
    private Map<String, Double> preferredCategories;
    private Map<String, Double> preferredBrands;
    private Map<String, Double> preferredSellers;
    private Double minPrice;
    private Double maxPrice;
}
