package com.pricepilot.watchlist.dto;

import com.pricepilot.watchlist.PriceWatchlistEntity;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WatchlistResponseDTO {

    private UUID id;
    private UUID productId;
    private String productName;
    private String brand;
    private String imageUrl;
    private BigDecimal targetPrice;
    private BigDecimal currentBestPrice;
    private BigDecimal priceDifference;
    private boolean active;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public static WatchlistResponseDTO fromEntity(PriceWatchlistEntity entity) {
        if (entity == null) {
            return null;
        }

        BigDecimal currentBest = entity.getCurrentBestPrice() != null ? entity.getCurrentBestPrice() : BigDecimal.ZERO;
        BigDecimal target = entity.getTargetPrice() != null ? entity.getTargetPrice() : BigDecimal.ZERO;
        BigDecimal diff = currentBest.subtract(target);

        return WatchlistResponseDTO.builder()
                .id(entity.getId())
                .productId(entity.getProduct().getId())
                .productName(entity.getProduct().getName())
                .brand(entity.getProduct().getBrand())
                .imageUrl(entity.getProduct().getImageUrl())
                .targetPrice(entity.getTargetPrice())
                .currentBestPrice(entity.getCurrentBestPrice())
                .priceDifference(diff)
                .active(entity.isActive())
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .build();
    }
}
