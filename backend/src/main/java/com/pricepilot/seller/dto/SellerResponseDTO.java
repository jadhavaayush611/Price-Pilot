package com.pricepilot.seller.dto;

import com.pricepilot.seller.SellerEntity;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SellerResponseDTO {

    private UUID id;
    private String name;
    private String websiteUrl;
    private String logoUrl;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public static SellerResponseDTO fromEntity(SellerEntity entity) {
        if (entity == null) {
            return null;
        }
        return SellerResponseDTO.builder()
                .id(entity.getId())
                .name(entity.getName())
                .websiteUrl(entity.getWebsiteUrl())
                .logoUrl(entity.getLogoUrl())
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .build();
    }
}
