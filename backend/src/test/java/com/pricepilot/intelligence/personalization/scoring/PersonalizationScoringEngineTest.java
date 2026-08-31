package com.pricepilot.intelligence.personalization.scoring;

import com.pricepilot.intelligence.personalization.preference.AvailabilityPreference;
import com.pricepilot.intelligence.personalization.preference.DealSensitivity;
import com.pricepilot.intelligence.personalization.preference.PriceSensitivity;
import com.pricepilot.intelligence.personalization.preference.UserShoppingPreferenceEntity;
import com.pricepilot.intelligence.personalization.signals.UserShoppingSignals;
import com.pricepilot.intelligence.recommendation.dto.EvidenceType;
import com.pricepilot.intelligence.recommendation.dto.ProductScore;
import com.pricepilot.product.dto.ProductResponseDTO;
import com.pricepilot.productprice.dto.ProductPriceResponseDTO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

class PersonalizationScoringEngineTest {

    private DefaultPersonalizationScorer scorer;

    @BeforeEach
    void setUp() {
        scorer = new DefaultPersonalizationScorer();
    }

    @Test
    @DisplayName("Applies explicit category, brand, and budget bonuses deterministically")
    void testExplicitPreferenceAdjustments() {
        UUID prodId = UUID.randomUUID();
        ProductResponseDTO product = createProduct(prodId, "iPhone 15", "Apple", "Smartphones", BigDecimal.valueOf(800));

        ProductScore baseScore = new ProductScore(prodId, "iPhone 15", 80.0, 75.0, 85.0, 80.0, new HashMap<>(), "TOP_RATED");

        UserShoppingPreferenceEntity preferences = UserShoppingPreferenceEntity.builder()
                .preferredCategories(Set.of("Smartphones"))
                .preferredBrands(Set.of("Apple"))
                .minBudget(BigDecimal.valueOf(500))
                .maxBudget(BigDecimal.valueOf(1000))
                .minRating(4.0)
                .dealSensitivity(DealSensitivity.MEDIUM)
                .priceSensitivity(PriceSensitivity.MEDIUM)
                .availabilityPreference(AvailabilityPreference.ALL)
                .build();

        PersonalizationResult result = scorer.scorePersonalization(product, baseScore, preferences, null);

        assertNotNull(result);
        assertEquals(80.0, result.getBaseScore());

        // Category (+12) + Brand (+10) + Budget (+8) + Rating (+6) = +36 (bounded to 35.0 max contribution)
        assertTrue(result.getPersonalizationContribution() > 0);
        assertTrue(result.getFinalScore() > result.getBaseScore());

        // Verify explainability items
        boolean hasCategoryEvidence = result.getPersonalizationEvidence().stream()
                .anyMatch(e -> e.getType() == EvidenceType.PREFERRED_CATEGORY);
        boolean hasBrandEvidence = result.getPersonalizationEvidence().stream()
                .anyMatch(e -> e.getType() == EvidenceType.PREFERRED_BRAND);
        boolean hasBudgetEvidence = result.getPersonalizationEvidence().stream()
                .anyMatch(e -> e.getType() == EvidenceType.WITHIN_BUDGET);

        assertTrue(hasCategoryEvidence);
        assertTrue(hasBrandEvidence);
        assertTrue(hasBudgetEvidence);
    }

    @Test
    @DisplayName("Penalizes products exceeding user maximum budget")
    void testExceedsBudgetPenalty() {
        UUID prodId = UUID.randomUUID();
        ProductResponseDTO product = createProduct(prodId, "Expensive Laptop", "Razer", "Laptops", BigDecimal.valueOf(3500));

        ProductScore baseScore = new ProductScore(prodId, "Expensive Laptop", 85.0, 70.0, 95.0, 80.0, new HashMap<>(), "TOP_RATED");

        UserShoppingPreferenceEntity preferences = UserShoppingPreferenceEntity.builder()
                .maxBudget(BigDecimal.valueOf(2000))
                .build();

        PersonalizationResult result = scorer.scorePersonalization(product, baseScore, preferences, null);

        assertTrue(result.getPersonalizationContribution() < 0);
        assertEquals(-10.0, result.getPersonalizationContribution());

        boolean hasExceedsTradeOff = result.getPersonalizationTradeOffs().stream()
                .anyMatch(e -> e.getType() == EvidenceType.EXCEEDS_BUDGET);
        assertTrue(hasExceedsTradeOff);
    }

