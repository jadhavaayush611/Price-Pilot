package com.pricepilot.intelligence.personalization.scoring;

import com.pricepilot.intelligence.personalization.context.PersonalizationContext;
import com.pricepilot.intelligence.personalization.context.PersonalizationSignal;
import com.pricepilot.intelligence.personalization.context.PersonalizationSignalType;
import com.pricepilot.intelligence.personalization.preference.AvailabilityPreference;
import com.pricepilot.intelligence.personalization.preference.DealSensitivity;
import com.pricepilot.intelligence.personalization.preference.PriceSensitivity;
import com.pricepilot.intelligence.recommendation.dto.EvidenceType;
import com.pricepilot.intelligence.recommendation.dto.ProductScore;
import com.pricepilot.product.dto.ProductResponseDTO;
import com.pricepilot.productprice.dto.ProductPriceResponseDTO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Phase 6.4 - Personalized Scoring Strategy Core Test Suite")
class PersonalizedScoringStrategyTest {

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
    @DisplayName("Invariant 1: Empty Context Neutrality")
    class EmptyContextNeutrality {

        @Test
        @DisplayName("Empty context yields exactly 0.0 adjustment and final score == base score")
        void testEmptyContextYieldsZeroAdjustment() {
            UUID prodId = UUID.randomUUID();
            ProductResponseDTO product = createProduct(prodId, "Laptop Pro", "TechCorp", "Laptops", BigDecimal.valueOf(1200), BigDecimal.valueOf(10));
            PersonalizationContext emptyContext = PersonalizationContext.empty(userId);

            PersonalizedScore score = strategy.score(product, 80.0, emptyContext);

            assertNotNull(score);
            assertEquals(80.0, score.getBaseScore());
            assertEquals(0.0, score.getPersonalizationAdjustment());
            assertEquals(80.0, score.getFinalScore());
            assertTrue(score.getBreakdown().isEmpty());
            assertTrue(score.getPositiveEvidence().isEmpty());
            assertTrue(score.getTradeOffs().isEmpty());
            assertTrue(score.isEligible());
        }

        @Test
        @DisplayName("Null context gracefully defaults to neutral score")
        void testNullContextYieldsNeutralScore() {
            UUID prodId = UUID.randomUUID();
            ProductResponseDTO product = createProduct(prodId, "Laptop Pro", "TechCorp", "Laptops", BigDecimal.valueOf(1200), null);

            PersonalizedScore score = strategy.score(product, 75.0, null);

            assertNotNull(score);
            assertEquals(75.0, score.getBaseScore());
            assertEquals(0.0, score.getPersonalizationAdjustment());
            assertEquals(75.0, score.getFinalScore());
        }
    }

    @Nested
    @DisplayName("Explicit Preference Dimension Scoring")
    class ExplicitPreferenceScoring {

        @Test
        @DisplayName("Explicit category match awards +12.0 bonus and evidence")
        void testExplicitCategoryMatch() {
            UUID prodId = UUID.randomUUID();
            ProductResponseDTO product = createProduct(prodId, "MacBook Pro", "Apple", "Laptops", BigDecimal.valueOf(2000), null);

            PersonalizationContext context = PersonalizationContext.builder(userId)
                    .addPreferredCategory("Laptops")
                    .build();

            PersonalizedScore score = strategy.score(product, 70.0, context);

            assertEquals(70.0, score.getBaseScore());
            assertEquals(12.0, score.getPersonalizationAdjustment());
            assertEquals(82.0, score.getFinalScore());
            assertEquals(12.0, score.getBreakdown().get("PreferredCategoryMatch"));
            assertTrue(score.getPositiveEvidence().stream()
                    .anyMatch(e -> e.getType() == EvidenceType.PREFERRED_CATEGORY));
        }

        @Test
        @DisplayName("Explicit category mismatch awards 0.0 bonus")
        void testExplicitCategoryMismatch() {
            UUID prodId = UUID.randomUUID();
            ProductResponseDTO product = createProduct(prodId, "Refrigerator", "LG", "Appliances", BigDecimal.valueOf(1000), null);

            PersonalizationContext context = PersonalizationContext.builder(userId)
                    .addPreferredCategory("Smartphones")
                    .build();

            PersonalizedScore score = strategy.score(product, 70.0, context);

            assertEquals(0.0, score.getPersonalizationAdjustment());
            assertEquals(70.0, score.getFinalScore());
            assertNull(score.getBreakdown().get("PreferredCategoryMatch"));
        }

        @Test
        @DisplayName("Explicit brand match awards +10.0 bonus and evidence")
        void testExplicitBrandMatch() {
            UUID prodId = UUID.randomUUID();
            ProductResponseDTO product = createProduct(prodId, "Galaxy S24", "Samsung", "Smartphones", BigDecimal.valueOf(900), null);

            PersonalizationContext context = PersonalizationContext.builder(userId)
                    .addPreferredBrand("Samsung")
                    .build();

            PersonalizedScore score = strategy.score(product, 75.0, context);

            assertEquals(10.0, score.getPersonalizationAdjustment());
            assertEquals(85.0, score.getFinalScore());
            assertEquals(10.0, score.getBreakdown().get("PreferredBrandMatch"));
            assertTrue(score.getPositiveEvidence().stream()
                    .anyMatch(e -> e.getType() == EvidenceType.PREFERRED_BRAND));
        }

