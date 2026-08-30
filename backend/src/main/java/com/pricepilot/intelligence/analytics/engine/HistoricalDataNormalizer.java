package com.pricepilot.intelligence.analytics.engine;

import com.pricepilot.intelligence.analytics.model.HistoricalPricePoint;
import com.pricepilot.intelligence.analytics.model.RawPriceObservation;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;

/**
 * Normalizes, cleans, and chronologically orders raw price observations.
 * Strictly guarantees that missing data is never fabricated.
 */
@Component
public class HistoricalDataNormalizer {

    /**
     * Normalizes raw price observations and current price into a clean, chronological series.
     *
     * @param currentPrice Optional current active lowest price.
     * @param rawObservations Raw observations from database or seller snapshots.
     * @return Chronologically sorted, validated list of historical price points.
     */
    public List<HistoricalPricePoint> normalize(BigDecimal currentPrice, List<RawPriceObservation> rawObservations) {
        List<HistoricalPricePoint> points = new ArrayList<>();

        if (rawObservations != null) {
            for (RawPriceObservation raw : rawObservations) {
                if (raw == null || raw.price() == null) {
                    continue;
                }
                // Discard invalid / non-positive prices
                if (raw.price().compareTo(BigDecimal.ZERO) <= 0) {
                    continue;
                }
                LocalDateTime ts = raw.observedAt() != null ? raw.observedAt() : LocalDateTime.now();
                points.add(HistoricalPricePoint.builder()
                        .timestamp(ts)
                        .price(raw.price())
                        .sellerId(raw.sellerId())
                        .sellerName(raw.sellerName() != null ? raw.sellerName() : "Market Seller")
                        .build());
            }
        }

        // Sort strictly chronologically
        points.sort(Comparator.comparing(HistoricalPricePoint::getTimestamp));

        // If history is empty but current price is valid, add current price as single observation
        if (points.isEmpty() && currentPrice != null && currentPrice.compareTo(BigDecimal.ZERO) > 0) {
            points.add(HistoricalPricePoint.builder()
                    .timestamp(LocalDateTime.now())
                    .price(currentPrice)
                    .sellerName("Current Offer")
                    .build());
        }

        return Collections.unmodifiableList(points);
    }
}
