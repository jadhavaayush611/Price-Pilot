package com.pricepilot.intelligence.alternative.model;

import com.pricepilot.intelligence.discovery.intent.ShoppingQueryIntent;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/**
 * Universal response DTO for alternative finder results.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AlternativeResponseDTO {

    private SourceProductContextDTO sourceProductContext;
    private AlternativeType alternativeType;
    private AlternativeMode executionMode;
    private String query;
    private ShoppingQueryIntent interpretedIntent;
    private long totalFound;
    @Builder.Default
    private List<AlternativeProductDTO> content = new ArrayList<>();
    private long executionTimeMs;
    @Builder.Default
    private List<String> availableCategories = new ArrayList<>();
    @Builder.Default
    private List<String> availableBrands = new ArrayList<>();
    @Builder.Default
    private List<String> appliedNotes = new ArrayList<>();
}