        @Test
        @DisplayName("Explicit budget match awards +8.0 bonus within min/max bounds")
        void testExplicitBudgetMatch() {
            UUID prodId = UUID.randomUUID();
            ProductResponseDTO product = createProduct(prodId, "Headphones", "Sony", "Audio", BigDecimal.valueOf(250), null);

            PersonalizationContext context = PersonalizationContext.builder(userId)
                    .minBudget(BigDecimal.valueOf(100))
                    .maxBudget(BigDecimal.valueOf(300))
                    .build();

            PersonalizedScore score = strategy.score(product, 60.0, context);

            assertEquals(8.0, score.getPersonalizationAdjustment());
            assertEquals(68.0, score.getFinalScore());
            assertEquals(8.0, score.getBreakdown().get("BudgetMatch"));
            assertTrue(score.getPositiveEvidence().stream()
                    .anyMatch(e -> e.getType() == EvidenceType.WITHIN_BUDGET));
        }

        @Test
        @DisplayName("Explicit budget exceed penalty deducts -10.0 and logs trade-off")
        void testExplicitBudgetExceedPenalty() {
            UUID prodId = UUID.randomUUID();
            ProductResponseDTO product = createProduct(prodId, "Luxury Watch", "Rolex", "Watches", BigDecimal.valueOf(5000), null);

            PersonalizationContext context = PersonalizationContext.builder(userId)
                    .maxBudget(BigDecimal.valueOf(2000))
                    .build();

            PersonalizedScore score = strategy.score(product, 80.0, context);

            assertEquals(-10.0, score.getPersonalizationAdjustment());
            assertEquals(70.0, score.getFinalScore());
            assertEquals(-10.0, score.getBreakdown().get("ExceedsBudgetPenalty"));
            assertTrue(score.getTradeOffs().stream()
                    .anyMatch(e -> e.getType() == EvidenceType.EXCEEDS_BUDGET));
        }

        @Test
        @DisplayName("Explicit rating threshold met awards +6.0 bonus")
        void testExplicitRatingThresholdMet() {
            UUID prodId = UUID.randomUUID();
            ProductResponseDTO product = createProduct(prodId, "Camera", "Canon", "Cameras", BigDecimal.valueOf(600), null);

            PersonalizationContext context = PersonalizationContext.builder(userId)
                    .minRating(4.0)
                    .build();

            PersonalizedScore score = strategy.score(product, 75.0, context);

            assertEquals(6.0, score.getPersonalizationAdjustment());
            assertEquals(81.0, score.getFinalScore());
            assertEquals(6.0, score.getBreakdown().get("RatingThresholdMatch"));
            assertTrue(score.getPositiveEvidence().stream()
                    .anyMatch(e -> e.getType() == EvidenceType.RATING_CRITERIA_MET));
        }

        @Test
        @DisplayName("Explicit deal sensitivity match awards +8.0 bonus for high discount")
        void testExplicitDealSensitivityMatch() {
            UUID prodId = UUID.randomUUID();
            ProductResponseDTO product = createProduct(prodId, "Sneakers", "Nike", "Shoes", BigDecimal.valueOf(80), BigDecimal.valueOf(25));

            PersonalizationContext context = PersonalizationContext.builder(userId)
                    .dealSensitivity(DealSensitivity.HIGH)
                    .build();

            PersonalizedScore score = strategy.score(product, 65.0, context);

            assertEquals(8.0, score.getPersonalizationAdjustment());
            assertEquals(73.0, score.getFinalScore());
            assertEquals(8.0, score.getBreakdown().get("DealSensitivityMatch"));
            assertTrue(score.getPositiveEvidence().stream()
                    .anyMatch(e -> e.getType() == EvidenceType.DEAL_SENSITIVITY_MATCH));
        }

        @Test
        @DisplayName("Availability preference IN_STOCK_ONLY applies +4.0 in-stock bonus or -20.0 penalty")
        void testAvailabilityPreference() {
            UUID prodId1 = UUID.randomUUID();
            UUID prodId2 = UUID.randomUUID();

            ProductResponseDTO inStockProduct = createProduct(prodId1, "In Stock Item", "BrandA", "CategoryA", BigDecimal.valueOf(50), null);
            ProductResponseDTO outOfStockProduct = createProduct(prodId2, "Out of Stock Item", "BrandA", "CategoryA", null, null);

            PersonalizationContext context = PersonalizationContext.builder(userId)
                    .availabilityPreference(AvailabilityPreference.IN_STOCK_ONLY)
                    .build();

            PersonalizedScore inStockScore = strategy.score(inStockProduct, 70.0, context);
            PersonalizedScore outOfStockScore = strategy.score(outOfStockProduct, 70.0, context);

            assertEquals(4.0, inStockScore.getPersonalizationAdjustment());
            assertEquals(74.0, inStockScore.getFinalScore());

            assertEquals(-20.0, outOfStockScore.getPersonalizationAdjustment());
            assertEquals(50.0, outOfStockScore.getFinalScore());
            assertTrue(outOfStockScore.getTradeOffs().stream()
                    .anyMatch(e -> e.getType() == EvidenceType.LIMITED_AVAILABILITY));
        }
    }

