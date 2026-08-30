package com.pricepilot.intelligence.analytics.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * Historical statistical calculations for product pricing.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PriceStatistics {
    private BigDecimal currentPrice;
    private BigDecimal historicalMin;
    private BigDecimal historicalMax;
    private BigDecimal historicalAvg;
    private BigDecimal historicalMedian;
    private BigDecimal priceRange;
    private int observationCount;
    private BigDecimal historicalLowDistance;
    private BigDecimal historicalAverageDistance;
}
