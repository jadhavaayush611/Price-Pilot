package com.pricepilot.intelligence.personalization.scoring;

import com.pricepilot.intelligence.recommendation.dto.EvidenceItem;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PersonalizationResult {
    private double baseScore;
    private double personalizationContribution;
    private double finalScore;

    @Builder.Default
    private Map<String, Double> personalizationBreakdown = new HashMap<>();

    @Builder.Default
    private List<EvidenceItem> personalizationEvidence = new ArrayList<>();

    @Builder.Default
    private List<EvidenceItem> personalizationTradeOffs = new ArrayList<>();
}
