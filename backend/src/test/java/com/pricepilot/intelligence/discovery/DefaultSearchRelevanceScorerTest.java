package com.pricepilot.intelligence.discovery;

import com.pricepilot.intelligence.analytics.model.DealQuality;
import com.pricepilot.intelligence.analytics.model.PurchaseSignal;
import com.pricepilot.intelligence.discovery.interpretation.InterpretedQuery;
import com.pricepilot.intelligence.discovery.normalization.QueryNormalizer;
import com.pricepilot.intelligence.discovery.ranking.DefaultSearchRelevanceScorer;
import com.pricepilot.intelligence.discovery.ranking.ScoredProductCandidate;
import com.pricepilot.product.ProductEntity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

public class DefaultSearchRelevanceScorerTest {

    private DefaultSearchRelevanceScorer scorer;

    @BeforeEach
    void setUp() {
        scorer = new DefaultSearchRelevanceScorer(new QueryNormalizer());
    }

    @Test
    @DisplayName("Exact name match receives +100 points")
    void testExactNameMatch() {
        ProductEntity p = ProductEntity.builder()
                .name("iPhone 15 Pro")
                .brand("Apple")
                .category("Smartphone")
                .build();
        p.setId(UUID.randomUUID());

        ScoredProductCandidate candidate = ScoredProductCandidate.builder()
                .product(p)
                .build();

        InterpretedQuery query = InterpretedQuery.builder()
                .cleanSearchTerms("iphone 15 pro")
                .normalizedQuery("iphone 15 pro")
                .searchTokens(List.of("iphone", "15", "pro"))
                .build();

        scorer.scoreCandidate(candidate, query);

        assertTrue(candidate.getRelevanceScore() >= 100.0, "Exact match score should be >= 100, got: " + candidate.getRelevanceScore());
        assertTrue(candidate.getRelevanceReasons().contains("Exact product-name match"));
    }

    @Test
    @DisplayName("Prefix match receives +60 points")
    void testPrefixMatch() {
        ProductEntity p = ProductEntity.builder()
                .name("MacBook Pro 14 M3 Max")
                .brand("Apple")
                .category("Laptop")
                .build();
        p.setId(UUID.randomUUID());

        ScoredProductCandidate candidate = ScoredProductCandidate.builder()
                .product(p)
                .build();

        InterpretedQuery query = InterpretedQuery.builder()
                .cleanSearchTerms("macbook pro")
                .normalizedQuery("macbook pro")
                .searchTokens(List.of("macbook", "pro"))
                .build();

        scorer.scoreCandidate(candidate, query);

        assertTrue(candidate.getRelevanceScore() >= 60.0);
        assertTrue(candidate.getRelevanceReasons().contains("Product name starts with query terms"));
    }

    @Test
    @DisplayName("Brand match receives +30 points")
    void testBrandMatch() {
        ProductEntity p = ProductEntity.builder()
                .name("Galaxy S24 Ultra")
                .brand("Samsung")
                .category("Smartphone")
                .build();
        p.setId(UUID.randomUUID());

        ScoredProductCandidate candidate = ScoredProductCandidate.builder()
                .product(p)
                .build();

        InterpretedQuery query = InterpretedQuery.builder()
                .detectedBrand("Samsung")
                .searchTokens(List.of("s24"))
                .build();

        scorer.scoreCandidate(candidate, query);

        assertTrue(candidate.getRelevanceScore() >= 30.0);
        assertTrue(candidate.getRelevanceReasons().stream().anyMatch(r -> r.contains("Samsung")));
    }

    @Test
    @DisplayName("Shopping intelligence boosts score and adds verified badges")
    void testShoppingIntelligenceBoosts() {
        ProductEntity p = ProductEntity.builder()
                .name("Sony WH-1000XM5")
                .brand("Sony")
                .category("Headphones")
                .build();
        p.setId(UUID.randomUUID());

        ScoredProductCandidate candidate = ScoredProductCandidate.builder()
                .product(p)
                .currentBestPrice(BigDecimal.valueOf(299.00))
                .dealQuality(DealQuality.EXCELLENT_DEAL)
                .isHistoricalLow(true)
                .build();

        InterpretedQuery query = InterpretedQuery.builder()
                .cleanSearchTerms("sony headphones")
                .searchTokens(List.of("sony", "headphones"))
                .build();

        scorer.scoreCandidate(candidate, query);

        assertTrue(candidate.getBadges().contains("Best Value"));
        assertTrue(candidate.getBadges().contains("Lowest Historical Price"));
        assertTrue(candidate.getBadges().contains("In Stock"));
    }

    @Test
    @DisplayName("Token match receives proportional +40 points")
    void testTokenMatch() {
        ProductEntity p = ProductEntity.builder()
                .name("Apple MacBook Pro 16 Inch M3 Max Space Black")
                .brand("Apple")
                .category("Laptop")
                .build();
        p.setId(UUID.randomUUID());

        ScoredProductCandidate candidate = ScoredProductCandidate.builder().product(p).build();

        InterpretedQuery query = InterpretedQuery.builder()
                .cleanSearchTerms("macbook m3 max")
                .searchTokens(List.of("macbook", "m3", "max"))
                .build();

        scorer.scoreCandidate(candidate, query);

        assertTrue(candidate.getRelevanceScore() >= 40.0, "All tokens matched product name, should receive >= 40 points");
        assertTrue(candidate.getRelevanceReasons().stream().anyMatch(r -> r.contains("Matches all query terms in product name")));
    }

