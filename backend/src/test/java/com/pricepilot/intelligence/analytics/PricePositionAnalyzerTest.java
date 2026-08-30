package com.pricepilot.intelligence.analytics;

import com.pricepilot.intelligence.analytics.engine.PricePositionAnalyzer;
import com.pricepilot.intelligence.analytics.model.DealQuality;
import com.pricepilot.intelligence.analytics.model.PriceStatistics;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;

class PricePositionAnalyzerTest {

    private PricePositionAnalyzer analyzer;

    @BeforeEach
    void setUp() {
        analyzer = new PricePositionAnalyzer();
    }

    private PriceStatistics stats(double current, double min, double avg, double max, int count) {
        return PriceStatistics.builder()
                .currentPrice(BigDecimal.valueOf(current))
                .historicalMin(BigDecimal.valueOf(min))
                .historicalAvg(BigDecimal.valueOf(avg))
                .historicalMax(BigDecimal.valueOf(max))
                .priceRange(BigDecimal.valueOf(max - min))
                .observationCount(count)
                .build();
    }

    @Test
    @DisplayName("Classifies EXCELLENT_DEAL when current price is within 2% of historical min")
    void testExcellentDeal() {
        PriceStatistics s = stats(101.0, 100.0, 150.0, 200.0, 5);
        var result = analyzer.evaluatePosition(s);

        assertEquals(DealQuality.EXCELLENT_DEAL, result.classification());
        assertTrue(result.score() >= 95.0, "Score should be >= 95 for price at minimum, was: " + result.score());
    }

    @Test
    @DisplayName("Classifies GOOD_DEAL when current price is > 5% below average")
    void testGoodDeal() {
        PriceStatistics s = stats(130.0, 100.0, 150.0, 200.0, 5); // 130 is 13.3% below 150
        var result = analyzer.evaluatePosition(s);

        assertEquals(DealQuality.GOOD_DEAL, result.classification());
        assertTrue(result.score() > 50.0);
    }

    @Test
    @DisplayName("Classifies FAIR_PRICE when current price is near average (+/- 5%)")
    void testFairPrice() {
        PriceStatistics s = stats(152.0, 100.0, 150.0, 200.0, 5);
        var result = analyzer.evaluatePosition(s);

        assertEquals(DealQuality.FAIR_PRICE, result.classification());
    }

    @Test
    @DisplayName("Classifies HIGH_PRICE when current price is near historical maximum")
    void testHighPrice() {
        PriceStatistics s = stats(199.0, 100.0, 150.0, 200.0, 5);
        var result = analyzer.evaluatePosition(s);

        assertEquals(DealQuality.HIGH_PRICE, result.classification());
        assertTrue(result.score() <= 5.0, "Score should be <= 5 for price near peak, was: " + result.score());
    }

    @Test
    @DisplayName("Handles constant price across all observations without divide by zero")
    void testConstantPrice() {
        PriceStatistics s = stats(100.0, 100.0, 100.0, 100.0, 5);
        var result = analyzer.evaluatePosition(s);

        assertEquals(DealQuality.FAIR_PRICE, result.classification());
        assertEquals(50.0, result.score());
    }

    @Test
    @DisplayName("Returns INSUFFICIENT_DATA when observation count is less than 2")
    void testInsufficientData() {
        PriceStatistics s = stats(100.0, 100.0, 100.0, 100.0, 1);
        var result = analyzer.evaluatePosition(s);

        assertEquals(DealQuality.INSUFFICIENT_DATA, result.classification());
        assertNull(result.score());
    }
}
