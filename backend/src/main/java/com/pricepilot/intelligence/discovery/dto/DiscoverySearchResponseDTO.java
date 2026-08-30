package com.pricepilot.intelligence.discovery.dto;

import com.pricepilot.intelligence.discovery.interpretation.InterpretedQuery;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/**
 * Root discovery response payload containing search results, query diagnostics, and available facets.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DiscoverySearchResponseDTO {

    @Builder.Default
    private List<DiscoveryProductDTO> content = new ArrayList<>();

    private int page;
    private int size;
    private long totalElements;
    private int totalPages;

    private InterpretedQuery interpretedQuery;
    private String appliedSort;
    private long executionTimeMs;

    @Builder.Default
    private List<String> availableCategories = new ArrayList<>();
    @Builder.Default
    private List<String> availableBrands = new ArrayList<>();
}
