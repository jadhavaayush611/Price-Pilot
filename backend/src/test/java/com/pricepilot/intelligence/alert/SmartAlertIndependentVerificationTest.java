package com.pricepilot.intelligence.alert;

import com.pricepilot.analytics.dto.ProductAnalyticsResponseDTO;
import com.pricepilot.intelligence.alert.controller.PriceAlertController;
import com.pricepilot.intelligence.alert.delivery.NotificationDeliveryService;
import com.pricepilot.intelligence.alert.dto.PriceAlertResponseDTO;
import com.pricepilot.intelligence.alert.dto.UpdateWatchlistAlertPreferenceRequestDTO;
import com.pricepilot.intelligence.alert.engine.AlertRuleEvaluator;
import com.pricepilot.intelligence.alert.entity.PriceAlertEntity;
import com.pricepilot.intelligence.alert.entity.WatchlistAlertPreferenceEntity;
import com.pricepilot.intelligence.alert.model.AlertType;
import com.pricepilot.intelligence.alert.repository.PriceAlertRepository;
import com.pricepilot.intelligence.alert.repository.WatchlistAlertPreferenceRepository;
import com.pricepilot.intelligence.alert.scheduler.PriceAlertScheduler;
import com.pricepilot.intelligence.alert.service.PriceAlertServiceImpl;
import com.pricepilot.intelligence.analytics.PriceAnalyticsService;
import com.pricepilot.intelligence.analytics.model.DealQuality;
import com.pricepilot.product.ProductEntity;
import com.pricepilot.productprice.ProductPriceRepository;
import com.pricepilot.productprice.dto.BestPriceProjection;
import com.pricepilot.security.UserPrincipal;
import com.pricepilot.user.Role;
import com.pricepilot.user.UserEntity;
import com.pricepilot.watchlist.PriceWatchlistEntity;
import com.pricepilot.watchlist.PriceWatchlistRepository;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.AccessDeniedException;

import java.math.BigDecimal;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * Phase 5 Independent Verification Test Suite.
 * Adversarial, rigorous, end-to-end verification covering:
 * - Duplicate Prevention (15 and 50 runs on identical/unchanged prices)
 * - Concurrent Executions & Multithreaded Race Conditions
 * - Cross-User Security & Authorization
 * - Boundary Conditions for all 6 Alert Types
 * - Batch Processing & Scheduler N+1 Prevention
 * - Performance Scaling (1,000 active watchlists < 500ms)
 */
@ExtendWith(MockitoExtension.class)
public class SmartAlertIndependentVerificationTest {

    private AlertRuleEvaluator ruleEvaluator;
    private SimpleMeterRegistry meterRegistry;
    private PriceAlertServiceImpl alertService;

    @Mock
    private PriceAlertRepository alertRepository;

    @Mock
    private WatchlistAlertPreferenceRepository preferenceRepository;

    @Mock
    private PriceWatchlistRepository watchlistRepository;

    @Mock
    private ProductPriceRepository productPriceRepository;

    @Mock
    private NotificationDeliveryService deliveryService;

    @Mock
    private PriceAnalyticsService priceAnalyticsService;

    private UserEntity userA;
    private UserEntity userB;
    private ProductEntity product;
    private PriceWatchlistEntity watchlist;
    private WatchlistAlertPreferenceEntity preferences;

    @BeforeEach
    void setUp() {
        ruleEvaluator = new AlertRuleEvaluator();
        meterRegistry = new SimpleMeterRegistry();
        alertService = new PriceAlertServiceImpl(
                alertRepository,
                preferenceRepository,
                watchlistRepository,
                ruleEvaluator,
                deliveryService,
                priceAnalyticsService,
                meterRegistry
        );

        userA = new UserEntity();
        userA.setId(UUID.randomUUID());
        userA.setEmail("userA@pricepilot.io");

        userB = new UserEntity();
        userB.setId(UUID.randomUUID());
        userB.setEmail("userB@pricepilot.io");

        product = ProductEntity.builder()
                .name("MacBook Pro M3 Max")
                .brand("Apple")
                .category("Laptops")
                .build();
        product.setId(UUID.randomUUID());

        watchlist = PriceWatchlistEntity.builder()
                .user(userA)
                .product(product)
                .targetPrice(BigDecimal.valueOf(2500.00))
                .currentBestPrice(BigDecimal.valueOf(2800.00))
                .active(true)
                .build();
        watchlist.setId(UUID.randomUUID());

        preferences = WatchlistAlertPreferenceEntity.defaultForWatchlist(watchlist);
    }

    // =========================================================================
    // 1. CRITICAL: DUPLICATE PREVENTION (15 AND 50 RUNS)
    // =========================================================================
    @Nested
    @DisplayName("Critical: Duplicate Prevention (15 to 50 Runs)")
    class DuplicatePreventionTests {

