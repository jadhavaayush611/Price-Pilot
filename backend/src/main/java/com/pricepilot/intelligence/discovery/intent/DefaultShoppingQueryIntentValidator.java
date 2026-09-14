package com.pricepilot.intelligence.discovery.intent;

import com.pricepilot.intelligence.discovery.hybrid.HybridSearchRequest;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * Default implementation of ShoppingQueryIntentValidator.
 *
 * Enforces hard mathematical bounds and logical consistency on parsed intent:
 * - Rejects negative prices, invalid ratings (< 0 or > 5), and out-of-bound discounts (< 0% or > 100%).
 * - Rejects contradictory constraints (minPrice > maxPrice).
 * - Maps valid intent into a bounded HybridSearchRequest for downstream candidate discovery.
 */
@Component
public class DefaultShoppingQueryIntentValidator implements ShoppingQueryIntentValidator {

    private static final int MAX_PAGE_SIZE = 50;

    @Override
    public ShoppingQueryValidationResult validate(ShoppingQueryIntent intent) {
        if (intent == null) {
            return ShoppingQueryValidationResult.invalid(
                    ShoppingQueryIntent.builder().rawQuery("").semanticQuery("").build(),
                    List.of("Intent cannot be null")
            );
        }

        List<String> errors = new ArrayList<>();

        if (intent.isHasConflicts()) {
            errors.add(intent.getConflictDescription() != null
                    ? intent.getConflictDescription()
                    : "Conflicting constraints detected in user query");
            return ShoppingQueryValidationResult.conflict(intent, errors);
        }

        // Numeric price checks
        if (intent.getMinPrice() != null && intent.getMinPrice().compareTo(BigDecimal.ZERO) < 0) {
            errors.add("Minimum price cannot be negative: " + intent.getMinPrice());
        }
        if (intent.getMaxPrice() != null && intent.getMaxPrice().compareTo(BigDecimal.ZERO) < 0) {
            errors.add("Maximum price cannot be negative: " + intent.getMaxPrice());
        }
        if (intent.getMinPrice() != null && intent.getMaxPrice() != null
                && intent.getMinPrice().compareTo(intent.getMaxPrice()) > 0) {
            errors.add("Minimum price (" + intent.getMinPrice() + ") exceeds maximum price (" + intent.getMaxPrice() + ")");
            return ShoppingQueryValidationResult.conflict(intent, errors);
        }

        // Rating checks
        if (intent.getMinRating() != null && (intent.getMinRating() < 0.0 || intent.getMinRating() > 5.0)) {
            errors.add("Minimum rating must be between 0.0 and 5.0, given: " + intent.getMinRating());
        }

        // Discount checks
        if (intent.getMinDiscount() != null) {
            if (intent.getMinDiscount().compareTo(BigDecimal.ZERO) < 0 || intent.getMinDiscount().compareTo(BigDecimal.valueOf(100)) > 0) {
                errors.add("Discount percentage must be between 0 and 100, given: " + intent.getMinDiscount());
            }
        }

        if (!errors.isEmpty()) {
            return ShoppingQueryValidationResult.invalid(intent, errors);
        }

        return ShoppingQueryValidationResult.success(intent);
    }

    @Override
    public HybridSearchRequest toHybridSearchRequest(ShoppingQueryIntent intent, int page, int size, String sortOverride) {
        if (intent == null) {
            return HybridSearchRequest.builder().query("").page(0).size(10).build();
        }

        // Determine effective semantic query
        String effectiveQuery = "";
        if (intent.getSemanticQuery() != null && !intent.getSemanticQuery().isBlank()) {
            effectiveQuery = intent.getSemanticQuery().trim();
        } else if (intent.getCategory() != null && !intent.getCategory().isBlank()) {
            effectiveQuery = intent.getCategory().trim();
        } else if (intent.getBrand() != null && !intent.getBrand().isBlank()) {
            effectiveQuery = intent.getBrand().trim();
        } else if (intent.getRawQuery() != null) {
            effectiveQuery = intent.getRawQuery().trim();
        }

        // Determine effective sort
        String effectiveSort = "relevance";
        if (sortOverride != null && !sortOverride.isBlank() && !"default".equalsIgnoreCase(sortOverride)) {
            effectiveSort = sortOverride.trim();
        } else if (intent.getSortIntent() != null && !intent.getSortIntent().isBlank()) {
            effectiveSort = intent.getSortIntent().trim();
        }

        int boundedPage = Math.max(0, page);
        int boundedSize = Math.max(1, Math.min(MAX_PAGE_SIZE, size > 0 ? size : 10));

        return HybridSearchRequest.builder()
                .query(effectiveQuery)
                .category(intent.getCategory())
                .brand(intent.getBrand())
                .minPrice(intent.getMinPrice())
                .maxPrice(intent.getMaxPrice())
                .minRating(intent.getMinRating())
                .minDiscount(intent.getMinDiscount())
                .inStock(intent.getInStock())
                .sellerId(intent.getSellerId())
                .sort(effectiveSort)
                .page(boundedPage)
                .size(boundedSize)
                .build();
    }
}
