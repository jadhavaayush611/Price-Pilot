package com.pricepilot.intelligence.dashboard.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Chronological price milestone or alert event item for Dashboard V2.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RecentActivityDTO implements Serializable {

    private String id;
    private UUID productId;
    private String productName;
    private String productImageUrl;
    private String eventType;
    private String title;
    private String description;
    private BigDecimal observedPrice;
    private BigDecimal amountChange;
    private Double percentageChange;
    private LocalDateTime timestamp;
}