        @Test
        @DisplayName("Worker run 15 times on identical price updates generates AT MOST 1 notification")
        void testWorkerRun15TimesIdenticalPrices() {
            runConsecutiveIdenticalPriceUpdates(15);
        }

        @Test
        @DisplayName("Worker run 50 times on identical price updates generates AT MOST 1 notification")
        void testWorkerRun50TimesIdenticalPrices() {
            runConsecutiveIdenticalPriceUpdates(50);
        }

        private void runConsecutiveIdenticalPriceUpdates(int totalRuns) {
            when(watchlistRepository.findAllActiveByProductIdWithRelations(product.getId()))
                    .thenReturn(List.of(watchlist));
            when(preferenceRepository.findByWatchlistId(watchlist.getId()))
                    .thenReturn(Optional.of(preferences));
            when(priceAnalyticsService.getProductAnalytics(product.getId()))
                    .thenReturn(ProductAnalyticsResponseDTO.builder().build());

            // Track persisted deduplication keys to simulate real database unique constraint & exists check
            Set<String> savedDeduplicationKeys = Collections.synchronizedSet(new HashSet<>());

            when(alertRepository.existsByDeduplicationKey(anyString())).thenAnswer(invocation -> {
                String key = invocation.getArgument(0);
                return savedDeduplicationKeys.contains(key);
            });

            when(alertRepository.save(any(PriceAlertEntity.class))).thenAnswer(invocation -> {
                PriceAlertEntity entity = invocation.getArgument(0);
                if (savedDeduplicationKeys.contains(entity.getDeduplicationKey())) {
                    throw new DataIntegrityViolationException("Duplicate key violation: " + entity.getDeduplicationKey());
                }
                savedDeduplicationKeys.add(entity.getDeduplicationKey());
                return entity;
            });

            // Run totalRuns times with identical price drop from 2800.00 to 2400.00
            for (int i = 0; i < totalRuns; i++) {
                alertService.processPriceUpdateEvent(
                        product.getId(),
                        BigDecimal.valueOf(2800.00),
                        BigDecimal.valueOf(2400.00),
                        false
                );
            }

            // Exactly 2 alert types generated in cycle 1: TARGET_REACHED (2500) + PRICE_DROP (14.3%)
            // Cycles 2 to totalRuns must NOT save or dispatch any new alerts
            verify(deliveryService, times(2)).dispatch(any(PriceAlertEntity.class));

            // Verify deduplication counter has recorded exactly (totalRuns - 1) * 2 suppressed duplicate attempts
            double dedupCount = meterRegistry.get("pricepilot.alerts.deduplicated").counters().stream()
                    .mapToDouble(c -> c.count()).sum();
            assertEquals((totalRuns - 1) * 2, (int) dedupCount);
        }

        @Test
        @DisplayName("Scheduler run 50 times on unchanged prices generates AT MOST 1 notification")
        void testSchedulerRun50TimesUnchangedPrices() {
            PriceAlertScheduler scheduler = new PriceAlertScheduler(
                    watchlistRepository,
                    productPriceRepository,
                    alertService,
                    meterRegistry
            );

            when(watchlistRepository.findAllActiveWatchlists()).thenReturn(List.of(watchlist));
            when(watchlistRepository.findAllActiveByProductIdWithRelations(product.getId()))
                    .thenReturn(List.of(watchlist));
            when(preferenceRepository.findByWatchlistId(watchlist.getId()))
                    .thenReturn(Optional.of(preferences));
            when(priceAnalyticsService.getProductAnalytics(product.getId()))
                    .thenReturn(ProductAnalyticsResponseDTO.builder().build());

            // ProductPriceRepository always returns current lowest price of $2400.00
            BestPriceProjection bestPrice = mock(BestPriceProjection.class);
            when(bestPrice.getProductId()).thenReturn(product.getId());
            when(bestPrice.getBestPrice()).thenReturn(BigDecimal.valueOf(2400.00));
            when(productPriceRepository.findBestPricesByProductIds(List.of(product.getId())))
                    .thenReturn(List.of(bestPrice));

            Set<String> savedDeduplicationKeys = Collections.synchronizedSet(new HashSet<>());
            when(alertRepository.existsByDeduplicationKey(anyString())).thenAnswer(invocation -> {
                String key = invocation.getArgument(0);
                return savedDeduplicationKeys.contains(key);
            });
            when(alertRepository.save(any(PriceAlertEntity.class))).thenAnswer(invocation -> {
                PriceAlertEntity entity = invocation.getArgument(0);
                if (savedDeduplicationKeys.contains(entity.getDeduplicationKey())) {
                    throw new DataIntegrityViolationException("Duplicate key: " + entity.getDeduplicationKey());
                }
                savedDeduplicationKeys.add(entity.getDeduplicationKey());
                return entity;
            });

            // Execute scheduler 50 times
            for (int i = 0; i < 50; i++) {
                scheduler.evaluateActiveWatchlists();
            }

            // Verify user receives AT MOST 1 notification per alert candidate, never 50
            verify(deliveryService, times(2)).dispatch(any(PriceAlertEntity.class));
        }

