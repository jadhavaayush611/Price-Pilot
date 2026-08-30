package com.pricepilot.intelligence.dashboard.service;

import com.pricepilot.analytics.dto.ProductAnalyticsResponseDTO;
import com.pricepilot.intelligence.alert.dto.PriceAlertResponseDTO;
import com.pricepilot.intelligence.alert.entity.PriceAlertEntity;
import com.pricepilot.intelligence.alert.repository.PriceAlertRepository;
import com.pricepilot.intelligence.analytics.PriceAnalyticsService;
import com.pricepilot.intelligence.analytics.model.DealQuality;
import com.pricepilot.intelligence.comparison.repository.SavedComparisonRepository;
import com.pricepilot.intelligence.dashboard.dto.*;
import com.pricepilot.intelligence.dashboard.ranking.AttentionRankingStrategy;
import com.pricepilot.intelligence.recommendation.RecommendationService;
import com.pricepilot.intelligence.recommendation.dto.RecommendationResponse;
import com.pricepilot.product.ProductEntity;
import com.pricepilot.savedproduct.SavedProductRepository;
import com.pricepilot.watchlist.PriceWatchlistEntity;
import com.pricepilot.watchlist.PriceWatchlistRepository;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class DashboardV2ServiceImpl implements DashboardV2Service {

    private static final Logger log = LoggerFactory.getLogger(DashboardV2ServiceImpl.class);

    private static final int MAX_WATCHLISTS = 15;
    private static final int MAX_ATTENTION_ITEMS = 6;
    private static final int MAX_OPPORTUNITIES = 6;
    private static final int MAX_RECENT_ALERTS = 8;
    private static final int MAX_RECOMMENDATIONS = 4;
    private static final int MAX_RECENT_ACTIVITY = 10;

    private final PriceWatchlistRepository watchlistRepository;
    private final PriceAnalyticsService priceAnalyticsService;
    private final PriceAlertRepository alertRepository;
    private final RecommendationService recommendationService;
    private final SavedComparisonRepository savedComparisonRepository;
    private final SavedProductRepository savedProductRepository;
    private final AttentionRankingStrategy attentionRankingStrategy;

    private final Counter requestCounter;
    private final Counter failureCounter;
    private final Counter emptyDashboardCounter;
    private final Timer latencyTimer;

    public DashboardV2ServiceImpl(
            PriceWatchlistRepository watchlistRepository,
            PriceAnalyticsService priceAnalyticsService,
            PriceAlertRepository alertRepository,
            RecommendationService recommendationService,
            SavedComparisonRepository savedComparisonRepository,
            SavedProductRepository savedProductRepository,
            AttentionRankingStrategy attentionRankingStrategy,
            MeterRegistry meterRegistry) {
        this.watchlistRepository = watchlistRepository;
        this.priceAnalyticsService = priceAnalyticsService;
        this.alertRepository = alertRepository;
        this.recommendationService = recommendationService;
        this.savedComparisonRepository = savedComparisonRepository;
        this.savedProductRepository = savedProductRepository;
        this.attentionRankingStrategy = attentionRankingStrategy;

        this.requestCounter = Counter.builder("pricepilot.dashboard.requests")
                .description("Total Dashboard V2 aggregation requests")
                .register(meterRegistry);
        this.failureCounter = Counter.builder("pricepilot.dashboard.failures")
                .description("Failed Dashboard V2 requests")
                .register(meterRegistry);
        this.emptyDashboardCounter = Counter.builder("pricepilot.dashboard.empty")
                .description("Dashboard V2 rendered in initial empty state")
                .register(meterRegistry);
        this.latencyTimer = Timer.builder("pricepilot.dashboard.latency")
                .description("Dashboard V2 aggregation latency")
                .register(meterRegistry);
    }

    @Override
    @Cacheable(value = "dashboard-v2", key = "#userId")
    public DashboardV2ResponseDTO getDashboardV2(UUID userId, String email) {
        requestCounter.increment();
        long start = System.nanoTime();

        try {
            // 1. Fetch user's watchlists eagerly loaded with product (bounded)
            List<PriceWatchlistEntity> allWatchlists = watchlistRepository.findAllByUserIdWithProduct(userId);
            int totalActiveWatchlistsCount = (int) allWatchlists.stream().filter(PriceWatchlistEntity::isActive).count();
            List<PriceWatchlistEntity> activeWatchlists = allWatchlists.stream()
                    .filter(PriceWatchlistEntity::isActive)
                    .limit(MAX_WATCHLISTS)
                    .collect(Collectors.toList());

            if (allWatchlists.isEmpty()) {
                emptyDashboardCounter.increment();
            }

            // 2. Fetch unread alerts & recent alerts (bounded)
            long unreadAlertsCount = 0;
            List<PriceAlertResponseDTO> recentAlerts = Collections.emptyList();
            Set<UUID> productsWithUnreadAlerts = new HashSet<>();
            try {
                unreadAlertsCount = alertRepository.countUnreadByUserId(userId);
                List<PriceAlertEntity> alertEntities = alertRepository.findAllByUserIdWithProduct(
                        userId, PageRequest.of(0, MAX_RECENT_ALERTS)
                ).getContent();

                recentAlerts = alertEntities.stream()
                        .map(PriceAlertResponseDTO::fromEntity)
                        .collect(Collectors.toList());

                // Track which products have unread alerts
                for (PriceAlertEntity pa : alertEntities) {
                    if (pa.getReadAt() == null && pa.getProduct() != null) {
                        productsWithUnreadAlerts.add(pa.getProduct().getId());
                    }
                }
            } catch (Exception e) {
                log.warn("Unable to fetch alerts for dashboard user {}: {}", userId, e.getMessage());
            }

            // 3. Evaluate each watched product against Phase 4 price analytics
            List<WatchedProductCardDTO> watchedCards = new ArrayList<>();
            List<AttentionItemDTO> attentionCandidates = new ArrayList<>();
            List<PriceOpportunityDTO> opportunities = new ArrayList<>();
            List<RecentActivityDTO> activityList = new ArrayList<>();

            int historicalLowCount = 0;
            int goodOrExcellentDealCount = 0;
            int recentPriceDropCount = 0;

            for (PriceWatchlistEntity w : activeWatchlists) {
                ProductEntity product = w.getProduct();
                if (product == null) continue;

                UUID prodId = product.getId();
                BigDecimal currentPrice = w.getCurrentBestPrice();
                BigDecimal targetPrice = w.getTargetPrice();
                boolean targetMet = targetPrice != null && currentPrice != null && currentPrice.compareTo(targetPrice) <= 0;

                ProductAnalyticsResponseDTO analytics = null;
                try {
                    analytics = priceAnalyticsService.getProductAnalytics(prodId);
                } catch (Exception e) {
                    log.warn("Price analytics unavailable for watched product {}: {}", prodId, e.getMessage());
                }

                // Summary metrics evaluation
                if (analytics != null) {
                    if (analytics.getHistoricalMin() != null && currentPrice != null
                            && currentPrice.compareTo(analytics.getHistoricalMin()) <= 0) {
                        historicalLowCount++;
                    }
                    if (analytics.getDealQuality() == DealQuality.EXCELLENT_DEAL || analytics.getDealQuality() == DealQuality.GOOD_DEAL) {
                        goodOrExcellentDealCount++;
                    }
                    if (analytics.getPriceChangeCount() > 0 && analytics.getTrendPercentage() != null && analytics.getTrendPercentage() < 0) {
                        recentPriceDropCount++;
                    }
                }

                // Build Watched Card
                WatchedProductCardDTO card = WatchedProductCardDTO.builder()
                        .productId(prodId)
                        .watchlistId(w.getId())
                        .productName(product.getName())
                        .brand(product.getBrand())
                        .category(product.getCategory())
                        .imageUrl(product.getImageUrl())
                        .currentPrice(currentPrice)
                        .targetPrice(targetPrice)
                        .historicalMin(analytics != null ? analytics.getHistoricalMin() : null)
                        .historicalAvg(analytics != null ? analytics.getHistoricalAvg() : null)
                        .volatility(analytics != null && analytics.getVolatility() != null ? analytics.getVolatility().name() : null)
                        .trend(analytics != null && analytics.getTrend() != null ? analytics.getTrend().name() : null)
                        .trendPercentage(analytics != null ? analytics.getTrendPercentage() : null)
                        .dealQuality(analytics != null && analytics.getDealQuality() != null ? analytics.getDealQuality().name() : null)
                        .purchaseSignal(analytics != null && analytics.getPurchaseSignal() != null ? analytics.getPurchaseSignal().name() : null)
                        .targetMet(targetMet)
                        .active(w.isActive())
                        .build();
                watchedCards.add(card);

                // Attention Item Candidate
                AttentionItemDTO attention = attentionRankingStrategy.evaluateAttentionItem(
                        w, analytics, productsWithUnreadAlerts.contains(prodId)
                );
                if (attention != null) {
                    attentionCandidates.add(attention);
                }

                // Price Opportunity Candidate
                if (analytics != null && (analytics.getDealQuality() == DealQuality.EXCELLENT_DEAL
                        || analytics.getDealQuality() == DealQuality.GOOD_DEAL
                        || (analytics.getHistoricalMin() != null && currentPrice != null && currentPrice.compareTo(analytics.getHistoricalMin()) <= 0)
                        || targetMet)) {

                    String evidence = analytics.getSupportingEvidence() != null && !analytics.getSupportingEvidence().isEmpty()
                            ? analytics.getSupportingEvidence().get(0)
                            : (analytics.getPurchaseSignalReason() != null ? analytics.getPurchaseSignalReason() : "Current price is at a favorable position.");

                    opportunities.add(PriceOpportunityDTO.builder()
                            .productId(prodId)
                            .productName(product.getName())
                            .productImageUrl(product.getImageUrl())
                            .brand(product.getBrand())
                            .currentPrice(currentPrice)
                            .historicalMin(analytics.getHistoricalMin())
                            .historicalAvg(analytics.getHistoricalAvg())
                            .dealQuality(analytics.getDealQuality() != null ? analytics.getDealQuality().name() : null)
                            .purchaseSignal(analytics.getPurchaseSignal() != null ? analytics.getPurchaseSignal().name() : null)
                            .keyEvidence(evidence)
                            .navigationUrl("/product/" + prodId)
                            .build());
                }

                // Recent Activity Extraction from Analytics Historical Events
                if (analytics != null && analytics.getHistoricalEvents() != null) {
                    for (var event : analytics.getHistoricalEvents()) {
                        activityList.add(RecentActivityDTO.builder()
                                .id(prodId + "-" + event.getOccurredAt())
                                .productId(prodId)
                                .productName(product.getName())
                                .productImageUrl(product.getImageUrl())
                                .eventType(event.getEventType() != null ? event.getEventType().name() : "PRICE_EVENT")
                                .title(event.getDescription() != null ? event.getDescription() : "Price shifted")
                                .description(String.format("Observed at $%s", event.getResultingPrice()))
                                .observedPrice(event.getResultingPrice())
                                .amountChange(event.getAmountChange())
                                .percentageChange(event.getPercentageChange())
                                .timestamp(event.getOccurredAt())
                                .build());
                    }
                }
            }

            // Deterministic Attention Ranking
            attentionCandidates.sort(attentionRankingStrategy.comparator());
            List<AttentionItemDTO> sortedAttention = attentionCandidates.stream()
                    .limit(MAX_ATTENTION_ITEMS)
                    .collect(Collectors.toList());

            // Limit Opportunities
            List<PriceOpportunityDTO> sortedOpportunities = opportunities.stream()
                    .limit(MAX_OPPORTUNITIES)
                    .collect(Collectors.toList());

            // Sort Activity Chronologically Descending
            activityList.sort((a, b) -> {
                if (a.getTimestamp() == null && b.getTimestamp() == null) return 0;
                if (a.getTimestamp() == null) return 1;
                if (b.getTimestamp() == null) return -1;
                return b.getTimestamp().compareTo(a.getTimestamp());
            });
            List<RecentActivityDTO> sortedActivity = activityList.stream()
                    .limit(MAX_RECENT_ACTIVITY)
                    .collect(Collectors.toList());

            // 4. Counts for Saved Comparisons & Saved Products
            long savedComparisonsCount = 0;
            try {
                savedComparisonsCount = savedComparisonRepository.countByUserId(userId);
            } catch (Exception e) {
                log.warn("Saved comparisons count unavailable for user {}: {}", userId, e.getMessage());
            }

            long savedProductsCount = 0;
            try {
                savedProductsCount = savedProductRepository.countByUserId(userId);
            } catch (Exception e) {
                log.warn("Saved products count unavailable for user {}: {}", userId, e.getMessage());
            }

            // 5. Personal Recommendations (Failure isolated)
            DashboardRecommendationsDTO recommendationsDTO = null;
            try {
                RecommendationResponse recResponse = recommendationService.getPersonalizedRecommendations(userId, MAX_RECOMMENDATIONS);
                if (recResponse != null && recResponse.getRecommendedProducts() != null) {
                    List<DashboardRecommendationsDTO.RecommendationItemDTO> recItems = recResponse.getRecommendedProducts().stream()
                            .limit(MAX_RECOMMENDATIONS)
                            .map(rp -> {
                                com.pricepilot.intelligence.recommendation.dto.ProductScore matchedScore = null;
                                if (recResponse.getScores() != null) {
                                    for (com.pricepilot.intelligence.recommendation.dto.ProductScore s : recResponse.getScores()) {
                                        if (s.getProductId().equals(rp.getId())) {
                                            matchedScore = s;
                                            break;
                                        }
                                    }
                                }

                                BigDecimal minPrice = null;
                                if (rp.getPrices() != null && !rp.getPrices().isEmpty()) {
                                    minPrice = rp.getPrices().stream()
                                            .map(com.pricepilot.productprice.dto.ProductPriceResponseDTO::getCurrentPrice)
                                            .filter(Objects::nonNull)
                                            .min(BigDecimal::compareTo)
                                            .orElse(null);
                                }

                                Double score = matchedScore != null ? Double.valueOf(matchedScore.getOverallScore()) : recResponse.getScore();

                                return DashboardRecommendationsDTO.RecommendationItemDTO.builder()
                                        .productId(rp.getId())
                                        .productName(rp.getName())
                                        .productImageUrl(rp.getImageUrl())
                                        .brand(rp.getBrand())
                                        .currentPrice(minPrice)
                                        .recommendationType(recResponse.getRecommendationType())
                                        .score(score)
                                        .confidence(recResponse.getConfidence())
                                        .keyReason(matchedScore != null && matchedScore.getRecommendationBadge() != null
                                                ? matchedScore.getRecommendationBadge() : "Recommended match")
                                        .explanation(recResponse.getExplanation())
                                        .build();
                            })
                            .collect(Collectors.toList());

                    recommendationsDTO = DashboardRecommendationsDTO.builder()
                            .items(recItems)
                            .strategyUsed(recResponse.getStrategyUsed())
                            .generatedAt(recResponse.getGeneratedAt() != null ? recResponse.getGeneratedAt().toString() : LocalDateTime.now().toString())
                            .available(true)
                            .build();
                }
            } catch (Exception e) {
                log.warn("Personal recommendations unavailable for dashboard user {}: {}", userId, e.getMessage());
                recommendationsDTO = DashboardRecommendationsDTO.builder()
                        .items(Collections.emptyList())
                        .strategyUsed("UNAVAILABLE")
                        .generatedAt(LocalDateTime.now().toString())
                        .available(false)
                        .build();
            }

            // Assemble Overview Metrics
            DashboardOverviewDTO overview = DashboardOverviewDTO.builder()
                    .activeWatchlistsCount(totalActiveWatchlistsCount)
                    .unreadAlertsCount(unreadAlertsCount)
                    .historicalLowCount(historicalLowCount)
                    .goodOrExcellentDealCount(goodOrExcellentDealCount)
                    .recentPriceDropCount(recentPriceDropCount)
                    .savedComparisonsCount(savedComparisonsCount)
                    .savedProductsCount(savedProductsCount)
                    .build();

            DashboardV2ResponseDTO response = DashboardV2ResponseDTO.builder()
                    .overview(overview)
                    .attentionItems(sortedAttention)
                    .priceOpportunities(sortedOpportunities)
                    .watchedProducts(watchedCards)
                    .recentAlerts(recentAlerts)
                    .recommendations(recommendationsDTO)
                    .recentActivity(sortedActivity)
                    .generatedAt(LocalDateTime.now())
                    .build();

            latencyTimer.record(System.nanoTime() - start, java.util.concurrent.TimeUnit.NANOSECONDS);
            return response;

        } catch (Exception e) {
            failureCounter.increment();
            log.error("Failed to generate Dashboard V2 for user {}: {}", userId, e.getMessage(), e);
            throw e;
        }
    }
}
