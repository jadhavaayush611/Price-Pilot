package com.pricepilot.intelligence.analytics.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Meaningful price movement or milestone event detected along historical timeline.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PriceDropRecoveryEvent {

    public enum EventType {
        MAJOR_DROP,
        PRICE_INCREASE,
        RECOVERY_AFTER_DROP,
        NEW_HISTORICAL_LOW,
        NEW_HISTORICAL_HIGH
    }

    private EventType eventType;
    private Double percentageChange;
    private BigDecimal amountChange;
    private BigDecimal resultingPrice;
    private LocalDateTime occurredAt;
    private String description;
}