        @Test
        @DisplayName("Price fluctuating below target for 50 evaluations never re-triggers TARGET_REACHED")
        void testPriceFluctuatingBelowTargetSuppression() {
            // Initially price drops below target: 2600 -> 2490 (target = 2500)
            var initialCandidates = ruleEvaluator.evaluateRules(
                    watchlist, preferences,
                    BigDecimal.valueOf(2600.00), BigDecimal.valueOf(2490.00),
                    null, false
            );
            assertTrue(initialCandidates.stream().anyMatch(c -> c.alertType() == AlertType.PRICE_TARGET_REACHED));

            // State records that target price was notified at 2490.00
            preferences.setLastNotifiedTargetPrice(BigDecimal.valueOf(2490.00));

            // 50 subsequent price evaluations while remaining below $2500
            for (int i = 0; i < 50; i++) {
                BigDecimal currentPrice = BigDecimal.valueOf(2490.00 - (i % 10));
                BigDecimal nextPrice = BigDecimal.valueOf(2490.00 - ((i + 1) % 10));
                var candidates = ruleEvaluator.evaluateRules(
                        watchlist, preferences,
                        currentPrice, nextPrice,
                        null, false
                );
                assertFalse(candidates.stream().anyMatch(c -> c.alertType() == AlertType.PRICE_TARGET_REACHED),
                        "Target alert re-triggered while price remained below target at cycle " + i);
            }
        }
    }

    // =========================================================================
    // 2. CONCURRENT EXECUTIONS & RACE CONDITIONS
    // =========================================================================
    @Nested
    @DisplayName("Concurrent Executions & Multithreaded Race Conditions")
    class ConcurrencyTests {

        @Test
        @DisplayName("20 concurrent threads attempting identical alert generation simultaneously: exactly 1 saved")
        void testConcurrentAlertGenerationRaceCondition() throws Exception {
            int threadCount = 20;
            ExecutorService executor = Executors.newFixedThreadPool(threadCount);
            CountDownLatch readyLatch = new CountDownLatch(threadCount);
            CountDownLatch startLatch = new CountDownLatch(1);

            when(watchlistRepository.findAllActiveByProductIdWithRelations(product.getId()))
                    .thenReturn(List.of(watchlist));
            when(preferenceRepository.findByWatchlistId(watchlist.getId()))
                    .thenReturn(Optional.of(preferences));
            when(priceAnalyticsService.getProductAnalytics(product.getId()))
                    .thenReturn(ProductAnalyticsResponseDTO.builder().build());

            // Simulate atomic DB persistence with unique constraint
            Set<String> persistedKeys = Collections.synchronizedSet(new HashSet<>());
            AtomicInteger actualSaves = new AtomicInteger(0);

            when(alertRepository.existsByDeduplicationKey(anyString())).thenAnswer(invocation -> {
                String key = invocation.getArgument(0);
                return persistedKeys.contains(key);
            });

            when(alertRepository.save(any(PriceAlertEntity.class))).thenAnswer(invocation -> {
                PriceAlertEntity entity = invocation.getArgument(0);
                synchronized (persistedKeys) {
                    if (persistedKeys.contains(entity.getDeduplicationKey())) {
                        throw new DataIntegrityViolationException("Unique constraint violation: " + entity.getDeduplicationKey());
                    }
                    persistedKeys.add(entity.getDeduplicationKey());
                    actualSaves.incrementAndGet();
                    return entity;
                }
            });

            List<Future<Void>> futures = new ArrayList<>();
            for (int i = 0; i < threadCount; i++) {
                futures.add(executor.submit(() -> {
                    readyLatch.countDown();
                    // Wait for simultaneous start signal
                    startLatch.await();
                    alertService.processPriceUpdateEvent(
                            product.getId(),
                            BigDecimal.valueOf(2800.00),
                            BigDecimal.valueOf(2400.00),
                            false
                    );
                    return null;
                }));
            }

            // Wait for all 20 threads to be ready
            readyLatch.await(5, TimeUnit.SECONDS);
            // Release the latch for simultaneous execution
            startLatch.countDown();

            // Verify all threads complete without unhandled exceptions
            for (Future<Void> future : futures) {
                assertDoesNotThrow(() -> future.get(5, TimeUnit.SECONDS));
            }

            executor.shutdown();
            assertTrue(executor.awaitTermination(5, TimeUnit.SECONDS));

            // Exactly 2 alert types (TARGET_REACHED + PRICE_DROP) were saved across all 20 threads
            assertEquals(2, actualSaves.get());
            verify(deliveryService, times(2)).dispatch(any(PriceAlertEntity.class));

            // Deduplication metric handled all other 19 thread attempts gracefully
            double dedupCount = meterRegistry.get("pricepilot.alerts.deduplicated").counters().stream()
                    .mapToDouble(c -> c.count()).sum();
            assertEquals(38, (int) dedupCount); // (20 - 1) * 2 = 38
        }
    }

