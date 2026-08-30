package com.pricepilot.intelligence.analytics.engine;

import com.pricepilot.intelligence.analytics.model.HistoricalPricePoint;
import com.pricepilot.intelligence.analytics.model.PriceTrend;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

/**
 * Deterministic windowed price trend analyzer.
 * Compares recent window average price against historical baseline window.
 */
@Component
public class PriceTrendAnalyzer {

    public static final int MIN_OBSERVATIONS_FOR_TREND = 3;
    private static final double STABLE_THRESHOLD_PERCENT = 2.0; // Movements within +/- 2% treated as STABLE

    public record TrendResult(PriceTrend trend, Double percentageChange) {}

    /**
     * Determines price trend by comparing historical baseline against recent pricing window.
     *
     * @param priceSeries Chronologically ordered price series.
     * @return TrendResult containing trend enum and percentage change.
     */
    public TrendResult analyzeTrend(List<HistoricalPricePoint> priceSeries) {
        if (priceSeries == null || priceSeries.size() < MIN_OBSERVATIONS_FOR_TREND) {
            return new TrendResult(PriceTrend.INSUFFICIENT_DATA, 0.0);
        }

        int total = priceSeries.size();
        // Baseline window = earlier 60%, Recent window = remaining latest 40%
        int splitIndex = Math.max(1, (int) Math.round(total * 0.60));
        if (splitIndex >= total) {
            splitIndex = total - 1;
        }

        List<HistoricalPricePoint> baselinePoints = priceSeries.subList(0, splitIndex);
        List<HistoricalPricePoint> recentPoints = priceSeries.subList(splitIndex, total);

        double baselineAvg = baselinePoints.stream()
                .mapToDouble(p -> p.getPrice().doubleValue())
                .average()
                .orElse(0.0);

        double recentAvg = recentPoints.stream()
                .mapToDouble(p -> p.getPrice().doubleValue())
                .average()
                .orElse(0.0);

        if (baselineAvg <= 0.0) {
            return new TrendResult(PriceTrend.INSUFFICIENT_DATA, 0.0);
        }

        double diffPercent = ((recentAvg - baselineAvg) / baselineAvg) * 100.0;
        double roundedDiff = BigDecimal.valueOf(diffPercent).setScale(2, RoundingMode.HALF_UP).doubleValue();

        PriceTrend trend;
        if (Math.abs(diffPercent) <= STABLE_THRESHOLD_PERCENT) {
            trend = PriceTrend.STABLE;
        } else if (diffPercent > STABLE_THRESHOLD_PERCENT) {
            trend = PriceTrend.RISING;
        } else {
            trend = PriceTrend.FALLING;
        }

        return new TrendResult(trend, roundedDiff);
    }
}
