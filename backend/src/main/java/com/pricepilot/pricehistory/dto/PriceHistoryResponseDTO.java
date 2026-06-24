package com.pricepilot.pricehistory.dto;

import com.pricepilot.pricehistory.PriceHistoryEntity;
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
public class PriceHistoryResponseDTO {

    private UUID id;
    private UUID productId;
    private String productName;
    private UUID sellerId;
    private String sellerName;
    private BigDecimal oldPrice;
    private BigDecimal newPrice;
    private BigDecimal priceDifference;
    private BigDecimal changePercentage;
    private LocalDateTime changedAt;

    public static PriceHistoryResponseDTO fromEntity(PriceHistoryEntity entity) {
        if (entity == null) {
            return null;
        }
        return PriceHistoryResponseDTO.builder()
                .id(entity.getId())
                .productId(entity.getProduct() != null ? entity.getProduct().getId() : null)
                .productName(entity.getProduct() != null ? entity.getProduct().getName() : null)
                .sellerId(entity.getSeller() != null ? entity.getSeller().getId() : null)
                .sellerName(entity.getSeller() != null ? entity.getSeller().getName() : null)
                .oldPrice(entity.getOldPrice())
                .newPrice(entity.getNewPrice())
                .priceDifference(entity.getPriceDifference())
                .changePercentage(entity.getChangePercentage())
                .changedAt(entity.getChangedAt())
                .build();
    }
}
