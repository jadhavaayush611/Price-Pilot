package com.pricepilot.intelligence.dashboard.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * Deterministically scored attention item representing a watched product requiring user review.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AttentionItemDTO implements Serializable {

    private UUID productId;
    private String productName;
    private String productImageUrl;
    private String brand;
    private int urgencyScore;
    private String urgencyLevel; // CRITICAL, HIGH, MEDIUM, INFO
    private String primaryReason;
    private List<String> supportingEvidence;
    private BigDecimal currentPrice;
    private BigDecimal targetPrice;
    private String dealQuality;
    private String purchaseSignal;
    private String navigationUrl;
}
