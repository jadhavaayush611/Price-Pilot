package com.pricepilot.intelligence.personalization.scoring;

import com.pricepilot.intelligence.personalization.context.PersonalizationContext;
import com.pricepilot.intelligence.personalization.context.PersonalizationSignal;
import com.pricepilot.intelligence.personalization.context.PersonalizationSignalType;
import com.pricepilot.intelligence.personalization.preference.AvailabilityPreference;
import com.pricepilot.intelligence.personalization.preference.DealSensitivity;
import com.pricepilot.intelligence.personalization.preference.PriceSensitivity;
import com.pricepilot.intelligence.recommendation.dto.EvidenceItem;
import com.pricepilot.product.dto.ProductResponseDTO;
import com.pricepilot.productprice.dto.ProductPriceResponseDTO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Phase 6.4 - Personalized Scoring Adversarial & Invariant Test Suite")
class PersonalizedScoringAdversarialTest {

    private DefaultPersonalizedScoringStrategy strategy;
    private UUID userId;

    @BeforeEach
    void setUp() {
        strategy = new DefaultPersonalizedScoringStrategy();
        userId = UUID.randomUUID();
    }

    private ProductResponseDTO createProduct(UUID id, String name, String brand, String category, BigDecimal price, BigDecimal discountPct) {
        ProductPriceResponseDTO priceDTO = ProductPriceResponseDTO.builder()
                .currentPrice(price)
                .discountPercentage(discountPct != null ? discountPct : BigDecimal.ZERO)
                .build();

        return ProductResponseDTO.builder()
                .id(id)
                .name(name)
                .brand(brand)
                .category(category)
                .prices(price != null ? List.of(priceDTO) : Collections.emptyList())
                .build();
    }

    @Nested
    @DisplayName("Invariant 2: Adjustment Bounds Enforced")
    class AdjustmentBounds {

        @Test
        @DisplayName("Caps maximum positive personalization adjustment at +35.0 even when raw sum is +54.0")
        void testMaxPositiveAdjustmentCap() {
            UUID prodId = UUID.randomUUID();
            // Product matches Category (+12), Brand (+10), Budget (+8), Rating (+6), Deal (+8), CatAffinity (+6), BrandAffinity (+4) -> Raw = +54.0
            ProductResponseDTO product = createProduct(prodId, "Elite Phone", "BrandMax", "SuperCategory", BigDecimal.valueOf(500), BigDecimal.valueOf(30));

            PersonalizationContext context = PersonalizationContext.builder(userId)
                    .addPreferredCategory("SuperCategory")
                    .addPreferredBrand("BrandMax")
                    .minBudget(BigDecimal.valueOf(100))
                    .maxBudget(BigDecimal.valueOf(1000))
                    .minRating(4.0)
                    .dealSensitivity(DealSensitivity.HIGH)
                    .addCategoryAffinity("SuperCategory", 1.0)
                    .addBrandAffinity("BrandMax", 1.0)
                    .build();

            PersonalizedScore score = strategy.score(product, 50.0, context);

            assertEquals(35.0, score.getPersonalizationAdjustment(), "Adjustment must be strictly capped at +35.0");
            assertEquals(85.0, score.getFinalScore());
        }

        @Test
        @DisplayName("Caps maximum negative personalization adjustment at -30.0 even with compounded penalties")
        void testMaxNegativeAdjustmentCap() {
            UUID prodId = UUID.randomUUID();
            // Out of stock penalty (-20) + Exceeds budget penalty (-10) = -30.0
            ProductResponseDTO product = createProduct(prodId, "Expensive Sold Out Item", "BrandX", "CategoryY", null, null);

            PersonalizationContext context = PersonalizationContext.builder(userId)
                    .availabilityPreference(AvailabilityPreference.IN_STOCK_ONLY)
                    .maxBudget(BigDecimal.valueOf(100))
                    .build();

            PersonalizedScore score = strategy.score(product, 80.0, context);

            assertTrue(score.getPersonalizationAdjustment() >= -30.0, "Adjustment cannot be lower than -30.0");
            assertEquals(-20.0, score.getPersonalizationAdjustment());
        }
    }

    @Nested
    @DisplayName("Invariant 3: Final Score Range [0.0, 100.0] Clamping")
    class FinalScoreClamping {

        @Test
        @DisplayName("Clamps final score to maximum 100.0 when baseScore (90.0) + adjustment (35.0) = 125.0")
        void testFinalScoreCeilingClamp() {
            UUID prodId = UUID.randomUUID();
            ProductResponseDTO product = createProduct(prodId, "Flagship Phone", "Apple", "Smartphones", BigDecimal.valueOf(800), BigDecimal.valueOf(20));

            PersonalizationContext context = PersonalizationContext.builder(userId)
                    .addPreferredCategory("Smartphones")
                    .addPreferredBrand("Apple")
                    .build();

            PersonalizedScore score = strategy.score(product, 95.0, context);

            assertEquals(95.0, score.getBaseScore());
            assertEquals(22.0, score.getPersonalizationAdjustment());
            assertEquals(100.0, score.getFinalScore(), "Final score must not exceed 100.0 ceiling");
        }

        @Test
        @DisplayName("Clamps final score to minimum 0.0 when baseScore (10.0) + penalty (-20.0) = -10.0")
        void testFinalScoreFloorClamp() {
            UUID prodId = UUID.randomUUID();
            ProductResponseDTO product = createProduct(prodId, "Unavailable Item", "BrandZ", "CategoryZ", null, null);

            PersonalizationContext context = PersonalizationContext.builder(userId)
                    .availabilityPreference(AvailabilityPreference.IN_STOCK_ONLY)
                    .build();

            PersonalizedScore score = strategy.score(product, 10.0, context);

            assertEquals(10.0, score.getBaseScore());
            assertEquals(-20.0, score.getPersonalizationAdjustment());
            assertEquals(0.0, score.getFinalScore(), "Final score must not fall below 0.0 floor");
        }
    }