    @Nested
    @DisplayName("Behavioral Signal Dimension Scoring")
    class BehavioralSignalScoring {

        @Test
        @DisplayName("Behavioral category affinity scales linearly up to +6.0")
        void testBehavioralCategoryAffinity() {
            UUID prodId = UUID.randomUUID();
            ProductResponseDTO product = createProduct(prodId, "Tablet", "Apple", "Tablets", BigDecimal.valueOf(500), null);

            PersonalizationContext context = PersonalizationContext.builder(userId)
                    .addCategoryAffinity("Tablets", 0.75)
                    .build();

            PersonalizedScore score = strategy.score(product, 70.0, context);

            // 0.75 * 6.0 = 4.5
            assertEquals(4.5, score.getPersonalizationAdjustment());
            assertEquals(74.5, score.getFinalScore());
            assertEquals(4.5, score.getBreakdown().get("BehavioralCategoryAffinity"));
            assertTrue(score.getPositiveEvidence().stream()
                    .anyMatch(e -> e.getType() == EvidenceType.BEHAVIORAL_AFFINITY));
        }

        @Test
        @DisplayName("Behavioral brand affinity scales linearly up to +4.0")
        void testBehavioralBrandAffinity() {
            UUID prodId = UUID.randomUUID();
            ProductResponseDTO product = createProduct(prodId, "Monitor", "Dell", "Monitors", BigDecimal.valueOf(300), null);

            PersonalizationContext context = PersonalizationContext.builder(userId)
                    .addBrandAffinity("Dell", 0.50)
                    .build();

            PersonalizedScore score = strategy.score(product, 70.0, context);

            // 0.50 * 4.0 = 2.0
            assertEquals(2.0, score.getPersonalizationAdjustment());
            assertEquals(72.0, score.getFinalScore());
            assertEquals(2.0, score.getBreakdown().get("BehavioralBrandAffinity"));
        }

        @Test
        @DisplayName("Behavioral interaction affinity awards +3.0 bonus when user interacted recently")
        void testBehavioralInteractionAffinity() {
            UUID prodId = UUID.randomUUID();
            ProductResponseDTO product = createProduct(prodId, "Keyboard", "Logitech", "Accessories", BigDecimal.valueOf(100), null);

            PersonalizationContext context = PersonalizationContext.builder(userId)
                    .addSignal(PersonalizationSignal.behavioral(
                            PersonalizationSignalType.INTERACTION_AFFINITY,
                            prodId.toString(),
                            prodId.toString(),
                            1.0
                    ))
                    .build();

            PersonalizedScore score = strategy.score(product, 80.0, context);

            assertEquals(3.0, score.getPersonalizationAdjustment());
            assertEquals(83.0, score.getFinalScore());
            assertEquals(3.0, score.getBreakdown().get("BehavioralInteractionAffinity"));
        }
    }

    @Nested
    @DisplayName("Deterministic Comparator & Ranking")
    class DeterministicRanking {

        @Test
        @DisplayName("Ranks candidates deterministically using 5-tier tie breaking")
        void testDeterministicComparator5Tiers() {
            UUID id1 = UUID.fromString("00000000-0000-0000-0000-000000000001");
            UUID id2 = UUID.fromString("00000000-0000-0000-0000-000000000002");

            ProductResponseDTO p1 = createProduct(id1, "Product A", "BrandA", "CategoryA", BigDecimal.valueOf(100), null);
            ProductResponseDTO p2 = createProduct(id2, "Product B", "BrandA", "CategoryA", BigDecimal.valueOf(100), null);

            PersonalizedScore s1 = PersonalizedScore.builder()
                    .baseScore(80.0)
                    .personalizationAdjustment(5.0)
                    .finalScore(85.0)
                    .build();

            PersonalizedScore s2 = PersonalizedScore.builder()
                    .baseScore(80.0)
                    .personalizationAdjustment(5.0)
                    .finalScore(85.0)
                    .build();

            Map<UUID, PersonalizedScore> scoreMap = Map.of(id1, s1, id2, s2);
            Comparator<ProductResponseDTO> comparator = strategy.getDeterministicComparator(scoreMap);

            List<ProductResponseDTO> list = new ArrayList<>(List.of(p2, p1));
            list.sort(comparator);

            // Tied on finalScore (85), baseScore (80), adjustment (5), price (100) -> id1 wins by UUID tie breaker
            assertEquals(id1, list.get(0).getId());
            assertEquals(id2, list.get(1).getId());
        }
    }
}
