package com.pricepilot.interaction.dto;

import com.pricepilot.interaction.InteractionType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserInteractionEventResponseDTO {
    private UUID id;
    private UUID userId;
    private String userEmail;
    private UUID productId;
    private String productName;
    private UUID sellerId;
    private String sellerName;
    private InteractionType interactionType;
    private Map<String, Object> metadata;
    private LocalDateTime createdAt;
}
