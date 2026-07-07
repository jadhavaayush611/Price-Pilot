package com.pricepilot.dataset.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProductAnalyticsDatasetDTO {
    private UUID id;
    private UUID productId;
    private long viewCount;
    private long saveCount;
    private long watchlistCount;
    private long priceChangeCount;
    private double trendingScore;
    private LocalDateTime lastViewedAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
