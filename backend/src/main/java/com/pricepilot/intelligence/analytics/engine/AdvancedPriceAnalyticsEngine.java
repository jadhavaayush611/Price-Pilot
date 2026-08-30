package com.pricepilot.intelligence.analytics.engine;

import com.pricepilot.intelligence.analytics.model.*;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Orchestrates the modular price intelligence pipeline:
 * Raw Observations -> Normalization -> Statistics & Volatility -> Trend -> Position -> Purchase Signal -> Event Detection.
 */
@Component
public class AdvancedPriceAnalyticsEngine {

    private final HistoricalDataNormalizer normalizer;
    private final StatisticalPriceAnalyzer statisticalAnalyzer;
    private final PriceTrendAnalyzer trendAnalyzer;
    private final PricePositionAnalyzer positionAnalyzer;
    private final PurchaseSignalGenerator signalGenerator;
    private final PriceEventDetector eventDetector;

    public AdvancedPriceAnalyticsEngine(
            HistoricalDataNormalizer normalizer,
            StatisticalPriceAnalyzer statisticalAnalyzer,
            PriceTrendAnalyzer trendAnalyzer,
            PricePositionAnalyzer positionAnalyzer,
            PurchaseSignalGenerator signalGenerator,
            PriceEventDetector eventDetector) {
        this.normalizer = normalizer;
        this.statisticalAnalyzer = statisticalAnalyzer;
        this.trendAnalyzer = trendAnalyzer;
        this.positionAnalyzer = positionAnalyzer;
        this.signalGenerator = signalGenerator;
        this.eventDetector = eventDetector;
    }

    public record AnalysisResult(
            PriceStatistics statistics,
            StatisticalPriceAnalyzer.VolatilityResult volatility,
            PriceTrendAnalyzer.TrendResult trend,
            PricePositionAnalyzer.PositionResult position,
            PurchaseSignalGenerator.SignalResult signal,
            List<PriceDropRecoveryEvent> events,
            List<HistoricalPricePoint> normalizedSeries
    ) {}

    /**
     * Executes end-to-end deterministic price intelligence analysis.
     */
    public AnalysisResult analyze(BigDecimal currentPrice, List<RawPriceObservation> rawObservations) {
        // 1. Data Normalization
        List<HistoricalPricePoint> series = normalizer.normalize(currentPrice, rawObservations);

        // 2. Statistical Analysis & Volatility
        PriceStatistics stats = statisticalAnalyzer.calculateStatistics(currentPrice, series);
        StatisticalPriceAnalyzer.VolatilityResult volResult = statisticalAnalyzer.calculateVolatility(series);

        // 3. Trend Analysis
        PriceTrendAnalyzer.TrendResult trendResult = trendAnalyzer.analyzeTrend(series);

        // 4. Price Position & Deal Quality
        PricePositionAnalyzer.PositionResult posResult = positionAnalyzer.evaluatePosition(stats);

        // 5. Purchase Signal Generation
        PurchaseSignalGenerator.SignalResult signalResult = signalGenerator.generateSignal(
                stats,
                trendResult.trend(),
                trendResult.percentageChange(),
                volResult.classification(),
                volResult.value(),
                posResult.classification(),
                posResult.score()
        );

        // 6. Historical Milestone Event Detection
        List<PriceDropRecoveryEvent> events = eventDetector.detectEvents(series);

        return new AnalysisResult(stats, volResult, trendResult, posResult, signalResult, events, series);
    }
}
