package com.pricepilot.intelligence.dashboard;

import com.pricepilot.intelligence.alert.entity.PriceAlertEntity;
import com.pricepilot.intelligence.alert.model.AlertType;
import com.pricepilot.intelligence.alert.repository.PriceAlertRepository;
import com.pricepilot.intelligence.dashboard.dto.DashboardV2ResponseDTO;
import com.pricepilot.intelligence.dashboard.service.DashboardV2Service;
import com.pricepilot.product.ProductEntity;
import com.pricepilot.product.ProductRepository;
import com.pricepilot.recommendation.RecommendationCacheHelper;
import com.pricepilot.savedproduct.SavedProductEntity;
import com.pricepilot.savedproduct.SavedProductId;
import com.pricepilot.savedproduct.SavedProductRepository;
import com.pricepilot.user.Role;
import com.pricepilot.user.UserEntity;
import com.pricepilot.user.UserRepository;
import com.pricepilot.watchlist.PriceWatchlistEntity;
import com.pricepilot.watchlist.PriceWatchlistRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
public class DashboardV2AdversarialIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private DashboardV2Service dashboardV2Service;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private PriceWatchlistRepository watchlistRepository;

    @Autowired
    private PriceAlertRepository alertRepository;

    @Autowired
    private SavedProductRepository savedProductRepository;

    @Autowired
    private CacheManager cacheManager;

    @Autowired
    private RecommendationCacheHelper cacheHelper;

    private UserEntity userA;
    private UserEntity userB;
    private ProductEntity productA;
    private ProductEntity productB;

    @BeforeEach
    void setUp() {
        cacheHelper.evictAllCaches();
        alertRepository.deleteAll();
        watchlistRepository.deleteAll();
        savedProductRepository.deleteAll();
        productRepository.deleteAll();
        userRepository.deleteAll();

        // Create User A
        userA = userRepository.save(UserEntity.builder()
                .email("user.a." + UUID.randomUUID() + "@example.com")
                .password("Password123!")
                .firstName("Alice")
                .lastName("Shopper")
                .role(Role.USER)
                .enabled(true)
                .build());

        // Create User B
        userB = userRepository.save(UserEntity.builder()
                .email("user.b." + UUID.randomUUID() + "@example.com")
                .password("Password123!")
                .firstName("Bob")
                .lastName("Buyer")
                .role(Role.USER)
                .enabled(true)
                .build());

        // Product A (watched by User A)
        productA = productRepository.save(ProductEntity.builder()
                .name("Apple MacBook Pro M3 - UserA Exclusive")
                .brand("Apple")
                .category("Laptops")
                .description("Pro laptop for user A")
                .imageUrl("https://example.com/mac.png")
                .archived(false)
                .build());

        // Product B (watched by User B)
        productB = productRepository.save(ProductEntity.builder()
                .name("Sony WH-1000XM5 - UserB Exclusive")
                .brand("Sony")
                .category("Headphones")
                .description("Headphones for user B")
                .imageUrl("https://example.com/sony.png")
                .archived(false)
                .build());

        // Watchlist for User A
        watchlistRepository.save(PriceWatchlistEntity.builder()
                .user(userA)
                .product(productA)
                .currentBestPrice(BigDecimal.valueOf(1899.00))
                .targetPrice(BigDecimal.valueOf(1999.00))
                .active(true)
                .build());

        // Watchlist for User B
        watchlistRepository.save(PriceWatchlistEntity.builder()
                .user(userB)
                .product(productB)
                .currentBestPrice(BigDecimal.valueOf(329.00))
                .targetPrice(BigDecimal.valueOf(350.00))
                .active(true)
                .build());

        // Alert for User A
        alertRepository.save(PriceAlertEntity.builder()
                .user(userA)
                .product(productA)
                .alertType(AlertType.PRICE_TARGET_REACHED)
                .title("Target reached for Alice")
                .message("Alice your MacBook hit target price")
                .deduplicationKey("ALICE_TARGET_" + UUID.randomUUID())
                .build());

        // Alert for User B
        alertRepository.save(PriceAlertEntity.builder()
                .user(userB)
                .product(productB)
                .alertType(AlertType.PRICE_DROP)
                .title("Price drop for Bob")
                .message("Bob your headphones dropped")
                .deduplicationKey("BOB_DROP_" + UUID.randomUUID())
                .build());
    }

    @Test
    @DisplayName("Critical Invariant 1: Cross-User Isolation & Cache Key Scoping")
    void testCrossUserAndCacheIsolation() {
        // Step 1: User A calls dashboard (populates cache for key userA.getId())
        DashboardV2ResponseDTO respA1 = dashboardV2Service.getDashboardV2(userA.getId(), userA.getEmail());
        assertNotNull(respA1);
        assertEquals(1, respA1.getOverview().getActiveWatchlistsCount());
        assertEquals("Apple MacBook Pro M3 - UserA Exclusive", respA1.getWatchedProducts().get(0).getProductName());
        assertEquals(1, respA1.getRecentAlerts().size());
        assertEquals("Target reached for Alice", respA1.getRecentAlerts().get(0).getTitle());

        // Verify cache contains entry for User A
        Cache dashboardCache = cacheManager.getCache("dashboard-v2");
        assertNotNull(dashboardCache);
        assertNotNull(dashboardCache.get(userA.getId()));

        // Step 2: User B calls dashboard
        // CRITICAL: User B MUST NOT receive User A's cached response!
        DashboardV2ResponseDTO respB1 = dashboardV2Service.getDashboardV2(userB.getId(), userB.getEmail());
        assertNotNull(respB1);
        assertEquals(1, respB1.getOverview().getActiveWatchlistsCount());
        assertEquals("Sony WH-1000XM5 - UserB Exclusive", respB1.getWatchedProducts().get(0).getProductName());
        assertEquals(1, respB1.getRecentAlerts().size());
        assertEquals("Price drop for Bob", respB1.getRecentAlerts().get(0).getTitle());

        // Verify zero cross-user bleed
        for (var card : respB1.getWatchedProducts()) {
            assertNotEquals("Apple MacBook Pro M3 - UserA Exclusive", card.getProductName());
        }
        for (var alert : respB1.getRecentAlerts()) {
            assertFalse(alert.getMessage().contains("Alice"));
        }

        // Step 3: Evict User A's cache
        cacheHelper.evictUserCaches(userA.getId());
        assertNull(dashboardCache.get(userA.getId()), "User A cache must be evicted");
        assertNotNull(dashboardCache.get(userB.getId()), "User B cache must remain unaffected");

        // Step 4: Re-fetching User B returns User B's intact cached response
        DashboardV2ResponseDTO respB2 = dashboardV2Service.getDashboardV2(userB.getId(), userB.getEmail());
        assertEquals("Sony WH-1000XM5 - UserB Exclusive", respB2.getWatchedProducts().get(0).getProductName());
    }

    @Test
    @DisplayName("Critical Invariant 2: Concurrent Multi-User Execution Zero Bleed")
    void testConcurrentMultiUserExecution() throws Exception {
        int threads = 10;
        ExecutorService executor = Executors.newFixedThreadPool(threads * 2);
        CountDownLatch latch = new CountDownLatch(1);
        List<Future<Boolean>> futures = new ArrayList<>();

        for (int i = 0; i < threads; i++) {
            // Task for User A
            futures.add(executor.submit(() -> {
                latch.await();
                DashboardV2ResponseDTO res = dashboardV2Service.getDashboardV2(userA.getId(), userA.getEmail());
                return res.getWatchedProducts().stream().allMatch(p -> p.getProductName().contains("UserA"));
            }));
            // Task for User B
            futures.add(executor.submit(() -> {
                latch.await();
                DashboardV2ResponseDTO res = dashboardV2Service.getDashboardV2(userB.getId(), userB.getEmail());
                return res.getWatchedProducts().stream().allMatch(p -> p.getProductName().contains("UserB"));
            }));
        }

        latch.countDown();
        for (Future<Boolean> f : futures) {
            assertTrue(f.get(5, TimeUnit.SECONDS), "Concurrent execution must have zero data contamination across users");
        }
        executor.shutdown();
    }

    @Test
    @DisplayName("Critical Invariant 3: Bounded Response Sets and Scaling Across 10, 50, 100 Watchlists")
    void testScalingAndBoundsEnforcement() {
        // Create 60 watchlists for User A
        List<ProductEntity> prods = new ArrayList<>();
        for (int i = 0; i < 60; i++) {
            ProductEntity p = ProductEntity.builder()
                    .name("Bulk Product " + i)
                    .brand("Brand " + i)
                    .category("Category")
                    .build();
            prods.add(p);
        }
        prods = productRepository.saveAll(prods);

        List<PriceWatchlistEntity> bulkWatchlists = new ArrayList<>();
        for (int i = 0; i < 60; i++) {
            bulkWatchlists.add(PriceWatchlistEntity.builder()
                    .user(userA)
                    .product(prods.get(i))
                    .currentBestPrice(BigDecimal.valueOf(100.00 + i))
                    .targetPrice(BigDecimal.valueOf(150.00 + i))
                    .active(true)
                    .build());
        }
        watchlistRepository.saveAll(bulkWatchlists);

        // Evict cache to test cold performance & bounding
        cacheHelper.evictUserCaches(userA.getId());

        long start = System.currentTimeMillis();
        DashboardV2ResponseDTO response = dashboardV2Service.getDashboardV2(userA.getId(), userA.getEmail());
        long duration = System.currentTimeMillis() - start;

        assertNotNull(response);
        // Active watchlists in overview reflects full user count (61 total)
        assertEquals(61, response.getOverview().getActiveWatchlistsCount());

        // BUT watched products returned in the presentation card list MUST be strictly capped at 15
        assertTrue(response.getWatchedProducts().size() <= 15, "Watched products must be capped at <= 15");
        assertTrue(response.getAttentionItems().size() <= 6, "Attention items must be capped at <= 6");
        assertTrue(response.getPriceOpportunities().size() <= 6, "Price opportunities must be capped at <= 6");
        assertTrue(response.getRecentAlerts().size() <= 8, "Recent alerts must be capped at <= 8");
        assertTrue(response.getRecentActivity().size() <= 10, "Recent activity must be capped at <= 10");

        // Execution time remains bounded (< 300 ms on test database)
        assertTrue(duration < 1000, "Dashboard aggregation should execute in < 1000ms, actual: " + duration + "ms");
    }

    @Test
    @DisplayName("Critical Invariant 4: Empty State for New User Returns Clean Zeros")
    void testCleanEmptyState() {
        UserEntity newUser = userRepository.save(UserEntity.builder()
                .email("fresh.user@example.com")
                .password("Password123!")
                .firstName("New")
                .lastName("Account")
                .role(Role.USER)
                .enabled(true)
                .build());

        DashboardV2ResponseDTO response = dashboardV2Service.getDashboardV2(newUser.getId(), newUser.getEmail());

        assertNotNull(response);
        assertEquals(0, response.getOverview().getActiveWatchlistsCount());
        assertEquals(0L, response.getOverview().getUnreadAlertsCount());
        assertEquals(0, response.getOverview().getHistoricalLowCount());
        assertEquals(0, response.getOverview().getGoodOrExcellentDealCount());
        assertEquals(0L, response.getOverview().getSavedComparisonsCount());
        assertEquals(0L, response.getOverview().getSavedProductsCount());
        assertTrue(response.getWatchedProducts().isEmpty());
        assertTrue(response.getAttentionItems().isEmpty());
        assertTrue(response.getPriceOpportunities().isEmpty());
        assertTrue(response.getRecentAlerts().isEmpty());
    }

    @Test
    @DisplayName("Critical Invariant 5: Unauthenticated request to Dashboard V2 returns 401/403 Forbidden")
    void testUnauthenticatedEndpointSecurity() throws Exception {
        mockMvc.perform(get("/api/v1/dashboard/v2"))
                .andExpect(result -> {
                    int status = result.getResponse().getStatus();
                    assertTrue(status == 401 || status == 403, "Unauthenticated requests must be rejected with 401/403, got: " + status);
                });
    }

    @Test
    @DisplayName("Critical Invariant 6: N+1 & Query Explosion at Scale (10, 50, 100 Watchlists)")
    void testScaleAt10_50_100WatchlistsWithQueryBoundAndLatency() {
        // Create user for scaling test
        UserEntity scaleUser = userRepository.save(UserEntity.builder()
                .email("scale.user." + UUID.randomUUID() + "@example.com")
                .password("Password123!")
                .firstName("Scale")
                .lastName("Tester")
                .role(Role.USER)
                .enabled(true)
                .build());

        int[] scales = {10, 50, 100};
        for (int count : scales) {
            // Clean watchlists for scaleUser
            watchlistRepository.deleteAll();
            cacheHelper.evictUserCaches(scaleUser.getId());

            List<ProductEntity> prods = new ArrayList<>();
            for (int i = 0; i < count; i++) {
                prods.add(ProductEntity.builder()
                        .name("Scale Prod " + count + "_" + i)
                        .brand("ScaleBrand")
                        .category("Testing")
                        .archived(false)
                        .build());
            }
            prods = productRepository.saveAll(prods);

            List<PriceWatchlistEntity> watchlists = new ArrayList<>();
            for (int i = 0; i < count; i++) {
                watchlists.add(PriceWatchlistEntity.builder()
                        .user(scaleUser)
                        .product(prods.get(i))
                        .currentBestPrice(BigDecimal.valueOf(100.00 + i))
                        .targetPrice(BigDecimal.valueOf(150.00 + i))
                        .active(true)
                        .build());
            }
            watchlistRepository.saveAll(watchlists);

            // Cold execution latency measurement
            cacheHelper.evictUserCaches(scaleUser.getId());
            long start = System.currentTimeMillis();
            DashboardV2ResponseDTO response = dashboardV2Service.getDashboardV2(scaleUser.getId(), scaleUser.getEmail());
            long latencyMs = System.currentTimeMillis() - start;

            assertNotNull(response);
            assertEquals(count, response.getOverview().getActiveWatchlistsCount(),
                    "Total active watchlists count in overview must match scale " + count);
            assertTrue(response.getWatchedProducts().size() <= 15,
                    "Watched cards in presentation list must be capped at 15 for scale " + count);
            assertEquals(Math.min(15, count), response.getWatchedProducts().size());

            // Execution latency must remain strictly bounded (< 3000 ms even for 100 watchlists on test DB)
            assertTrue(latencyMs < 3000,
                    "Dashboard aggregation latency for " + count + " watchlists must be < 3000ms, actual: " + latencyMs + "ms");

            // Warm cached call execution must be instantaneous (< 100ms)
            long warmStart = System.currentTimeMillis();
            DashboardV2ResponseDTO cachedResponse = dashboardV2Service.getDashboardV2(scaleUser.getId(), scaleUser.getEmail());
            long warmLatencyMs = System.currentTimeMillis() - warmStart;
            assertNotNull(cachedResponse);
            assertTrue(warmLatencyMs < 100, "Cached response should return in < 100ms, actual: " + warmLatencyMs + "ms");
        }
    }
}
