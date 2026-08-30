package com.pricepilot.intelligence.discovery.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Request criteria for search and discovery.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DiscoverySearchRequestDTO {

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

    @Builder.Default
    private String sort = "relevance";

    @Builder.Default
    private int page = 0;

    @Builder.Default
    private int size = 10;
}
