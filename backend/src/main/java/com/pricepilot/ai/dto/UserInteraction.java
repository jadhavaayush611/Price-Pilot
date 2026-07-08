package com.pricepilot.ai.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserInteraction {
    private String productId;
    private String interactionType;
    private String createdAt;
}
