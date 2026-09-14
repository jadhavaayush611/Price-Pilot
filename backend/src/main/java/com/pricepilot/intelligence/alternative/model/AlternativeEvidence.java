package com.pricepilot.intelligence.alternative.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Factual, structured evidence supporting an alternative recommendation.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AlternativeEvidence {
    private AlternativeEvidenceCategory category;
    private String sourceValue;
    private String candidateValue;
    private String relationship;
    private double confidence;
    private String description;
}
