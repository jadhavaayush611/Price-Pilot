package com.pricepilot.intelligence.dashboard;

import com.pricepilot.analytics.dto.ProductAnalyticsResponseDTO;
import com.pricepilot.intelligence.analytics.model.DealQuality;
import com.pricepilot.intelligence.analytics.model.PriceTrend;
import com.pricepilot.intelligence.analytics.model.PurchaseSignal;
import com.pricepilot.intelligence.dashboard.dto.AttentionItemDTO;
import com.pricepilot.intelligence.dashboard.ranking.AttentionRankingStrategy;
import com.pricepilot.product.ProductEntity;
import com.pricepilot.watchlist.PriceWatchlistEntity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class AttentionRankingStrategyTest {

    private AttentionRankingStrategy rankingStrategy;
    private ProductEntity productA;
    private ProductEntity productB;
    private PriceWatchlistEntity watchlistA;
    private PriceWatchlistEntity watchlistB;

    @BeforeEach
    void setUp() {
        rankingStrategy = new AttentionRankingStrategy();

        productA = ProductEntity.builder()
                .name("MacBook Pro M3 Max")
                .brand("Apple")
                .imageUrl("https://example.com/macbook.png")
                .build();
        productA.setId(UUID.randomUUID());

        productB = ProductEntity.builder()
                .name("Sony WH-1000XM5")
                .brand("Sony")
                .imageUrl("https://example.com/sony.png")
                .build();
        productB.setId(UUID.randomUUID());

        watchlistA = PriceWatchlistEntity.builder()
                .product(productA)
                .currentBestPrice(BigDecimal.valueOf(2400.00))
                .targetPrice(BigDecimal.valueOf(2500.00))
                .active(true)
                .build();

        watchlistB = PriceWatchlistEntity.builder()
                .product(productB)
                .currentBestPrice(BigDecimal.valueOf(350.00))
                .targetPrice(BigDecimal.valueOf(300.00))
                .active(true)
                .build();
    }

    @Test
    @DisplayName("Target price met awards 100 points and marks critical urgency")
    void testTargetPriceMetScoring() {
        ProductAnalyticsResponseDTO analytics = ProductAnalyticsResponseDTO.builder()
                .historicalMin(BigDecimal.valueOf(2300.00))
                .dealQuality(DealQuality.EXCELLENT_DEAL)
                .purchaseSignal(PurchaseSignal.BUY_NOW)
                .build();

        AttentionItemDTO item = rankingStrategy.evaluateAttentionItem(watchlistA, analytics, true);

        assertNotNull(item);
        // Target Met (100) + Excellent Deal (70) + Buy Now (60) + Unread Alert (25) = 255
        assertEquals(255, item.getUrgencyScore());
        assertEquals("CRITICAL", item.getUrgencyLevel());
        assertTrue(item.getPrimaryReason().contains("Target price reached"));
    }

    @Test
    @DisplayName("Historical low awards 90 points")
    void testHistoricalLowScoring() {
        // Current price 350 <= historical min 350
        ProductAnalyticsResponseDTO analytics = ProductAnalyticsResponseDTO.builder()
                .historicalMin(BigDecimal.valueOf(350.00))
                .dealQuality(DealQuality.GOOD_DEAL)
                .trend(PriceTrend.FALLING)
                .trendPercentage(-5.0)
                .build();

        AttentionItemDTO item = rankingStrategy.evaluateAttentionItem(watchlistB, analytics, false);

        assertNotNull(item);
        // Historical Low (90) + Good Deal (50) + Falling Trend (30) = 170
        assertEquals(170, item.getUrgencyScore());
        assertEquals("CRITICAL", item.getUrgencyLevel());
        assertTrue(item.getPrimaryReason().contains("all-time historical low"));
    }

    @Test
    @DisplayName("Item with score below 40 threshold is filtered out of attention feed")
    void testSubThresholdFilteredOut() {
        // Price above target (350 > 300), no historical low, fair price, no falling trend, no unread alert
        ProductAnalyticsResponseDTO analytics = ProductAnalyticsResponseDTO.builder()
                .historicalMin(BigDecimal.valueOf(280.00))
                .dealQuality(DealQuality.FAIR_PRICE)
                .trend(PriceTrend.STABLE)
                .purchaseSignal(PurchaseSignal.WAIT)
                .build();

        AttentionItemDTO item = rankingStrategy.evaluateAttentionItem(watchlistB, analytics, false);

        assertNull(item);
    }

    @Test
    @DisplayName("Individual signals: Target Met alone awards 100 points")
    void testTargetMetAlone() {
        AttentionItemDTO item = rankingStrategy.evaluateAttentionItem(watchlistA, null, false);
        assertNotNull(item);
        assertEquals(100, item.getUrgencyScore());
        assertEquals("HIGH", item.getUrgencyLevel());
    }

    @Test
    @DisplayName("Individual signals: Historical Low alone awards 90 points")
    void testHistoricalLowAlone() {
        ProductAnalyticsResponseDTO analytics = ProductAnalyticsResponseDTO.builder()
                .historicalMin(BigDecimal.valueOf(350.00))
                .build();
        AttentionItemDTO item = rankingStrategy.evaluateAttentionItem(watchlistB, analytics, false);
        assertNotNull(item);
        assertEquals(90, item.getUrgencyScore());
        assertEquals("HIGH", item.getUrgencyLevel());
    }

    @Test
    @DisplayName("Individual signals: Excellent Deal alone awards 70 points")
    void testExcellentDealAlone() {
        ProductAnalyticsResponseDTO analytics = ProductAnalyticsResponseDTO.builder()
                .dealQuality(DealQuality.EXCELLENT_DEAL)
                .build();
        AttentionItemDTO item = rankingStrategy.evaluateAttentionItem(watchlistB, analytics, false);
        assertNotNull(item);
        assertEquals(70, item.getUrgencyScore());
        assertEquals("MEDIUM", item.getUrgencyLevel());
    }

    @Test
    @DisplayName("Individual signals: Good Deal alone awards 50 points")
    void testGoodDealAlone() {
        ProductAnalyticsResponseDTO analytics = ProductAnalyticsResponseDTO.builder()
                .dealQuality(DealQuality.GOOD_DEAL)
                .build();
        AttentionItemDTO item = rankingStrategy.evaluateAttentionItem(watchlistB, analytics, false);
        assertNotNull(item);
        assertEquals(50, item.getUrgencyScore());
        assertEquals("MEDIUM", item.getUrgencyLevel());
    }

    @Test
    @DisplayName("Individual signals: Buy Now alone awards 60 points")
    void testBuyNowAlone() {
        ProductAnalyticsResponseDTO analytics = ProductAnalyticsResponseDTO.builder()
                .purchaseSignal(PurchaseSignal.BUY_NOW)
                .build();
        AttentionItemDTO item = rankingStrategy.evaluateAttentionItem(watchlistB, analytics, false);
        assertNotNull(item);
        assertEquals(60, item.getUrgencyScore());
        assertEquals("MEDIUM", item.getUrgencyLevel());
    }

    @Test
    @DisplayName("Individual signals: Good Time alone awards 40 points (exact threshold)")
    void testGoodTimeAlone() {
        ProductAnalyticsResponseDTO analytics = ProductAnalyticsResponseDTO.builder()
                .purchaseSignal(PurchaseSignal.GOOD_TIME)
                .build();
        AttentionItemDTO item = rankingStrategy.evaluateAttentionItem(watchlistB, analytics, false);
        assertNotNull(item);
        assertEquals(40, item.getUrgencyScore());
        assertEquals("MEDIUM", item.getUrgencyLevel());
    }

    @Test
    @DisplayName("Individual signals: Falling trend (30) + unread alert (25) = 55 points exceeds threshold")
    void testFallingTrendAndUnreadAlertCombined() {
        ProductAnalyticsResponseDTO analytics = ProductAnalyticsResponseDTO.builder()
                .trend(PriceTrend.FALLING)
                .trendPercentage(-4.2)
                .build();
        // Falling trend alone (+30) is below 40 -> returns null
        assertNull(rankingStrategy.evaluateAttentionItem(watchlistB, analytics, false));

        // Unread alert alone (+25) is below 40 -> returns null
        assertNull(rankingStrategy.evaluateAttentionItem(watchlistB, null, true));

        // Combined: 30 + 25 = 55 -> returns attention item
        AttentionItemDTO item = rankingStrategy.evaluateAttentionItem(watchlistB, analytics, true);
        assertNotNull(item);
        assertEquals(55, item.getUrgencyScore());
        assertEquals("MEDIUM", item.getUrgencyLevel());
    }

    @Test
    @DisplayName("Deterministic tie-breaking: score descending -> price ascending -> productId lexicographical")
    void testDeterministicTieBreakingFullHierarchy() {
        UUID id1 = UUID.fromString("00000000-0000-0000-0000-000000000001");
        UUID id2 = UUID.fromString("00000000-0000-0000-0000-000000000002");
        UUID id3 = UUID.fromString("00000000-0000-0000-0000-000000000003");
        UUID id4 = UUID.fromString("00000000-0000-0000-0000-000000000004");

        // item1: score 100, price $500, id 0001
        AttentionItemDTO item1 = AttentionItemDTO.builder().productId(id1).urgencyScore(100).currentPrice(BigDecimal.valueOf(500.00)).build();
        // item2: score 150 (highest score, should win regardless of price/id)
        AttentionItemDTO item2 = AttentionItemDTO.builder().productId(id2).urgencyScore(150).currentPrice(BigDecimal.valueOf(900.00)).build();
        // item3: score 100, price $300 (same score as item1, but lower price, should come before item1)
        AttentionItemDTO item3 = AttentionItemDTO.builder().productId(id3).urgencyScore(100).currentPrice(BigDecimal.valueOf(300.00)).build();
        // item4: score 100, price $500 (same score and same price as item1, id4 > id1 so item1 comes before item4)
        AttentionItemDTO item4 = AttentionItemDTO.builder().productId(id4).urgencyScore(100).currentPrice(BigDecimal.valueOf(500.00)).build();

        List<AttentionItemDTO> list = new ArrayList<>(List.of(item1, item2, item3, item4));
        list.sort(rankingStrategy.comparator());

        // 1st: item2 (score 150)
        assertEquals(id2, list.get(0).getProductId());
        // 2nd: item3 (score 100, price $300)
        assertEquals(id3, list.get(1).getProductId());
        // 3rd: item1 (score 100, price $500, id ...0001)
        assertEquals(id1, list.get(2).getProductId());
        // 4th: item4 (score 100, price $500, id ...0004)
        assertEquals(id4, list.get(3).getProductId());
    }

    @Test
    @DisplayName("Architectural verification: confirm ZERO LLM is used in AttentionRankingStrategy")
    void testZeroLlmInRankingStrategy() {
        // Assert that AttentionRankingStrategy has no fields of AI/LLM clients, HTTP clients, or external services
        java.lang.reflect.Field[] fields = AttentionRankingStrategy.class.getDeclaredFields();
        for (java.lang.reflect.Field field : fields) {
            String typeName = field.getType().getName().toLowerCase();
            assertFalse(typeName.contains("ai"), "Ranking strategy must not depend on AI services: " + field.getName());
            assertFalse(typeName.contains("client"), "Ranking strategy must not depend on network clients: " + field.getName());
            assertFalse(typeName.contains("rest"), "Ranking strategy must not depend on REST templates: " + field.getName());
            assertFalse(typeName.contains("llm"), "Ranking strategy must not depend on LLMs: " + field.getName());
        }
        // Assert deterministic evaluation produces identical outputs for identical inputs across repeated invocations
        AttentionItemDTO res1 = rankingStrategy.evaluateAttentionItem(watchlistA, null, false);
        AttentionItemDTO res2 = rankingStrategy.evaluateAttentionItem(watchlistA, null, false);
        assertNotNull(res1);
        assertNotNull(res2);
        assertEquals(res1.getUrgencyScore(), res2.getUrgencyScore());
        assertEquals(res1.getUrgencyLevel(), res2.getUrgencyLevel());
        assertEquals(res1.getPrimaryReason(), res2.getPrimaryReason());
    }
}