    @Nested
    @DisplayName("Invariant 4: Hard Constraints & Non-Mutation")
    class HardConstraintsNonMutation {

        @Test
        @DisplayName("Personalization does not mutate product data or change candidate eligibility")
        void testNonMutationAndEligibility() {
            UUID prodId = UUID.randomUUID();
            ProductResponseDTO product = createProduct(prodId, "Immutable Product", "BrandA", "CategoryA", BigDecimal.valueOf(200), BigDecimal.valueOf(10));
            String originalName = product.getName();
            BigDecimal originalPrice = product.getPrices().get(0).getCurrentPrice();

            PersonalizationContext context = PersonalizationContext.builder(userId)
                    .addPreferredCategory("CategoryA")
                    .build();

            PersonalizedScore score = strategy.score(product, 75.0, context);

            assertEquals(originalName, product.getName());
            assertEquals(originalPrice, product.getPrices().get(0).getCurrentPrice());
            assertTrue(score.isEligible(), "Personalization must not invalidate candidate eligibility");
        }
    }

    @Nested
    @DisplayName("Invariant 5: Numeric Safety & Robustness")
    class NumericSafety {

        @Test
        @DisplayName("Sanitizes NaN and Infinite base scores safely to default score")
        void testNaNSanitization() {
            UUID prodId = UUID.randomUUID();
            ProductResponseDTO product = createProduct(prodId, "Sample", "Brand", "Category", BigDecimal.valueOf(50), null);
            PersonalizationContext context = PersonalizationContext.empty(userId);

            PersonalizedScore nanScore = strategy.score(product, Double.NaN, context);
            assertEquals(75.0, nanScore.getBaseScore());
            assertEquals(75.0, nanScore.getFinalScore());

            PersonalizedScore infScore = strategy.score(product, Double.POSITIVE_INFINITY, context);
            assertEquals(75.0, infScore.getBaseScore());
        }

        @Test
        @DisplayName("PersonalizedScore model safely sanitizes constructor inputs")
        void testModelNumericSanitization() {
            PersonalizedScore score = PersonalizedScore.builder()
                    .baseScore(Double.NaN)
                    .personalizationAdjustment(Double.POSITIVE_INFINITY)
                    .finalScore(Double.NaN)
                    .build();

            assertEquals(75.0, score.getBaseScore());
            assertEquals(0.0, score.getPersonalizationAdjustment());
            assertEquals(75.0, score.getFinalScore());
        }

        @Test
        @DisplayName("Handles null product and null entries gracefully in comparator")
        void testNullHandlingInComparator() {
            UUID id1 = UUID.randomUUID();
            ProductResponseDTO p1 = createProduct(id1, "Valid Product", "Brand", "Cat", BigDecimal.valueOf(100), null);

            PersonalizedScore s1 = PersonalizedScore.neutral(80.0);
            Map<UUID, PersonalizedScore> map = Map.of(id1, s1);

            Comparator<ProductResponseDTO> comparator = strategy.getDeterministicComparator(map);

            List<ProductResponseDTO> list = new ArrayList<>(Arrays.asList(null, p1));
            assertDoesNotThrow(() -> list.sort(comparator));
            assertEquals(p1, list.get(0));
            assertNull(list.get(1));
        }
    }

    @Nested
    @DisplayName("Invariant 6: Immutability Enforced")
    class ImmutabilityEnforcement {

        @Test
        @DisplayName("PersonalizedScore collections are strictly unmodifiable")
        void testCollectionImmutability() {
            UUID prodId = UUID.randomUUID();
            ProductResponseDTO product = createProduct(prodId, "Item", "Brand", "Category", BigDecimal.valueOf(50), null);

            PersonalizationContext context = PersonalizationContext.builder(userId)
                    .addPreferredCategory("Category")
                    .build();

            PersonalizedScore score = strategy.score(product, 75.0, context);

            assertThrows(UnsupportedOperationException.class, () ->
                    score.getBreakdown().put("Hack", 99.0));

            assertThrows(UnsupportedOperationException.class, () ->
                    score.getPositiveEvidence().add(new EvidenceItem(prodId, "Item", null, null, null, null, null, true, 1.0)));

            assertThrows(UnsupportedOperationException.class, () ->
                    score.getTradeOffs().clear());
        }
    }

    @Nested
    @DisplayName("Invariant 7: Contradictory Explicit vs Behavioral Signals")
    class ContradictorySignals {

        @Test
        @DisplayName("Evaluates coexisting signals predictably without signal collision")
        void testContradictorySignalsCoexistence() {
            UUID prodId = UUID.randomUUID();
            ProductResponseDTO product = createProduct(prodId, "Hybrid Laptop", "Dell", "Laptops", BigDecimal.valueOf(1000), null);

            // Explicit preference prefers "Dell" (+10), but Behavioral affinity is for "Apple" (Dell affinity = 0.0)
            PersonalizationContext context = PersonalizationContext.builder(userId)
                    .addPreferredBrand("Dell")
                    .addBrandAffinity("Apple", 1.0)
                    .build();

            PersonalizedScore score = strategy.score(product, 70.0, context);

            // Dell receives explicit bonus (+10.0) and 0.0 behavioral bonus
            assertEquals(10.0, score.getPersonalizationAdjustment());
            assertEquals(80.0, score.getFinalScore());
            assertEquals(10.0, score.getBreakdown().get("PreferredBrandMatch"));
            assertNull(score.getBreakdown().get("BehavioralBrandAffinity"));
        }
    }
}