    @Test
    @DisplayName("Category match receives +20 points")
    void testCategoryMatch() {
        ProductEntity p = ProductEntity.builder()
                .name("Generic Studio")
                .brand("Generic")
                .category("Headphones")
                .build();
        p.setId(UUID.randomUUID());

        ScoredProductCandidate candidate = ScoredProductCandidate.builder().product(p).build();

        InterpretedQuery query = InterpretedQuery.builder()
                .detectedCategory("Headphones")
                .searchTokens(List.of("studio"))
                .build();

        scorer.scoreCandidate(candidate, query);

        assertTrue(candidate.getRelevanceScore() >= 20.0);
        assertTrue(candidate.getRelevanceReasons().stream().anyMatch(r -> r.contains("Matches requested category")));
    }

    @Test
    @DisplayName("Description match receives +10 points")
    void testDescriptionMatch() {
        ProductEntity p = ProductEntity.builder()
                .name("Ultrabook Pro")
                .brand("Generic")
                .category("Computers")
                .description("Equipped with 32gb ram and oled display")
                .build();
        p.setId(UUID.randomUUID());

        ScoredProductCandidate candidate = ScoredProductCandidate.builder().product(p).build();

        InterpretedQuery query = InterpretedQuery.builder()
                .cleanSearchTerms("oled")
                .searchTokens(List.of("oled"))
                .build();

        scorer.scoreCandidate(candidate, query);

        assertTrue(candidate.getRelevanceScore() >= 10.0);
        assertTrue(candidate.getRelevanceReasons().stream().anyMatch(r -> r.contains("specifications/description")));
    }

    @Test
    @DisplayName("PurchaseSignal.BUY_NOW receives +10 points and Buy Now badge")
    void testBuyNowSignalBoost() {
        ProductEntity p = ProductEntity.builder()
                .name("Sony WH-1000XM5")
                .brand("Sony")
                .category("Headphones")
                .build();
        p.setId(UUID.randomUUID());

        ScoredProductCandidate candidate = ScoredProductCandidate.builder()
                .product(p)
                .currentBestPrice(BigDecimal.valueOf(299.00))
                .purchaseSignal(PurchaseSignal.BUY_NOW)
                .build();

        InterpretedQuery query = InterpretedQuery.builder()
                .cleanSearchTerms("sony")
                .searchTokens(List.of("sony"))
                .build();

        scorer.scoreCandidate(candidate, query);

        assertTrue(candidate.getBadges().contains("Buy Now"));
        assertTrue(candidate.getRelevanceReasons().stream().anyMatch(r -> r.contains("Strong buy recommendation")));
    }

    @Test
    @DisplayName("Deterministic tie-breaking comparator evaluates 5-level hierarchy")
    void testDeterministicTieBreaking() {
        Comparator<ScoredProductCandidate> comparator = scorer.getDeterministicComparator();

        UUID id1 = UUID.fromString("00000000-0000-0000-0000-000000000001");
        UUID id2 = UUID.fromString("00000000-0000-0000-0000-000000000002");

        ProductEntity p1 = ProductEntity.builder().name("Prod 1").build();
        p1.setId(id1);
        ProductEntity p2 = ProductEntity.builder().name("Prod 2").build();
        p2.setId(id2);

        // 1. Relevance score descending
        ScoredProductCandidate cHigh = ScoredProductCandidate.builder().product(p1).relevanceScore(80.0).build();
        ScoredProductCandidate cLow = ScoredProductCandidate.builder().product(p2).relevanceScore(50.0).build();
        assertTrue(comparator.compare(cHigh, cLow) < 0, "Higher relevance score should rank first");

        // 2. Deal quality tie-breaking
        ScoredProductCandidate cDealEx = ScoredProductCandidate.builder().product(p1).relevanceScore(70.0).dealQuality(DealQuality.EXCELLENT_DEAL).build();
        ScoredProductCandidate cDealGood = ScoredProductCandidate.builder().product(p2).relevanceScore(70.0).dealQuality(DealQuality.GOOD_DEAL).build();
        assertTrue(comparator.compare(cDealEx, cDealGood) < 0, "Better deal quality should break relevance ties");

        // 3. Product rating descending
        ScoredProductCandidate cRatingHigh = ScoredProductCandidate.builder().product(p1).relevanceScore(70.0).dealQuality(DealQuality.GOOD_DEAL).rating(4.8).build();
        ScoredProductCandidate cRatingLow = ScoredProductCandidate.builder().product(p2).relevanceScore(70.0).dealQuality(DealQuality.GOOD_DEAL).rating(4.2).build();
        assertTrue(comparator.compare(cRatingHigh, cRatingLow) < 0, "Higher rating should break ties when relevance and deal quality are equal");

        // 4. Current price tie-breaking
        ScoredProductCandidate cCheap = ScoredProductCandidate.builder().product(p1).relevanceScore(70.0).currentBestPrice(BigDecimal.valueOf(100.00)).build();
        ScoredProductCandidate cExpensive = ScoredProductCandidate.builder().product(p2).relevanceScore(70.0).currentBestPrice(BigDecimal.valueOf(200.00)).build();
        assertTrue(comparator.compare(cCheap, cExpensive) < 0, "Lower price should break ties");

        // 5. Lexicographical UUID tie-breaking
        ScoredProductCandidate cId1 = ScoredProductCandidate.builder().product(p1).relevanceScore(70.0).currentBestPrice(BigDecimal.valueOf(100.00)).build();
        ScoredProductCandidate cId2 = ScoredProductCandidate.builder().product(p2).relevanceScore(70.0).currentBestPrice(BigDecimal.valueOf(100.00)).build();
        assertTrue(comparator.compare(cId1, cId2) < 0, "Lower UUID should break final ties deterministically");
    }
}
