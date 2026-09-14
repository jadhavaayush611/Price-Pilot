package com.pricepilot.intelligence.alternative;

import com.pricepilot.intelligence.alternative.model.*;
import com.pricepilot.intelligence.alternative.scoring.DefaultAlternativeScoringStrategy;
import com.pricepilot.intelligence.analytics.model.DealQuality;
import com.pricepilot.intelligence.discovery.normalization.QueryNormalizer;
import com.pricepilot.product.ProductEntity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class DefaultAlternativeScoringStrategyTest {

    private DefaultAlternativeScoringStrategy strategy;
    private QueryNormalizer queryNormalizer;

    @BeforeEach
    void setUp() {
        queryNormalizer = new QueryNormalizer();
        strategy = new DefaultAlternativeScoringStrategy(queryNormalizer);
    }

    @Test
    @DisplayName("Eligibility: Excludes source product from alternatives")
    void testExcludesSelf() {
        UUID productId = UUID.randomUUID();
        SourceProductContextDTO src = SourceProductContextDTO.builder()
                .id(productId)
                .currentBestPrice(BigDecimal.valueOf(100))
                .build();

        AlternativeCandidate candidate = AlternativeCandidate.builder()
                .product(ProductEntity.builder().name("Source Product").build())
                .currentBestPrice(BigDecimal.valueOf(80))
                .build();
        candidate.getProduct().setId(productId);

        assertThat(strategy.isEligible(candidate, src, AlternativeType.CHEAPER)).isFalse();
        assertThat(strategy.isEligible(candidate, src, AlternativeType.SIMILAR)).isFalse();
    }

    @Test
    @DisplayName("Eligibility: CHEAPER requires candidate price < source price")
    void testCheaperEligibility() {
        UUID srcId = UUID.randomUUID();
        UUID candId = UUID.randomUUID();

        SourceProductContextDTO src = SourceProductContextDTO.builder()
                .id(srcId)
                .currentBestPrice(BigDecimal.valueOf(100.00))
                .build();

        AlternativeCandidate cheapCand = AlternativeCandidate.builder()
                .product(ProductEntity.builder().name("Cheaper Product").build())
                .currentBestPrice(BigDecimal.valueOf(79.99))
                .build();
        cheapCand.getProduct().setId(candId);

        AlternativeCandidate expensiveCand = AlternativeCandidate.builder()
                .product(ProductEntity.builder().name("Expensive Product").build())
                .currentBestPrice(BigDecimal.valueOf(120.00))
                .build();
        expensiveCand.getProduct().setId(UUID.randomUUID());

        assertThat(strategy.isEligible(cheapCand, src, AlternativeType.CHEAPER)).isTrue();
        assertThat(strategy.isEligible(expensiveCand, src, AlternativeType.CHEAPER)).isFalse();
    }

    @Test
    @DisplayName("Eligibility: BUDGET_FALLBACK requires at least 20% savings")
    void testBudgetFallbackEligibility() {
        UUID srcId = UUID.randomUUID();
        SourceProductContextDTO src = SourceProductContextDTO.builder()
                .id(srcId)
                .currentBestPrice(BigDecimal.valueOf(100.00))
                .build();

        AlternativeCandidate validBudget = AlternativeCandidate.builder()
                .product(ProductEntity.builder().name("Budget Pick").build())
                .currentBestPrice(BigDecimal.valueOf(75.00)) // 25% savings
                .build();
        validBudget.getProduct().setId(UUID.randomUUID());

        AlternativeCandidate smallDiscount = AlternativeCandidate.builder()
                .product(ProductEntity.builder().name("Slightly Cheaper").build())
                .currentBestPrice(BigDecimal.valueOf(90.00)) // 10% savings
                .build();
        smallDiscount.getProduct().setId(UUID.randomUUID());

        assertThat(strategy.isEligible(validBudget, src, AlternativeType.BUDGET_FALLBACK)).isTrue();
        assertThat(strategy.isEligible(smallDiscount, src, AlternativeType.BUDGET_FALLBACK)).isFalse();
    }

    @Test
    @DisplayName("Scoring: CHEAPER computes price difference, evidence, reason codes, and explanation")
    void testCheaperScoringAndEvidence() {
        UUID srcId = UUID.randomUUID();
        SourceProductContextDTO src = SourceProductContextDTO.builder()
                .id(srcId)
                .name("Sony WH-1000XM5")
                .category("Headphones")
                .brand("Sony")
                .currentBestPrice(BigDecimal.valueOf(400.00))
                .rating(4.5)
                .build();

        UUID candId = UUID.randomUUID();
        AlternativeCandidate candidate = AlternativeCandidate.builder()
                .product(ProductEntity.builder()
                        .name("Soundcore Space Q45")
                        .category("Headphones")
                        .brand("Anker")
                        .build())
                .currentBestPrice(BigDecimal.valueOf(150.00))
                .semanticSimilarityScore(0.88)
                .build();
        candidate.getProduct().setId(candId);

        strategy.scoreCandidate(candidate, src, AlternativeType.CHEAPER);

        assertThat(candidate.getAlternativeScore()).isGreaterThan(50.0);
        assertThat(candidate.getPriceDifference()).isEqualByComparingTo(BigDecimal.valueOf(-250.00));
        assertThat(candidate.getPriceDifferencePercentage()).isLessThan(0.0);
        assertThat(candidate.getReasonCodes()).contains(
                AlternativeReasonCode.LOWER_PRICE,
                AlternativeReasonCode.HIGH_SEMANTIC_SIMILARITY,
                AlternativeReasonCode.SIMILAR_CATEGORY,
                AlternativeReasonCode.BUDGET_SAVING
        );
        assertThat(candidate.getEvidence()).isNotEmpty();
        assertThat(candidate.getPrimaryExplanation()).contains("Saves $250.00");
    }

    @Test
    @DisplayName("Scoring: BETTER_VALUE boosts candidates with EXCELLENT_DEAL and discount")
    void testBetterValueScoring() {
        UUID srcId = UUID.randomUUID();
        SourceProductContextDTO src = SourceProductContextDTO.builder()
                .id(srcId)
                .name("Baseline Laptop")
                .category("Laptops")
                .currentBestPrice(BigDecimal.valueOf(1000.00))
                .rating(4.2)
                .build();

        UUID candId = UUID.randomUUID();
        AlternativeCandidate candidate = AlternativeCandidate.builder()
                .product(ProductEntity.builder()
                        .name("Deal Laptop")
                        .category("Laptops")
                        .build())
                .currentBestPrice(BigDecimal.valueOf(850.00))
                .discountPercentage(BigDecimal.valueOf(25.0))
                .dealQuality(DealQuality.EXCELLENT_DEAL)
                .semanticSimilarityScore(0.85)
                .build();
        candidate.getProduct().setId(candId);

        strategy.scoreCandidate(candidate, src, AlternativeType.BETTER_VALUE);

        assertThat(candidate.getAlternativeScore()).isGreaterThan(60.0);
        assertThat(candidate.getReasonCodes()).contains(
                AlternativeReasonCode.BETTER_DEAL,
                AlternativeReasonCode.SIGNIFICANT_DISCOUNT,
                AlternativeReasonCode.LOWER_PRICE
        );
        assertThat(candidate.getBadges()).contains("Best Value");
    }

    @Test
    @DisplayName("Deterministic Comparator: Enforces strict tie-breaking order")
    void testDeterministicComparatorOrder() {
        UUID id1 = UUID.fromString("00000000-0000-0000-0000-000000000001");
        UUID id2 = UUID.fromString("00000000-0000-0000-0000-000000000002");

        AlternativeCandidate c1 = AlternativeCandidate.builder()
                .product(ProductEntity.builder().name("Prod 1").build())
                .alternativeScore(80.0)
                .semanticSimilarityScore(0.90)
                .dealQuality(DealQuality.EXCELLENT_DEAL)
                .currentBestPrice(BigDecimal.valueOf(100))
                .build();
        c1.getProduct().setId(id1);

        AlternativeCandidate c2 = AlternativeCandidate.builder()
                .product(ProductEntity.builder().name("Prod 2").build())
                .alternativeScore(80.0) // Equal alternativeScore
                .semanticSimilarityScore(0.95) // Higher similarity
                .dealQuality(DealQuality.GOOD_DEAL)
                .currentBestPrice(BigDecimal.valueOf(100))
                .build();
        c2.getProduct().setId(id2);

        List<AlternativeCandidate> list = new ArrayList<>(List.of(c1, c2));
        list.sort(strategy.getDeterministicComparator());

        // c2 should come first due to higher semantic similarity
        assertThat(list.get(0).getProductId()).isEqualTo(id2);
        assertThat(list.get(1).getProductId()).isEqualTo(id1);
    }
}
