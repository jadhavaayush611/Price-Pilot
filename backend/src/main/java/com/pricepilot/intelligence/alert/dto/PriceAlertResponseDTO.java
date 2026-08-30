package com.pricepilot.intelligence.alert.dto;

import com.pricepilot.intelligence.alert.entity.PriceAlertEntity;
import com.pricepilot.intelligence.alert.model.AlertType;
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
public class PriceAlertResponseDTO {

    private UUID id;
    private UUID userId;
    private UUID productId;
    private String productName;
    private String productImageUrl;
    private UUID watchlistId;
    private AlertType alertType;
    private String title;
    private String message;
    private BigDecimal triggerValue;
    private BigDecimal observedValue;
    private boolean read;
    private LocalDateTime readAt;
    private LocalDateTime createdAt;

    public static PriceAlertResponseDTO fromEntity(PriceAlertEntity entity) {
        if (entity == null) {
            return null;
        }

        return PriceAlertResponseDTO.builder()
                .id(entity.getId())
                .userId(entity.getUser() != null ? entity.getUser().getId() : null)
                .productId(entity.getProduct() != null ? entity.getProduct().getId() : null)
                .productName(entity.getProduct() != null ? entity.getProduct().getName() : null)
                .productImageUrl(entity.getProduct() != null ? entity.getProduct().getImageUrl() : null)
                .watchlistId(entity.getWatchlist() != null ? entity.getWatchlist().getId() : null)
                .alertType(entity.getAlertType())
                .title(entity.getTitle())
                .message(entity.getMessage())
                .triggerValue(entity.getTriggerValue())
                .observedValue(entity.getObservedValue())
                .read(entity.isRead())
                .readAt(entity.getReadAt())
                .createdAt(entity.getCreatedAt())
                .build();
    }
}
