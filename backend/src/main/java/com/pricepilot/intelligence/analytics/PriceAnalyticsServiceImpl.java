package com.pricepilot.intelligence.analytics;

import com.pricepilot.analytics.ProductAnalyticsService;
import com.pricepilot.analytics.dto.ProductAnalyticsResponseDTO;
import com.pricepilot.intelligence.analytics.engine.AdvancedPriceAnalyticsEngine;
import com.pricepilot.intelligence.analytics.model.RawPriceObservation;
import com.pricepilot.pricehistory.PriceHistoryEntity;
import com.pricepilot.pricehistory.PriceHistoryRepository;
import com.pricepilot.product.ProductService;
import com.pricepilot.product.dto.ProductResponseDTO;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Service implementation for Advanced Price Intelligence engine.
 * Computes deterministic statistics, trends, volatility, price positions, purchase signals, and historical events.
 */
@Service("intelligencePriceAnalyticsService")
public class PriceAnalyticsServiceImpl implements PriceAnalyticsService {

    private static final Logger log = LoggerFactory.getLogger(PriceAnalyticsServiceImpl.class);

    private final ProductAnalyticsService coreProductAnalyticsService;
    private final ProductService productService;
    private final PriceHistoryRepository priceHistoryRepository;
    private final AdvancedPriceAnalyticsEngine priceAnalyticsEngine;
    private final MeterRegistry meterRegistry;

    private final Counter requestCounter;
    private final Counter insufficientDataCounter;
    private final Timer analyticsTimer;

    public PriceAnalyticsServiceImpl(
            ProductAnalyticsService coreProductAnalyticsService,
            ProductService productService,
            PriceHistoryRepository priceHistoryRepository,
            AdvancedPriceAnalyticsEngine priceAnalyticsEngine,
            MeterRegistry meterRegistry) {
        this.coreProductAnalyticsService = coreProductAnalyticsService;
        this.productService = productService;
        this.priceHistoryRepository = priceHistoryRepository;
        this.priceAnalyticsEngine = priceAnalyticsEngine;
        this.meterRegistry = meterRegistry;

        this.requestCounter = Counter.builder("pricepilot.analytics.requests.total")
                .description("Total number of advanced price analytics evaluation requests")
                .register(meterRegistry);
        this.insufficientDataCounter = Counter.builder("pricepilot.analytics.insufficient_data.total")
                .description("Total number of analytics requests with insufficient historical observations")
                .register(meterRegistry);
        this.analyticsTimer = Timer.builder("pricepilot.analytics.duration")
                .description("Latency distribution for price analytics processing")
                .register(meterRegistry);
    }

    @Override
    @Transactional(readOnly = true)
    @Cacheable(value = "price-analytics", key = "#productId", condition = "#productId != null")
    public ProductAnalyticsResponseDTO getProductAnalytics(UUID productId) {
        if (productId == null) {
            throw new IllegalArgumentException("Product ID cannot be null for price analytics");
        }

        requestCounter.increment();
        long startNanos = System.nanoTime();

        try {
            // 1. Fetch product to verify existence and get current active prices
            ProductResponseDTO product = productService.getProductById(productId);
            BigDecimal currentPrice = null;
            if (product.getPrices() != null && !product.getPrices().isEmpty()) {
                currentPrice = product.getPrices().stream()
                        .map(com.pricepilot.productprice.dto.ProductPriceResponseDTO::getCurrentPrice)
                        .filter(java.util.Objects::nonNull)
                        .min(BigDecimal::compareTo)
                        .orElse(null);
            }

            // 2. Fetch engagement metrics (views, saves, watchlists)
            ProductAnalyticsResponseDTO engagement = coreProductAnalyticsService.getProductAnalytics(productId);

            // 3. Query historical records chronologically with seller joined
            List<PriceHistoryEntity> histories = priceHistoryRepository.findByProductIdChronological(productId);

            // 4. Map to decoupled raw observations without leaking entities
            List<RawPriceObservation> rawObservations = new ArrayList<>();
            for (PriceHistoryEntity ph : histories) {
                if (ph.getOldPrice() != null && ph.getOldPrice().compareTo(BigDecimal.ZERO) > 0 && rawObservations.isEmpty()) {
                    // First recorded old price serves as earliest observation baseline
                    LocalDateTime initialTime = ph.getChangedAt() != null ? ph.getChangedAt().minusHours(1) : ph.getCreatedAt();
                    rawObservations.add(new RawPriceObservation(
                            ph.getOldPrice(),
                            initialTime,
                            ph.getSeller() != null ? ph.getSeller().getId() : null,
                            ph.getSeller() != null ? ph.getSeller().getName() : null
                    ));
                }
                rawObservations.add(new RawPriceObservation(
                        ph.getNewPrice(),
                        ph.getChangedAt(),
                        ph.getSeller() != null ? ph.getSeller().getId() : null,
                        ph.getSeller() != null ? ph.getSeller().getName() : null
                ));
            }

            // 5. Execute analytics pipeline
            AdvancedPriceAnalyticsEngine.AnalysisResult analysis = priceAnalyticsEngine.analyze(currentPrice, rawObservations);

            // 6. Metrics & logging
            if (analysis.statistics().getObservationCount() < 2) {
                insufficientDataCounter.increment();
            }
            meterRegistry.counter("pricepilot.analytics.signal.distribution",
                    "signal", analysis.signal().signal().name()).increment();

            log.info("Completed price intelligence analysis for product: {} | Observations: {} | Signal: {} | Deal: {}",
                    productId, analysis.statistics().getObservationCount(),
                    analysis.signal().signal(), analysis.position().classification());

            // 7. Assemble composite response DTO
            return ProductAnalyticsResponseDTO.builder()
                    .productId(productId)
                    .viewCount(engagement.getViewCount())
                    .saveCount(engagement.getSaveCount())
                    .watchlistCount(engagement.getWatchlistCount())
                    .priceChangeCount(engagement.getPriceChangeCount())
                    .trendingScore(engagement.getTrendingScore())
                    .currentPrice(analysis.statistics().getCurrentPrice())
                    .historicalMin(analysis.statistics().getHistoricalMin())
                    .historicalMax(analysis.statistics().getHistoricalMax())
                    .historicalAvg(analysis.statistics().getHistoricalAvg())
                    .historicalMedian(analysis.statistics().getHistoricalMedian())
                    .priceRange(analysis.statistics().getPriceRange())
                    .volatilityValue(analysis.volatility().value())
                    .volatility(analysis.volatility().classification())
                    .trend(analysis.trend().trend())
                    .trendPercentage(analysis.trend().percentageChange())
                    .pricePositionScore(analysis.position().score())
                    .dealQuality(analysis.position().classification())
                    .purchaseSignal(analysis.signal().signal())
                    .purchaseSignalReason(analysis.signal().reason())
                    .supportingEvidence(analysis.signal().evidence())
                    .observationCount(analysis.statistics().getObservationCount())
                    .historicalLowDistance(analysis.statistics().getHistoricalLowDistance())
                    .historicalAverageDistance(analysis.statistics().getHistoricalAverageDistance())
                    .historicalEvents(analysis.events())
                    .priceSeries(analysis.normalizedSeries())
                    .analyzedAt(LocalDateTime.now())
                    .build();

        } finally {
            analyticsTimer.record(System.nanoTime() - startNanos, java.util.concurrent.TimeUnit.NANOSECONDS);
        }
    }
}
