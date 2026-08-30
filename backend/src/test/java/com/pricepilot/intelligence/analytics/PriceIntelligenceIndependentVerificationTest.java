package com.pricepilot.intelligence.analytics;

import com.pricepilot.intelligence.analytics.engine.*;
import com.pricepilot.intelligence.analytics.model.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Independent Verification Test Suite for Phase 4: Advanced Price Intelligence.
 * Validates statistical rigor, trend classification, purchase signal integrity,
 * data boundary edge cases, and algorithmic performance at scale.
 */
public class PriceIntelligenceIndependentVerificationTest {

    private HistoricalDataNormalizer normalizer;
    private StatisticalPriceAnalyzer statisticalAnalyzer;
    private PriceTrendAnalyzer trendAnalyzer;
    private PricePositionAnalyzer positionAnalyzer;
    private PurchaseSignalGenerator signalGenerator;
    private PriceEventDetector eventDetector;
    private AdvancedPriceAnalyticsEngine engine;

    @BeforeEach
    void setUp() {
        normalizer = new HistoricalDataNormalizer();
        statisticalAnalyzer = new StatisticalPriceAnalyzer();
        trendAnalyzer = new PriceTrendAnalyzer();
        positionAnalyzer = new PricePositionAnalyzer();
        signalGenerator = new PurchaseSignalGenerator();
        eventDetector = new PriceEventDetector();
        engine = new AdvancedPriceAnalyticsEngine(
                normalizer, statisticalAnalyzer, trendAnalyzer, positionAnalyzer, signalGenerator, eventDetector
        );
    }

    private HistoricalPricePoint pt(double price, int dayOffset) {
        return HistoricalPricePoint.builder()
                .price(BigDecimal.valueOf(price).setScale(2, RoundingMode.HALF_UP))
                .timestamp(LocalDateTime.of(2026, 8, 1, 12, 0).plusDays(dayOffset))
                .sellerName("Verified Merchant")
                .sellerId(UUID.randomUUID())
                .build();
    }

    // =========================================================================
    // A. Statistical Correctness
    // =========================================================================
    @Nested
    @DisplayName("A. Statistical Correctness Verification")
    class StatisticalCorrectnessVerification {

        @Test
        @DisplayName("Verify hand-calculated 3-element dataset [100.00, 200.00, 300.00]")
        void testHandCalculatedDataset1() {
            List<HistoricalPricePoint> series = List.of(pt(100.00, 1), pt(200.00, 2), pt(300.00, 3));
            BigDecimal currentPrice = BigDecimal.valueOf(150.00);

            PriceStatistics stats = statisticalAnalyzer.calculateStatistics(currentPrice, series);

            // Min = 100.00, Max = 300.00, Range = 200.00
            assertEquals(0, BigDecimal.valueOf(100.00).compareTo(stats.getHistoricalMin()));
            assertEquals(0, BigDecimal.valueOf(300.00).compareTo(stats.getHistoricalMax()));
            assertEquals(0, BigDecimal.valueOf(200.00).compareTo(stats.getPriceRange()));

            // Average = (100 + 200 + 300) / 3 = 200.00
            assertEquals(0, BigDecimal.valueOf(200.00).compareTo(stats.getHistoricalAvg()));

            // Median = 200.00 (middle element of 3)
            assertEquals(0, BigDecimal.valueOf(200.00).compareTo(stats.getHistoricalMedian()));

            // Low distance = 150.00 - 100.00 = 50.00
            assertEquals(0, BigDecimal.valueOf(50.00).compareTo(stats.getHistoricalLowDistance()));

            // Avg distance = 150.00 - 200.00 = -50.00
            assertEquals(0, BigDecimal.valueOf(-50.00).compareTo(stats.getHistoricalAverageDistance()));

            // Volatility hand calculation:
            // mean = 200, sum((x - 200)^2) = 100^2 + 0 + 100^2 = 20000
            // sample variance = 20000 / (3 - 1) = 10000
            // sample stddev = sqrt(10000) = 100.0
            // CV = 100.0 / 200.0 = 0.50 (>= 0.15 -> HIGH)
            var vol = statisticalAnalyzer.calculateVolatility(series);
            assertEquals(PriceVolatility.HIGH, vol.classification());
            assertEquals(0.50, vol.value(), 0.001);
        }

