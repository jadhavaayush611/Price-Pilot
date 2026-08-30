package com.pricepilot.intelligence.analytics.engine;

import com.pricepilot.intelligence.analytics.model.*;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Generates conservative, evidence-grounded purchase timing signals.
 * Strictly avoids speculative claims about future prices.
 */
@Component
public class PurchaseSignalGenerator {

    public record SignalResult(PurchaseSignal signal, String reason, List<String> evidence) {}

    /**
     * Synthesizes historical price metrics into an actionable, transparent purchase recommendation signal.
     */
    public SignalResult generateSignal(
            PriceStatistics stats,
            PriceTrend trend,
            Double trendPercentage,
            PriceVolatility volatility,
            Double volatilityValue,
            DealQuality dealQuality,
            Double positionScore) {

        if (stats == null || stats.getObservationCount() < 2 || dealQuality == DealQuality.INSUFFICIENT_DATA) {
            return new SignalResult(
                    PurchaseSignal.INSUFFICIENT_DATA,
                    "Insufficient historical price records to determine a purchase timing signal.",
                    Collections.emptyList()
            );
        }

        BigDecimal current = stats.getCurrentPrice();
        BigDecimal min = stats.getHistoricalMin();
        BigDecimal avg = stats.getHistoricalAvg();
        BigDecimal max = stats.getHistoricalMax();

        PurchaseSignal signal;
        String reason;

        if (dealQuality == DealQuality.EXCELLENT_DEAL && trend != PriceTrend.FALLING) {
            signal = PurchaseSignal.BUY_NOW;
            reason = String.format("Current price ($%s) is at or near the all-time historical low ($%s) and significantly below the historical average ($%s).", current, min, avg);
        } else if (dealQuality == DealQuality.EXCELLENT_DEAL || dealQuality == DealQuality.GOOD_DEAL) {
            signal = PurchaseSignal.GOOD_TIME;
            reason = String.format("Current price ($%s) is favorably positioned below the historical average ($%s), offering strong comparative value.", current, avg);
        } else if (dealQuality == DealQuality.HIGH_PRICE || (dealQuality == DealQuality.ABOVE_AVERAGE && trend == PriceTrend.FALLING)) {
            signal = PurchaseSignal.WAIT;
            reason = String.format("Current price ($%s) is elevated near the historical high ($%s); waiting may yield better value.", current, max);
        } else if (trend == PriceTrend.FALLING && trendPercentage != null && trendPercentage < -5.0) {
            signal = PurchaseSignal.WAIT;
            reason = String.format("Active downward price trend (%s%%) suggests potential further reductions; waiting is reasonable.", trendPercentage);
        } else {
            signal = PurchaseSignal.NEUTRAL;
            reason = String.format("Current price ($%s) is aligned with typical historical market averages ($%s).", current, avg);
        }

        List<String> evidence = buildEvidence(stats, trend, trendPercentage, volatility, volatilityValue);

        return new SignalResult(signal, reason, Collections.unmodifiableList(evidence));
    }

    private List<String> buildEvidence(
            PriceStatistics stats,
            PriceTrend trend,
            Double trendPercentage,
            PriceVolatility volatility,
            Double volatilityValue) {

        List<String> evidence = new ArrayList<>();
        BigDecimal current = stats.getCurrentPrice();
        BigDecimal min = stats.getHistoricalMin();
        BigDecimal avg = stats.getHistoricalAvg();

        // 1. Historical minimum distance
        if (min != null && min.compareTo(BigDecimal.ZERO) > 0) {
            double lowDiffPercent = ((current.doubleValue() - min.doubleValue()) / min.doubleValue()) * 100.0;
            double roundedDiff = BigDecimal.valueOf(lowDiffPercent).setScale(1, RoundingMode.HALF_UP).doubleValue();
            if (roundedDiff <= 1.0) {
                evidence.add(String.format("Current price is at the recorded all-time low ($%s)", min));
            } else {
                evidence.add(String.format("Current price is %s%% above the recorded all-time low ($%s)", roundedDiff, min));
            }
        }

        // 2. Historical average comparison
        if (avg != null && avg.compareTo(BigDecimal.ZERO) > 0) {
            double avgDiffPercent = ((current.doubleValue() - avg.doubleValue()) / avg.doubleValue()) * 100.0;
            double roundedAvgDiff = BigDecimal.valueOf(Math.abs(avgDiffPercent)).setScale(1, RoundingMode.HALF_UP).doubleValue();
            if (avgDiffPercent < 0) {
                evidence.add(String.format("Current price is %s%% below the historical average ($%s)", roundedAvgDiff, avg));
            } else if (avgDiffPercent > 0) {
                evidence.add(String.format("Current price is %s%% above the historical average ($%s)", roundedAvgDiff, avg));
            } else {
                evidence.add(String.format("Current price matches the historical average ($%s)", avg));
            }
        }

        // 3. Volatility factor
        if (volatility != null && volatility != PriceVolatility.INSUFFICIENT_DATA && volatilityValue != null) {
            double pct = BigDecimal.valueOf(volatilityValue * 100.0).setScale(1, RoundingMode.HALF_UP).doubleValue();
            String stabilityDesc = switch (volatility) {
                case LOW -> "high price consistency";
                case MEDIUM -> "moderate price fluctuations";
                case HIGH -> "frequent, volatile price swings";
                default -> "variable pricing";
            };
            evidence.add(String.format("Price volatility is %s (CV: %s%%), indicating %s", volatility.name(), pct, stabilityDesc));
        }

        // 4. Trend factor
        if (trend != null && trend != PriceTrend.INSUFFICIENT_DATA && trendPercentage != null) {
            evidence.add(String.format("Recent trajectory reflects a %s trend (%s%s%% relative to baseline)",
                    trend.name(), trendPercentage >= 0 ? "+" : "", trendPercentage));
        }

        return evidence;
    }
}
