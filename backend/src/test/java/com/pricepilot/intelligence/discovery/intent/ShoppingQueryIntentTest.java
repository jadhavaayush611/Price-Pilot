package com.pricepilot.intelligence.discovery.intent;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class ShoppingQueryIntentTest {

    @Test
    @DisplayName("Should create immutable ShoppingQueryIntent with valid values")
    void testShoppingQueryIntentCreation() {
        UUID sellerId = UUID.randomUUID();
        ShoppingQueryIntent intent = ShoppingQueryIntent.builder()
                .rawQuery("wireless headphones under ₹10,000 with 4+ stars")
                .semanticQuery("wireless headphones")
                .category("Headphones")
                .brand("Sony")
                .minPrice(BigDecimal.valueOf(1000))
                .maxPrice(BigDecimal.valueOf(10000))
                .minRating(4.0)
                .minDiscount(BigDecimal.valueOf(10))
                .inStock(true)
                .sellerId(sellerId)
                .sortIntent("price-asc")
                .dealIntent(true)
                .hasConflicts(false)
                .confidenceNotes(List.of("Parsed max price 10000", "Parsed rating 4.0"))
                .detectedAttributes(Map.of("category", "Headphones"))
                .build();

        assertThat(intent.getRawQuery()).isEqualTo("wireless headphones under ₹10,000 with 4+ stars");
        assertThat(intent.getSemanticQuery()).isEqualTo("wireless headphones");
        assertThat(intent.getCategory()).isEqualTo("Headphones");
        assertThat(intent.getBrand()).isEqualTo("Sony");
        assertThat(intent.getMinPrice()).isEqualTo(BigDecimal.valueOf(1000));
        assertThat(intent.getMaxPrice()).isEqualTo(BigDecimal.valueOf(10000));
        assertThat(intent.getMinRating()).isEqualTo(4.0);
        assertThat(intent.getMinDiscount()).isEqualTo(BigDecimal.valueOf(10));
        assertThat(intent.getInStock()).isTrue();
        assertThat(intent.getSellerId()).isEqualTo(sellerId);
        assertThat(intent.getSortIntent()).isEqualTo("price-asc");
        assertThat(intent.getDealIntent()).isTrue();
        assertThat(intent.isHasConflicts()).isFalse();
        assertThat(intent.getConfidenceNotes()).hasSize(2);
        assertThat(intent.hasStructuredConstraints()).isTrue();
        assertThat(intent.hasSemanticQuery()).isTrue();
    }

    @Test
    @DisplayName("Should handle empty and default intent gracefully")
    void testDefaultIntent() {
        ShoppingQueryIntent intent = ShoppingQueryIntent.builder().build();

        assertThat(intent.getRawQuery()).isEmpty();
        assertThat(intent.getSemanticQuery()).isEmpty();
        assertThat(intent.getCategory()).isNull();
        assertThat(intent.getBrand()).isNull();
        assertThat(intent.getMinPrice()).isNull();
        assertThat(intent.getMaxPrice()).isNull();
        assertThat(intent.hasStructuredConstraints()).isFalse();
        assertThat(intent.hasSemanticQuery()).isFalse();
        assertThat(intent.isHasConflicts()).isFalse();
        assertThat(intent.getConfidenceNotes()).isEmpty();
    }
}
