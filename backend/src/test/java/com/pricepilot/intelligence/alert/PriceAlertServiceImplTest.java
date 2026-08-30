package com.pricepilot.intelligence.alert;

import com.pricepilot.analytics.dto.ProductAnalyticsResponseDTO;
import com.pricepilot.intelligence.alert.delivery.NotificationDeliveryService;
import com.pricepilot.intelligence.alert.dto.UpdateWatchlistAlertPreferenceRequestDTO;
import com.pricepilot.intelligence.alert.engine.AlertRuleEvaluator;
import com.pricepilot.intelligence.alert.entity.PriceAlertEntity;
import com.pricepilot.intelligence.alert.entity.WatchlistAlertPreferenceEntity;
import com.pricepilot.intelligence.alert.model.AlertType;
import com.pricepilot.intelligence.alert.repository.PriceAlertRepository;
import com.pricepilot.intelligence.alert.repository.WatchlistAlertPreferenceRepository;
import com.pricepilot.intelligence.alert.service.PriceAlertServiceImpl;
import com.pricepilot.intelligence.analytics.PriceAnalyticsService;
import com.pricepilot.product.ProductEntity;
import com.pricepilot.user.UserEntity;
import com.pricepilot.watchlist.PriceWatchlistEntity;
import com.pricepilot.watchlist.PriceWatchlistRepository;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.access.AccessDeniedException;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PriceAlertServiceImplTest {

    @Mock
    private PriceAlertRepository alertRepository;

    @Mock
    private WatchlistAlertPreferenceRepository preferenceRepository;

    @Mock
    private PriceWatchlistRepository watchlistRepository;

    @Mock
    private AlertRuleEvaluator alertRuleEvaluator;

    @Mock
    private NotificationDeliveryService deliveryService;

    @Mock
    private PriceAnalyticsService priceAnalyticsService;

    private SimpleMeterRegistry meterRegistry;
    private PriceAlertServiceImpl alertService;

    private UUID userAId;
    private UUID userBId;
    private UserEntity userA;
    private UserEntity userB;
    private ProductEntity product;
    private PriceWatchlistEntity watchlistA;

    @BeforeEach
    void setUp() {
        meterRegistry = new SimpleMeterRegistry();
        alertService = new PriceAlertServiceImpl(
                alertRepository,
                preferenceRepository,
                watchlistRepository,
                alertRuleEvaluator,
                deliveryService,
                priceAnalyticsService,
                meterRegistry
        );

        userAId = UUID.randomUUID();
        userBId = UUID.randomUUID();

        userA = new UserEntity();
        userA.setId(userAId);
        userA.setEmail("userA@example.com");

        userB = new UserEntity();
        userB.setId(userBId);
        userB.setEmail("userB@example.com");

        product = ProductEntity.builder()
                .name("Sony WH-1000XM5")
                .build();
        product.setId(UUID.randomUUID());

        watchlistA = PriceWatchlistEntity.builder()
                .user(userA)
                .product(product)
                .targetPrice(BigDecimal.valueOf(300.00))
                .currentBestPrice(BigDecimal.valueOf(350.00))
                .active(true)
                .build();
        watchlistA.setId(UUID.randomUUID());
    }

    @Test
    @DisplayName("processPriceUpdateEvent creates alert and delivers when rule matches")
    void testProcessPriceUpdateSuccess() {
        WatchlistAlertPreferenceEntity prefs = WatchlistAlertPreferenceEntity.defaultForWatchlist(watchlistA);
        when(watchlistRepository.findAllActiveByProductIdWithRelations(product.getId())).thenReturn(List.of(watchlistA));
        when(preferenceRepository.findByWatchlistId(watchlistA.getId())).thenReturn(Optional.of(prefs));
        when(priceAnalyticsService.getProductAnalytics(product.getId())).thenReturn(ProductAnalyticsResponseDTO.builder().build());

        var candidate = new AlertRuleEvaluator.AlertCandidate(
                AlertType.PRICE_TARGET_REACHED,
                "Target Price Reached!",
                "Target of $300 reached at $280",
                BigDecimal.valueOf(300.00),
                BigDecimal.valueOf(280.00),
                "TARGET:" + watchlistA.getId() + ":300.00:280.00"
        );
        when(alertRuleEvaluator.evaluateRules(any(), any(), any(), any(), any(), anyBoolean()))
                .thenReturn(List.of(candidate));
        when(alertRepository.existsByDeduplicationKey(candidate.deduplicationKey())).thenReturn(false);
        when(alertRepository.save(any(PriceAlertEntity.class))).thenAnswer(i -> i.getArgument(0));

        alertService.processPriceUpdateEvent(product.getId(), BigDecimal.valueOf(350.00), BigDecimal.valueOf(280.00), false);

        verify(alertRepository).save(any(PriceAlertEntity.class));
        verify(deliveryService).dispatch(any(PriceAlertEntity.class));
        verify(preferenceRepository).save(prefs);
    }

    @Test
    @DisplayName("processPriceUpdateEvent skips duplicate alert if deduplication key already exists")
    void testProcessPriceUpdateDeduplicated() {
        WatchlistAlertPreferenceEntity prefs = WatchlistAlertPreferenceEntity.defaultForWatchlist(watchlistA);
        when(watchlistRepository.findAllActiveByProductIdWithRelations(product.getId())).thenReturn(List.of(watchlistA));
        when(preferenceRepository.findByWatchlistId(watchlistA.getId())).thenReturn(Optional.of(prefs));

        var candidate = new AlertRuleEvaluator.AlertCandidate(
                AlertType.PRICE_TARGET_REACHED,
                "Target Price Reached!",
                "Already alerted",
                BigDecimal.valueOf(300.00),
                BigDecimal.valueOf(280.00),
                "TARGET:" + watchlistA.getId() + ":300.00:280.00"
        );
        when(alertRuleEvaluator.evaluateRules(any(), any(), any(), any(), any(), anyBoolean()))
                .thenReturn(List.of(candidate));
        when(alertRepository.existsByDeduplicationKey(candidate.deduplicationKey())).thenReturn(true);

        alertService.processPriceUpdateEvent(product.getId(), BigDecimal.valueOf(350.00), BigDecimal.valueOf(280.00), false);

        verify(alertRepository, never()).save(any(PriceAlertEntity.class));
        verify(deliveryService, never()).dispatch(any(PriceAlertEntity.class));
    }

    @Test
    @DisplayName("processPriceUpdateEvent handles concurrent DB constraint violation safely")
    void testProcessPriceUpdateConcurrentConstraintHandled() {
        WatchlistAlertPreferenceEntity prefs = WatchlistAlertPreferenceEntity.defaultForWatchlist(watchlistA);
        when(watchlistRepository.findAllActiveByProductIdWithRelations(product.getId())).thenReturn(List.of(watchlistA));
        when(preferenceRepository.findByWatchlistId(watchlistA.getId())).thenReturn(Optional.of(prefs));

        var candidate = new AlertRuleEvaluator.AlertCandidate(
                AlertType.PRICE_TARGET_REACHED,
                "Target Price Reached!",
                "Target reached",
                BigDecimal.valueOf(300.00),
                BigDecimal.valueOf(280.00),
                "TARGET:" + watchlistA.getId() + ":300.00:280.00"
        );
        when(alertRuleEvaluator.evaluateRules(any(), any(), any(), any(), any(), anyBoolean()))
                .thenReturn(List.of(candidate));
        when(alertRepository.existsByDeduplicationKey(candidate.deduplicationKey())).thenReturn(false);
        when(alertRepository.save(any(PriceAlertEntity.class))).thenThrow(new DataIntegrityViolationException("Duplicate key"));

        assertDoesNotThrow(() ->
                alertService.processPriceUpdateEvent(product.getId(), BigDecimal.valueOf(350.00), BigDecimal.valueOf(280.00), false)
        );
    }

    @Test
    @DisplayName("User A cannot mark User B's alert as read")
    void testMarkAsReadOwnershipSecurity() {
        UUID alertId = UUID.randomUUID();
        PriceAlertEntity alertB = PriceAlertEntity.builder()
                .user(userB)
                .product(product)
                .alertType(AlertType.PRICE_DROP)
                .title("Drop")
                .message("Drop")
                .deduplicationKey("KEY-B")
                .build();
        alertB.setId(alertId);

        when(alertRepository.findById(alertId)).thenReturn(Optional.of(alertB));

        assertThrows(AccessDeniedException.class, () -> alertService.markAsRead(alertId, userAId));
    }

    @Test
    @DisplayName("User A cannot update User B's watchlist alert preferences")
    void testUpdatePreferencesOwnershipSecurity() {
        PriceWatchlistEntity watchlistB = PriceWatchlistEntity.builder()
                .user(userB)
                .product(product)
                .targetPrice(BigDecimal.valueOf(100.0))
                .currentBestPrice(BigDecimal.valueOf(120.0))
                .build();
        watchlistB.setId(UUID.randomUUID());

        when(watchlistRepository.findById(watchlistB.getId())).thenReturn(Optional.of(watchlistB));

        UpdateWatchlistAlertPreferenceRequestDTO req = UpdateWatchlistAlertPreferenceRequestDTO.builder()
                .enabled(false)
                .build();

        assertThrows(AccessDeniedException.class, () ->
                alertService.updateAlertPreferences(watchlistB.getId(), userAId, req)
        );
    }

    @Test
    @DisplayName("markAsRead marks alert readAt timestamp")
    void testMarkAsReadSuccess() {
        UUID alertId = UUID.randomUUID();
        PriceAlertEntity alertA = PriceAlertEntity.builder()
                .user(userA)
                .product(product)
                .alertType(AlertType.PRICE_DROP)
                .title("Drop")
                .message("Drop")
                .deduplicationKey("KEY-A")
                .build();
        alertA.setId(alertId);

        when(alertRepository.findById(alertId)).thenReturn(Optional.of(alertA));
        when(alertRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        var result = alertService.markAsRead(alertId, userAId);
        assertTrue(result.isRead());
        assertNotNull(result.getReadAt());
    }
}
