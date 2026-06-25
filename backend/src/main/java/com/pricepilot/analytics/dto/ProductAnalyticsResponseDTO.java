package com.pricepilot.analytics.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProductAnalyticsResponseDTO {
    private UUID productId;
    private long viewCount;
    private long saveCount;
    private long watchlistCount;
    private long priceChangeCount;
    private double trendingScore;
}
