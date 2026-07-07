package com.pricepilot.dataset.dto;

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
public class InteractionEventDatasetDTO {
    private UUID id;
    private UUID userId;
    private UUID productId;
    private UUID sellerId;
    private String interactionType;
    private String metadataJson;
    private LocalDateTime createdAt;
}
