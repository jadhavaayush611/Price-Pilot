package com.pricepilot.intelligence.discovery.intent;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * Request parameter object for executing natural-language shopping searches.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NaturalLanguageSearchRequest implements Serializable {

    private static final long serialVersionUID = 1L;

    private String query;
    private String sort;

    @Builder.Default
    private int page = 0;

    @Builder.Default
    private int size = 10;

    @Builder.Default
    private double structuredWeight = 0.70;

    @Builder.Default
    private double semanticWeight = 0.30;
}
