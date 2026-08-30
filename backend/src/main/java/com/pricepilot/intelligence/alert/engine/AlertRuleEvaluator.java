package com.pricepilot.intelligence.alert.engine;

import com.pricepilot.analytics.dto.ProductAnalyticsResponseDTO;
import com.pricepilot.intelligence.alert.entity.WatchlistAlertPreferenceEntity;
import com.pricepilot.intelligence.alert.model.AlertType;
import com.pricepilot.intelligence.analytics.model.DealQuality;
import com.pricepilot.watchlist.PriceWatchlistEntity;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Evaluates configured watchlist rules against incoming price updates and historical analytics.
 * Produces deterministic, deduplicatable alert candidates.
 */
@Component
public class AlertRuleEvaluator {

    public record AlertCandidate(
            AlertType alertType,
            String title,
            String message,
            BigDecimal triggerValue,
            BigDecimal observedValue,
            String deduplicationKey
    ) {}

    /**
     * Evaluates alert candidates for a specific watchlist.
     */
    public List<AlertCandidate> evaluateRules(
            PriceWatchlistEntity watchlist,
            WatchlistAlertPreferenceEntity prefs,
            BigDecimal oldPrice,
            BigDecimal newPrice,
            ProductAnalyticsResponseDTO analytics,
            boolean isBackInStock) {

        if (watchlist == null || prefs == null || !prefs.isEnabled() || newPrice == null) {
            return Collections.emptyList();
        }

        List<AlertCandidate> candidates = new ArrayList<>();
        String productName = watchlist.getProduct() != null ? watchlist.getProduct().getName() : "Watched Product";
        String watchlistId = watchlist.getId().toString();

        // 1. Target Price Alert
        if (prefs.isTargetPriceEnabled() && watchlist.getTargetPrice() != null) {
            BigDecimal target = watchlist.getTargetPrice();
            if (newPrice.compareTo(target) <= 0) {
                // Must have crossed below target (either first time, or after being above target)
                boolean wasAboveTarget = oldPrice != null && oldPrice.compareTo(target) > 0;
                boolean neverNotifiedOrReset = prefs.getLastNotifiedTargetPrice() == null
                        || prefs.getLastNotifiedTargetPrice().compareTo(target) > 0;

                if (wasAboveTarget || neverNotifiedOrReset) {
                    candidates.add(new AlertCandidate(
                            AlertType.PRICE_TARGET_REACHED,
                            "Target Price Reached!",
                            String.format("Current price ($%s) for %s has reached your target of $%s.",
                                    newPrice.setScale(2, RoundingMode.HALF_UP), productName, target.setScale(2, RoundingMode.HALF_UP)),
                            target,
                            newPrice,
                            String.format("TARGET:%s:%s:%s", watchlistId, target.setScale(2, RoundingMode.HALF_UP), newPrice.setScale(2, RoundingMode.HALF_UP))
                    ));
                }
            }
        }

        // 2. Price Drop Alert
        if (prefs.isPriceDropEnabled() && oldPrice != null && oldPrice.compareTo(newPrice) > 0 && oldPrice.compareTo(BigDecimal.ZERO) > 0) {
            BigDecimal diff = oldPrice.subtract(newPrice);
            double dropPercent = (diff.doubleValue() / oldPrice.doubleValue()) * 100.0;
            double threshold = prefs.getPriceDropPercentage() != null ? prefs.getPriceDropPercentage().doubleValue() : 10.0;

            if (dropPercent >= threshold) {
                double roundedDrop = BigDecimal.valueOf(dropPercent).setScale(1, RoundingMode.HALF_UP).doubleValue();
                candidates.add(new AlertCandidate(
                        AlertType.PRICE_DROP,
                        String.format("Price Drop Alert (-%s%%)", roundedDrop),
                        String.format("Price for %s dropped by %s%% from $%s to $%s, meeting your %s%% alert threshold.",
                                productName, roundedDrop,
                                oldPrice.setScale(2, RoundingMode.HALF_UP),
                                newPrice.setScale(2, RoundingMode.HALF_UP),
                                BigDecimal.valueOf(threshold).setScale(1, RoundingMode.HALF_UP)),
                        BigDecimal.valueOf(threshold),
                        BigDecimal.valueOf(roundedDrop),
                        String.format("DROP:%s:%s:%s", watchlistId, oldPrice.setScale(2, RoundingMode.HALF_UP), newPrice.setScale(2, RoundingMode.HALF_UP))
                ));
            }
        }

        // 3. Historical Low Alert
        if (prefs.isHistoricalLowEnabled() && analytics != null && analytics.getHistoricalMin() != null) {
            BigDecimal histMin = analytics.getHistoricalMin();
            if (newPrice.compareTo(histMin) <= 0) {
                // Must be lower than previously notified historical low
                boolean isNewLow = prefs.getLastNotifiedHistoricalLow() == null
                        || newPrice.compareTo(prefs.getLastNotifiedHistoricalLow()) < 0;

                if (isNewLow) {
                    candidates.add(new AlertCandidate(
                            AlertType.HISTORICAL_LOW_REACHED,
                            "New All-Time Historical Low!",
                            String.format("Current price ($%s) for %s established a new all-time recorded low!",
                                    newPrice.setScale(2, RoundingMode.HALF_UP), productName),
                            histMin,
                            newPrice,
                            String.format("HIST_LOW:%s:%s", watchlistId, newPrice.setScale(2, RoundingMode.HALF_UP))
                    ));
                }
            }
        }

        // 4. Good Deal Alert
        if (prefs.isGoodDealEnabled() && analytics != null && analytics.getDealQuality() != null) {
            DealQuality deal = analytics.getDealQuality();
            if (deal == DealQuality.EXCELLENT_DEAL || deal == DealQuality.GOOD_DEAL) {
                boolean isNewDealState = prefs.getLastNotifiedDealQuality() == null
                        || !prefs.getLastNotifiedDealQuality().equals(deal.name());

                if (isNewDealState) {
                    String evidence = analytics.getSupportingEvidence() != null && !analytics.getSupportingEvidence().isEmpty()
                            ? analytics.getSupportingEvidence().get(0)
                            : (analytics.getPurchaseSignalReason() != null ? analytics.getPurchaseSignalReason() : "Current price is favorably below average.");

                    candidates.add(new AlertCandidate(
                            AlertType.GOOD_DEAL_DETECTED,
                            String.format("Good Deal Detected: %s", deal.name().replace('_', ' ')),
                            String.format("%s. %s", deal.name().replace('_', ' '), evidence),
                            analytics.getPricePositionScore() != null ? BigDecimal.valueOf(analytics.getPricePositionScore()) : BigDecimal.ZERO,
                            newPrice,
                            String.format("DEAL:%s:%s:%s", watchlistId, deal.name(), newPrice.setScale(2, RoundingMode.HALF_UP))
                    ));
                }
            }
        }

        // 5. Price Increase Alert
        if (prefs.isPriceIncreaseEnabled() && oldPrice != null && newPrice.compareTo(oldPrice) > 0 && oldPrice.compareTo(BigDecimal.ZERO) > 0) {
            BigDecimal diff = newPrice.subtract(oldPrice);
            double increasePercent = (diff.doubleValue() / oldPrice.doubleValue()) * 100.0;
            if (increasePercent >= 10.0) {
                double roundedInc = BigDecimal.valueOf(increasePercent).setScale(1, RoundingMode.HALF_UP).doubleValue();
                candidates.add(new AlertCandidate(
                        AlertType.PRICE_INCREASE,
                        String.format("Price Increase Alert (+%s%%)", roundedInc),
                        String.format("Price for %s increased by %s%% from $%s to $%s.",
                                productName, roundedInc,
                                oldPrice.setScale(2, RoundingMode.HALF_UP),
                                newPrice.setScale(2, RoundingMode.HALF_UP)),
                        BigDecimal.valueOf(10.00),
                        BigDecimal.valueOf(roundedInc),
                        String.format("INCREASE:%s:%s:%s", watchlistId, oldPrice.setScale(2, RoundingMode.HALF_UP), newPrice.setScale(2, RoundingMode.HALF_UP))
                ));
            }
        }

        // 6. Back in Stock Alert
        if (prefs.isBackInStockEnabled() && isBackInStock) {
            boolean wasOutOfStock = prefs.getLastNotifiedInStock() == null || !prefs.getLastNotifiedInStock();
            if (wasOutOfStock) {
                candidates.add(new AlertCandidate(
                        AlertType.BACK_IN_STOCK,
                        "Back in Stock!",
                        String.format("%s is now available back in stock at $%s.",
                                productName, newPrice.setScale(2, RoundingMode.HALF_UP)),
                        null,
                        newPrice,
                        String.format("STOCK:%s:%s", watchlistId, newPrice.setScale(2, RoundingMode.HALF_UP))
                ));
            }
        }

        return Collections.unmodifiableList(candidates);
    }
}
