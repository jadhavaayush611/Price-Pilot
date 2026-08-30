package com.pricepilot.intelligence.dashboard;

import com.pricepilot.analytics.dto.ProductAnalyticsResponseDTO;
import com.pricepilot.intelligence.alert.entity.PriceAlertEntity;
import com.pricepilot.intelligence.alert.model.AlertType;
import com.pricepilot.intelligence.alert.repository.PriceAlertRepository;
import com.pricepilot.intelligence.analytics.PriceAnalyticsService;
import com.pricepilot.intelligence.analytics.model.DealQuality;
import com.pricepilot.intelligence.analytics.model.PurchaseSignal;
import com.pricepilot.intelligence.comparison.repository.SavedComparisonRepository;
import com.pricepilot.intelligence.dashboard.dto.DashboardV2ResponseDTO;
import com.pricepilot.intelligence.dashboard.ranking.AttentionRankingStrategy;
import com.pricepilot.intelligence.dashboard.service.DashboardV2ServiceImpl;
import com.pricepilot.intelligence.recommendation.RecommendationService;
import com.pricepilot.intelligence.recommendation.dto.RecommendationResponse;
import com.pricepilot.product.ProductEntity;
import com.pricepilot.product.dto.ProductResponseDTO;
import com.pricepilot.savedproduct.SavedProductRepository;
import com.pricepilot.user.UserEntity;
import com.pricepilot.watchlist.PriceWatchlistEntity;
import com.pricepilot.watchlist.PriceWatchlistRepository;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DashboardV2ServiceImplTest {

    @Mock
    private PriceWatchlistRepository watchlistRepository;

    @Mock
    private PriceAnalyticsService priceAnalyticsService;

    @Mock
    private PriceAlertRepository alertRepository;

    @Mock
    private RecommendationService recommendationService;

    @Mock
    private SavedComparisonRepository savedComparisonRepository;

    @Mock
    private SavedProductRepository savedProductRepository;

    private AttentionRankingStrategy rankingStrategy;
    private SimpleMeterRegistry meterRegistry;
    private DashboardV2ServiceImpl dashboardService;

    private UUID userId;
    private String userEmail;
    private ProductEntity product;
    private PriceWatchlistEntity watchlist;

    @BeforeEach
    void setUp() {
        rankingStrategy = new AttentionRankingStrategy();
        meterRegistry = new SimpleMeterRegistry();
        dashboardService = new DashboardV2ServiceImpl(
                watchlistRepository,
                priceAnalyticsService,
                alertRepository,
                recommendationService,
                savedComparisonRepository,
                savedProductRepository,
                rankingStrategy,
                meterRegistry
        );

        userId = UUID.randomUUID();
        userEmail = "shopper@pricepilot.io";

        product = ProductEntity.builder()
                .name("Dell XPS 15")
                .brand("Dell")
                .category("Laptops")
                .imageUrl("https://example.com/dell.png")
                .build();
        product.setId(UUID.randomUUID());

        watchlist = PriceWatchlistEntity.builder()
                .product(product)
                .currentBestPrice(BigDecimal.valueOf(1400.00))
                .targetPrice(BigDecimal.valueOf(1500.00))
                .active(true)
                .build();
        watchlist.setId(UUID.randomUUID());
    }

    @Test
    @DisplayName("Aggregates all shopping intelligence sections successfully")
    void testGetDashboardV2AggregationSuccess() {
        when(watchlistRepository.findAllByUserIdWithProduct(userId)).thenReturn(List.of(watchlist));
        when(alertRepository.countUnreadByUserId(userId)).thenReturn(2L);

        PriceAlertEntity alert = PriceAlertEntity.builder()
                .user(new UserEntity())
                .product(product)
                .alertType(AlertType.PRICE_DROP)
                .title("Drop")
                .message("Price dropped")
                .deduplicationKey("KEY-1")
                .build();
        alert.setId(UUID.randomUUID());
        alert.setCreatedAt(LocalDateTime.now());
        alert.setUpdatedAt(LocalDateTime.now());

        when(alertRepository.findAllByUserIdWithProduct(eq(userId), any(PageRequest.class)))
                .thenReturn(new PageImpl<>(List.of(alert)));

        ProductAnalyticsResponseDTO analytics = ProductAnalyticsResponseDTO.builder()
                .historicalMin(BigDecimal.valueOf(1350.00))
                .historicalAvg(BigDecimal.valueOf(1600.00))
                .dealQuality(DealQuality.GOOD_DEAL)
                .purchaseSignal(PurchaseSignal.BUY_NOW)
                .supportingEvidence(List.of("12.5% below average"))
                .build();
        when(priceAnalyticsService.getProductAnalytics(product.getId())).thenReturn(analytics);

        when(savedComparisonRepository.countByUserId(userId)).thenReturn(3L);
        when(savedProductRepository.countByUserId(userId)).thenReturn(5L);

        ProductResponseDTO recProd = ProductResponseDTO.builder()
                .id(UUID.randomUUID())
                .name("ThinkPad X1")
                .brand("Lenovo")
                .build();
        RecommendationResponse recResponse = new RecommendationResponse();
        recResponse.setRecommendedProducts(List.of(recProd));
        recResponse.setRecommendationType("BEST_VALUE");
        recResponse.setConfidence(0.92);
        recResponse.setExplanation("Top price-performance ratio");
        recResponse.setStrategyUsed("HYBRID_SCORE");
        recResponse.setGeneratedAt(LocalDateTime.now());
        when(recommendationService.getPersonalizedRecommendations(userId, 4)).thenReturn(recResponse);

        DashboardV2ResponseDTO result = dashboardService.getDashboardV2(userId, userEmail);

        assertNotNull(result);
        assertEquals(1, result.getOverview().getActiveWatchlistsCount());
        assertEquals(2L, result.getOverview().getUnreadAlertsCount());
        assertEquals(1, result.getOverview().getGoodOrExcellentDealCount());
        assertEquals(3L, result.getOverview().getSavedComparisonsCount());
        assertEquals(5L, result.getOverview().getSavedProductsCount());

        assertFalse(result.getWatchedProducts().isEmpty());
        assertEquals("Dell XPS 15", result.getWatchedProducts().get(0).getProductName());

        assertFalse(result.getAttentionItems().isEmpty());
        assertEquals("CRITICAL", result.getAttentionItems().get(0).getUrgencyLevel());

        assertFalse(result.getPriceOpportunities().isEmpty());
        assertFalse(result.getRecentAlerts().isEmpty());

        assertNotNull(result.getRecommendations());
        assertTrue(result.getRecommendations().isAvailable());
        assertEquals(1, result.getRecommendations().getItems().size());
    }

    @Test
    @DisplayName("Empty state handled cleanly with zero counts and empty lists")
    void testEmptyDashboardHandled() {
        when(watchlistRepository.findAllByUserIdWithProduct(userId)).thenReturn(Collections.emptyList());
        when(alertRepository.countUnreadByUserId(userId)).thenReturn(0L);
        when(alertRepository.findAllByUserIdWithProduct(eq(userId), any(PageRequest.class)))
                .thenReturn(new PageImpl<>(Collections.emptyList()));
        when(savedComparisonRepository.countByUserId(userId)).thenReturn(0L);
        when(savedProductRepository.countByUserId(userId)).thenReturn(0L);
        when(recommendationService.getPersonalizedRecommendations(userId, 4)).thenReturn(null);

        DashboardV2ResponseDTO result = dashboardService.getDashboardV2(userId, userEmail);

        assertNotNull(result);
        assertEquals(0, result.getOverview().getActiveWatchlistsCount());
        assertEquals(0L, result.getOverview().getUnreadAlertsCount());
        assertEquals(0, result.getOverview().getHistoricalLowCount());
        assertEquals(0, result.getOverview().getGoodOrExcellentDealCount());
        assertTrue(result.getWatchedProducts().isEmpty());
        assertTrue(result.getAttentionItems().isEmpty());
        assertTrue(result.getPriceOpportunities().isEmpty());
        assertTrue(result.getRecentAlerts().isEmpty());

        // Empty metric incremented
        assertEquals(1.0, meterRegistry.get("pricepilot.dashboard.empty").counter().count());
    }

    @Test
    @DisplayName("Failure isolation: if recommendation service fails, dashboard still renders other sections")
    void testRecommendationFailureDegradation() {
        when(watchlistRepository.findAllByUserIdWithProduct(userId)).thenReturn(List.of(watchlist));
        when(alertRepository.countUnreadByUserId(userId)).thenReturn(0L);
        when(alertRepository.findAllByUserIdWithProduct(eq(userId), any(PageRequest.class)))
                .thenReturn(new PageImpl<>(Collections.emptyList()));

        when(recommendationService.getPersonalizedRecommendations(userId, 4))
                .thenThrow(new RuntimeException("Recommendation AI microservice unavailable"));

        DashboardV2ResponseDTO result = dashboardService.getDashboardV2(userId, userEmail);

        assertNotNull(result);
        assertEquals(1, result.getOverview().getActiveWatchlistsCount());
        assertFalse(result.getWatchedProducts().isEmpty());
        assertNotNull(result.getRecommendations());
        assertFalse(result.getRecommendations().isAvailable());
        assertEquals("UNAVAILABLE", result.getRecommendations().getStrategyUsed());
    }

    @Test
    @DisplayName("Failure isolation: if analytics fail for a product, basic watched card is still preserved")
    void testAnalyticsFailureDegradation() {
        when(watchlistRepository.findAllByUserIdWithProduct(userId)).thenReturn(List.of(watchlist));
        when(alertRepository.countUnreadByUserId(userId)).thenReturn(0L);
        when(alertRepository.findAllByUserIdWithProduct(eq(userId), any(PageRequest.class)))
                .thenReturn(new PageImpl<>(Collections.emptyList()));

        when(priceAnalyticsService.getProductAnalytics(product.getId()))
                .thenThrow(new RuntimeException("Analytics calculation timed out"));

        DashboardV2ResponseDTO result = dashboardService.getDashboardV2(userId, userEmail);

        assertNotNull(result);
        assertEquals(1, result.getWatchedProducts().size());
        assertEquals("Dell XPS 15", result.getWatchedProducts().get(0).getProductName());
        assertNull(result.getWatchedProducts().get(0).getDealQuality());
    }

    @Test
    @DisplayName("Bounded limits enforced: watchlists capped to 15, attention capped to 6")
    void testBoundedLimitsEnforced() {
        List<PriceWatchlistEntity> manyWatchlists = new ArrayList<>();
        for (int i = 0; i < 25; i++) {
            ProductEntity p = ProductEntity.builder()
                    .name("Product " + i)
                    .brand("Brand")
                    .build();
            p.setId(UUID.randomUUID());

            PriceWatchlistEntity w = PriceWatchlistEntity.builder()
                    .product(p)
                    .currentBestPrice(BigDecimal.valueOf(100.00))
                    .targetPrice(BigDecimal.valueOf(120.00)) // target met (+100)
                    .active(true)
                    .build();
            w.setId(UUID.randomUUID());
            manyWatchlists.add(w);
        }

        when(watchlistRepository.findAllByUserIdWithProduct(userId)).thenReturn(manyWatchlists);
        when(alertRepository.countUnreadByUserId(userId)).thenReturn(0L);
        when(alertRepository.findAllByUserIdWithProduct(eq(userId), any(PageRequest.class)))
                .thenReturn(new PageImpl<>(Collections.emptyList()));

        DashboardV2ResponseDTO result = dashboardService.getDashboardV2(userId, userEmail);

        // Active watchlists capped to MAX_WATCHLISTS = 15
        assertEquals(15, result.getWatchedProducts().size());
        // Attention items capped to MAX_ATTENTION_ITEMS = 6
        assertEquals(6, result.getAttentionItems().size());
    }

    @Test
    @DisplayName("Failure isolation: if alert service fails, unread count defaults to 0 and recent alerts are empty")
    void testAlertServiceFailureDegradation() {
        when(watchlistRepository.findAllByUserIdWithProduct(userId)).thenReturn(List.of(watchlist));
        when(alertRepository.countUnreadByUserId(userId)).thenThrow(new RuntimeException("Alert database timeout"));

        DashboardV2ResponseDTO result = dashboardService.getDashboardV2(userId, userEmail);

        assertNotNull(result);
        assertEquals(1, result.getOverview().getActiveWatchlistsCount());
        assertEquals(0L, result.getOverview().getUnreadAlertsCount());
        assertTrue(result.getRecentAlerts().isEmpty());
    }

    @Test
    @DisplayName("Strict user isolation: User A calling dashboard queries strictly User A data, never User B")
    void testStrictUserIsolation() {
        UUID userBId = UUID.randomUUID();

        when(watchlistRepository.findAllByUserIdWithProduct(userId)).thenReturn(List.of(watchlist));
        when(alertRepository.countUnreadByUserId(userId)).thenReturn(1L);
        when(alertRepository.findAllByUserIdWithProduct(eq(userId), any(PageRequest.class)))
                .thenReturn(new PageImpl<>(Collections.emptyList()));
        when(savedComparisonRepository.countByUserId(userId)).thenReturn(2L);
        when(savedProductRepository.countByUserId(userId)).thenReturn(4L);

        DashboardV2ResponseDTO result = dashboardService.getDashboardV2(userId, userEmail);

        assertNotNull(result);
        // Verify User A repositories called with User A ID
        verify(watchlistRepository).findAllByUserIdWithProduct(userId);
        verify(alertRepository).countUnreadByUserId(userId);
        verify(alertRepository).findAllByUserIdWithProduct(eq(userId), any(PageRequest.class));
        verify(savedComparisonRepository).countByUserId(userId);
        verify(savedProductRepository).countByUserId(userId);
        verify(recommendationService).getPersonalizedRecommendations(userId, 4);

        // Verify User B was NEVER queried
        verify(watchlistRepository, never()).findAllByUserIdWithProduct(userBId);
        verify(alertRepository, never()).countUnreadByUserId(userBId);
        verify(savedComparisonRepository, never()).countByUserId(userBId);
        verify(savedProductRepository, never()).countByUserId(userBId);
        verify(recommendationService, never()).getPersonalizedRecommendations(eq(userBId), anyInt());
    }

    @Test
    @DisplayName("Bounded limits enforced for opportunities (6), alerts (8), recommendations (4), and activity (10)")
    void testAllBoundedLimitsEnforced() {
        // Setup 15 watchlists that each generate an opportunity and multiple historical events
        List<PriceWatchlistEntity> watchlists = new ArrayList<>();
        for (int i = 0; i < 15; i++) {
            ProductEntity p = ProductEntity.builder().name("Prod " + i).brand("Brand").build();
            p.setId(UUID.randomUUID());
            PriceWatchlistEntity w = PriceWatchlistEntity.builder()
                    .product(p)
                    .currentBestPrice(BigDecimal.valueOf(100.00))
                    .targetPrice(BigDecimal.valueOf(150.00))
                    .active(true)
                    .build();
            w.setId(UUID.randomUUID());
            watchlists.add(w);

            ProductAnalyticsResponseDTO analytics = ProductAnalyticsResponseDTO.builder()
                    .dealQuality(DealQuality.EXCELLENT_DEAL)
                    .historicalEvents(List.of(
                            com.pricepilot.intelligence.analytics.model.PriceDropRecoveryEvent.builder()
                                    .eventType(com.pricepilot.intelligence.analytics.model.PriceDropRecoveryEvent.EventType.MAJOR_DROP)
                                    .resultingPrice(BigDecimal.valueOf(100.00))
                                    .occurredAt(LocalDateTime.now().minusHours(i))
                                    .build()
                    ))
                    .build();
            when(priceAnalyticsService.getProductAnalytics(p.getId())).thenReturn(analytics);
        }

        when(watchlistRepository.findAllByUserIdWithProduct(userId)).thenReturn(watchlists);

        // Alerts mock returns 8 items (bounded to MAX_RECENT_ALERTS = 8)
        List<PriceAlertEntity> alertEntities = new ArrayList<>();
        for (int i = 0; i < 8; i++) {
            PriceAlertEntity pa = PriceAlertEntity.builder()
                    .user(new UserEntity())
                    .product(product)
                    .alertType(AlertType.PRICE_DROP)
                    .title("Alert " + i)
                    .message("Msg " + i)
                    .deduplicationKey("KEY-" + i)
                    .build();
            pa.setId(UUID.randomUUID());
            pa.setCreatedAt(LocalDateTime.now());
            pa.setUpdatedAt(LocalDateTime.now());
            alertEntities.add(pa);
        }
        when(alertRepository.countUnreadByUserId(userId)).thenReturn(8L);
        when(alertRepository.findAllByUserIdWithProduct(eq(userId), eq(PageRequest.of(0, 8))))
                .thenReturn(new PageImpl<>(alertEntities));

        // Recommendations mock returns 10 products, should be capped to 4
        List<ProductResponseDTO> recProds = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            recProds.add(ProductResponseDTO.builder().id(UUID.randomUUID()).name("Rec " + i).build());
        }
        RecommendationResponse recResponse = new RecommendationResponse();
        recResponse.setRecommendedProducts(recProds);
        recResponse.setRecommendationType("TOP_PICKS");
        when(recommendationService.getPersonalizedRecommendations(userId, 4)).thenReturn(recResponse);

        DashboardV2ResponseDTO result = dashboardService.getDashboardV2(userId, userEmail);

        assertEquals(6, result.getPriceOpportunities().size()); // Capped at MAX_OPPORTUNITIES = 6
        assertEquals(8, result.getRecentAlerts().size()); // Capped at MAX_RECENT_ALERTS = 8
        assertEquals(4, result.getRecommendations().getItems().size()); // Capped at MAX_RECOMMENDATIONS = 4
        assertEquals(10, result.getRecentActivity().size()); // Capped at MAX_RECENT_ACTIVITY = 10
    }

    @Test
    @DisplayName("Failure isolation: simultaneous failures across recommendations, analytics, alerts, comparisons and products gracefully degrade to 200 OK")
    void testSimultaneousSubsystemFailuresGracefulDegradation() {
        when(watchlistRepository.findAllByUserIdWithProduct(userId)).thenReturn(List.of(watchlist));
        when(alertRepository.countUnreadByUserId(userId)).thenThrow(new RuntimeException("Alert DB connection timeout"));
        when(priceAnalyticsService.getProductAnalytics(product.getId()))
                .thenThrow(new RuntimeException("Analytics engine unavailable"));
        when(savedComparisonRepository.countByUserId(userId))
                .thenThrow(new RuntimeException("Comparison repo down"));
        when(savedProductRepository.countByUserId(userId))
                .thenThrow(new RuntimeException("Saved product repo down"));
        when(recommendationService.getPersonalizedRecommendations(userId, 4))
                .thenThrow(new RuntimeException("AI Recommendation microservice timeout"));

        DashboardV2ResponseDTO result = dashboardService.getDashboardV2(userId, userEmail);

        assertNotNull(result, "Dashboard response must not be null despite multiple simultaneous subsystem failures");
        assertEquals(1, result.getOverview().getActiveWatchlistsCount());
        assertEquals(0L, result.getOverview().getUnreadAlertsCount());
        assertEquals(0L, result.getOverview().getSavedComparisonsCount());
        assertEquals(0L, result.getOverview().getSavedProductsCount());

        // Watched products card list preserves core product data
        assertEquals(1, result.getWatchedProducts().size());
        assertEquals("Dell XPS 15", result.getWatchedProducts().get(0).getProductName());
        assertEquals(BigDecimal.valueOf(1400.00), result.getWatchedProducts().get(0).getCurrentPrice());
        assertNull(result.getWatchedProducts().get(0).getDealQuality());

        // Alerts and recommendations degrade gracefully
        assertTrue(result.getRecentAlerts().isEmpty());
        assertNotNull(result.getRecommendations());
        assertFalse(result.getRecommendations().isAvailable());
        assertEquals("UNAVAILABLE", result.getRecommendations().getStrategyUsed());
        assertTrue(result.getRecommendations().getItems().isEmpty());
    }

    @Test
    @DisplayName("Dashboard Response Bounds: Stress test with extreme user data (100 watchlists, 500 alerts, 50 recommendations, 100 historical events)")
    void testExtremeStressDashboardBoundsEnforcement() {
        // 100 watchlists
        List<PriceWatchlistEntity> hundredWatchlists = new ArrayList<>();
        for (int i = 0; i < 100; i++) {
            ProductEntity p = ProductEntity.builder()
                    .name("Extreme Prod " + i)
                    .brand("Brand " + i)
                    .build();
            p.setId(UUID.randomUUID());

            PriceWatchlistEntity w = PriceWatchlistEntity.builder()
                    .product(p)
                    .currentBestPrice(BigDecimal.valueOf(50.00 + i))
                    .targetPrice(BigDecimal.valueOf(100.00 + i)) // target met (+100) -> attention candidate & opportunity
                    .active(true)
                    .build();
            w.setId(UUID.randomUUID());
            hundredWatchlists.add(w);

            // Each product has 5 historical events (total 500 events)
            List<com.pricepilot.intelligence.analytics.model.PriceDropRecoveryEvent> events = new ArrayList<>();
            for (int e = 0; e < 5; e++) {
                events.add(com.pricepilot.intelligence.analytics.model.PriceDropRecoveryEvent.builder()
                        .eventType(com.pricepilot.intelligence.analytics.model.PriceDropRecoveryEvent.EventType.MAJOR_DROP)
                        .resultingPrice(BigDecimal.valueOf(50.00 + i - e))
                        .occurredAt(LocalDateTime.now().minusDays(e).minusHours(i))
                        .build());
            }

            ProductAnalyticsResponseDTO analytics = ProductAnalyticsResponseDTO.builder()
                    .historicalMin(BigDecimal.valueOf(45.00 + i))
                    .historicalAvg(BigDecimal.valueOf(80.00 + i))
                    .dealQuality(DealQuality.EXCELLENT_DEAL)
                    .purchaseSignal(PurchaseSignal.BUY_NOW)
                    .historicalEvents(events)
                    .build();
            lenient().when(priceAnalyticsService.getProductAnalytics(p.getId())).thenReturn(analytics);
        }

        when(watchlistRepository.findAllByUserIdWithProduct(userId)).thenReturn(hundredWatchlists);

        // 500 unread alerts, 8 recent alerts fetched by pageable
        when(alertRepository.countUnreadByUserId(userId)).thenReturn(500L);
        List<PriceAlertEntity> alertEntities = new ArrayList<>();
        for (int i = 0; i < 8; i++) {
            PriceAlertEntity pa = PriceAlertEntity.builder()
                    .user(new UserEntity())
                    .product(product)
                    .alertType(AlertType.PRICE_DROP)
                    .title("Alert " + i)
                    .message("Msg " + i)
                    .deduplicationKey("KEY-" + i)
                    .build();
            pa.setId(UUID.randomUUID());
            pa.setCreatedAt(LocalDateTime.now());
            pa.setUpdatedAt(LocalDateTime.now());
            alertEntities.add(pa);
        }
        when(alertRepository.findAllByUserIdWithProduct(eq(userId), eq(PageRequest.of(0, 8))))
                .thenReturn(new PageImpl<>(alertEntities));

        // 50 recommendations returned by mock service
        List<ProductResponseDTO> fiftyRecs = new ArrayList<>();
        for (int i = 0; i < 50; i++) {
            fiftyRecs.add(ProductResponseDTO.builder().id(UUID.randomUUID()).name("Rec Prod " + i).build());
        }
        RecommendationResponse recResponse = new RecommendationResponse();
        recResponse.setRecommendedProducts(fiftyRecs);
        recResponse.setRecommendationType("TOP_PICKS");
        when(recommendationService.getPersonalizedRecommendations(userId, 4)).thenReturn(recResponse);

        DashboardV2ResponseDTO response = dashboardService.getDashboardV2(userId, userEmail);

        assertNotNull(response);
        // Total count in overview reflects full user scale (100 active watchlists, 500 unread alerts)
        assertEquals(100, response.getOverview().getActiveWatchlistsCount());
        assertEquals(500L, response.getOverview().getUnreadAlertsCount());

        // Strict Presentation Bounds Enforced:
        // watchedProducts <= 15
        assertTrue(response.getWatchedProducts().size() <= 15, "watchedProducts must be <= 15, actual: " + response.getWatchedProducts().size());
        assertEquals(15, response.getWatchedProducts().size());

        // attentionItems <= 6
        assertTrue(response.getAttentionItems().size() <= 6, "attentionItems must be <= 6, actual: " + response.getAttentionItems().size());
        assertEquals(6, response.getAttentionItems().size());

        // priceOpportunities <= 6
        assertTrue(response.getPriceOpportunities().size() <= 6, "priceOpportunities must be <= 6, actual: " + response.getPriceOpportunities().size());
        assertEquals(6, response.getPriceOpportunities().size());

        // recentAlerts <= 8
        assertTrue(response.getRecentAlerts().size() <= 8, "recentAlerts must be <= 8, actual: " + response.getRecentAlerts().size());
        assertEquals(8, response.getRecentAlerts().size());

        // recommendations <= 4
        assertNotNull(response.getRecommendations());
        assertTrue(response.getRecommendations().getItems().size() <= 4, "recommendations must be <= 4, actual: " + response.getRecommendations().getItems().size());
        assertEquals(4, response.getRecommendations().getItems().size());

        // recentActivity <= 10
        assertTrue(response.getRecentActivity().size() <= 10, "recentActivity must be <= 10, actual: " + response.getRecentActivity().size());
        assertEquals(10, response.getRecentActivity().size());
    }
}
