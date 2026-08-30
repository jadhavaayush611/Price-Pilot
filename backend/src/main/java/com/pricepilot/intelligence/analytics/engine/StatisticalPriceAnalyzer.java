package com.pricepilot.intelligence.analytics.engine;

import com.pricepilot.intelligence.analytics.model.HistoricalPricePoint;
import com.pricepilot.intelligence.analytics.model.PriceStatistics;
import com.pricepilot.intelligence.analytics.model.PriceVolatility;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Computes deterministic descriptive statistics, price position distances,
 * and statistical coefficient-of-variation (CV) volatility.
 */
@Component
public class StatisticalPriceAnalyzer {

    public static final int MIN_OBSERVATIONS_FOR_VOLATILITY = 3;
    private static final double LOW_VOLATILITY_THRESHOLD = 0.05;
    private static final double MEDIUM_VOLATILITY_THRESHOLD = 0.15;

    public record VolatilityResult(Double value, PriceVolatility classification) {}

    /**
     * Calculates basic historical statistics (min, max, average, median, range, distances).
     */
    public PriceStatistics calculateStatistics(BigDecimal currentPrice, List<HistoricalPricePoint> priceSeries) {
        if (priceSeries == null || priceSeries.isEmpty()) {
            return PriceStatistics.builder()
                    .currentPrice(currentPrice)
                    .historicalMin(null)
                    .historicalMax(null)
                    .historicalAvg(null)
                    .historicalMedian(null)
                    .priceRange(null)
                    .observationCount(0)
                    .historicalLowDistance(null)
                    .historicalAverageDistance(null)
                    .build();
        }

        List<BigDecimal> prices = priceSeries.stream()
                .map(HistoricalPricePoint::getPrice)
                .toList();

        int n = prices.size();
        BigDecimal min = prices.stream().min(BigDecimal::compareTo).orElse(currentPrice);
        BigDecimal max = prices.stream().max(BigDecimal::compareTo).orElse(currentPrice);
        BigDecimal sum = prices.stream().reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal avg = sum.divide(BigDecimal.valueOf(n), 2, RoundingMode.HALF_UP);

        BigDecimal median = calculateMedian(prices);
        BigDecimal range = max.subtract(min);

        BigDecimal effectiveCurrent = currentPrice != null ? currentPrice : prices.get(n - 1);
        BigDecimal lowDistance = effectiveCurrent != null && min != null ? effectiveCurrent.subtract(min) : null;
        BigDecimal avgDistance = effectiveCurrent != null && avg != null ? effectiveCurrent.subtract(avg) : null;

        return PriceStatistics.builder()
                .currentPrice(effectiveCurrent)
                .historicalMin(min)
                .historicalMax(max)
                .historicalAvg(avg)
                .historicalMedian(median)
                .priceRange(range)
                .observationCount(n)
                .historicalLowDistance(lowDistance)
                .historicalAverageDistance(avgDistance)
                .build();
    }

    /**
     * Calculates median price from observation set.
     */
    public BigDecimal calculateMedian(List<BigDecimal> rawPrices) {
        if (rawPrices == null || rawPrices.isEmpty()) {
            return null;
        }
        List<BigDecimal> sorted = new ArrayList<>(rawPrices);
        Collections.sort(sorted);
        int size = sorted.size();
        if (size % 2 != 0) {
            return sorted.get(size / 2).setScale(2, RoundingMode.HALF_UP);
        } else {
            BigDecimal mid1 = sorted.get(size / 2 - 1);
            BigDecimal mid2 = sorted.get(size / 2);
            return mid1.add(mid2).divide(BigDecimal.valueOf(2), 2, RoundingMode.HALF_UP);
        }
    }

    /**
     * Calculates statistical volatility using the Coefficient of Variation (CV = sigma / mu).
     * Requires at least 3 observations. Returns INSUFFICIENT_DATA otherwise.
     */
    public VolatilityResult calculateVolatility(List<HistoricalPricePoint> priceSeries) {
        if (priceSeries == null || priceSeries.size() < MIN_OBSERVATIONS_FOR_VOLATILITY) {
            return new VolatilityResult(null, PriceVolatility.INSUFFICIENT_DATA);
        }

        List<Double> values = priceSeries.stream()
                .map(p -> p.getPrice().doubleValue())
                .toList();

        double mean = values.stream().mapToDouble(Double::doubleValue).average().orElse(0.0);
        if (mean <= 0.0) {
            return new VolatilityResult(null, PriceVolatility.INSUFFICIENT_DATA);
        }

        double variance = 0.0;
        int n = values.size();
        for (double v : values) {
            variance += Math.pow(v - mean, 2);
        }
        double stdDev = Math.sqrt(variance / (n - 1));
        double cv = stdDev / mean;

        PriceVolatility classification;
        if (cv < LOW_VOLATILITY_THRESHOLD) {
            classification = PriceVolatility.LOW;
        } else if (cv < MEDIUM_VOLATILITY_THRESHOLD) {
            classification = PriceVolatility.MEDIUM;
        } else {
            classification = PriceVolatility.HIGH;
        }

        // Round CV to 4 decimal places for precision reporting
        double roundedCv = BigDecimal.valueOf(cv).setScale(4, RoundingMode.HALF_UP).doubleValue();
        return new VolatilityResult(roundedCv, classification);
    }
}
