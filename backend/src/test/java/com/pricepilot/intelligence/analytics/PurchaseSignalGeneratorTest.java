package com.pricepilot.intelligence.analytics;

import com.pricepilot.intelligence.analytics.engine.PurchaseSignalGenerator;
import com.pricepilot.intelligence.analytics.model.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;

class PurchaseSignalGeneratorTest {

    private PurchaseSignalGenerator generator;

    @BeforeEach
    void setUp() {
        generator = new PurchaseSignalGenerator();
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
    @DisplayName("Generates BUY_NOW signal for EXCELLENT_DEAL with stable or rising trend")
    void testBuyNowSignal() {
        PriceStatistics s = stats(100.0, 100.0, 150.0, 200.0, 6);
        var result = generator.generateSignal(
                s, PriceTrend.STABLE, 0.0, PriceVolatility.LOW, 0.03, DealQuality.EXCELLENT_DEAL, 98.0
        );

        assertEquals(PurchaseSignal.BUY_NOW, result.signal());
        assertNotNull(result.reason());
        assertTrue(result.reason().contains("historical low"));
        assertFalse(result.evidence().isEmpty());
    }

    @Test
    @DisplayName("Generates GOOD_TIME signal for GOOD_DEAL below average")
    void testGoodTimeSignal() {
        PriceStatistics s = stats(130.0, 100.0, 150.0, 200.0, 6);
        var result = generator.generateSignal(
                s, PriceTrend.STABLE, -0.5, PriceVolatility.MEDIUM, 0.08, DealQuality.GOOD_DEAL, 70.0
        );

        assertEquals(PurchaseSignal.GOOD_TIME, result.signal());
        assertTrue(result.reason().contains("favorably positioned"));
        assertFalse(result.evidence().isEmpty());
    }

    @Test
    @DisplayName("Generates WAIT signal when price is high or in sharp falling trend")
    void testWaitSignal() {
        PriceStatistics s = stats(195.0, 100.0, 150.0, 200.0, 6);
        var result = generator.generateSignal(
                s, PriceTrend.FALLING, -6.5, PriceVolatility.HIGH, 0.18, DealQuality.HIGH_PRICE, 5.0
        );

        assertEquals(PurchaseSignal.WAIT, result.signal());
        assertTrue(result.reason().contains("waiting"));
        assertFalse(result.evidence().isEmpty());
    }

    @Test
    @DisplayName("Generates INSUFFICIENT_DATA signal when observations are too few")
    void testInsufficientDataSignal() {
        PriceStatistics s = stats(100.0, 100.0, 100.0, 100.0, 1);
        var result = generator.generateSignal(
                s, PriceTrend.INSUFFICIENT_DATA, 0.0, PriceVolatility.INSUFFICIENT_DATA, null, DealQuality.INSUFFICIENT_DATA, null
        );

        assertEquals(PurchaseSignal.INSUFFICIENT_DATA, result.signal());
        assertTrue(result.evidence().isEmpty());
    }
}
