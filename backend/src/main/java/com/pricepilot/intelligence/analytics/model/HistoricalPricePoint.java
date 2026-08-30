package com.pricepilot.intelligence.analytics.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Frontend-consumable chronological price series point.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class HistoricalPricePoint {
    private LocalDateTime timestamp;
    private BigDecimal price;
    private UUID sellerId;
    private String sellerName;
}