        @Test
        @DisplayName("Verify hand-calculated 4-element dataset [100.00, 101.00, 102.00, 103.00]")
        void testHandCalculatedDataset2() {
            List<HistoricalPricePoint> series = List.of(pt(100.00, 1), pt(101.00, 2), pt(102.00, 3), pt(103.00, 4));

            PriceStatistics stats = statisticalAnalyzer.calculateStatistics(BigDecimal.valueOf(102.00), series);

            // Average = (100 + 101 + 102 + 103) / 4 = 101.50
            assertEquals(0, BigDecimal.valueOf(101.50).compareTo(stats.getHistoricalAvg()));

            // Even count median = (101.00 + 102.00) / 2 = 101.50
            assertEquals(0, BigDecimal.valueOf(101.50).compareTo(stats.getHistoricalMedian()));

            // Volatility hand calculation:
            // mean = 101.5, diffs = [-1.5, -0.5, 0.5, 1.5]
            // sum of squared diffs = 2.25 + 0.25 + 0.25 + 2.25 = 5.0
            // sample variance = 5.0 / 3 = 1.6667
            // sample stddev = 1.29099
            // CV = 1.29099 / 101.5 = 0.0127 (1.27% < 5% -> LOW)
            var vol = statisticalAnalyzer.calculateVolatility(series);
            assertEquals(PriceVolatility.LOW, vol.classification());
            assertEquals(0.0127, vol.value(), 0.001);
        }
    }

    // =========================================================================
    // B. Trend Correctness
    // =========================================================================
    @Nested
    @DisplayName("B. Trend Correctness Verification")
    class TrendCorrectnessVerification {

        @Test
        @DisplayName("Sequence 100 -> 102 -> 105 -> 110 must produce RISING trend")
        void testKnownRisingSequence() {
            List<HistoricalPricePoint> series = List.of(pt(100.0, 1), pt(102.0, 2), pt(105.0, 3), pt(110.0, 4));
            var result = trendAnalyzer.analyzeTrend(series);

            assertEquals(PriceTrend.RISING, result.trend());
            assertTrue(result.percentageChange() > 2.0, "Expected > 2.0%, got " + result.percentageChange());
        }

        @Test
        @DisplayName("Sequence 110 -> 105 -> 102 -> 100 must produce FALLING trend")
        void testKnownFallingSequence() {
            List<HistoricalPricePoint> series = List.of(pt(110.0, 1), pt(105.0, 2), pt(102.0, 3), pt(100.0, 4));
            var result = trendAnalyzer.analyzeTrend(series);

            assertEquals(PriceTrend.FALLING, result.trend());
            assertTrue(result.percentageChange() < -2.0, "Expected < -2.0%, got " + result.percentageChange());
        }

        @Test
        @DisplayName("Sequence 100 -> 100 -> 100 -> 100 must produce STABLE trend")
        void testKnownStableSequence() {
            List<HistoricalPricePoint> series = List.of(pt(100.0, 1), pt(100.0, 2), pt(100.0, 3), pt(100.0, 4));
            var result = trendAnalyzer.analyzeTrend(series);

            assertEquals(PriceTrend.STABLE, result.trend());
            assertEquals(0.0, result.percentageChange());
        }

        @Test
        @DisplayName("Insufficient observations (< 3) produce INSUFFICIENT_DATA without failing")
        void testInsufficientObservationsTrend() {
            var res0 = trendAnalyzer.analyzeTrend(List.of());
            assertEquals(PriceTrend.INSUFFICIENT_DATA, res0.trend());

            var res1 = trendAnalyzer.analyzeTrend(List.of(pt(100.0, 1)));
            assertEquals(PriceTrend.INSUFFICIENT_DATA, res1.trend());

            var res2 = trendAnalyzer.analyzeTrend(List.of(pt(100.0, 1), pt(120.0, 2)));
            assertEquals(PriceTrend.INSUFFICIENT_DATA, res2.trend());
        }
    }

    // =========================================================================
    // C. Purchase-Signal Integrity
    // =========================================================================
    @Nested
    @DisplayName("C. Purchase Signal & Evidence Integrity Verification")
    class PurchaseSignalIntegrityVerification {

        @Test
        @DisplayName("When current price = historical low, signal is BUY_NOW with grounded evidence")
        void testSignalAtHistoricalLow() {
            List<RawPriceObservation> raw = List.of(
                    new RawPriceObservation(BigDecimal.valueOf(200.0), LocalDateTime.now().minusDays(3), UUID.randomUUID(), "Seller A"),
                    new RawPriceObservation(BigDecimal.valueOf(150.0), LocalDateTime.now().minusDays(2), UUID.randomUUID(), "Seller B"),
                    new RawPriceObservation(BigDecimal.valueOf(100.0), LocalDateTime.now().minusDays(1), UUID.randomUUID(), "Seller C")
            );

            // Current price is at all-time low
            var result = engine.analyze(BigDecimal.valueOf(100.0), raw);

            assertEquals(DealQuality.EXCELLENT_DEAL, result.position().classification());
            assertNotNull(result.signal().signal());
            assertTrue(result.signal().signal() == PurchaseSignal.BUY_NOW || result.signal().signal() == PurchaseSignal.GOOD_TIME);

            // Verify evidence strictly cites historical low and contains no future certainty claims
            List<String> evidence = result.signal().evidence();
            assertFalse(evidence.isEmpty());
            assertTrue(evidence.stream().anyMatch(e -> e.contains("all-time low")), "Evidence should cite all-time low");

            for (String claim : evidence) {
                assertFalse(claim.toLowerCase().contains("will rise"), "Must not predict future certainty");
                assertFalse(claim.toLowerCase().contains("guaranteed"), "Must not guarantee price movements");
            }
        }

