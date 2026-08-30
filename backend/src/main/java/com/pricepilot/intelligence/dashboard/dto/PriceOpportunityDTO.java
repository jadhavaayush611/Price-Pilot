package com.pricepilot.intelligence.dashboard.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.UUID;

/**
 * Concise representation of a currently attractive purchasing opportunity.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PriceOpportunityDTO implements Serializable {

    private UUID productId;
    private String productName;
    private String productImageUrl;
    private String brand;
    private BigDecimal currentPrice;
    private BigDecimal historicalMin;
    private BigDecimal historicalAvg;
    private String dealQuality;
    private String purchaseSignal;
    private String keyEvidence;
    private String navigationUrl;
}
