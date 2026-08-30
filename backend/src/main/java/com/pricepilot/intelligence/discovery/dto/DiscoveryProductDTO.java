package com.pricepilot.intelligence.discovery.dto;

import com.pricepilot.intelligence.analytics.model.DealQuality;
import com.pricepilot.intelligence.analytics.model.PurchaseSignal;
import com.pricepilot.intelligence.analytics.model.PriceTrend;
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
 * Clean discovery result representation enriched with deterministic shopping intelligence.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DiscoveryProductDTO {

    private UUID id;
    private String name;
    private String brand;
    private String category;
    private String description;
    private String imageUrl;
    private boolean archived;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    // Pricing
    private BigDecimal currentBestPrice;
    private BigDecimal originalPrice;
    private BigDecimal discountPercentage;
    @Builder.Default
    private List<ProductPriceSearchResultDTO> prices = new ArrayList<>();

    // Social / ratings
    private Double rating;
    private Long reviewCount;
    private Boolean inStock;

    // Relevance & Discovery explanations
    private Double relevanceScore;
    @Builder.Default
    private List<String> discoveryBadges = new ArrayList<>();
    @Builder.Default
    private List<String> discoveryReasons = new ArrayList<>();

    // Shopping intelligence (Phase 4 reuse)
    private DealQuality dealQuality;
    private PriceTrend priceTrend;
    private PurchaseSignal purchaseSignal;
    private Boolean isHistoricalLow;
}
