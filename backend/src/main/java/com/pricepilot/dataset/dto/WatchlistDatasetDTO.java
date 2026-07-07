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
public class WatchlistDatasetDTO {
    private UUID id;
    private UUID userId;
    private UUID productId;
    private BigDecimal targetPrice;
    private BigDecimal currentBestPrice;
    private boolean active;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