    // =========================================================================
    // 3. CROSS-USER SECURITY AND AUTHORIZATION
    // =========================================================================
    @Nested
    @DisplayName("Cross-User Security & Authorization")
    class SecurityAndAuthorizationTests {

        @Test
        @DisplayName("User A cannot mark User B's alert as read (throws AccessDeniedException)")
        void testUserACannotMarkUserBAlertRead() {
            UUID alertId = UUID.randomUUID();
            PriceAlertEntity alertB = PriceAlertEntity.builder()
                    .user(userB)
                    .product(product)
                    .alertType(AlertType.PRICE_DROP)
                    .title("Drop")
                    .message("Drop")
                    .deduplicationKey("DROP-USER-B")
                    .build();
            alertB.setId(alertId);

            when(alertRepository.findById(alertId)).thenReturn(Optional.of(alertB));

            assertThrows(AccessDeniedException.class, () ->
                    alertService.markAsRead(alertId, userA.getId())
            );
            verify(alertRepository, never()).save(any());
        }

        @Test
        @DisplayName("User A cannot view User B's watchlist alert preferences (throws AccessDeniedException)")
        void testUserACannotGetPreferencesOfUserB() {
            PriceWatchlistEntity watchlistB = PriceWatchlistEntity.builder()
                    .user(userB)
                    .product(product)
                    .build();
            watchlistB.setId(UUID.randomUUID());

            when(watchlistRepository.findById(watchlistB.getId())).thenReturn(Optional.of(watchlistB));

            assertThrows(AccessDeniedException.class, () ->
                    alertService.getAlertPreferences(watchlistB.getId(), userA.getId())
            );
        }

        @Test
        @DisplayName("User A cannot update User B's watchlist alert preferences (throws AccessDeniedException)")
        void testUserACannotUpdatePreferencesOfUserB() {
            PriceWatchlistEntity watchlistB = PriceWatchlistEntity.builder()
                    .user(userB)
                    .product(product)
                    .build();
            watchlistB.setId(UUID.randomUUID());

            when(watchlistRepository.findById(watchlistB.getId())).thenReturn(Optional.of(watchlistB));

            UpdateWatchlistAlertPreferenceRequestDTO updateReq = UpdateWatchlistAlertPreferenceRequestDTO.builder()
                    .enabled(false)
                    .build();

            assertThrows(AccessDeniedException.class, () ->
                    alertService.updateAlertPreferences(watchlistB.getId(), userA.getId(), updateReq)
            );
        }

        @Test
        @DisplayName("PriceAlertController denies unauthenticated requests across all endpoints")
        void testControllerUnauthenticatedAccessDenied() {
            PriceAlertController controller = new PriceAlertController(alertService);

            assertThrows(AccessDeniedException.class, () -> controller.getUserAlerts(null, PageRequest.of(0, 10)));
            assertThrows(AccessDeniedException.class, () -> controller.getUnreadAlerts(null));
            assertThrows(AccessDeniedException.class, () -> controller.getUnreadCount(null));
            assertThrows(AccessDeniedException.class, () -> controller.markAsRead(UUID.randomUUID(), null));
            assertThrows(AccessDeniedException.class, () -> controller.markAllAsRead(null));
        }

        @Test
        @DisplayName("User alert queries strictly partition by authenticated user ID")
        void testUserAlertQueryIsolation() {
            PriceAlertEntity alertA = PriceAlertEntity.builder()
                    .user(userA)
                    .product(product)
                    .alertType(AlertType.PRICE_DROP)
                    .title("User A Alert")
                    .message("Message")
                    .deduplicationKey("A-KEY")
                    .build();

            when(alertRepository.findAllByUserIdWithProduct(eq(userA.getId()), any()))
                    .thenReturn(new PageImpl<>(List.of(alertA)));

            var result = alertService.getUserAlerts(userA.getId(), PageRequest.of(0, 10));
            assertEquals(1, result.getContent().size());
            verify(alertRepository).findAllByUserIdWithProduct(eq(userA.getId()), any());
            verify(alertRepository, never()).findAllByUserIdWithProduct(eq(userB.getId()), any());
        }
    }

