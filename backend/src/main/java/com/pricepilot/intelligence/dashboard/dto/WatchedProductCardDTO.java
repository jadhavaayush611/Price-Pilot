package com.pricepilot.intelligence.dashboard.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.UUID;

/**
 * Concise watched product card for Dashboard V2.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WatchedProductCardDTO implements Serializable {

    private UUID productId;
    private UUID watchlistId;
    private String productName;
    private String brand;
    private String category;
    private String imageUrl;
    private BigDecimal currentPrice;
    private BigDecimal targetPrice;
    private BigDecimal historicalMin;
    private BigDecimal historicalAvg;
    private String volatility;
    private String trend;
    private Double trendPercentage;
    private String dealQuality;
    private String purchaseSignal;
    private boolean targetMet;
    private boolean active;
}
