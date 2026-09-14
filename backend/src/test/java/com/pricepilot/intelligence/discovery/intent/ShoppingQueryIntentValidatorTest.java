package com.pricepilot.intelligence.discovery.intent;

import com.pricepilot.intelligence.discovery.hybrid.HybridSearchRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class ShoppingQueryIntentValidatorTest {

    private DefaultShoppingQueryIntentValidator validator;

    @BeforeEach
    void setUp() {
        validator = new DefaultShoppingQueryIntentValidator();
    }

    @Test
    @DisplayName("Validation: Valid intent passes successfully")
    void testValidIntentValidation() {
        ShoppingQueryIntent intent = ShoppingQueryIntent.builder()
                .rawQuery("headphones under 10000")
                .semanticQuery("headphones")
                .category("Headphones")
                .minPrice(BigDecimal.valueOf(1000))
                .maxPrice(BigDecimal.valueOf(10000))
                .minRating(4.0)
                .minDiscount(BigDecimal.valueOf(15))
                .inStock(true)
                .build();

        ShoppingQueryValidationResult result = validator.validate(intent);

        assertThat(result.isValid()).isTrue();
        assertThat(result.isHasConflicts()).isFalse();
        assertThat(result.getErrors()).isEmpty();
    }

    @Test
    @DisplayName("Validation: Negative prices must fail validation")
    void testNegativePriceValidation() {
        ShoppingQueryIntent intent = ShoppingQueryIntent.builder()
                .rawQuery("laptop")
                .minPrice(BigDecimal.valueOf(-500))
                .build();

        ShoppingQueryValidationResult result = validator.validate(intent);

        assertThat(result.isValid()).isFalse();
        assertThat(result.getErrors()).anyMatch(e -> e.contains("negative"));
    }

    @Test
    @DisplayName("Validation: minPrice exceeding maxPrice must fail as conflict")
    void testMinPriceExceedsMaxPrice() {
        ShoppingQueryIntent intent = ShoppingQueryIntent.builder()
                .rawQuery("laptop")
                .minPrice(BigDecimal.valueOf(20000))
                .maxPrice(BigDecimal.valueOf(10000))
                .build();

        ShoppingQueryValidationResult result = validator.validate(intent);

        assertThat(result.isValid()).isFalse();
        assertThat(result.isHasConflicts()).isTrue();
        assertThat(result.getErrors()).anyMatch(e -> e.contains("exceeds"));
    }

    @Test
    @DisplayName("Validation: Rating outside 0.0-5.0 must fail validation")
    void testInvalidRatingValidation() {
        ShoppingQueryIntent intent = ShoppingQueryIntent.builder()
                .rawQuery("laptop")
                .minRating(6.5)
                .build();

        ShoppingQueryValidationResult result = validator.validate(intent);

        assertThat(result.isValid()).isFalse();
        assertThat(result.getErrors()).anyMatch(e -> e.contains("between 0.0 and 5.0"));
    }

    @Test
    @DisplayName("Transformation: Maps ShoppingQueryIntent to HybridSearchRequest properly")
    void testToHybridSearchRequest() {
        ShoppingQueryIntent intent = ShoppingQueryIntent.builder()
                .rawQuery("wireless headphones under ₹10,000")
                .semanticQuery("wireless headphones")
                .category("Headphones")
                .brand("Sony")
                .maxPrice(BigDecimal.valueOf(10000))
                .minRating(4.0)
                .inStock(true)
                .sortIntent("price-asc")
                .build();

        HybridSearchRequest request = validator.toHybridSearchRequest(intent, 1, 20, null);

        assertThat(request.getQuery()).isEqualTo("wireless headphones");
        assertThat(request.getCategory()).isEqualTo("Headphones");
        assertThat(request.getBrand()).isEqualTo("Sony");
        assertThat(request.getMaxPrice()).isEqualTo(BigDecimal.valueOf(10000));
        assertThat(request.getMinRating()).isEqualTo(4.0);
        assertThat(request.getInStock()).isTrue();
        assertThat(request.getSort()).isEqualTo("price-asc");
        assertThat(request.getPage()).isEqualTo(1);
        assertThat(request.getSize()).isEqualTo(20);
    }
}
