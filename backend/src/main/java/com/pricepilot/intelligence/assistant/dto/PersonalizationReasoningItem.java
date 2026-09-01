package com.pricepilot.intelligence.assistant.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PersonalizationReasoningItem {
    private String factor; // PREFERRED_CATEGORY, PREFERRED_BRAND, BUDGET_MATCH, RATING_CRITERIA_MET, DEAL_SENSITIVITY_MATCH, BEHAVIORAL_AFFINITY
    private UUID productId;
    private String productName;
    private String explanation;
    private Double scoreContribution;
    private Double matchStrength;
}
