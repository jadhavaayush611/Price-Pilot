package com.pricepilot.intelligence.alert.service;

import com.pricepilot.intelligence.alert.dto.PriceAlertResponseDTO;
import com.pricepilot.intelligence.alert.dto.UpdateWatchlistAlertPreferenceRequestDTO;
import com.pricepilot.intelligence.alert.dto.WatchlistAlertPreferenceDTO;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public interface PriceAlertService {

    Page<PriceAlertResponseDTO> getUserAlerts(UUID userId, Pageable pageable);

    List<PriceAlertResponseDTO> getUnreadAlerts(UUID userId);

    long getUnreadCount(UUID userId);

    PriceAlertResponseDTO markAsRead(UUID alertId, UUID userId);

    int markAllAsRead(UUID userId);

    WatchlistAlertPreferenceDTO getAlertPreferences(UUID watchlistId, UUID userId);

    WatchlistAlertPreferenceDTO updateAlertPreferences(UUID watchlistId, UUID userId, UpdateWatchlistAlertPreferenceRequestDTO request);

    void processPriceUpdateEvent(UUID productId, BigDecimal oldPrice, BigDecimal newPrice, boolean isBackInStock);
}
