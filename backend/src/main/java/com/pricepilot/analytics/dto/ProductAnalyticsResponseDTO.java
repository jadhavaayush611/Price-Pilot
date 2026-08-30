package com.pricepilot.analytics.dto;

import com.pricepilot.intelligence.analytics.model.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Enriched analytics response DTO preserving legacy engagement metrics
 * while delivering advanced deterministic price intelligence.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProductAnalyticsResponseDTO {

    // Legacy / Engagement Metrics (Preserved)
    private UUID productId;
    private long viewCount;
    private long saveCount;
    private long watchlistCount;
    private long priceChangeCount;
    private double trendingScore;

    // Advanced Price Intelligence Metrics (Phase 4)
    private BigDecimal currentPrice;
    private BigDecimal historicalMin;
    private BigDecimal historicalMax;
    private BigDecimal historicalAvg;
    private BigDecimal historicalMedian;
    private BigDecimal priceRange;
    private Double volatilityValue;
    private PriceVolatility volatility;
    private PriceTrend trend;
    private Double trendPercentage;
    private Double pricePositionScore;
    private DealQuality dealQuality;
    private PurchaseSignal purchaseSignal;
    private String purchaseSignalReason;
    private List<String> supportingEvidence;
    private int observationCount;
    private BigDecimal historicalLowDistance;
    private BigDecimal historicalAverageDistance;
    private List<PriceDropRecoveryEvent> historicalEvents;
    private List<HistoricalPricePoint> priceSeries;
    private LocalDateTime analyzedAt;

    /**
     * Backward-compatible constructor for legacy callers and tests.
     */
    public ProductAnalyticsResponseDTO(UUID productId, long viewCount, long saveCount, long watchlistCount, long priceChangeCount, double trendingScore) {
        this.productId = productId;
        this.viewCount = viewCount;
        this.saveCount = saveCount;
        this.watchlistCount = watchlistCount;
        this.priceChangeCount = priceChangeCount;
        this.trendingScore = trendingScore;
        this.analyzedAt = LocalDateTime.now();
    }
}
