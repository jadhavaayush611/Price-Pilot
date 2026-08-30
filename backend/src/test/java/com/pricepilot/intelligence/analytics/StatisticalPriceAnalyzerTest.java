package com.pricepilot.intelligence.analytics;

import com.pricepilot.intelligence.analytics.engine.StatisticalPriceAnalyzer;
import com.pricepilot.intelligence.analytics.model.HistoricalPricePoint;
import com.pricepilot.intelligence.analytics.model.PriceStatistics;
import com.pricepilot.intelligence.analytics.model.PriceVolatility;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class StatisticalPriceAnalyzerTest {

    private StatisticalPriceAnalyzer analyzer;

    @BeforeEach
    void setUp() {
        analyzer = new StatisticalPriceAnalyzer();
    }

    private HistoricalPricePoint pt(double price, int dayOffset) {
        return HistoricalPricePoint.builder()
                .price(BigDecimal.valueOf(price))
                .timestamp(LocalDateTime.now().plusDays(dayOffset))
                .sellerName("Merchant")
                .build();
    }

    @Test
    @DisplayName("Calculates basic statistics correctly across multiple observations")
    void testBasicStatistics() {
        List<HistoricalPricePoint> series = List.of(
                pt(100.0, 1),
                pt(200.0, 2),
                pt(300.0, 3)
        );

        PriceStatistics stats = analyzer.calculateStatistics(BigDecimal.valueOf(150.0), series);

        assertEquals(BigDecimal.valueOf(150.0), stats.getCurrentPrice());
        assertEquals(0, BigDecimal.valueOf(100.0).compareTo(stats.getHistoricalMin()));
        assertEquals(0, BigDecimal.valueOf(300.0).compareTo(stats.getHistoricalMax()));
        assertEquals(0, BigDecimal.valueOf(200.0).compareTo(stats.getHistoricalAvg()));
        assertEquals(0, BigDecimal.valueOf(200.0).compareTo(stats.getHistoricalMedian()));
        assertEquals(0, BigDecimal.valueOf(200.0).compareTo(stats.getPriceRange()));
        assertEquals(3, stats.getObservationCount());
        assertEquals(0, BigDecimal.valueOf(50.0).compareTo(stats.getHistoricalLowDistance())); // 150 - 100
        assertEquals(0, BigDecimal.valueOf(-50.0).compareTo(stats.getHistoricalAverageDistance())); // 150 - 200
    }

    @Test
    @DisplayName("Handles even number of observations for median calculation")
    void testEvenMedian() {
        List<HistoricalPricePoint> series = List.of(
                pt(100.0, 1),
                pt(200.0, 2),
                pt(300.0, 3),
                pt(400.0, 4)
        );

        PriceStatistics stats = analyzer.calculateStatistics(BigDecimal.valueOf(250.0), series);
        // Median of [100, 200, 300, 400] = (200 + 300) / 2 = 250
        assertEquals(0, BigDecimal.valueOf(250.0).compareTo(stats.getHistoricalMedian()));
    }

    @Test
    @DisplayName("Handles single observation without errors")
    void testSingleObservation() {
        List<HistoricalPricePoint> series = List.of(pt(199.99, 1));

        PriceStatistics stats = analyzer.calculateStatistics(BigDecimal.valueOf(199.99), series);

        assertEquals(1, stats.getObservationCount());
        assertEquals(0, BigDecimal.valueOf(199.99).compareTo(stats.getHistoricalMin()));
        assertEquals(0, BigDecimal.valueOf(199.99).compareTo(stats.getHistoricalMax()));
        assertEquals(0, BigDecimal.valueOf(0.0).compareTo(stats.getPriceRange()));
        assertEquals(0, BigDecimal.valueOf(0.0).compareTo(stats.getHistoricalLowDistance()));
    }

    @Test
    @DisplayName("Handles empty series gracefully")
    void testEmptySeries() {
        PriceStatistics stats = analyzer.calculateStatistics(BigDecimal.valueOf(100.0), List.of());

        assertEquals(0, stats.getObservationCount());
        assertNull(stats.getHistoricalMin());
        assertNull(stats.getHistoricalMax());
        assertNull(stats.getHistoricalAvg());
    }

    @Test
    @DisplayName("Calculates volatility classifications: LOW, MEDIUM, HIGH, INSUFFICIENT_DATA")
    void testVolatilityCalculations() {
        // 1. Insufficient data (< 3 observations)
        var volFew = analyzer.calculateVolatility(List.of(pt(100.0, 1), pt(105.0, 2)));
        assertEquals(PriceVolatility.INSUFFICIENT_DATA, volFew.classification());
        assertNull(volFew.value());

        // 2. Identical observations (zero variance -> LOW volatility)
        var volIdentical = analyzer.calculateVolatility(List.of(pt(100.0, 1), pt(100.0, 2), pt(100.0, 3)));
        assertEquals(PriceVolatility.LOW, volIdentical.classification());
        assertEquals(0.0, volIdentical.value());

        // 3. Low volatility (CV < 0.05)
        var volLow = analyzer.calculateVolatility(List.of(pt(100.0, 1), pt(102.0, 2), pt(101.0, 3), pt(103.0, 4)));
        assertEquals(PriceVolatility.LOW, volLow.classification());
        assertTrue(volLow.value() < 0.05);

        // 4. High volatility (CV >= 0.15)
        var volHigh = analyzer.calculateVolatility(List.of(pt(100.0, 1), pt(50.0, 2), pt(180.0, 3)));
        assertEquals(PriceVolatility.HIGH, volHigh.classification());
        assertTrue(volHigh.value() >= 0.15);
    }
}
