package com.pricepilot.intelligence.dashboard.ranking;

import com.pricepilot.analytics.dto.ProductAnalyticsResponseDTO;
import com.pricepilot.intelligence.analytics.model.DealQuality;
import com.pricepilot.intelligence.analytics.model.PriceTrend;
import com.pricepilot.intelligence.analytics.model.PurchaseSignal;
import com.pricepilot.intelligence.dashboard.dto.AttentionItemDTO;
import com.pricepilot.watchlist.PriceWatchlistEntity;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.*;

/**
 * Deterministic attention-ranking engine for Dashboard V2.
 * Scores watched items based on objective mathematical and historical price signals.
 * Does NOT use an LLM. Guarantees deterministic tie-breaking.
 */
@Component
public class AttentionRankingStrategy {

    public AttentionItemDTO evaluateAttentionItem(
            PriceWatchlistEntity watchlist,
            ProductAnalyticsResponseDTO analytics,
            boolean hasUnreadAlert) {

        if (watchlist == null || watchlist.getProduct() == null) {
            return null;
        }

        UUID productId = watchlist.getProduct().getId();
        String productName = watchlist.getProduct().getName();
        String brand = watchlist.getProduct().getBrand();
        String imageUrl = watchlist.getProduct().getImageUrl();

        BigDecimal currentPrice = watchlist.getCurrentBestPrice();
        BigDecimal targetPrice = watchlist.getTargetPrice();

        int score = 0;
        List<String> evidence = new ArrayList<>();
        String primaryReason = "Monitored item in active watchlist";

        // Signal 1: Target Price Met (+100)
        boolean targetMet = targetPrice != null && currentPrice != null && currentPrice.compareTo(targetPrice) <= 0;
        if (targetMet) {
            score += 100;
            primaryReason = String.format("Target price reached! Current: $%s (Target: $%s)", currentPrice, targetPrice);
            evidence.add(String.format("Current price ($%s) meets or beats your target ($%s)", currentPrice, targetPrice));
        }

        // Signal 2: Historical Low (+90)
        boolean isHistoricalLow = analytics != null && analytics.getHistoricalMin() != null && currentPrice != null
                && currentPrice.compareTo(analytics.getHistoricalMin()) <= 0;
        if (isHistoricalLow) {
            score += 90;
            if (!targetMet) {
                primaryReason = String.format("At all-time historical low ($%s)", currentPrice);
            }
            evidence.add("Current price matches or establishes a recorded all-time low");
        }

        // Signal 3: Deal Quality (+70 for EXCELLENT, +50 for GOOD)
        if (analytics != null && analytics.getDealQuality() != null) {
            DealQuality deal = analytics.getDealQuality();
            if (deal == DealQuality.EXCELLENT_DEAL) {
                score += 70;
                if (!targetMet && !isHistoricalLow) {
                    primaryReason = "Classified as an EXCELLENT DEAL";
                }
                evidence.add("Phase 4 analytics classifies this product as an EXCELLENT DEAL");
            } else if (deal == DealQuality.GOOD_DEAL) {
                score += 50;
                if (!targetMet && !isHistoricalLow) {
                    primaryReason = "Classified as a GOOD DEAL";
                }
                evidence.add("Phase 4 analytics classifies this product as a GOOD DEAL");
            }
        }

        // Signal 4: Purchase Signal (+60 for BUY_NOW, +40 for GOOD_TIME)
        if (analytics != null && analytics.getPurchaseSignal() != null) {
            PurchaseSignal signal = analytics.getPurchaseSignal();
            if (signal == PurchaseSignal.BUY_NOW) {
                score += 60;
                evidence.add("Recommended action is BUY NOW based on historical discount depth");
            } else if (signal == PurchaseSignal.GOOD_TIME) {
                score += 40;
                evidence.add("Recommended action is GOOD TIME TO BUY");
            }
        }

        // Signal 5: Trend (+30 for FALLING)
        if (analytics != null && analytics.getTrend() == PriceTrend.FALLING) {
            score += 30;
            evidence.add(String.format("Recent trajectory reflects a falling trend (%s%%)",
                    analytics.getTrendPercentage() != null ? analytics.getTrendPercentage() : 0.0));
        }

        // Signal 6: Unread alert exists (+25)
        if (hasUnreadAlert) {
            score += 25;
            evidence.add("Unread price alert waiting for review");
        }

        // Only surface as an attention item if urgency score indicates notable action
        if (score < 40) {
            return null;
        }

        String urgencyLevel;
        if (score >= 120) {
            urgencyLevel = "CRITICAL";
        } else if (score >= 80) {
            urgencyLevel = "HIGH";
        } else {
            urgencyLevel = "MEDIUM";
        }

        return AttentionItemDTO.builder()
                .productId(productId)
                .productName(productName)
                .productImageUrl(imageUrl)
                .brand(brand)
                .urgencyScore(score)
                .urgencyLevel(urgencyLevel)
                .primaryReason(primaryReason)
                .supportingEvidence(evidence)
                .currentPrice(currentPrice)
                .targetPrice(targetPrice)
                .dealQuality(analytics != null && analytics.getDealQuality() != null ? analytics.getDealQuality().name() : null)
                .purchaseSignal(analytics != null && analytics.getPurchaseSignal() != null ? analytics.getPurchaseSignal().name() : null)
                .navigationUrl("/product/" + productId)
                .build();
    }

    /**
     * Deterministic tie-breaking comparator:
     * 1. Urgency score descending
     * 2. Current price ascending (better budget first)
     * 3. Product ID string comparison (100% deterministic tie-breaker)
     */
    public Comparator<AttentionItemDTO> comparator() {
        return (a, b) -> {
            int cmp = Integer.compare(b.getUrgencyScore(), a.getUrgencyScore());
            if (cmp != 0) {
                return cmp;
            }
            if (a.getCurrentPrice() != null && b.getCurrentPrice() != null) {
                cmp = a.getCurrentPrice().compareTo(b.getCurrentPrice());
                if (cmp != 0) {
                    return cmp;
                }
            }
            return a.getProductId().toString().compareTo(b.getProductId().toString());
        };
    }
}
