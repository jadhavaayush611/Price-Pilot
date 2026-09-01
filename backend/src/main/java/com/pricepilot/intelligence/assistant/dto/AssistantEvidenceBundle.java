package com.pricepilot.intelligence.assistant.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AssistantEvidenceBundle {

    private AssistantIntent intent;

    @Builder.Default
    private List<GroundedEvidenceItem> factualEvidence = new ArrayList<>();

    @Builder.Default
    private List<PersonalizationReasoningItem> personalizationReasoning = new ArrayList<>();

    @Builder.Default
    private List<TradeOffItem> tradeOffs = new ArrayList<>();

    @Builder.Default
    private List<String> unknownOrInsufficientData = new ArrayList<>();

    @Builder.Default
    private List<Map<String, Object>> groundedProducts = new ArrayList<>();

    @Builder.Default
    private List<AssistantAction> suggestedActions = new ArrayList<>();

    private Double confidenceScore;
}
