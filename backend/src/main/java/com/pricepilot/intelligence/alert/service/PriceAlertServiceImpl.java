package com.pricepilot.intelligence.alert.service;

import com.pricepilot.analytics.dto.ProductAnalyticsResponseDTO;
import com.pricepilot.exception.ResourceNotFoundException;
import com.pricepilot.intelligence.alert.delivery.NotificationDeliveryService;
import com.pricepilot.intelligence.alert.dto.PriceAlertResponseDTO;
import com.pricepilot.intelligence.alert.dto.UpdateWatchlistAlertPreferenceRequestDTO;
import com.pricepilot.intelligence.alert.dto.WatchlistAlertPreferenceDTO;
import com.pricepilot.intelligence.alert.engine.AlertRuleEvaluator;
import com.pricepilot.intelligence.alert.entity.PriceAlertEntity;
import com.pricepilot.intelligence.alert.entity.WatchlistAlertPreferenceEntity;
import com.pricepilot.intelligence.alert.model.AlertType;
import com.pricepilot.intelligence.alert.repository.PriceAlertRepository;
import com.pricepilot.intelligence.alert.repository.WatchlistAlertPreferenceRepository;
import com.pricepilot.intelligence.analytics.PriceAnalyticsService;
import com.pricepilot.watchlist.PriceWatchlistEntity;
import com.pricepilot.watchlist.PriceWatchlistRepository;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class PriceAlertServiceImpl implements PriceAlertService {

    private static final Logger log = LoggerFactory.getLogger(PriceAlertServiceImpl.class);

    private final PriceAlertRepository alertRepository;
    private final WatchlistAlertPreferenceRepository preferenceRepository;
    private final PriceWatchlistRepository watchlistRepository;
    private final AlertRuleEvaluator alertRuleEvaluator;
    private final NotificationDeliveryService deliveryService;
    private final PriceAnalyticsService priceAnalyticsService;
    private final MeterRegistry meterRegistry;

    private final Counter evaluatedCounter;

    public PriceAlertServiceImpl(
            PriceAlertRepository alertRepository,
            WatchlistAlertPreferenceRepository preferenceRepository,
            PriceWatchlistRepository watchlistRepository,
            AlertRuleEvaluator alertRuleEvaluator,
            NotificationDeliveryService deliveryService,
            PriceAnalyticsService priceAnalyticsService,
            MeterRegistry meterRegistry) {
        this.alertRepository = alertRepository;
        this.preferenceRepository = preferenceRepository;
        this.watchlistRepository = watchlistRepository;
        this.alertRuleEvaluator = alertRuleEvaluator;
        this.deliveryService = deliveryService;
        this.priceAnalyticsService = priceAnalyticsService;
        this.meterRegistry = meterRegistry;

        this.evaluatedCounter = Counter.builder("pricepilot.alerts.evaluated")
                .description("Total number of price watchlists evaluated for alert triggers")
                .register(meterRegistry);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<PriceAlertResponseDTO> getUserAlerts(UUID userId, Pageable pageable) {
        return alertRepository.findAllByUserIdWithProduct(userId, pageable)
                .map(PriceAlertResponseDTO::fromEntity);
    }

    @Override
    @Transactional(readOnly = true)
    public List<PriceAlertResponseDTO> getUnreadAlerts(UUID userId) {
        return alertRepository.findUnreadByUserId(userId).stream()
                .map(PriceAlertResponseDTO::fromEntity)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public long getUnreadCount(UUID userId) {
        return alertRepository.countUnreadByUserId(userId);
    }

    @Autowired(required = false)
    private com.pricepilot.interaction.UserInteractionEventService eventService;

    @Override
    @Transactional
    public PriceAlertResponseDTO markAsRead(UUID alertId, UUID userId) {
        PriceAlertEntity alert = alertRepository.findById(alertId)
                .orElseThrow(() -> new ResourceNotFoundException("Alert not found with id: " + alertId));

        if (!alert.getUser().getId().equals(userId)) {
            throw new AccessDeniedException("You do not have permission to modify this alert");
        }

        alert.markAsRead();
        PriceAlertEntity saved = alertRepository.save(alert);

        if (eventService != null && saved.getProduct() != null) {
            try {
                eventService.trackEvent(
                        userId,
                        saved.getProduct().getId(),
                        null,
                        com.pricepilot.interaction.InteractionType.ALERT_INTERACTION,
                        java.util.Map.of("alertId", saved.getId().toString())
                );
            } catch (Exception ignored) {}
        }

        return PriceAlertResponseDTO.fromEntity(saved);
    }

    @Override
    @Transactional
    public int markAllAsRead(UUID userId) {
        return alertRepository.markAllAsReadForUser(userId, LocalDateTime.now());
    }

    @Override
    @Transactional
    public WatchlistAlertPreferenceDTO getAlertPreferences(UUID watchlistId, UUID userId) {
        PriceWatchlistEntity watchlist = watchlistRepository.findById(watchlistId)
                .orElseThrow(() -> new ResourceNotFoundException("Watchlist not found with id: " + watchlistId));

        if (!watchlist.getUser().getId().equals(userId)) {
            throw new AccessDeniedException("You do not have permission to view alert preferences for this watchlist");
        }

        WatchlistAlertPreferenceEntity prefs = preferenceRepository.findByWatchlistId(watchlistId)
                .orElseGet(() -> preferenceRepository.save(WatchlistAlertPreferenceEntity.defaultForWatchlist(watchlist)));

        return WatchlistAlertPreferenceDTO.fromEntity(prefs);
    }

    @Override
    @Transactional
    public WatchlistAlertPreferenceDTO updateAlertPreferences(
            UUID watchlistId,
            UUID userId,
            UpdateWatchlistAlertPreferenceRequestDTO request) {

        PriceWatchlistEntity watchlist = watchlistRepository.findById(watchlistId)
                .orElseThrow(() -> new ResourceNotFoundException("Watchlist not found with id: " + watchlistId));

        if (!watchlist.getUser().getId().equals(userId)) {
            throw new AccessDeniedException("You do not have permission to update alert preferences for this watchlist");
        }

        WatchlistAlertPreferenceEntity prefs = preferenceRepository.findByWatchlistId(watchlistId)
                .orElseGet(() -> WatchlistAlertPreferenceEntity.defaultForWatchlist(watchlist));

        if (request.getEnabled() != null) {
            prefs.setEnabled(request.getEnabled());
        }
        if (request.getPriceDropEnabled() != null) {
            prefs.setPriceDropEnabled(request.getPriceDropEnabled());
        }
        if (request.getPriceDropPercentage() != null) {
            prefs.setPriceDropPercentage(request.getPriceDropPercentage());
        }
        if (request.getTargetPriceEnabled() != null) {
            prefs.setTargetPriceEnabled(request.getTargetPriceEnabled());
        }
        if (request.getHistoricalLowEnabled() != null) {
            prefs.setHistoricalLowEnabled(request.getHistoricalLowEnabled());
        }
        if (request.getGoodDealEnabled() != null) {
            prefs.setGoodDealEnabled(request.getGoodDealEnabled());
        }
        if (request.getBackInStockEnabled() != null) {
            prefs.setBackInStockEnabled(request.getBackInStockEnabled());
        }
        if (request.getPriceIncreaseEnabled() != null) {
            prefs.setPriceIncreaseEnabled(request.getPriceIncreaseEnabled());
        }

        return WatchlistAlertPreferenceDTO.fromEntity(preferenceRepository.save(prefs));
    }

    @Override
    public void processPriceUpdateEvent(UUID productId, BigDecimal oldPrice, BigDecimal newPrice, boolean isBackInStock) {
        if (productId == null || newPrice == null) {
            return;
        }

        List<PriceWatchlistEntity> watchlists = watchlistRepository.findAllActiveByProductIdWithRelations(productId);
        if (watchlists.isEmpty()) {
            return;
        }

        ProductAnalyticsResponseDTO analytics = null;
        try {
            analytics = priceAnalyticsService.getProductAnalytics(productId);
        } catch (Exception e) {
            log.warn("Unable to fetch Phase 4 price analytics for product {}: {}", productId, e.getMessage());
        }

        for (PriceWatchlistEntity watchlist : watchlists) {
            evaluatedCounter.increment();

            WatchlistAlertPreferenceEntity prefs = preferenceRepository.findByWatchlistId(watchlist.getId())
                    .orElseGet(() -> preferenceRepository.save(WatchlistAlertPreferenceEntity.defaultForWatchlist(watchlist)));

            if (!prefs.isEnabled()) {
                continue;
            }

            List<AlertRuleEvaluator.AlertCandidate> candidates = alertRuleEvaluator.evaluateRules(
                    watchlist, prefs, oldPrice, newPrice, analytics, isBackInStock
            );

            for (AlertRuleEvaluator.AlertCandidate candidate : candidates) {
                // Deduplication check
                if (alertRepository.existsByDeduplicationKey(candidate.deduplicationKey())) {
                    meterRegistry.counter("pricepilot.alerts.deduplicated", "alert_type", candidate.alertType().name()).increment();
                    continue;
                }

                PriceAlertEntity alert = PriceAlertEntity.builder()
                        .user(watchlist.getUser())
                        .product(watchlist.getProduct())
                        .watchlist(watchlist)
                        .alertType(candidate.alertType())
                        .title(candidate.title())
                        .message(candidate.message())
                        .triggerValue(candidate.triggerValue())
                        .observedValue(candidate.observedValue())
                        .deduplicationKey(candidate.deduplicationKey())
                        .build();

                try {
                    PriceAlertEntity savedAlert = alertRepository.save(alert);
                    deliveryService.dispatch(savedAlert);

                    meterRegistry.counter("pricepilot.alerts.triggered", "alert_type", candidate.alertType().name()).increment();

                    // Update state trackers on preferences
                    updatePreferenceState(prefs, candidate.alertType(), newPrice, analytics, isBackInStock);
                    preferenceRepository.save(prefs);

                } catch (DataIntegrityViolationException dive) {
                    // Safe concurrent duplicate collision handled by DB constraint
                    meterRegistry.counter("pricepilot.alerts.deduplicated", "alert_type", candidate.alertType().name()).increment();
                }
            }

            // Update current best price on watchlist
            watchlist.setCurrentBestPrice(newPrice);
            watchlistRepository.save(watchlist);
        }
    }

    private void updatePreferenceState(
            WatchlistAlertPreferenceEntity prefs,
            AlertType alertType,
            BigDecimal newPrice,
            ProductAnalyticsResponseDTO analytics,
            boolean isBackInStock) {

        prefs.setLastNotifiedPrice(newPrice);
        switch (alertType) {
            case PRICE_TARGET_REACHED -> prefs.setLastNotifiedTargetPrice(newPrice);
            case HISTORICAL_LOW_REACHED -> prefs.setLastNotifiedHistoricalLow(newPrice);
            case GOOD_DEAL_DETECTED -> {
                if (analytics != null && analytics.getDealQuality() != null) {
                    prefs.setLastNotifiedDealQuality(analytics.getDealQuality().name());
                }
            }
            case BACK_IN_STOCK -> prefs.setLastNotifiedInStock(true);
            default -> {}
        }
    }
}
