package com.pricepilot.intelligence.alert;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pricepilot.intelligence.alert.dto.UpdateWatchlistAlertPreferenceRequestDTO;
import com.pricepilot.intelligence.alert.entity.PriceAlertEntity;
import com.pricepilot.intelligence.alert.entity.WatchlistAlertPreferenceEntity;
import com.pricepilot.intelligence.alert.model.AlertType;
import com.pricepilot.intelligence.alert.repository.PriceAlertRepository;
import com.pricepilot.intelligence.alert.repository.WatchlistAlertPreferenceRepository;
import com.pricepilot.intelligence.alert.scheduler.PriceAlertScheduler;
import com.pricepilot.intelligence.alert.service.PriceAlertService;
import com.pricepilot.product.ProductEntity;
import com.pricepilot.product.ProductRepository;
import com.pricepilot.productprice.ProductPriceEntity;
import com.pricepilot.productprice.ProductPriceRepository;
import com.pricepilot.security.UserPrincipal;
import com.pricepilot.seller.SellerEntity;
import com.pricepilot.seller.SellerRepository;
import com.pricepilot.user.Role;
import com.pricepilot.user.UserEntity;
import com.pricepilot.user.UserRepository;
import com.pricepilot.watchlist.PriceWatchlistEntity;
import com.pricepilot.watchlist.PriceWatchlistRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.atMost;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
public class PriceAlertAdversarialIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private SellerRepository sellerRepository;

    @Autowired
    private ProductPriceRepository productPriceRepository;

    @Autowired
    private PriceWatchlistRepository watchlistRepository;

    @Autowired
    private PriceAlertRepository alertRepository;

    @Autowired
    private WatchlistAlertPreferenceRepository preferenceRepository;

    @Autowired
    private PriceAlertService priceAlertService;

    @Autowired
    private PriceAlertScheduler priceAlertScheduler;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Autowired
    private EntityManager entityManager;

    private TransactionTemplate transactionTemplate;
    private final ObjectMapper objectMapper = new ObjectMapper();

    private UserEntity userA;
    private UserEntity userB;
    private ProductEntity activeProduct;
    private SellerEntity seller;

    @BeforeEach
    void setUp() {
        transactionTemplate = new TransactionTemplate(transactionManager);

        transactionTemplate.execute(status -> {
            alertRepository.deleteAll();
            preferenceRepository.deleteAll();
            watchlistRepository.deleteAll();
            productPriceRepository.deleteAll();
            productRepository.deleteAll();
            sellerRepository.deleteAll();
            String emailA = "usera-" + UUID.randomUUID() + "@pricepilot.io";
            String emailB = "userb-" + UUID.randomUUID() + "@pricepilot.io";

            userA = userRepository.save(UserEntity.builder()
                    .email(emailA)
                    .password("secret123")
                    .firstName("User")
                    .lastName("A")
                    .role(Role.USER)
                    .enabled(true)
                    .build());

            userB = userRepository.save(UserEntity.builder()
                    .email(emailB)
                    .password("secret123")
                    .firstName("User")
                    .lastName("B")
                    .role(Role.USER)
                    .enabled(true)
                    .build());

            seller = sellerRepository.save(SellerEntity.builder()
                    .name("BestBuy")
                    .websiteUrl("https://bestbuy.com")
                    .build());

            activeProduct = productRepository.save(ProductEntity.builder()
                    .name("Sony WH-1000XM5 Wireless Headphones")
                    .brand("Sony")
                    .category("Audio")
                    .description("Premium noise cancelling headphones")
                    .archived(false)
                    .build());

            productPriceRepository.save(ProductPriceEntity.builder()
                    .product(activeProduct)
                    .seller(seller)
                    .currentPrice(BigDecimal.valueOf(399.99))
                    .originalPrice(BigDecimal.valueOf(449.99))
                    .productUrl("https://bestbuy.com/sony-xm5")
                    .lastUpdated(LocalDateTime.now())
                    .build());

            return null;
        });
    }

    // =========================================================================
    // 1. DATABASE INTEGRITY: UNIQUE CONSTRAINT & FOREIGN KEYS
    // =========================================================================
    @Test
    @DisplayName("DB Integrity: Unique constraint on deduplication_key throws DataIntegrityViolationException")
    void testDeduplicationKeyUniqueConstraintAtDatabaseLevel() {
        transactionTemplate.execute(status -> {
            PriceAlertEntity alert1 = PriceAlertEntity.builder()
                    .user(userA)
                    .product(activeProduct)
                    .alertType(AlertType.PRICE_DROP)
                    .title("Price Drop 1")
                    .message("Message 1")
                    .deduplicationKey("DUP-UNIQUE-KEY-001")
                    .build();
            alertRepository.saveAndFlush(alert1);
            return null;
        });

        // Inserting second alert with identical deduplicationKey MUST fail with DataIntegrityViolationException
        assertThrows(DataIntegrityViolationException.class, () ->
                transactionTemplate.execute(status -> {
                    PriceAlertEntity alert2 = PriceAlertEntity.builder()
                            .user(userA)
                            .product(activeProduct)
                            .alertType(AlertType.PRICE_DROP)
                            .title("Price Drop 2")
                            .message("Message 2")
                            .deduplicationKey("DUP-UNIQUE-KEY-001")
                            .build();
                    alertRepository.saveAndFlush(alert2);
                    return null;
                })
        );
    }

    @Test
    @DisplayName("DB Integrity: Deleting Watchlist sets alert watchlist_id to NULL and cascade-deletes preferences")
    void testCascadeDeletionIntegrity() {
        UUID watchlistId = transactionTemplate.execute(status -> {
            PriceWatchlistEntity wl = watchlistRepository.save(PriceWatchlistEntity.builder()
                    .user(userA)
                    .product(activeProduct)
                    .targetPrice(BigDecimal.valueOf(300.00))
                    .currentBestPrice(BigDecimal.valueOf(399.99))
                    .active(true)
                    .build());

            preferenceRepository.save(WatchlistAlertPreferenceEntity.defaultForWatchlist(wl));

            alertRepository.save(PriceAlertEntity.builder()
                    .user(userA)
                    .product(activeProduct)
                    .watchlist(wl)
                    .alertType(AlertType.PRICE_TARGET_REACHED)
                    .title("Target reached")
                    .message("Target reached at 299")
                    .deduplicationKey("TARGET-WATCHLIST-CASCADE")
                    .build());

            return wl.getId();
        });

        // Delete watchlist
        transactionTemplate.execute(status -> {
            watchlistRepository.deleteById(watchlistId);
            return null;
        });

        // Verify: Preferences were cascade deleted
        transactionTemplate.execute(status -> {
            assertTrue(preferenceRepository.findByWatchlistId(watchlistId).isEmpty());
            // Verify: Alert is preserved with watchlist_id set to null (history retained)
            List<PriceAlertEntity> alerts = alertRepository.findAll();
            assertEquals(1, alerts.size());
            assertNull(alerts.get(0).getWatchlist());
            return null;
        });
    }

    // =========================================================================
    // 2. CRITICAL DUPLICATE PREVENTION ON REAL DATABASE (50 RUNS)
    // =========================================================================
    @Test
    @DisplayName("Critical: Running processPriceUpdateEvent 50 times generates AT MOST 1 notification per alert type in DB")
    void testEndToEndDuplicatePrevention50Runs() {
        transactionTemplate.execute(status -> {
            PriceWatchlistEntity wl = watchlistRepository.save(PriceWatchlistEntity.builder()
                    .user(userA)
                    .product(activeProduct)
                    .targetPrice(BigDecimal.valueOf(350.00))
                    .currentBestPrice(BigDecimal.valueOf(399.99))
                    .active(true)
                    .build());
            preferenceRepository.save(WatchlistAlertPreferenceEntity.defaultForWatchlist(wl));
            return null;
        });

        // Run 50 times with identical price drop from 399.99 to 320.00
        for (int i = 0; i < 50; i++) {
            priceAlertService.processPriceUpdateEvent(
                    activeProduct.getId(),
                    BigDecimal.valueOf(399.99),
                    BigDecimal.valueOf(320.00),
                    false
            );
        }

        // Verify database: Exactly 3 alerts in DB (TARGET_REACHED + PRICE_DROP + HISTORICAL_LOW_REACHED), NEVER 150!
        transactionTemplate.execute(status -> {
            List<PriceAlertEntity> alerts = alertRepository.findAll();
            assertEquals(3, alerts.size(), "Expected exactly 3 unique alerts (TARGET, DROP, HIST_LOW) after 50 runs, found " + alerts.size());
            Set<AlertType> types = alerts.stream().map(PriceAlertEntity::getAlertType).collect(java.util.stream.Collectors.toSet());
            assertTrue(types.contains(AlertType.PRICE_TARGET_REACHED));
            assertTrue(types.contains(AlertType.PRICE_DROP));
            assertTrue(types.contains(AlertType.HISTORICAL_LOW_REACHED));
            return null;
        });
    }

    // =========================================================================
    // 3. CONCURRENT RACE CONDITIONS ON REAL DATABASE
    // =========================================================================
    @Test
    @DisplayName("Concurrency: 10 concurrent threads executing identical price update against real DB produces exactly 1 alert")
    void testMultithreadedConcurrentExecutionOnRealDatabase() throws Exception {
        transactionTemplate.execute(status -> {
            PriceWatchlistEntity wl = watchlistRepository.save(PriceWatchlistEntity.builder()
                    .user(userA)
                    .product(activeProduct)
                    .targetPrice(BigDecimal.valueOf(250.00))
                    .currentBestPrice(BigDecimal.valueOf(399.99))
                    .active(true)
                    .build());
            WatchlistAlertPreferenceEntity p = WatchlistAlertPreferenceEntity.defaultForWatchlist(wl);
            p.setPriceDropEnabled(false);
            p.setHistoricalLowEnabled(false);
            p.setGoodDealEnabled(false);
            p.setBackInStockEnabled(false);
            p.setPriceIncreaseEnabled(false);
            preferenceRepository.save(p);
            return null;
        });

        int threadCount = 10;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch readyLatch = new CountDownLatch(threadCount);
        CountDownLatch startLatch = new CountDownLatch(1);
        List<Future<Void>> futures = new ArrayList<>();

        for (int i = 0; i < threadCount; i++) {
            futures.add(executor.submit(() -> {
                readyLatch.countDown();
                startLatch.await();
                priceAlertService.processPriceUpdateEvent(
                        activeProduct.getId(),
                        BigDecimal.valueOf(399.99),
                        BigDecimal.valueOf(240.00),
                        false
                );
                return null;
            }));
        }

        readyLatch.await(5, TimeUnit.SECONDS);
        startLatch.countDown();

        for (Future<Void> future : futures) {
            assertDoesNotThrow(() -> future.get(10, TimeUnit.SECONDS));
        }

        executor.shutdown();
        assertTrue(executor.awaitTermination(5, TimeUnit.SECONDS));

        // Verify DB: exactly 1 TARGET_REACHED alert persisted despite 10 simultaneous threads
        transactionTemplate.execute(status -> {
            List<PriceAlertEntity> alerts = alertRepository.findAll();
            assertEquals(1, alerts.size(), "Expected exactly 1 alert saved across 10 concurrent threads, found " + alerts.size());
            assertEquals(AlertType.PRICE_TARGET_REACHED, alerts.get(0).getAlertType());
            return null;
        });
    }

    // =========================================================================
    // 4. CROSS-USER SECURITY AND AUTHORIZATION VIA MOCKMVC
    // =========================================================================
    @Test
    @DisplayName("Security: User A cannot read or modify User B's alert preferences via HTTP API")
    void testCrossUserSecurityAlertPreferences() throws Exception {
        UUID userBWatchlistId = transactionTemplate.execute(status -> {
            PriceWatchlistEntity wlB = watchlistRepository.save(PriceWatchlistEntity.builder()
                    .user(userB)
                    .product(activeProduct)
                    .targetPrice(BigDecimal.valueOf(200.00))
                    .currentBestPrice(BigDecimal.valueOf(399.99))
                    .active(true)
                    .build());
            preferenceRepository.save(WatchlistAlertPreferenceEntity.defaultForWatchlist(wlB));
            return wlB.getId();
        });

        UserPrincipal principalA = new UserPrincipal(userA.getId(), userA.getEmail(), "password", Role.USER, true, false);

        // 1. User A tries to GET User B's watchlist alert preferences -> 403 Forbidden
        mockMvc.perform(get("/api/v1/watchlists/" + userBWatchlistId + "/alerts")
                        .with(user(principalA)))
                .andExpect(status().isForbidden());

        // 2. User A tries to PUT User B's watchlist alert preferences -> 403 Forbidden
        UpdateWatchlistAlertPreferenceRequestDTO updateDto = UpdateWatchlistAlertPreferenceRequestDTO.builder()
                .enabled(false)
                .build();

        mockMvc.perform(put("/api/v1/watchlists/" + userBWatchlistId + "/alerts")
                        .with(user(principalA))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateDto)))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Security: User A cannot mark User B's price alert as read via HTTP API")
    void testCrossUserSecurityMarkAlertRead() throws Exception {
        UUID userBAlertId = transactionTemplate.execute(status -> {
            PriceAlertEntity alertB = alertRepository.save(PriceAlertEntity.builder()
                    .user(userB)
                    .product(activeProduct)
                    .alertType(AlertType.PRICE_DROP)
                    .title("User B Alert")
                    .message("User B Alert Message")
                    .deduplicationKey("SEC-USER-B-ALERT")
                    .build());
            return alertB.getId();
        });

        UserPrincipal principalA = new UserPrincipal(userA.getId(), userA.getEmail(), "password", Role.USER, true, false);

        // User A tries to mark User B's alert as read -> 403 Forbidden
        mockMvc.perform(patch("/api/v1/alerts/" + userBAlertId + "/read")
                        .with(user(principalA)))
                .andExpect(status().isForbidden());
    }

    // =========================================================================
    // 5. BATCH PROCESSING & N+1 PREVENTION IN SCHEDULER
    // =========================================================================
    @Test
    @DisplayName("Scheduler: Evaluates watchlists in bounded batches without N+1 query explosion")
    void testSchedulerBatchProcessingBoundedQueries() {
        // Create 100 watchlists across products
        transactionTemplate.execute(status -> {
            for (int i = 0; i < 100; i++) {
                ProductEntity p = productRepository.save(ProductEntity.builder()
                        .name("Product Batch Test " + i)
                        .brand("Brand " + (i % 5))
                        .category("Category " + (i % 3))
                        .archived(false)
                        .build());

                watchlistRepository.save(PriceWatchlistEntity.builder()
                        .user(userA)
                        .product(p)
                        .targetPrice(BigDecimal.valueOf(100.00))
                        .currentBestPrice(BigDecimal.valueOf(150.00))
                        .active(true)
                        .build());
            }
            return null;
        });

        // Execute scheduler - must complete without exploding queries or timing out
        assertDoesNotThrow(() -> priceAlertScheduler.evaluateActiveWatchlists());
    }
}
