package com.pricepilot.intelligence.analytics;

import com.pricepilot.intelligence.analytics.engine.*;
import com.pricepilot.intelligence.analytics.model.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class AdvancedPriceAnalyticsEngineTest {

    private AdvancedPriceAnalyticsEngine engine;

    @BeforeEach
    void setUp() {
        engine = new AdvancedPriceAnalyticsEngine(
                new HistoricalDataNormalizer(),
                new StatisticalPriceAnalyzer(),
                new PriceTrendAnalyzer(),
                new PricePositionAnalyzer(),
                new PurchaseSignalGenerator(),
                new PriceEventDetector()
        );
    }

    @Test
    @DisplayName("Executes full pipeline and normalizes out-of-order raw observations")
    void testEndToEndAnalysisWithOutOfOrderRecords() {
        LocalDateTime now = LocalDateTime.now();

        // Deliberately out-of-order raw observations with invalid/null price
        List<RawPriceObservation> raw = List.of(
                new RawPriceObservation(BigDecimal.valueOf(180.0), now.plusDays(3), UUID.randomUUID(), "Seller A"),
                new RawPriceObservation(BigDecimal.valueOf(200.0), now.plusDays(1), UUID.randomUUID(), "Seller B"),
                new RawPriceObservation(BigDecimal.valueOf(-10.0), now.plusDays(2), UUID.randomUUID(), "Invalid Negative"),
                new RawPriceObservation(null, now.plusDays(2), UUID.randomUUID(), "Null Price"),
                new RawPriceObservation(BigDecimal.valueOf(150.0), now.plusDays(4), UUID.randomUUID(), "Seller C")
        );

        BigDecimal currentPrice = BigDecimal.valueOf(145.0);

        var result = engine.analyze(currentPrice, raw);

        // 1. Normalized series is strictly chronological and filtered
        assertEquals(3, result.normalizedSeries().size());
        assertEquals(BigDecimal.valueOf(200.0), result.normalizedSeries().get(0).getPrice());
        assertEquals(BigDecimal.valueOf(180.0), result.normalizedSeries().get(1).getPrice());
        assertEquals(BigDecimal.valueOf(150.0), result.normalizedSeries().get(2).getPrice());

        // 2. Statistics
        assertEquals(3, result.statistics().getObservationCount());
        assertEquals(0, BigDecimal.valueOf(150.0).compareTo(result.statistics().getHistoricalMin()));
        assertEquals(0, BigDecimal.valueOf(200.0).compareTo(result.statistics().getHistoricalMax()));
        assertEquals(0, BigDecimal.valueOf(145.0).compareTo(result.statistics().getCurrentPrice()));

        // 3. Volatility
        assertNotNull(result.volatility().classification());
        assertNotEquals(PriceVolatility.INSUFFICIENT_DATA, result.volatility().classification());

        // 4. Trend
        assertEquals(PriceTrend.FALLING, result.trend().trend());

        // 5. Deal position
        assertEquals(DealQuality.EXCELLENT_DEAL, result.position().classification());

        // 6. Signal & Evidence
        assertNotNull(result.signal().signal());
        assertFalse(result.signal().evidence().isEmpty());
    }

    @Test
    @DisplayName("Handles zero price observations truthfully without fabricating data")
    void testZeroObservations() {
        var result = engine.analyze(null, List.of());

        assertEquals(0, result.statistics().getObservationCount());
        assertNull(result.statistics().getHistoricalMin());
        assertNull(result.statistics().getHistoricalMax());
        assertEquals(PriceTrend.INSUFFICIENT_DATA, result.trend().trend());
        assertEquals(PriceVolatility.INSUFFICIENT_DATA, result.volatility().classification());
        assertEquals(DealQuality.INSUFFICIENT_DATA, result.position().classification());
        assertEquals(PurchaseSignal.INSUFFICIENT_DATA, result.signal().signal());
        assertTrue(result.events().isEmpty());
    }
}
