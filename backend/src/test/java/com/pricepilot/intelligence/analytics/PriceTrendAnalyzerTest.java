package com.pricepilot.intelligence.analytics;

import com.pricepilot.intelligence.analytics.engine.PriceTrendAnalyzer;
import com.pricepilot.intelligence.analytics.model.HistoricalPricePoint;
import com.pricepilot.intelligence.analytics.model.PriceTrend;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class PriceTrendAnalyzerTest {

    private PriceTrendAnalyzer analyzer;

    @BeforeEach
    void setUp() {
        analyzer = new PriceTrendAnalyzer();
    }

    private HistoricalPricePoint pt(double price, int dayOffset) {
        return HistoricalPricePoint.builder()
                .price(BigDecimal.valueOf(price))
                .timestamp(LocalDateTime.now().plusDays(dayOffset))
                .sellerName("Merchant")
                .build();
    }

    @Test
    @DisplayName("Identifies RISING price trend when recent window exceeds baseline by > 2%")
    void testRisingTrend() {
        List<HistoricalPricePoint> series = List.of(
                pt(100.0, 1),
                pt(102.0, 2),
                pt(115.0, 3),
                pt(120.0, 4)
        );

        var result = analyzer.analyzeTrend(series);
        assertEquals(PriceTrend.RISING, result.trend());
        assertTrue(result.percentageChange() > 2.0);
    }

    @Test
    @DisplayName("Identifies FALLING price trend when recent window drops below baseline by > 2%")
    void testFallingTrend() {
        List<HistoricalPricePoint> series = List.of(
                pt(200.0, 1),
                pt(195.0, 2),
                pt(170.0, 3),
                pt(160.0, 4)
        );

        var result = analyzer.analyzeTrend(series);
        assertEquals(PriceTrend.FALLING, result.trend());
        assertTrue(result.percentageChange() < -2.0);
    }

    @Test
    @DisplayName("Identifies STABLE price trend when movement is within +/- 2%")
    void testStableTrend() {
        List<HistoricalPricePoint> series = List.of(
                pt(100.0, 1),
                pt(101.0, 2),
                pt(100.5, 3),
                pt(101.2, 4)
        );

        var result = analyzer.analyzeTrend(series);
        assertEquals(PriceTrend.STABLE, result.trend());
        assertTrue(Math.abs(result.percentageChange()) <= 2.0);
    }

    @Test
    @DisplayName("Returns INSUFFICIENT_DATA when observations are less than 3")
    void testInsufficientDataTrend() {
        var resEmpty = analyzer.analyzeTrend(List.of());
        assertEquals(PriceTrend.INSUFFICIENT_DATA, resEmpty.trend());

        var resTwo = analyzer.analyzeTrend(List.of(pt(100.0, 1), pt(110.0, 2)));
        assertEquals(PriceTrend.INSUFFICIENT_DATA, resTwo.trend());
    }
}
