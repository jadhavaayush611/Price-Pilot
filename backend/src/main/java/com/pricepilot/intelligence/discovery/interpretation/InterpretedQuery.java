package com.pricepilot.intelligence.discovery.interpretation;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * Encapsulates the deterministic interpretation of a user search query.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InterpretedQuery {

    private String originalQuery;
    private String normalizedQuery;

    /**
     * Residual keyword terms after extracted intent phrases are filtered out.
     */
    private String cleanSearchTerms;

    /**
     * Individual search tokens for relevance scoring.
     */
    @Builder.Default
    private List<String> searchTokens = new ArrayList<>();

    // Extracted constraints
    private String detectedBrand;
    private String detectedCategory;
    private BigDecimal minPrice;
    private BigDecimal maxPrice;
    private Double minRating;
    private BigDecimal minDiscountPercentage;
    private Boolean inStockOnly;
    private Boolean dealIntent;

    /**
     * Explanatory notes of interpreted constraints for diagnostics/display.
     */
    @Builder.Default
    private List<String> interpretationNotes = new ArrayList<>();
}
