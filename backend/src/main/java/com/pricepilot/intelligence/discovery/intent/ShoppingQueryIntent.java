package com.pricepilot.intelligence.discovery.intent;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.ToString;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.*;

/**
 * Immutable domain contract representing interpreted shopping requirements extracted from natural language.
 *
 * Core Principle: Natural language is an input mechanism, not a source of truth.
 * This intent separates structured deterministic constraints (price, category, brand, rating, discount, stock)
 * from residual semantic query text for hybrid candidate retrieval.
 */
@Getter
@ToString
@Builder(toBuilder = true)
public final class ShoppingQueryIntent implements Serializable {

    private static final long serialVersionUID = 1L;

    private final String rawQuery;
    private final String semanticQuery;
    private final String category;
    private final String brand;
    private final BigDecimal minPrice;
    private final BigDecimal maxPrice;
    private final Double minRating;
    private final BigDecimal minDiscount;
    private final Boolean inStock;
    private final UUID sellerId;
    private final String sortIntent;
    private final Boolean dealIntent;
    private final boolean hasConflicts;
    private final String conflictDescription;
    private final List<String> confidenceNotes;
    private final Map<String, String> detectedAttributes;

    public ShoppingQueryIntent(
            String rawQuery,
            String semanticQuery,
            String category,
            String brand,
            BigDecimal minPrice,
            BigDecimal maxPrice,
            Double minRating,
            BigDecimal minDiscount,
            Boolean inStock,
            UUID sellerId,
            String sortIntent,
            Boolean dealIntent,
            boolean hasConflicts,
            String conflictDescription,
            List<String> confidenceNotes,
            Map<String, String> detectedAttributes) {
        this.rawQuery = rawQuery != null ? rawQuery : "";
        this.semanticQuery = semanticQuery != null ? semanticQuery.trim() : "";
        this.category = category != null && !category.isBlank() ? category.trim() : null;
        this.brand = brand != null && !brand.isBlank() ? brand.trim() : null;
        this.minPrice = minPrice;
        this.maxPrice = maxPrice;
        this.minRating = minRating;
        this.minDiscount = minDiscount;
        this.inStock = inStock;
        this.sellerId = sellerId;
        this.sortIntent = sortIntent != null && !sortIntent.isBlank() ? sortIntent.trim() : null;
        this.dealIntent = dealIntent;
        this.hasConflicts = hasConflicts;
        this.conflictDescription = conflictDescription;
        this.confidenceNotes = confidenceNotes != null ? Collections.unmodifiableList(new ArrayList<>(confidenceNotes)) : Collections.emptyList();
        this.detectedAttributes = detectedAttributes != null ? Collections.unmodifiableMap(new LinkedHashMap<>(detectedAttributes)) : Collections.emptyMap();
    }

    /**
     * Checks if any structured filter was extracted.
     */
    public boolean hasStructuredConstraints() {
        return category != null || brand != null || minPrice != null || maxPrice != null
                || minRating != null || minDiscount != null || inStock != null || sellerId != null;
    }

    /**
     * Checks if any semantic text remains for vector search.
     */
    public boolean hasSemanticQuery() {
        return semanticQuery != null && !semanticQuery.isBlank();
    }
}
