package com.pricepilot.intelligence.personalization.evidence;

import com.pricepilot.intelligence.personalization.context.PersonalizationContext;
import com.pricepilot.intelligence.personalization.context.PersonalizationSignal;
import com.pricepilot.intelligence.personalization.context.PersonalizationSignalType;
import com.pricepilot.intelligence.personalization.preference.AvailabilityPreference;
import com.pricepilot.intelligence.personalization.preference.DealSensitivity;
import com.pricepilot.intelligence.personalization.scoring.DefaultPersonalizedScoringStrategy;
import com.pricepilot.intelligence.personalization.scoring.PersonalizedScore;
import com.pricepilot.intelligence.recommendation.dto.EvidenceItem;
import com.pricepilot.intelligence.recommendation.dto.EvidenceType;
import com.pricepilot.product.dto.ProductResponseDTO;
import com.pricepilot.productprice.dto.ProductPriceResponseDTO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Phase 6.5 - Personalized Evidence Generator Test Suite")
class PersonalizedEvidenceGeneratorTest {

    private DefaultPersonalizedEvidenceGenerator generator;
    private DefaultPersonalizedScoringStrategy scorer;
    private UUID userId;

    @BeforeEach
    void setUp() {
        generator = new DefaultPersonalizedEvidenceGenerator();
        scorer = new DefaultPersonalizedScoringStrategy();
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
    @DisplayName("Empty Context & Score Invariants")
    class EmptyContextInvariants {

        @Test
        @DisplayName("Empty context yields empty PersonalizedEvidence with zero items")
        void testEmptyContextYieldsEmptyEvidence() {
            UUID prodId = UUID.randomUUID();
            ProductResponseDTO product = createProduct(prodId, "Laptop Pro", "Apple", "Laptops", BigDecimal.valueOf(1500), null);
            PersonalizationContext context = PersonalizationContext.empty(userId);
            PersonalizedScore score = scorer.score(product, 80.0, context);

            PersonalizedEvidence evidence = generator.generate(product, score, context);

            assertNotNull(evidence);
            assertTrue(evidence.isEmpty());
            assertFalse(evidence.hasPersonalization());
            assertEquals(0, evidence.totalEvidenceCount());
            assertEquals(0.0, evidence.getNetAdjustment());
            assertEquals(prodId, evidence.getProductId());
        }

        @Test
        @DisplayName("Null inputs yield safe empty PersonalizedEvidence")
        void testNullInputsYieldEmptyEvidence() {
            PersonalizedEvidence e1 = generator.generate(null, null, null);
            assertNotNull(e1);
            assertTrue(e1.isEmpty());

            UUID prodId = UUID.randomUUID();
            ProductResponseDTO product = createProduct(prodId, "Item", "Brand", "Category", BigDecimal.valueOf(100), null);
            PersonalizedEvidence e2 = generator.generate(product, null, null);
            assertNotNull(e2);
            assertTrue(e2.isEmpty());
            assertEquals(prodId, e2.getProductId());
        }
    }

    @Nested
    @DisplayName("Explicit Preference Grounded Evidence")
    class ExplicitPreferenceEvidence {

        @Test
        @DisplayName("Generates grounded category evidence for preferred category match")
        void testPreferredCategoryEvidence() {
            UUID prodId = UUID.randomUUID();
            ProductResponseDTO product = createProduct(prodId, "MacBook Air", "Apple", "Laptops", BigDecimal.valueOf(1100), null);

            PersonalizationContext context = PersonalizationContext.builder(userId)
                    .addPreferredCategory("Laptops")
                    .build();

            PersonalizedScore score = scorer.score(product, 75.0, context);
            PersonalizedEvidence evidence = generator.generate(product, score, context);

            assertNotNull(evidence);
            assertEquals(1, evidence.getPositiveEvidence().size());
            assertEquals(0, evidence.getTradeOffs().size());

            EvidenceItem item = evidence.getPositiveEvidence().get(0);
            assertEquals(EvidenceType.PREFERRED_CATEGORY, item.getType());
            assertEquals("Matches your preferred category: Laptops", item.getDescription());
            assertEquals("Category", item.getMetricName());
            assertEquals("Laptops", item.getMetricValue());
            assertTrue(item.isPositive());
            assertEquals(0.90, item.getImportance());
        }

        @Test
        @DisplayName("Generates grounded brand evidence for preferred brand match")
        void testPreferredBrandEvidence() {
            UUID prodId = UUID.randomUUID();
            ProductResponseDTO product = createProduct(prodId, "Galaxy Tab", "Samsung", "Tablets", BigDecimal.valueOf(600), null);

            PersonalizationContext context = PersonalizationContext.builder(userId)
                    .addPreferredBrand("Samsung")
                    .build();

            PersonalizedScore score = scorer.score(product, 75.0, context);
            PersonalizedEvidence evidence = generator.generate(product, score, context);

            assertEquals(1, evidence.getPositiveEvidence().size());
            EvidenceItem item = evidence.getPositiveEvidence().get(0);
            assertEquals(EvidenceType.PREFERRED_BRAND, item.getType());
            assertEquals("Matches your preferred brand: Samsung", item.getDescription());
            assertEquals("Brand", item.getMetricName());
            assertEquals("Samsung", item.getMetricValue());
        }

        @Test
        @DisplayName("Generates grounded budget evidence within min and max budget bounds")
        void testWithinBudgetEvidence() {
            UUID prodId = UUID.randomUUID();
            ProductResponseDTO product = createProduct(prodId, "Headphones", "Sony", "Audio", BigDecimal.valueOf(250), null);

            PersonalizationContext context = PersonalizationContext.builder(userId)
                    .minBudget(BigDecimal.valueOf(100))
                    .maxBudget(BigDecimal.valueOf(400))
                    .build();

            PersonalizedScore score = scorer.score(product, 70.0, context);
            PersonalizedEvidence evidence = generator.generate(product, score, context);

            assertEquals(1, evidence.getPositiveEvidence().size());
            EvidenceItem item = evidence.getPositiveEvidence().get(0);
            assertEquals(EvidenceType.WITHIN_BUDGET, item.getType());
            assertTrue(item.getDescription().contains("fits your budget ($100 - $400)"));
        }

        @Test
        @DisplayName("Generates grounded trade-off when product price exceeds max budget")
        void testExceedsBudgetTradeOff() {
            UUID prodId = UUID.randomUUID();
            ProductResponseDTO product = createProduct(prodId, "High-End TV", "Sony", "TVs", BigDecimal.valueOf(3000), null);

            PersonalizationContext context = PersonalizationContext.builder(userId)
                    .maxBudget(BigDecimal.valueOf(1500))
                    .build();

            PersonalizedScore score = scorer.score(product, 80.0, context);
            PersonalizedEvidence evidence = generator.generate(product, score, context);

            assertEquals(0, evidence.getPositiveEvidence().size());
            assertEquals(1, evidence.getTradeOffs().size());

            EvidenceItem tradeOff = evidence.getTradeOffs().get(0);
            assertEquals(EvidenceType.EXCEEDS_BUDGET, tradeOff.getType());
            assertEquals("Price ($3000) exceeds your max budget of $1500", tradeOff.getDescription());
            assertFalse(tradeOff.isPositive());
        }

        @Test
        @DisplayName("Generates rating threshold evidence when candidate satisfies min rating")
        void testRatingThresholdEvidence() {
            UUID prodId = UUID.randomUUID();
            ProductResponseDTO product = createProduct(prodId, "Camera", "Canon", "Cameras", BigDecimal.valueOf(700), null);

            PersonalizationContext context = PersonalizationContext.builder(userId)
                    .minRating(4.0)
                    .build();

            PersonalizedScore score = scorer.score(product, 75.0, context);
            PersonalizedEvidence evidence = generator.generate(product, score, context);

            assertEquals(1, evidence.getPositiveEvidence().size());
            EvidenceItem item = evidence.getPositiveEvidence().get(0);
            assertEquals(EvidenceType.RATING_CRITERIA_MET, item.getType());
            assertTrue(item.getDescription().contains("meets your minimum threshold of 4.0★"));
        }

        @Test
        @DisplayName("Generates deal sensitivity match evidence when discount is 15% or higher")
        void testDealSensitivityEvidence() {
            UUID prodId = UUID.randomUUID();
            ProductResponseDTO product = createProduct(prodId, "Sneakers", "Nike", "Shoes", BigDecimal.valueOf(90), BigDecimal.valueOf(25));

            PersonalizationContext context = PersonalizationContext.builder(userId)
                    .dealSensitivity(DealSensitivity.HIGH)
                    .build();

            PersonalizedScore score = scorer.score(product, 70.0, context);
            PersonalizedEvidence evidence = generator.generate(product, score, context);

            assertEquals(1, evidence.getPositiveEvidence().size());
            EvidenceItem item = evidence.getPositiveEvidence().get(0);
            assertEquals(EvidenceType.DEAL_SENSITIVITY_MATCH, item.getType());
            assertTrue(item.getDescription().contains("25% discount aligns with your high deal sensitivity"));
        }

        @Test
        @DisplayName("Generates availability trade-off when candidate is out of stock under IN_STOCK_ONLY preference")
        void testAvailabilityMismatchTradeOff() {
            UUID prodId = UUID.randomUUID();
            ProductResponseDTO product = createProduct(prodId, "Rare Collectible", "RareBrand", "Collectibles", null, null);

            PersonalizationContext context = PersonalizationContext.builder(userId)
                    .availabilityPreference(AvailabilityPreference.IN_STOCK_ONLY)
                    .build();

            PersonalizedScore score = scorer.score(product, 70.0, context);
            PersonalizedEvidence evidence = generator.generate(product, score, context);

            assertEquals(1, evidence.getTradeOffs().size());
            EvidenceItem tradeOff = evidence.getTradeOffs().get(0);
            assertEquals(EvidenceType.LIMITED_AVAILABILITY, tradeOff.getType());
            assertEquals("Currently out of stock, conflicting with in-stock preference", tradeOff.getDescription());
        }
    }

    @Nested
    @DisplayName("Behavioral Signal Grounded Evidence")
    class BehavioralSignalEvidence {

        @Test
        @DisplayName("Generates behavioral category affinity evidence using interest wording")
        void testBehavioralCategoryAffinityEvidence() {
            UUID prodId = UUID.randomUUID();
            ProductResponseDTO product = createProduct(prodId, "Gaming Console", "Sony", "Gaming", BigDecimal.valueOf(499), null);

            PersonalizationContext context = PersonalizationContext.builder(userId)
                    .addCategoryAffinity("Gaming", 0.8)
                    .build();

            PersonalizedScore score = scorer.score(product, 75.0, context);
            PersonalizedEvidence evidence = generator.generate(product, score, context);

            assertEquals(1, evidence.getPositiveEvidence().size());
            EvidenceItem item = evidence.getPositiveEvidence().get(0);
            assertEquals(EvidenceType.BEHAVIORAL_AFFINITY, item.getType());
            assertEquals("Matches your recent interest in Gaming", item.getDescription());
            assertEquals("CategoryAffinity", item.getMetricName());
            assertEquals(0.8, item.getMetricValue());
        }

        @Test
        @DisplayName("Generates behavioral brand affinity evidence using frequent interest wording")
        void testBehavioralBrandAffinityEvidence() {
            UUID prodId = UUID.randomUUID();
            ProductResponseDTO product = createProduct(prodId, "Smartwatch", "Garmin", "Wearables", BigDecimal.valueOf(350), null);

            PersonalizationContext context = PersonalizationContext.builder(userId)
                    .addBrandAffinity("Garmin", 0.6)
                    .build();

            PersonalizedScore score = scorer.score(product, 75.0, context);
            PersonalizedEvidence evidence = generator.generate(product, score, context);

            assertEquals(1, evidence.getPositiveEvidence().size());
            EvidenceItem item = evidence.getPositiveEvidence().get(0);
            assertEquals(EvidenceType.BEHAVIORAL_AFFINITY, item.getType());
            assertEquals("Matches your frequent interest in Garmin", item.getDescription());
        }

        @Test
        @DisplayName("Generates interaction affinity evidence for recently interacted product")
        void testInteractionAffinityEvidence() {
            UUID prodId = UUID.randomUUID();
            ProductResponseDTO product = createProduct(prodId, "Mechanical Keyboard", "Keychron", "Keyboards", BigDecimal.valueOf(120), null);

            PersonalizationContext context = PersonalizationContext.builder(userId)
                    .addSignal(PersonalizationSignal.behavioral(
                            PersonalizationSignalType.INTERACTION_AFFINITY,
                            prodId.toString(),
                            prodId.toString(),
                            1.0
                    ))
                    .build();

            PersonalizedScore score = scorer.score(product, 75.0, context);
            PersonalizedEvidence evidence = generator.generate(product, score, context);

            assertEquals(1, evidence.getPositiveEvidence().size());
            EvidenceItem item = evidence.getPositiveEvidence().get(0);
            assertEquals(EvidenceType.BEHAVIORAL_AFFINITY, item.getType());
            assertEquals("Matches your recent interest in this product", item.getDescription());
        }
    }

    @Nested
    @DisplayName("Deterministic Ordering")
    class DeterministicEvidenceOrdering {

        @Test
        @DisplayName("Orders positive evidence deterministically by importance descending, then type, then description")
        void testDeterministicPositiveOrdering() {
            UUID prodId = UUID.randomUUID();
            ProductResponseDTO product = createProduct(prodId, "All-Match Laptop", "Apple", "Laptops", BigDecimal.valueOf(1000), BigDecimal.valueOf(20));

            PersonalizationContext context = PersonalizationContext.builder(userId)
                    .addPreferredCategory("Laptops") // importance 0.90
                    .addPreferredBrand("Apple")     // importance 0.85
                    .minBudget(BigDecimal.valueOf(500))
                    .maxBudget(BigDecimal.valueOf(1500)) // importance 0.80
                    .dealSensitivity(DealSensitivity.HIGH) // importance 0.75
                    .minRating(4.0) // importance 0.70
                    .addCategoryAffinity("Laptops", 0.9) // importance 0.65
                    .addBrandAffinity("Apple", 0.8) // importance 0.60
                    .build();

            PersonalizedScore score = scorer.score(product, 70.0, context);
            PersonalizedEvidence evidence = generator.generate(product, score, context);

            List<EvidenceItem> positive = evidence.getPositiveEvidence();
            assertTrue(positive.size() >= 5);

            for (int i = 0; i < positive.size() - 1; i++) {
                EvidenceItem curr = positive.get(i);
                EvidenceItem next = positive.get(i + 1);
                assertTrue(curr.getImportance() >= next.getImportance(),
                        String.format("Evidence item %s (%.2f) must be >= item %s (%.2f)",
                                curr.getType(), curr.getImportance(), next.getType(), next.getImportance()));
            }
        }
    }
}
