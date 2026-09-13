package com.pricepilot.intelligence.discovery.hybrid;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.UUID;

/**
 * Domain request object for executing hybrid discovery search.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class HybridSearchRequest implements Serializable {

    private static final long serialVersionUID = 1L;

    private String query;
    private String category;
    private String brand;
    private BigDecimal minPrice;
    private BigDecimal maxPrice;
    private Double minRating;
    private BigDecimal minDiscount;
    private Boolean inStock;
    private String dealQuality;
    private UUID sellerId;
    private String sort;

    @Builder.Default
    private int page = 0;

    @Builder.Default
    private int size = 10;

    @Builder.Default
    private int structuredCandidateLimit = 50;

    @Builder.Default
    private int semanticCandidateLimit = 50;

    @Builder.Default
    private int maxFusedCandidates = 100;

    @Builder.Default
    private double minSimilarityScore = 0.20;

    @Builder.Default
    private double semanticWeight = 0.30;

    @Builder.Default
    private double structuredWeight = 0.70;
}
