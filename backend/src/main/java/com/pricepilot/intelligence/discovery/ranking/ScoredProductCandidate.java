package com.pricepilot.intelligence.discovery.ranking;

import com.pricepilot.product.ProductEntity;
import com.pricepilot.intelligence.analytics.model.DealQuality;
import com.pricepilot.intelligence.analytics.model.PurchaseSignal;
import com.pricepilot.intelligence.analytics.model.PriceTrend;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Intermediate candidate wrapper for relevance evaluation, intelligence signals, and deterministic tie-breaking.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ScoredProductCandidate {

    private ProductEntity product;
    private BigDecimal currentBestPrice;
    private BigDecimal originalPrice;
    private BigDecimal discountPercentage;
    private Double rating;
    private Long reviewCount;

    // Relevance scoring
    private double relevanceScore;
    @Builder.Default
    private List<String> relevanceReasons = new ArrayList<>();
    @Builder.Default
    private List<String> badges = new ArrayList<>();

    // Intelligence signals from Phase 4
    private DealQuality dealQuality;
    private PurchaseSignal purchaseSignal;
    private PriceTrend priceTrend;
    private Boolean isHistoricalLow;

    public UUID getProductId() {
        return product != null ? product.getId() : null;
    }
}