        @Test
        @DisplayName("When current price = historical average, signal is NEUTRAL with average evidence")
        void testSignalAtHistoricalAverage() {
            List<RawPriceObservation> raw = List.of(
                    new RawPriceObservation(BigDecimal.valueOf(100.0), LocalDateTime.now().minusDays(3), UUID.randomUUID(), "Seller A"),
                    new RawPriceObservation(BigDecimal.valueOf(150.0), LocalDateTime.now().minusDays(2), UUID.randomUUID(), "Seller B"),
                    new RawPriceObservation(BigDecimal.valueOf(200.0), LocalDateTime.now().minusDays(1), UUID.randomUUID(), "Seller C")
            );

            // Current price is at average (150.0)
            var result = engine.analyze(BigDecimal.valueOf(150.0), raw);

            assertEquals(DealQuality.FAIR_PRICE, result.position().classification());
            assertEquals(PurchaseSignal.NEUTRAL, result.signal().signal());
            assertTrue(result.signal().reason().contains("typical historical market averages"));
            assertTrue(result.signal().evidence().stream().anyMatch(e -> e.contains("matches the historical average") || e.contains("average")));
        }

        @Test
        @DisplayName("When current price > historical average (near peak), signal is WAIT")
        void testSignalAboveHistoricalAverage() {
            List<RawPriceObservation> raw = List.of(
                    new RawPriceObservation(BigDecimal.valueOf(100.0), LocalDateTime.now().minusDays(3), UUID.randomUUID(), "Seller A"),
                    new RawPriceObservation(BigDecimal.valueOf(120.0), LocalDateTime.now().minusDays(2), UUID.randomUUID(), "Seller B"),
                    new RawPriceObservation(BigDecimal.valueOf(140.0), LocalDateTime.now().minusDays(1), UUID.randomUUID(), "Seller C")
            );

            // Current price is 199.0 (far above historical range)
            var result = engine.analyze(BigDecimal.valueOf(199.0), raw);

            assertEquals(PurchaseSignal.WAIT, result.signal().signal());
            assertTrue(result.signal().reason().contains("waiting"));
            assertTrue(result.signal().evidence().stream().anyMatch(e -> e.contains("above the historical average")));
        }

        @Test
        @DisplayName("When history is insufficient (< 2), signal is INSUFFICIENT_DATA with empty evidence")
        void testSignalInsufficientHistory() {
            List<RawPriceObservation> raw = List.of(
                    new RawPriceObservation(BigDecimal.valueOf(100.0), LocalDateTime.now(), UUID.randomUUID(), "Seller A")
            );

            var result = engine.analyze(BigDecimal.valueOf(100.0), raw);

            assertEquals(PurchaseSignal.INSUFFICIENT_DATA, result.signal().signal());
            assertEquals(DealQuality.INSUFFICIENT_DATA, result.position().classification());
            assertTrue(result.signal().evidence().isEmpty());
        }
    }

    // =========================================================================
    // D. Data Integrity & Robustness
    // =========================================================================
    @Nested
    @DisplayName("D. Data Integrity & Boundary Verification")
    class DataIntegrityVerification {

        @Test
        @DisplayName("Rejects null prices, negative prices, and preserves valid observations")
        void testNullAndNegativePrices() {
            LocalDateTime now = LocalDateTime.now();
            List<RawPriceObservation> raw = List.of(
                    new RawPriceObservation(null, now.minusDays(4), UUID.randomUUID(), "Seller 1"),
                    new RawPriceObservation(BigDecimal.valueOf(-50.0), now.minusDays(3), UUID.randomUUID(), "Seller 2"),
                    new RawPriceObservation(BigDecimal.ZERO, now.minusDays(2), UUID.randomUUID(), "Seller 3"),
                    new RawPriceObservation(BigDecimal.valueOf(150.0), now.minusDays(1), UUID.randomUUID(), "Seller 4")
            );

            var result = engine.analyze(BigDecimal.valueOf(150.0), raw);

            assertEquals(1, result.normalizedSeries().size());
            assertEquals(0, BigDecimal.valueOf(150.0).compareTo(result.normalizedSeries().get(0).getPrice()));
            assertEquals(1, result.statistics().getObservationCount());
        }

