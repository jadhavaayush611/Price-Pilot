package com.pricepilot.dataset.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PriceHistoryDatasetDTO {
    private UUID id;
    private UUID productId;
    private UUID sellerId;
    private BigDecimal oldPrice;
    private BigDecimal newPrice;
    private BigDecimal priceDifference;
    private BigDecimal changePercentage;
    private LocalDateTime changedAt;
}
