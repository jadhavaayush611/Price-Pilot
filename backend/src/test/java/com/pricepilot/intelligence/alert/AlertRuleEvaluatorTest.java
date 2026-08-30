package com.pricepilot.intelligence.alert;

import com.pricepilot.analytics.dto.ProductAnalyticsResponseDTO;
import com.pricepilot.intelligence.alert.engine.AlertRuleEvaluator;
import com.pricepilot.intelligence.alert.entity.WatchlistAlertPreferenceEntity;
import com.pricepilot.intelligence.alert.model.AlertType;
import com.pricepilot.intelligence.analytics.model.DealQuality;
import com.pricepilot.product.ProductEntity;
import com.pricepilot.user.UserEntity;
import com.pricepilot.watchlist.PriceWatchlistEntity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class AlertRuleEvaluatorTest {

    private AlertRuleEvaluator evaluator;
    private PriceWatchlistEntity watchlist;
    private WatchlistAlertPreferenceEntity prefs;
    private ProductEntity product;

    @BeforeEach
    void setUp() {
        evaluator = new AlertRuleEvaluator();

        product = ProductEntity.builder()
                .name("MacBook Pro M3")
                .brand("Apple")
                .category("Electronics")
                .build();
        product.setId(UUID.randomUUID());

        UserEntity user = new UserEntity();
        user.setId(UUID.randomUUID());

        watchlist = PriceWatchlistEntity.builder()
                .user(user)
                .product(product)
                .targetPrice(BigDecimal.valueOf(1800.00))
                .currentBestPrice(BigDecimal.valueOf(2000.00))
                .active(true)
                .build();
        watchlist.setId(UUID.randomUUID());

        prefs = WatchlistAlertPreferenceEntity.defaultForWatchlist(watchlist);
    }

    @Test
    @DisplayName("Target price alert triggers when price crosses below target")
    void testTargetPriceReached() {
        // Price drops from 2000 to 1750 (target is 1800)
        var candidates = evaluator.evaluateRules(
                watchlist, prefs, BigDecimal.valueOf(2000.00), BigDecimal.valueOf(1750.00), null, false
        );

        assertTrue(candidates.stream().anyMatch(c -> c.alertType() == AlertType.PRICE_TARGET_REACHED));
        var targetAlert = candidates.stream().filter(c -> c.alertType() == AlertType.PRICE_TARGET_REACHED).findFirst().get();
        assertEquals(BigDecimal.valueOf(1800.00), targetAlert.triggerValue());
        assertEquals(BigDecimal.valueOf(1750.00), targetAlert.observedValue());
        assertTrue(targetAlert.deduplicationKey().contains("TARGET:"));
    }

    @Test
    @DisplayName("Target price alert does NOT repeat while price remains below target")
    void testTargetPriceNoRepeatedAlertWhileBelow() {
        prefs.setLastNotifiedTargetPrice(BigDecimal.valueOf(1750.00));

        // Price moves from 1750 to 1740 (still below 1800, never rose above)
        var candidates = evaluator.evaluateRules(
                watchlist, prefs, BigDecimal.valueOf(1750.00), BigDecimal.valueOf(1740.00), null, false
        );

        assertFalse(candidates.stream().anyMatch(c -> c.alertType() == AlertType.PRICE_TARGET_REACHED));
    }

    @Test
    @DisplayName("Target price alert re-arms when price rises above target and crosses again")
    void testTargetPriceReArmedAfterRising() {
        prefs.setLastNotifiedTargetPrice(BigDecimal.valueOf(1750.00));

        // Price rose to 1850 previously, and now drops back to 1780
        var candidates = evaluator.evaluateRules(
                watchlist, prefs, BigDecimal.valueOf(1850.00), BigDecimal.valueOf(1780.00), null, false
        );

        assertTrue(candidates.stream().anyMatch(c -> c.alertType() == AlertType.PRICE_TARGET_REACHED));
    }

    @Test
    @DisplayName("Price drop alert triggers when configured percentage threshold is crossed")
    void testPriceDropThresholdCrossed() {
        prefs.setPriceDropPercentage(BigDecimal.valueOf(10.00));

        // 2000 -> 1760 = 12.0% drop >= 10.0%
        var candidates = evaluator.evaluateRules(
                watchlist, prefs, BigDecimal.valueOf(2000.00), BigDecimal.valueOf(1760.00), null, false
        );

        assertTrue(candidates.stream().anyMatch(c -> c.alertType() == AlertType.PRICE_DROP));
        var dropAlert = candidates.stream().filter(c -> c.alertType() == AlertType.PRICE_DROP).findFirst().get();
        assertEquals(BigDecimal.valueOf(12.0), dropAlert.observedValue());
    }

    @Test
    @DisplayName("Price drop alert does NOT trigger for minor noise below threshold")
    void testPriceDropMinorNoiseSuppressed() {
        prefs.setPriceDropPercentage(BigDecimal.valueOf(10.00));

        // 2000 -> 1950 = 2.5% drop < 10%
        var candidates = evaluator.evaluateRules(
                watchlist, prefs, BigDecimal.valueOf(2000.00), BigDecimal.valueOf(1950.00), null, false
        );

        assertFalse(candidates.stream().anyMatch(c -> c.alertType() == AlertType.PRICE_DROP));
    }

    @Test
    @DisplayName("Historical low alert triggers when price establishes new low, ignores identical price")
    void testHistoricalLowAlert() {
        ProductAnalyticsResponseDTO analytics = ProductAnalyticsResponseDTO.builder()
                .historicalMin(BigDecimal.valueOf(1700.00))
                .build();

        // 1. First time reaching 1700.00
        var candidates1 = evaluator.evaluateRules(
                watchlist, prefs, BigDecimal.valueOf(1750.00), BigDecimal.valueOf(1700.00), analytics, false
        );
        assertTrue(candidates1.stream().anyMatch(c -> c.alertType() == AlertType.HISTORICAL_LOW_REACHED));

        // Simulate state update
        prefs.setLastNotifiedHistoricalLow(BigDecimal.valueOf(1700.00));

        // 2. Identical price observation of 1700.00 must NOT trigger duplicate
        var candidates2 = evaluator.evaluateRules(
                watchlist, prefs, BigDecimal.valueOf(1700.00), BigDecimal.valueOf(1700.00), analytics, false
        );
        assertFalse(candidates2.stream().anyMatch(c -> c.alertType() == AlertType.HISTORICAL_LOW_REACHED));

        // 3. Deeper drop to 1650.00 triggers new historical low
        var candidates3 = evaluator.evaluateRules(
                watchlist, prefs, BigDecimal.valueOf(1700.00), BigDecimal.valueOf(1650.00), analytics, false
        );
        assertTrue(candidates3.stream().anyMatch(c -> c.alertType() == AlertType.HISTORICAL_LOW_REACHED));
    }

    @Test
    @DisplayName("Good deal alert triggers with factual Phase 4 evidence")
    void testGoodDealAlert() {
        ProductAnalyticsResponseDTO analytics = ProductAnalyticsResponseDTO.builder()
                .dealQuality(DealQuality.EXCELLENT_DEAL)
                .pricePositionScore(95.0)
                .supportingEvidence(List.of("Current price is 8.4% below historical average and at recorded all-time low."))
                .build();

        var candidates = evaluator.evaluateRules(
                watchlist, prefs, BigDecimal.valueOf(2000.00), BigDecimal.valueOf(1650.00), analytics, false
        );

        assertTrue(candidates.stream().anyMatch(c -> c.alertType() == AlertType.GOOD_DEAL_DETECTED));
        var dealAlert = candidates.stream().filter(c -> c.alertType() == AlertType.GOOD_DEAL_DETECTED).findFirst().get();
        assertTrue(dealAlert.message().contains("EXCELLENT DEAL"));
        assertTrue(dealAlert.message().contains("8.4% below historical average"));
    }

    @Test
    @DisplayName("Back in stock alert triggers only on transition from out-of-stock")
    void testBackInStockAlert() {
        prefs.setBackInStockEnabled(true);
        prefs.setLastNotifiedInStock(false);

        var candidates1 = evaluator.evaluateRules(
                watchlist, prefs, BigDecimal.valueOf(2000.00), BigDecimal.valueOf(2000.00), null, true
        );
        assertTrue(candidates1.stream().anyMatch(c -> c.alertType() == AlertType.BACK_IN_STOCK));

        // Once notified as in stock, repeated in stock checks do not re-alert
        prefs.setLastNotifiedInStock(true);
        var candidates2 = evaluator.evaluateRules(
                watchlist, prefs, BigDecimal.valueOf(2000.00), BigDecimal.valueOf(2000.00), null, true
        );
        assertFalse(candidates2.stream().anyMatch(c -> c.alertType() == AlertType.BACK_IN_STOCK));
    }

    @Test
    @DisplayName("Disabled preferences suppress all alert candidate generation")
    void testDisabledPreferences() {
        prefs.setEnabled(false);

        var candidates = evaluator.evaluateRules(
                watchlist, prefs, BigDecimal.valueOf(2000.00), BigDecimal.valueOf(1000.00), null, true
        );
        assertTrue(candidates.isEmpty());
    }
}