        @Test
        @DisplayName("Sorts chronologically when input records are out-of-order")
        void testOutOfOrderTimestamps() {
            LocalDateTime now = LocalDateTime.now();
            List<RawPriceObservation> raw = List.of(
                    new RawPriceObservation(BigDecimal.valueOf(300.0), now.plusDays(3), UUID.randomUUID(), "Seller 3"),
                    new RawPriceObservation(BigDecimal.valueOf(100.0), now.plusDays(1), UUID.randomUUID(), "Seller 1"),
                    new RawPriceObservation(BigDecimal.valueOf(200.0), now.plusDays(2), UUID.randomUUID(), "Seller 2")
            );

            var result = engine.analyze(BigDecimal.valueOf(300.0), raw);

            List<HistoricalPricePoint> series = result.normalizedSeries();
            assertEquals(3, series.size());
            assertTrue(series.get(0).getTimestamp().isBefore(series.get(1).getTimestamp()));
            assertTrue(series.get(1).getTimestamp().isBefore(series.get(2).getTimestamp()));
            assertEquals(BigDecimal.valueOf(100.0), series.get(0).getPrice());
            assertEquals(BigDecimal.valueOf(200.0), series.get(1).getPrice());
            assertEquals(BigDecimal.valueOf(300.0), series.get(2).getPrice());
        }

        @Test
        @DisplayName("Handles identical prices across all observations without division-by-zero")
        void testIdenticalPrices() {
            LocalDateTime now = LocalDateTime.now();
            List<RawPriceObservation> raw = List.of(
                    new RawPriceObservation(BigDecimal.valueOf(99.99), now.minusDays(2), UUID.randomUUID(), "Seller A"),
                    new RawPriceObservation(BigDecimal.valueOf(99.99), now.minusDays(1), UUID.randomUUID(), "Seller B"),
                    new RawPriceObservation(BigDecimal.valueOf(99.99), now, UUID.randomUUID(), "Seller C")
            );

            var result = engine.analyze(BigDecimal.valueOf(99.99), raw);

            assertEquals(0, BigDecimal.valueOf(0.0).compareTo(result.statistics().getPriceRange()));
            assertEquals(PriceVolatility.LOW, result.volatility().classification());
            assertEquals(PriceTrend.STABLE, result.trend().trend());
            assertEquals(DealQuality.FAIR_PRICE, result.position().classification());
            assertEquals(50.0, result.position().score());
        }

        @Test
        @DisplayName("Handles missing seller information gracefully without NPE")
        void testMissingSellerInformation() {
            List<RawPriceObservation> raw = List.of(
                    new RawPriceObservation(BigDecimal.valueOf(100.0), LocalDateTime.now(), null, null)
            );

            var result = engine.analyze(BigDecimal.valueOf(100.0), raw);

            assertNotNull(result.normalizedSeries().get(0).getSellerName());
            assertEquals("Market Seller", result.normalizedSeries().get(0).getSellerName());
            assertNull(result.normalizedSeries().get(0).getSellerId());
        }
    }

    // =========================================================================
    // E. Performance Verification
    // =========================================================================
    @Nested
    @DisplayName("E. Performance & Scale Verification")
    class PerformanceVerification {

        @Test
        @DisplayName("Large price history (1,000 points) executes pipeline in < 50ms")
        void testLargePriceHistoryBenchmark() {
            LocalDateTime baseTime = LocalDateTime.of(2023, 1, 1, 0, 0);
            List<RawPriceObservation> largeHistory = new ArrayList<>(1000);

            double price = 500.0;
            for (int i = 0; i < 1000; i++) {
                price += (Math.sin(i * 0.1) * 5.0) + (i % 2 == 0 ? -1.0 : 1.0);
                largeHistory.add(new RawPriceObservation(
                        BigDecimal.valueOf(Math.max(10.0, price)).setScale(2, RoundingMode.HALF_UP),
                        baseTime.plusHours(i * 6),
                        UUID.randomUUID(),
                        "Merchant " + (i % 10)
                ));
            }

            long start = System.nanoTime();
            var result = engine.analyze(BigDecimal.valueOf(price), largeHistory);
            long elapsedMs = (System.nanoTime() - start) / 1_000_000;

            assertEquals(1000, result.statistics().getObservationCount());
            assertNotNull(result.volatility().classification());
            assertNotNull(result.trend().trend());
            assertNotNull(result.signal().signal());
            assertFalse(result.events().isEmpty());

            assertTrue(elapsedMs < 50, "Analytics execution on 1,000 points took " + elapsedMs + " ms (must be < 50ms)");
        }
    }
}
