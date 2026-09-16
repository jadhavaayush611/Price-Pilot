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

@DisplayName("Phase 6.5 - Personalized Evidence Adversarial & Invariant Test Suite")
class PersonalizedEvidenceAdversarialTest {

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
    @DisplayName("Invariant 1: Evidence Explains Score & Never Fabricates")
    class ScoreEvidenceConsistency {

        @Test
        @DisplayName("Does not generate evidence for dimension not present in score breakdown")
        void testNoEvidenceForUnscoredDimension() {
            UUID prodId = UUID.randomUUID();
            ProductResponseDTO product = createProduct(prodId, "Laptop", "Apple", "Laptops", BigDecimal.valueOf(1000), null);

            // Context has preferred brand "Apple", but score breakdown only contains CategoryMatch
            PersonalizationContext context = PersonalizationContext.builder(userId)
                    .addPreferredCategory("Laptops")
                    .addPreferredBrand("Apple")
                    .build();

            // Artificially construct a PersonalizedScore that only contains PreferredCategoryMatch in breakdown
            PersonalizedScore partialScore = PersonalizedScore.builder()
                    .baseScore(70.0)
                    .personalizationAdjustment(12.0)
                    .finalScore(82.0)
                    .breakdown(Map.of("PreferredCategoryMatch", 12.0))
                    .build();

            PersonalizedEvidence evidence = generator.generate(product, partialScore, context);

            // Only category evidence should be generated, brand evidence must be omitted because score breakdown didn't include it
            assertEquals(1, evidence.getPositiveEvidence().size());
            assertEquals(EvidenceType.PREFERRED_CATEGORY, evidence.getPositiveEvidence().get(0).getType());
            assertTrue(evidence.getPositiveEvidence().stream()
                    .noneMatch(e -> e.getType() == EvidenceType.PREFERRED_BRAND));
        }

        @Test
        @DisplayName("Omits evidence if supporting product facts are missing despite score breakdown entry")
        void testUngroundedEvidenceOmission() {
            UUID prodId = UUID.randomUUID();
            // Product has null brand
            ProductResponseDTO product = createProduct(prodId, "Generic Device", null, "Electronics", BigDecimal.valueOf(100), null);

            PersonalizationContext context = PersonalizationContext.builder(userId)
                    .addPreferredBrand("Apple")
                    .build();

            // Mismatched score breakdown that contains brand match
            PersonalizedScore scoreWithMismatchedBrand = PersonalizedScore.builder()
                    .baseScore(70.0)
                    .personalizationAdjustment(10.0)
                    .finalScore(80.0)
                    .breakdown(Map.of("PreferredBrandMatch", 10.0))
                    .build();

            PersonalizedEvidence evidence = generator.generate(product, scoreWithMismatchedBrand, context);

            // Grounding check rejects the brand evidence because candidate brand is null!
            assertTrue(evidence.getPositiveEvidence().isEmpty(), "Evidence must be omitted when product facts are missing");
        }
    }

    @Nested
    @DisplayName("Invariant 2: Contradictory Explicit vs Behavioral Signals")
    class ContradictorySignals {

        @Test
        @DisplayName("Preserves distinct provenance and generates grounded evidence for applicable signal")
        void testContradictorySignalsProvenance() {
            UUID prodId = UUID.randomUUID();
            ProductResponseDTO product = createProduct(prodId, "Dell XPS 15", "Dell", "Laptops", BigDecimal.valueOf(1400), null);

            // User explicitly prefers Dell, but has behavioral affinity for Lenovo
            PersonalizationContext context = PersonalizationContext.builder(userId)
                    .addPreferredBrand("Dell")
                    .addBrandAffinity("Lenovo", 0.9)
                    .build();

            PersonalizedScore score = scorer.score(product, 75.0, context);
            PersonalizedEvidence evidence = generator.generate(product, score, context);

            assertEquals(1, evidence.getPositiveEvidence().size());
            EvidenceItem item = evidence.getPositiveEvidence().get(0);
            assertEquals(EvidenceType.PREFERRED_BRAND, item.getType());
            assertEquals("Matches your preferred brand: Dell", item.getDescription());
            assertTrue(evidence.getPositiveEvidence().stream()
                    .noneMatch(e -> e.getDescription().contains("Lenovo")));
        }
    }

