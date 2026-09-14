package com.pricepilot.intelligence.alternative.model;

import com.pricepilot.intelligence.analytics.model.DealQuality;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Factual context of the baseline/source product when finding alternatives.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SourceProductContextDTO {
    private UUID id;
    private String name;
    private String brand;
    private String category;
    private String description;
    private String imageUrl;
    private BigDecimal currentBestPrice;
    private BigDecimal originalPrice;
    private BigDecimal discountPercentage;
    private Double rating;
    private DealQuality dealQuality;
}