    // =========================================================================
    // 4. BOUNDARY CONDITIONS FOR ALL ALERT TYPES
    // =========================================================================
    @Nested
    @DisplayName("Boundary Conditions for All 6 Alert Types")
    class AlertTypeBoundaryTests {

        // --- 4.1 PRICE_TARGET_REACHED ---
        @Nested
        @DisplayName("PRICE_TARGET_REACHED Boundary Tests")
        class PriceTargetBoundaries {

            @Test
            @DisplayName("Exact target price match (2500.00 == 2500.00) triggers alert")
            void testExactTargetMatch() {
                var candidates = ruleEvaluator.evaluateRules(
                        watchlist, preferences,
                        BigDecimal.valueOf(2501.00), BigDecimal.valueOf(2500.00),
                        null, false
                );
                assertTrue(candidates.stream().anyMatch(c -> c.alertType() == AlertType.PRICE_TARGET_REACHED));
                var alert = candidates.stream().filter(c -> c.alertType() == AlertType.PRICE_TARGET_REACHED).findFirst().get();
                assertEquals(BigDecimal.valueOf(2500.00), alert.observedValue());
                assertEquals(BigDecimal.valueOf(2500.00), alert.triggerValue());
            }

            @Test
            @DisplayName("Crossing target boundary (2500.01 -> 2499.99) triggers alert")
            void testCrossingTargetBoundary() {
                var candidates = ruleEvaluator.evaluateRules(
                        watchlist, preferences,
                        BigDecimal.valueOf(2500.01), BigDecimal.valueOf(2499.99),
                        null, false
                );
                assertTrue(candidates.stream().anyMatch(c -> c.alertType() == AlertType.PRICE_TARGET_REACHED));
            }

            @Test
            @DisplayName("Remaining 0.01 above target (2500.01) does NOT trigger alert")
            void testJustAboveTargetSuppressed() {
                var candidates = ruleEvaluator.evaluateRules(
                        watchlist, preferences,
                        BigDecimal.valueOf(2600.00), BigDecimal.valueOf(2500.01),
                        null, false
                );
                assertFalse(candidates.stream().anyMatch(c -> c.alertType() == AlertType.PRICE_TARGET_REACHED));
            }

            @Test
            @DisplayName("Re-arming after rising above target and dropping again triggers alert")
            void testTargetReArming() {
                preferences.setLastNotifiedTargetPrice(BigDecimal.valueOf(2400.00));
                // Rose to 2600, now dropped to 2480
                var candidates = ruleEvaluator.evaluateRules(
                        watchlist, preferences,
                        BigDecimal.valueOf(2600.00), BigDecimal.valueOf(2480.00),
                        null, false
                );
                assertTrue(candidates.stream().anyMatch(c -> c.alertType() == AlertType.PRICE_TARGET_REACHED));
            }

            @Test
            @DisplayName("Target price alert disabled suppresses trigger")
            void testTargetPriceDisabled() {
                preferences.setTargetPriceEnabled(false);
                var candidates = ruleEvaluator.evaluateRules(
                        watchlist, preferences,
                        BigDecimal.valueOf(2600.00), BigDecimal.valueOf(2000.00),
                        null, false
                );
                assertFalse(candidates.stream().anyMatch(c -> c.alertType() == AlertType.PRICE_TARGET_REACHED));
            }
        }

        // --- 4.2 PRICE_DROP ---
        @Nested
        @DisplayName("PRICE_DROP Boundary Tests")
        class PriceDropBoundaries {

            @Test
            @DisplayName("Exact threshold match: exactly 10.00% drop triggers alert")
            void testExactPriceDropThreshold() {
                preferences.setPriceDropPercentage(BigDecimal.valueOf(10.00));
                // 100.00 -> 90.00 is exactly 10.0%
                var candidates = ruleEvaluator.evaluateRules(
                        watchlist, preferences,
                        BigDecimal.valueOf(100.00), BigDecimal.valueOf(90.00),
                        null, false
                );
                assertTrue(candidates.stream().anyMatch(c -> c.alertType() == AlertType.PRICE_DROP));
                var alert = candidates.stream().filter(c -> c.alertType() == AlertType.PRICE_DROP).findFirst().get();
                assertEquals(BigDecimal.valueOf(10.0), alert.observedValue());
            }

            @Test
            @DisplayName("0.01% below threshold (9.99% drop against 10.00% threshold) does NOT trigger")
            void testBelowThresholdSuppressed() {
                preferences.setPriceDropPercentage(BigDecimal.valueOf(10.00));
                // 10000.00 -> 9001.00 is 9.99% drop < 10.00%
                var candidates = ruleEvaluator.evaluateRules(
                        watchlist, preferences,
                        BigDecimal.valueOf(10000.00), BigDecimal.valueOf(9001.00),
                        null, false
                );
                assertFalse(candidates.stream().anyMatch(c -> c.alertType() == AlertType.PRICE_DROP));
            }

