package com.pricepilot.intelligence.alternative.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Universal request DTO for querying product alternatives via product-driven or query-driven modes.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AlternativeRequest {

    private UUID productId;
    private String query;
    @Builder.Default
    private AlternativeType type = AlternativeType.SIMILAR;
    private String category;
    private String brand;
    private BigDecimal minPrice;
    private BigDecimal maxPrice;
    private Double minRating;
    private BigDecimal minDiscount;
    private Boolean inStock;
    private Double minSemanticSimilarity;
    private UUID sellerId;

    @Builder.Default
    private Integer limit = 10;
    @Builder.Default
    private Double structuredWeight = 0.70;
    @Builder.Default
    private Double semanticWeight = 0.30;
    private String sort;

    public int getEffectiveLimit() {
        return limit != null && limit > 0 ? limit : 10;
    }

    public double getEffectiveStructuredWeight() {
        return structuredWeight != null ? structuredWeight : 0.70;
    }

    public double getEffectiveSemanticWeight() {
        return semanticWeight != null ? semanticWeight : 0.30;
    }
}
