package com.pricepilot.intelligence.discovery.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Autocomplete / discovery search suggestion.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SearchSuggestionDTO {

    private String text;
    private String type; // "PRODUCT", "BRAND", "CATEGORY"
    private UUID productId;
    private String category;
    private String brand;
    private BigDecimal bestPrice;
}