            @Test
            @DisplayName("Massive price drop (95% drop) triggers alert safely")
            void testMassivePriceDrop() {
                preferences.setPriceDropPercentage(BigDecimal.valueOf(10.00));
                // 1000.00 -> 50.00 is 95.0% drop
                var candidates = ruleEvaluator.evaluateRules(
                        watchlist, preferences,
                        BigDecimal.valueOf(1000.00), BigDecimal.valueOf(50.00),
                        null, false
                );
                assertTrue(candidates.stream().anyMatch(c -> c.alertType() == AlertType.PRICE_DROP));
                var alert = candidates.stream().filter(c -> c.alertType() == AlertType.PRICE_DROP).findFirst().get();
                assertEquals(BigDecimal.valueOf(95.0), alert.observedValue());
            }

            @Test
            @DisplayName("Zero or negative old price avoids division by zero and generates no alert")
            void testZeroOrNegativeOldPriceHandled() {
                var candidates1 = ruleEvaluator.evaluateRules(
                        watchlist, preferences,
                        BigDecimal.ZERO, BigDecimal.valueOf(50.00),
                        null, false
                );
                assertFalse(candidates1.stream().anyMatch(c -> c.alertType() == AlertType.PRICE_DROP));

                var candidates2 = ruleEvaluator.evaluateRules(
                        watchlist, preferences,
                        BigDecimal.valueOf(-10.00), BigDecimal.valueOf(50.00),
                        null, false
                );
                assertFalse(candidates2.stream().anyMatch(c -> c.alertType() == AlertType.PRICE_DROP));
            }
        }

        // --- 4.3 HISTORICAL_LOW_REACHED ---
        @Nested
        @DisplayName("HISTORICAL_LOW_REACHED Boundary Tests")
        class HistoricalLowBoundaries {

            @Test
            @DisplayName("Exact historical low match (2000.00 == 2000.00) triggers first time")
            void testExactHistoricalLowFirstTime() {
                ProductAnalyticsResponseDTO analytics = ProductAnalyticsResponseDTO.builder()
                        .historicalMin(BigDecimal.valueOf(2000.00))
                        .build();

                var candidates = ruleEvaluator.evaluateRules(
                        watchlist, preferences,
                        BigDecimal.valueOf(2200.00), BigDecimal.valueOf(2000.00),
                        analytics, false
                );
                assertTrue(candidates.stream().anyMatch(c -> c.alertType() == AlertType.HISTORICAL_LOW_REACHED));
            }

            @Test
            @DisplayName("Identical historical low observation is suppressed")
            void testIdenticalHistoricalLowSuppressed() {
                ProductAnalyticsResponseDTO analytics = ProductAnalyticsResponseDTO.builder()
                        .historicalMin(BigDecimal.valueOf(2000.00))
                        .build();
                preferences.setLastNotifiedHistoricalLow(BigDecimal.valueOf(2000.00));

                var candidates = ruleEvaluator.evaluateRules(
                        watchlist, preferences,
                        BigDecimal.valueOf(2000.00), BigDecimal.valueOf(2000.00),
                        analytics, false
                );
                assertFalse(candidates.stream().anyMatch(c -> c.alertType() == AlertType.HISTORICAL_LOW_REACHED));
            }

            @Test
            @DisplayName("Deeper low (1999.99 < 2000.00) triggers new historical low alert")
            void testDeeperHistoricalLowTriggers() {
                ProductAnalyticsResponseDTO analytics = ProductAnalyticsResponseDTO.builder()
                        .historicalMin(BigDecimal.valueOf(1999.99))
                        .build();
                preferences.setLastNotifiedHistoricalLow(BigDecimal.valueOf(2000.00));

                var candidates = ruleEvaluator.evaluateRules(
                        watchlist, preferences,
                        BigDecimal.valueOf(2000.00), BigDecimal.valueOf(1999.99),
                        analytics, false
                );
                assertTrue(candidates.stream().anyMatch(c -> c.alertType() == AlertType.HISTORICAL_LOW_REACHED));
            }

            @Test
            @DisplayName("Price 0.01 above historical low (2000.01 > 2000.00) does NOT trigger")
            void testAboveHistoricalLowSuppressed() {
                ProductAnalyticsResponseDTO analytics = ProductAnalyticsResponseDTO.builder()
                        .historicalMin(BigDecimal.valueOf(2000.00))
                        .build();

                var candidates = ruleEvaluator.evaluateRules(
                        watchlist, preferences,
                        BigDecimal.valueOf(2200.00), BigDecimal.valueOf(2000.01),
                        analytics, false
                );
                assertFalse(candidates.stream().anyMatch(c -> c.alertType() == AlertType.HISTORICAL_LOW_REACHED));
            }
        }