    @Test
    @DisplayName("Penalizes out-of-stock products when user prefers IN_STOCK_ONLY")
    void testOutOfStockPenalty() {
        UUID prodId = UUID.randomUUID();
        ProductResponseDTO product = ProductResponseDTO.builder()
                .id(prodId)
                .name("Sold Out GPU")
                .brand("Nvidia")
                .category("Graphics Cards")
                .prices(Collections.emptyList()) // No prices / out of stock
                .build();

        ProductScore baseScore = new ProductScore(prodId, "Sold Out GPU", 85.0, 70.0, 95.0, 80.0, new HashMap<>(), "TOP_RATED");

        UserShoppingPreferenceEntity preferences = UserShoppingPreferenceEntity.builder()
                .availabilityPreference(AvailabilityPreference.IN_STOCK_ONLY)
                .build();

        PersonalizationResult result = scorer.scorePersonalization(product, baseScore, preferences, null);

        assertTrue(result.getPersonalizationContribution() <= -20.0);
        boolean hasAvailabilityTradeOff = result.getPersonalizationTradeOffs().stream()
                .anyMatch(e -> e.getType() == EvidenceType.LIMITED_AVAILABILITY);
        assertTrue(hasAvailabilityTradeOff);
    }

    @Test
    @DisplayName("Integrates behavioral signals affinity into score without explicit preferences")
    void testBehavioralAffinityScoring() {
        UUID prodId = UUID.randomUUID();
        ProductResponseDTO product = createProduct(prodId, "Pixel 8", "Google", "Smartphones", BigDecimal.valueOf(699));

        ProductScore baseScore = new ProductScore(prodId, "Pixel 8", 82.0, 80.0, 85.0, 80.0, new HashMap<>(), "RECOMMENDED");

        UserShoppingSignals signals = UserShoppingSignals.builder()
                .categoryAffinity(Map.of("smartphones", 1.0))
                .brandAffinity(Map.of("google", 0.8))
                .build();

        PersonalizationResult result = scorer.scorePersonalization(product, baseScore, null, signals);

        assertTrue(result.getPersonalizationContribution() > 0);
        assertEquals(82.0, result.getBaseScore());
        assertEquals(result.getBaseScore() + result.getPersonalizationContribution(), result.getFinalScore(), 0.01);

        boolean hasBehavioralEvidence = result.getPersonalizationEvidence().stream()
                .anyMatch(e -> e.getType() == EvidenceType.BEHAVIORAL_AFFINITY);
        assertTrue(hasBehavioralEvidence);
    }

    @Test
    @DisplayName("Ranks deterministically using 5-tier tie-breaking")
    void testDeterministicTieBreaking() {
        UUID id1 = UUID.fromString("00000000-0000-0000-0000-000000000001");
        UUID id2 = UUID.fromString("00000000-0000-0000-0000-000000000002");

        ProductResponseDTO p1 = createProduct(id1, "Product A", "BrandX", "CategoryY", BigDecimal.valueOf(100));
        ProductResponseDTO p2 = createProduct(id2, "Product B", "BrandX", "CategoryY", BigDecimal.valueOf(100));

        // Identical final score, base score, personalization contribution, and price
        PersonalizationResult r1 = PersonalizationResult.builder().baseScore(80.0).personalizationContribution(5.0).finalScore(85.0).build();
        PersonalizationResult r2 = PersonalizationResult.builder().baseScore(80.0).personalizationContribution(5.0).finalScore(85.0).build();

        Map<UUID, PersonalizationResult> results = Map.of(id1, r1, id2, r2);
        Comparator<ProductResponseDTO> comparator = scorer.getDeterministicPersonalizedComparator(results);

        List<ProductResponseDTO> list = new ArrayList<>(List.of(p2, p1));
        list.sort(comparator);

        // id1 is lexicographically smaller than id2, so id1 must consistently win the tie!
        assertEquals(id1, list.get(0).getId());
        assertEquals(id2, list.get(1).getId());
    }

    private ProductResponseDTO createProduct(UUID id, String name, String brand, String category, BigDecimal price) {
        ProductPriceResponseDTO priceDTO = ProductPriceResponseDTO.builder()
                .currentPrice(price)
                .discountPercentage(BigDecimal.valueOf(10))
                .build();

        return ProductResponseDTO.builder()
                .id(id)
                .name(name)
                .brand(brand)
                .category(category)
                .prices(List.of(priceDTO))
                .build();
    }
}
