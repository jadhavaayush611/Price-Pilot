package com.pricepilot.intelligence.comparison.comparator;

import com.pricepilot.intelligence.comparison.dto.ComparisonRow;
import com.pricepilot.product.dto.ProductResponseDTO;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Registry component that collects all ComparisonRowComparator beans in the Spring context
 * and executes them in sequence based on their order.
 */
@Component
public class ComparisonRowRegistry {

    private final List<ComparisonRowComparator> comparators;

    public ComparisonRowRegistry(List<ComparisonRowComparator> comparators) {
        this.comparators = comparators.stream()
                .sorted(Comparator.comparingInt(ComparisonRowComparator::getOrder))
                .collect(Collectors.toList());
    }

    /**
     * Generates matrix rows by executing registered row comparators.
     *
     * @param products List of compared product response DTOs
     * @return List of ComparisonRow instances
     */
    public List<ComparisonRow> generateRows(List<ProductResponseDTO> products) {
        if (products == null || products.isEmpty()) {
            return List.of();
        }
        return comparators.stream()
                .map(comparator -> comparator.compare(products))
                .collect(Collectors.toList());
    }
}
