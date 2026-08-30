package com.pricepilot.intelligence.analytics;

import com.pricepilot.intelligence.analytics.engine.PriceEventDetector;
import com.pricepilot.intelligence.analytics.model.HistoricalPricePoint;
import com.pricepilot.intelligence.analytics.model.PriceDropRecoveryEvent;
import com.pricepilot.intelligence.analytics.model.PriceDropRecoveryEvent.EventType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class PriceEventDetectorTest {

    private PriceEventDetector detector;

    @BeforeEach
    void setUp() {
        detector = new PriceEventDetector();
    }

    private HistoricalPricePoint pt(double price, int dayOffset) {
        return HistoricalPricePoint.builder()
                .price(BigDecimal.valueOf(price))
                .timestamp(LocalDateTime.now().plusDays(dayOffset))
                .sellerName("Merchant")
                .build();
    }

    @Test
    @DisplayName("Detects MAJOR_DROP when single price decrease is >= 10%")
    void testMajorDrop() {
        List<HistoricalPricePoint> series = List.of(
                pt(100.0, 1),
                pt(85.0, 2) // 15% drop
        );

        List<PriceDropRecoveryEvent> events = detector.detectEvents(series);
        assertTrue(events.stream().anyMatch(e -> e.getEventType() == EventType.MAJOR_DROP));
        assertTrue(events.stream().anyMatch(e -> e.getEventType() == EventType.NEW_HISTORICAL_LOW));
    }

    @Test
    @DisplayName("Detects RECOVERY_AFTER_DROP when price rebounds after previous drop")
    void testRecoveryAfterDrop() {
        List<HistoricalPricePoint> series = List.of(
                pt(100.0, 1),
                pt(80.0, 2),  // 20% drop -> previousWasDrop = true
                pt(95.0, 3)   // 18.75% increase -> RECOVERY_AFTER_DROP
        );

        List<PriceDropRecoveryEvent> events = detector.detectEvents(series);
        assertTrue(events.stream().anyMatch(e -> e.getEventType() == EventType.RECOVERY_AFTER_DROP));
    }

    @Test
    @DisplayName("Detects NEW_HISTORICAL_HIGH when price exceeds previous peak")
    void testNewHistoricalHigh() {
        List<HistoricalPricePoint> series = List.of(
                pt(100.0, 1),
                pt(95.0, 2),
                pt(120.0, 3) // new high
        );

        List<PriceDropRecoveryEvent> events = detector.detectEvents(series);
        assertTrue(events.stream().anyMatch(e -> e.getEventType() == EventType.NEW_HISTORICAL_HIGH));
    }

    @Test
    @DisplayName("Ignores insignificant price noise under 2%")
    void testNoiseSuppression() {
        List<HistoricalPricePoint> series = List.of(
                pt(100.0, 1),
                pt(100.5, 2), // +0.5% (noise)
                pt(100.2, 3)  // -0.3% (noise)
        );

        List<PriceDropRecoveryEvent> events = detector.detectEvents(series);
        // Only new high for 100.5 might trigger if > 100.0, but no major drop or increase
        assertFalse(events.stream().anyMatch(e -> e.getEventType() == EventType.MAJOR_DROP || e.getEventType() == EventType.PRICE_INCREASE));
    }
}
