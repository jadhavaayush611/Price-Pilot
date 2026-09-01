package com.pricepilot.intelligence.assistant.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GroundedEvidenceItem {
    private String id;
    private UUID productId;
    private String productName;
    private String factType; // PRICE, DISCOUNT, HISTORICAL_LOW, DEAL_QUALITY, VOLATILITY, TREND, RATING, AVAILABILITY, SELLER
    private String description;
    private Object factualValue;
    private Object benchmarkValue;
    private boolean verified;
    private Double confidence;
}
