package com.pricepilot.intelligence.alternative.model;

import com.pricepilot.intelligence.analytics.model.DealQuality;
import com.pricepilot.intelligence.analytics.model.PriceTrend;
import com.pricepilot.intelligence.analytics.model.PurchaseSignal;
import com.pricepilot.product.dto.ProductPriceSearchResultDTO;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Detailed representation of an alternative candidate product with score, evidence, and comparisons.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AlternativeProductDTO {
    private UUID id;
    private String name;
    private String brand;
    private String category;
    private String description;
    private String imageUrl;
    private boolean archived;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    // Price & Availability
    private BigDecimal currentBestPrice;
    private BigDecimal originalPrice;
    private BigDecimal discountPercentage;
    @Builder.Default
    private List<ProductPriceSearchResultDTO> prices = new ArrayList<>();
    private boolean inStock;

    // Scoring & Comparison
    private double alternativeScore;
    private Double semanticSimilarityScore;
    private double relevanceScore;
    private Double rating;
    private BigDecimal priceDifference; // candidatePrice - sourcePrice (negative = savings)
    private Double priceDifferencePercentage; // ((candidate - source) / source) * 100

    // Shopping Intelligence Signals
    private DealQuality dealQuality;
    private PriceTrend priceTrend;
    private PurchaseSignal purchaseSignal;
    private Boolean isHistoricalLow;

    // Explainability & Badges
    @Builder.Default
    private List<String> badges = new ArrayList<>();
    @Builder.Default
    private List<AlternativeReasonCode> reasonCodes = new ArrayList<>();
    @Builder.Default
    private List<AlternativeEvidence> evidence = new ArrayList<>();
    private String primaryExplanation;
}