    @Nested
    @DisplayName("Invariant 3: Immutability & Safety")
    class ImmutabilityAndSafety {

        @Test
        @DisplayName("PersonalizedEvidence collections cannot be mutated by caller")
        void testEvidenceImmutability() {
            UUID prodId = UUID.randomUUID();
            ProductResponseDTO product = createProduct(prodId, "Camera", "Sony", "Cameras", BigDecimal.valueOf(800), null);

            PersonalizationContext context = PersonalizationContext.builder(userId)
                    .addPreferredCategory("Cameras")
                    .build();

            PersonalizedScore score = scorer.score(product, 75.0, context);
            PersonalizedEvidence evidence = generator.generate(product, score, context);

            assertThrows(UnsupportedOperationException.class, () ->
                    evidence.getPositiveEvidence().add(new EvidenceItem()));

            assertThrows(UnsupportedOperationException.class, () ->
                    evidence.getTradeOffs().clear());
        }

        @Test
        @DisplayName("PersonalizedEvidence constructor sanitizes NaN and Infinity net adjustments")
        void testNetAdjustmentSanitization() {
            PersonalizedEvidence e1 = PersonalizedEvidence.builder()
                    .productId(UUID.randomUUID())
                    .netAdjustment(Double.NaN)
                    .build();
            assertEquals(0.0, e1.getNetAdjustment());

            PersonalizedEvidence e2 = PersonalizedEvidence.builder()
                    .productId(UUID.randomUUID())
                    .netAdjustment(Double.POSITIVE_INFINITY)
                    .build();
            assertEquals(0.0, e2.getNetAdjustment());
        }
    }

    @Nested
    @DisplayName("Invariant 4: Non-Mutation of Inputs")
    class NonMutationOfInputs {

        @Test
        @DisplayName("Evidence generation does not mutate product, context, or score objects")
        void testInputsAreNotMutated() {
            UUID prodId = UUID.randomUUID();
            ProductResponseDTO product = createProduct(prodId, "Original Name", "Original Brand", "Original Cat", BigDecimal.valueOf(500), BigDecimal.valueOf(10));
            String nameBefore = product.getName();
            String brandBefore = product.getBrand();

            PersonalizationContext context = PersonalizationContext.builder(userId)
                    .addPreferredCategory("Original Cat")
                    .build();
            int signalsCountBefore = context.signalCount();

            PersonalizedScore score = scorer.score(product, 75.0, context);
            double scoreBefore = score.getFinalScore();
            double adjBefore = score.getPersonalizationAdjustment();

            PersonalizedEvidence evidence = generator.generate(product, score, context);

            assertNotNull(evidence);
            assertEquals(nameBefore, product.getName());
            assertEquals(brandBefore, product.getBrand());
            assertEquals(signalsCountBefore, context.signalCount());
            assertEquals(scoreBefore, score.getFinalScore());
            assertEquals(adjBefore, score.getPersonalizationAdjustment());
        }
    }

    @Nested
    @DisplayName("Invariant 5: Privacy Boundary & No History Leakage")
    class PrivacyBoundary {

        @Test
        @DisplayName("Evidence descriptions contain no raw clickstream timestamps, IPs, or event UUIDs")
        void testNoPrivacyLeakageInEvidenceDescriptions() {
            UUID prodId = UUID.randomUUID();
            ProductResponseDTO product = createProduct(prodId, "Monitor", "LG", "Displays", BigDecimal.valueOf(300), null);

            PersonalizationContext context = PersonalizationContext.builder(userId)
                    .addSignal(PersonalizationSignal.behavioral(
                            PersonalizationSignalType.INTERACTION_AFFINITY,
                            prodId.toString(),
                            prodId.toString(),
                            1.0
                    ))
                    .addCategoryAffinity("Displays", 0.7)
                    .build();

            PersonalizedScore score = scorer.score(product, 75.0, context);
            PersonalizedEvidence evidence = generator.generate(product, score, context);

            for (EvidenceItem item : evidence.getPositiveEvidence()) {
                String desc = item.getDescription();
                assertFalse(desc.contains("timestamp"), "Description should not mention timestamps");
                assertFalse(desc.contains("click"), "Description should not mention raw clickstream");
                assertFalse(desc.contains("ip="), "Description should not expose IP addresses");
                assertFalse(desc.contains("session"), "Description should not expose session details");
            }
        }
    }
}
