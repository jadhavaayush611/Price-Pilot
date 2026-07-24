package com.pricepilot.intelligence.comparison.comparator;

import com.pricepilot.intelligence.comparison.dto.ComparisonRow;
import com.pricepilot.product.dto.ProductResponseDTO;

import java.util.List;

/**
 * Strategy interface for generating a specific feature or specification comparison row across products.
 * Implementing beans are automatically registered with ComparisonRowRegistry.
 */
public interface ComparisonRowComparator {

    /**
     * Unique identifier key for this row comparator.
     */
    String getRowKey();

    /**
     * Ordering sequence index in the comparison matrix (lower numbers appear first).
     */
    int getOrder();

    /**
     * Generates a ComparisonRow for the specified list of products.
     *
     * @param products List of compared product response DTOs
     * @return Formatted ComparisonRow
     */
    ComparisonRow compare(List<ProductResponseDTO> products);
}
