package com.pricepilot.intelligence.dashboard.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * Concise recommendation section for Dashboard V2.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DashboardRecommendationsDTO implements Serializable {

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RecommendationItemDTO implements Serializable {
        private UUID productId;
        private String productName;
        private String productImageUrl;
        private String brand;
        private BigDecimal currentPrice;
        private String recommendationType;
        private Double score;
        private Double confidence;
        private String keyReason;
        private String explanation;
    }

    private List<RecommendationItemDTO> items;
    private String strategyUsed;
    private String generatedAt;
    private boolean available;
}
