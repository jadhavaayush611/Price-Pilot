package com.pricepilot.intelligence.alternative.model;

import com.pricepilot.intelligence.analytics.model.DealQuality;
import com.pricepilot.intelligence.analytics.model.PriceTrend;
import com.pricepilot.intelligence.analytics.model.PurchaseSignal;
import com.pricepilot.product.ProductEntity;
import com.pricepilot.productprice.ProductPriceEntity;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Internal candidate representation during alternative scoring and ranking.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AlternativeCandidate {

    private ProductEntity product;
    private BigDecimal currentBestPrice;
    private BigDecimal originalPrice;
    private BigDecimal discountPercentage;
    @Builder.Default
    private List<ProductPriceEntity> prices = new ArrayList<>();

    private Double semanticSimilarityScore;
    private double relevanceScore;
    private double alternativeScore;
    private Double rating;

    private BigDecimal priceDifference;
    private Double priceDifferencePercentage;

    private DealQuality dealQuality;
    private PriceTrend priceTrend;
    private PurchaseSignal purchaseSignal;
    private Boolean isHistoricalLow;

    @Builder.Default
    private List<String> badges = new ArrayList<>();
    @Builder.Default
    private List<AlternativeReasonCode> reasonCodes = new ArrayList<>();
    @Builder.Default
    private List<AlternativeEvidence> evidence = new ArrayList<>();
    private String primaryExplanation;

    public UUID getProductId() {
        return product != null ? product.getId() : null;
    }
}