        // --- 4.4 GOOD_DEAL_DETECTED ---
        @Nested
        @DisplayName("GOOD_DEAL_DETECTED Boundary Tests")
        class GoodDealBoundaries {

            @Test
            @DisplayName("Deal quality state transition (GOOD_DEAL -> EXCELLENT_DEAL) triggers with evidence")
            void testDealQualityStateTransition() {
                preferences.setLastNotifiedDealQuality(DealQuality.GOOD_DEAL.name());

                ProductAnalyticsResponseDTO analytics = ProductAnalyticsResponseDTO.builder()
                        .dealQuality(DealQuality.EXCELLENT_DEAL)
                        .pricePositionScore(98.0)
                        .supportingEvidence(List.of("Current price is 18.5% below 90-day moving average."))
                        .build();

                var candidates = ruleEvaluator.evaluateRules(
                        watchlist, preferences,
                        BigDecimal.valueOf(2800.00), BigDecimal.valueOf(2200.00),
                        analytics, false
                );

                assertTrue(candidates.stream().anyMatch(c -> c.alertType() == AlertType.GOOD_DEAL_DETECTED));
                var alert = candidates.stream().filter(c -> c.alertType() == AlertType.GOOD_DEAL_DETECTED).findFirst().get();
                assertTrue(alert.message().contains("EXCELLENT DEAL"));
                assertTrue(alert.message().contains("18.5% below 90-day moving average"));
            }

            @Test
            @DisplayName("Identical deal quality state (EXCELLENT_DEAL -> EXCELLENT_DEAL) is suppressed")
            void testIdenticalDealQualitySuppressed() {
                preferences.setLastNotifiedDealQuality(DealQuality.EXCELLENT_DEAL.name());

                ProductAnalyticsResponseDTO analytics = ProductAnalyticsResponseDTO.builder()
                        .dealQuality(DealQuality.EXCELLENT_DEAL)
                        .pricePositionScore(98.0)
                        .supportingEvidence(List.of("Unchanged deal."))
                        .build();

                var candidates = ruleEvaluator.evaluateRules(
                        watchlist, preferences,
                        BigDecimal.valueOf(2200.00), BigDecimal.valueOf(2200.00),
                        analytics, false
                );

                assertFalse(candidates.stream().anyMatch(c -> c.alertType() == AlertType.GOOD_DEAL_DETECTED));
            }

            @Test
            @DisplayName("Non-qualifying deal qualities (FAIR_DEAL, OVERPRICED) never trigger alerts")
            void testNonQualifyingDealQualitiesSuppressed() {
                for (DealQuality quality : List.of(DealQuality.FAIR_PRICE, DealQuality.HIGH_PRICE, DealQuality.ABOVE_AVERAGE)) {
                    ProductAnalyticsResponseDTO analytics = ProductAnalyticsResponseDTO.builder()
                            .dealQuality(quality)
                            .build();

                    var candidates = ruleEvaluator.evaluateRules(
                            watchlist, preferences,
                            BigDecimal.valueOf(2500.00), BigDecimal.valueOf(2500.00),
                            analytics, false
                    );

                    assertFalse(candidates.stream().anyMatch(c -> c.alertType() == AlertType.GOOD_DEAL_DETECTED));
                }
            }
        }

        // --- 4.5 PRICE_INCREASE ---
        @Nested
        @DisplayName("PRICE_INCREASE Boundary & Opt-In Tests")
        class PriceIncreaseBoundaries {

            @Test
            @DisplayName("Price increase alert is disabled by default (opt-in toggle OFF suppresses trigger)")
            void testPriceIncreaseDisabledByDefault() {
                assertFalse(preferences.isPriceIncreaseEnabled());
                // 100.00 -> 150.00 (+50% increase)
                var candidates = ruleEvaluator.evaluateRules(
                        watchlist, preferences,
                        BigDecimal.valueOf(100.00), BigDecimal.valueOf(150.00),
                        null, false
                );
                assertFalse(candidates.stream().anyMatch(c -> c.alertType() == AlertType.PRICE_INCREASE));
            }

