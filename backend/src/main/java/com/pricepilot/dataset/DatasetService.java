package com.pricepilot.dataset;

import com.pricepilot.dataset.dto.*;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.time.LocalDateTime;
import java.util.UUID;

public interface DatasetService {

    Page<ProductDatasetDTO> getProductsDataset(
            String category, 
            String brand, 
            Boolean archived, 
            LocalDateTime startDate, 
            LocalDateTime endDate, 
            Pageable pageable);

    Page<ProductAnalyticsDatasetDTO> getProductAnalyticsDataset(
            UUID productId, 
            LocalDateTime startDate, 
            LocalDateTime endDate, 
            Pageable pageable);

    Page<InteractionEventDatasetDTO> getInteractionEventsDataset(
            UUID userId, 
            UUID productId, 
            UUID sellerId, 
            String type, 
            LocalDateTime startDate, 
            LocalDateTime endDate, 
            Pageable pageable);

    Page<WatchlistDatasetDTO> getWatchlistsDataset(
            UUID userId, 
            UUID productId, 
            Boolean active, 
            LocalDateTime startDate, 
            LocalDateTime endDate, 
            Pageable pageable);

    Page<SavedProductDatasetDTO> getSavedProductsDataset(
            UUID userId, 
            UUID productId, 
            LocalDateTime startDate, 
            LocalDateTime endDate, 
            Pageable pageable);

    Page<SearchHistoryDatasetDTO> getSearchHistoryDataset(
            UUID userId, 
            String keyword, 
            LocalDateTime startDate, 
            LocalDateTime endDate, 
            Pageable pageable);

    Page<DashboardSummaryDatasetDTO> getDashboardSummaryDataset(
            UUID userId, 
            String role, 
            LocalDateTime startDate, 
            LocalDateTime endDate, 
            Pageable pageable);

    Page<PriceHistoryDatasetDTO> getPriceHistoryDataset(
            UUID productId, 
            UUID sellerId, 
            LocalDateTime startDate, 
            LocalDateTime endDate, 
            Pageable pageable);
}
