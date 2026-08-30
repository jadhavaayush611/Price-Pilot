package com.pricepilot.intelligence.analytics.engine;

import com.pricepilot.intelligence.analytics.model.HistoricalPricePoint;
import com.pricepilot.intelligence.analytics.model.PriceDropRecoveryEvent;
import com.pricepilot.intelligence.analytics.model.PriceDropRecoveryEvent.EventType;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Detects significant price movements, milestone lows/highs, and drop-recovery cycles along the chronological timeline.
 */
@Component
public class PriceEventDetector {

    public static final double MAJOR_DROP_THRESHOLD_PERCENT = -10.0;
    public static final double MAJOR_INCREASE_THRESHOLD_PERCENT = 10.0;
    public static final double RECOVERY_THRESHOLD_PERCENT = 8.0;
    public static final double MINIMUM_NOISE_THRESHOLD_PERCENT = 2.0;

    /**
     * Scans chronological price series and identifies milestone events.
     */
    public List<PriceDropRecoveryEvent> detectEvents(List<HistoricalPricePoint> priceSeries) {
        if (priceSeries == null || priceSeries.size() < 2) {
            return Collections.emptyList();
        }

        List<PriceDropRecoveryEvent> events = new ArrayList<>();

        BigDecimal runningMin = priceSeries.get(0).getPrice();
        BigDecimal runningMax = priceSeries.get(0).getPrice();
        boolean previousWasDrop = false;

        for (int i = 1; i < priceSeries.size(); i++) {
            HistoricalPricePoint prev = priceSeries.get(i - 1);
            HistoricalPricePoint curr = priceSeries.get(i);

            BigDecimal prevPrice = prev.getPrice();
            BigDecimal currPrice = curr.getPrice();

            if (prevPrice.compareTo(BigDecimal.ZERO) <= 0) {
                continue;
            }

            BigDecimal diffAmount = currPrice.subtract(prevPrice);
            double diffPercent = (diffAmount.doubleValue() / prevPrice.doubleValue()) * 100.0;
            double roundedPercent = BigDecimal.valueOf(diffPercent).setScale(2, RoundingMode.HALF_UP).doubleValue();

            // Check for new historical low
            if (currPrice.compareTo(runningMin) < 0) {
                runningMin = currPrice;
                events.add(PriceDropRecoveryEvent.builder()
                        .eventType(EventType.NEW_HISTORICAL_LOW)
                        .percentageChange(roundedPercent)
                        .amountChange(diffAmount)
                        .resultingPrice(currPrice)
                        .occurredAt(curr.getTimestamp())
                        .description(String.format("Established new all-time low at $%s (%s%% change)", currPrice, roundedPercent))
                        .build());
            }

            // Check for new historical high
            if (currPrice.compareTo(runningMax) > 0) {
                runningMax = currPrice;
                events.add(PriceDropRecoveryEvent.builder()
                        .eventType(EventType.NEW_HISTORICAL_HIGH)
                        .percentageChange(roundedPercent)
                        .amountChange(diffAmount)
                        .resultingPrice(currPrice)
                        .occurredAt(curr.getTimestamp())
                        .description(String.format("Established new all-time high at $%s (+%s%% change)", currPrice, roundedPercent))
                        .build());
            }

            // Significant drop or increase
            if (diffPercent <= MAJOR_DROP_THRESHOLD_PERCENT) {
                previousWasDrop = true;
                events.add(PriceDropRecoveryEvent.builder()
                        .eventType(EventType.MAJOR_DROP)
                        .percentageChange(roundedPercent)
                        .amountChange(diffAmount)
                        .resultingPrice(currPrice)
                        .occurredAt(curr.getTimestamp())
                        .description(String.format("Significant price drop of %s%% (-$%s)", Math.abs(roundedPercent), diffAmount.abs()))
                        .build());
            } else if (diffPercent >= MAJOR_INCREASE_THRESHOLD_PERCENT) {
                if (previousWasDrop && diffPercent >= RECOVERY_THRESHOLD_PERCENT) {
                    events.add(PriceDropRecoveryEvent.builder()
                            .eventType(EventType.RECOVERY_AFTER_DROP)
                            .percentageChange(roundedPercent)
                            .amountChange(diffAmount)
                            .resultingPrice(currPrice)
                            .occurredAt(curr.getTimestamp())
                            .description(String.format("Price rebounded by +%s%% (+$%s) following previous price drop", roundedPercent, diffAmount))
                            .build());
                } else {
                    events.add(PriceDropRecoveryEvent.builder()
                            .eventType(EventType.PRICE_INCREASE)
                            .percentageChange(roundedPercent)
                            .amountChange(diffAmount)
                            .resultingPrice(currPrice)
                            .occurredAt(curr.getTimestamp())
                            .description(String.format("Price increased by +%s%% (+$%s)", roundedPercent, diffAmount))
                            .build());
                }
                previousWasDrop = false;
            } else if (Math.abs(diffPercent) >= MINIMUM_NOISE_THRESHOLD_PERCENT) {
                previousWasDrop = diffPercent < 0;
            }
        }

        return Collections.unmodifiableList(events);
    }
}
