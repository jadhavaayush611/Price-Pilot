package com.pricepilot.intelligence.analytics.engine;

import com.pricepilot.intelligence.analytics.model.DealQuality;
import com.pricepilot.intelligence.analytics.model.PriceStatistics;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Deterministic price position and deal quality evaluator.
 * Evaluates current price strictly against verified historical distribution.
 */
@Component
public class PricePositionAnalyzer {

    public record PositionResult(Double score, DealQuality classification) {}

    /**
     * Evaluates current price position relative to historical min, avg, and max.
     */
    public PositionResult evaluatePosition(PriceStatistics stats) {
        if (stats == null || stats.getObservationCount() < 2 || stats.getCurrentPrice() == null
                || stats.getHistoricalMin() == null || stats.getHistoricalMax() == null) {
            return new PositionResult(null, DealQuality.INSUFFICIENT_DATA);
        }

        BigDecimal current = stats.getCurrentPrice();
        BigDecimal min = stats.getHistoricalMin();
        BigDecimal max = stats.getHistoricalMax();
        BigDecimal avg = stats.getHistoricalAvg();

        if (max.compareTo(min) == 0) {
            // Price has remained constant across all recorded observations
            return new PositionResult(50.0, DealQuality.FAIR_PRICE);
        }

        // Relative range position: 0.0 (at min) to 1.0 (at max)
        double rangeDiff = max.subtract(min).doubleValue();
        double currentDiff = current.subtract(min).doubleValue();
        double rawPos = Math.max(0.0, Math.min(1.0, currentDiff / rangeDiff));

        // Invert so 100 represents historical minimum (best deal) and 0 represents historical maximum
        double score = (1.0 - rawPos) * 100.0;
        double roundedScore = BigDecimal.valueOf(score).setScale(1, RoundingMode.HALF_UP).doubleValue();

        DealQuality quality;
        if (current.compareTo(min.multiply(BigDecimal.valueOf(1.02))) <= 0) {
            // At or within 2% of all-time historical low
            quality = DealQuality.EXCELLENT_DEAL;
        } else if (avg != null && current.compareTo(avg.multiply(BigDecimal.valueOf(0.95))) < 0) {
            // Noticeably below historical average (> 5% savings)
            quality = DealQuality.GOOD_DEAL;
        } else if (avg != null && current.compareTo(avg.multiply(BigDecimal.valueOf(1.05))) <= 0) {
            // Fair market price (within +/- 5% of historical average)
            quality = DealQuality.FAIR_PRICE;
        } else if (current.compareTo(max.multiply(BigDecimal.valueOf(0.98))) < 0) {
            // Higher than average but below high
            quality = DealQuality.ABOVE_AVERAGE;
        } else {
            // Near or at historical high
            quality = DealQuality.HIGH_PRICE;
        }

        return new PositionResult(roundedScore, quality);
    }
}