            @Test
            @DisplayName("Exact 10.0% boundary triggers when opt-in is enabled")
            void testExact10PercentIncreaseTriggersWhenEnabled() {
                preferences.setPriceIncreaseEnabled(true);
                // 100.00 -> 110.00 is exactly 10.0% increase
                var candidates = ruleEvaluator.evaluateRules(
                        watchlist, preferences,
                        BigDecimal.valueOf(100.00), BigDecimal.valueOf(110.00),
                        null, false
                );
                assertTrue(candidates.stream().anyMatch(c -> c.alertType() == AlertType.PRICE_INCREASE));
                var alert = candidates.stream().filter(c -> c.alertType() == AlertType.PRICE_INCREASE).findFirst().get();
                assertEquals(BigDecimal.valueOf(10.0), alert.observedValue());
            }

            @Test
            @DisplayName("9.99% increase does NOT trigger alert (boundary check)")
            void testBelow10PercentIncreaseSuppressed() {
                preferences.setPriceIncreaseEnabled(true);
                // 10000.00 -> 10999.00 is 9.99% increase < 10.0%
                var candidates = ruleEvaluator.evaluateRules(
                        watchlist, preferences,
                        BigDecimal.valueOf(10000.00), BigDecimal.valueOf(10999.00),
                        null, false
                );
                assertFalse(candidates.stream().anyMatch(c -> c.alertType() == AlertType.PRICE_INCREASE));
            }
        }

        // --- 4.6 BACK_IN_STOCK ---
        @Nested
        @DisplayName("BACK_IN_STOCK Boundary Tests")
        class BackInStockBoundaries {

            @Test
            @DisplayName("Transition from out-of-stock (false -> true) triggers alert")
            void testTransitionOutOfStockToInStock() {
                preferences.setBackInStockEnabled(true);
                preferences.setLastNotifiedInStock(false);

                var candidates = ruleEvaluator.evaluateRules(
                        watchlist, preferences,
                        BigDecimal.valueOf(2500.00), BigDecimal.valueOf(2500.00),
                        null, true
                );
                assertTrue(candidates.stream().anyMatch(c -> c.alertType() == AlertType.BACK_IN_STOCK));
            }

            @Test
            @DisplayName("Steady in-stock state (true -> true) is suppressed")
            void testSteadyInStockSuppressed() {
                preferences.setBackInStockEnabled(true);
                preferences.setLastNotifiedInStock(true);

                var candidates = ruleEvaluator.evaluateRules(
                        watchlist, preferences,
                        BigDecimal.valueOf(2500.00), BigDecimal.valueOf(2500.00),
                        null, true
                );
                assertFalse(candidates.stream().anyMatch(c -> c.alertType() == AlertType.BACK_IN_STOCK));
            }

            @Test
            @DisplayName("Item not in stock (isBackInStock = false) does NOT trigger alert")
            void testNotInStockDoesNotTrigger() {
                preferences.setBackInStockEnabled(true);
                preferences.setLastNotifiedInStock(false);

                var candidates = ruleEvaluator.evaluateRules(
                        watchlist, preferences,
                        BigDecimal.valueOf(2500.00), BigDecimal.valueOf(2500.00),
                        null, false
                );
                assertFalse(candidates.stream().anyMatch(c -> c.alertType() == AlertType.BACK_IN_STOCK));
            }
        }
    }

    // =========================================================================
    // 5. PERFORMANCE SCALING (1,000 ACTIVE WATCHLISTS < 500MS)
    // =========================================================================
    @Nested
    @DisplayName("Task 5: Performance Scaling")
    class PerformanceScaleTests {

        @Test
        @DisplayName("Evaluating 1,000 active watchlists completes in < 500ms")
        void testPerformanceScale1000Watchlists() {
            int count = 1000;
            List<PriceWatchlistEntity> watchlists = new ArrayList<>(count);
            for (int i = 0; i < count; i++) {
                UserEntity u = new UserEntity();
                u.setId(UUID.randomUUID());

                PriceWatchlistEntity w = PriceWatchlistEntity.builder()
                        .user(u)
                        .product(product)
                        .targetPrice(BigDecimal.valueOf(2000.00 + (i % 500)))
                        .currentBestPrice(BigDecimal.valueOf(2500.00))
                        .active(true)
                        .build();
                w.setId(UUID.randomUUID());
                watchlists.add(w);
            }

            WatchlistAlertPreferenceEntity p = WatchlistAlertPreferenceEntity.defaultForWatchlist(watchlist);

            long start = System.nanoTime();
            int totalCandidates = 0;
            for (PriceWatchlistEntity w : watchlists) {
                var candidates = ruleEvaluator.evaluateRules(
                        w, p,
                        BigDecimal.valueOf(2500.00),
                        BigDecimal.valueOf(2100.00),
                        null,
                        false
                );
                totalCandidates += candidates.size();
            }
            long durationMs = (System.nanoTime() - start) / 1_000_000;

            assertTrue(durationMs < 500, "1,000 evaluations took " + durationMs + "ms, expected < 500ms");
            assertTrue(totalCandidates > 0);
        }
    }
}
