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
public class TradeOffItem {
    private String dimension; // PRICE_VS_RATING, BUDGET_EXCEEDED, SELLER_REPUTATION, AVAILABILITY, DISCOUNT_DEPTH
    private UUID productId;
    private String productName;
    private String description;
    private String impact; // POSITIVE, NEUTRAL, NEGATIVE
    private String severity; // INFO, WARNING, OPPORTUNITY
}
