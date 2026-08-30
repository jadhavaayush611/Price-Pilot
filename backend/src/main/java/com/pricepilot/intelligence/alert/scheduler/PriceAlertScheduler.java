package com.pricepilot.intelligence.alert.scheduler;

import com.pricepilot.intelligence.alert.service.PriceAlertService;
import com.pricepilot.productprice.ProductPriceRepository;
import com.pricepilot.productprice.dto.BestPriceProjection;
import com.pricepilot.watchlist.PriceWatchlistEntity;
import com.pricepilot.watchlist.PriceWatchlistRepository;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Lightweight scheduled background evaluation for price watchlists.
 * Evaluates active watchlists in bounded batches without N+1 query overhead.
 */
@Component
public class PriceAlertScheduler {

    private static final Logger log = LoggerFactory.getLogger(PriceAlertScheduler.class);
    private static final int BATCH_SIZE = 50;

    private final PriceWatchlistRepository watchlistRepository;
    private final ProductPriceRepository productPriceRepository;
    private final PriceAlertService priceAlertService;
    private final Counter runCounter;

    public PriceAlertScheduler(
            PriceWatchlistRepository watchlistRepository,
            ProductPriceRepository productPriceRepository,
            PriceAlertService priceAlertService,
            MeterRegistry meterRegistry) {
        this.watchlistRepository = watchlistRepository;
        this.productPriceRepository = productPriceRepository;
        this.priceAlertService = priceAlertService;
        this.runCounter = Counter.builder("pricepilot.alerts.scheduler.runs")
                .description("Total number of scheduled watchlist alert evaluations")
                .register(meterRegistry);
    }

    @Scheduled(fixedDelayString = "${pricepilot.alerts.scheduler.delay-ms:300000}", initialDelay = 60000)
    public void evaluateActiveWatchlists() {
        runCounter.increment();
        log.info("Starting scheduled price watchlist alert evaluation...");

        List<PriceWatchlistEntity> active = watchlistRepository.findAllActiveWatchlists();
        if (active.isEmpty()) {
            log.info("No active watchlists to evaluate.");
            return;
        }

        // Partition into bounded batches
        for (int i = 0; i < active.size(); i += BATCH_SIZE) {
            int end = Math.min(i + BATCH_SIZE, active.size());
            List<PriceWatchlistEntity> batch = active.subList(i, end);
            processBatch(batch);
        }

        log.info("Scheduled price watchlist alert evaluation finished for {} watchlists.", active.size());
    }

    private void processBatch(List<PriceWatchlistEntity> batch) {
        List<UUID> productIds = batch.stream()
                .map(w -> w.getProduct().getId())
                .distinct()
                .collect(Collectors.toList());

        List<BestPriceProjection> bestPrices = productPriceRepository.findBestPricesByProductIds(productIds);
        Map<UUID, BigDecimal> bestPriceMap = bestPrices.stream()
                .collect(Collectors.toMap(BestPriceProjection::getProductId, BestPriceProjection::getBestPrice));

        for (PriceWatchlistEntity watchlist : batch) {
            UUID prodId = watchlist.getProduct().getId();
            BigDecimal currentLowest = bestPriceMap.get(prodId);

            if (currentLowest != null) {
                BigDecimal previousBest = watchlist.getCurrentBestPrice();
                // If price changed or target condition requires evaluation
                if (previousBest == null || previousBest.compareTo(currentLowest) != 0 || currentLowest.compareTo(watchlist.getTargetPrice()) <= 0) {
                    try {
                        priceAlertService.processPriceUpdateEvent(prodId, previousBest, currentLowest, false);
                    } catch (Exception e) {
                        log.error("Error evaluating scheduled alerts for watchlist [id={}, product={}]",
                                watchlist.getId(), prodId, e);
                    }
                }
            }
        }
    }
}
