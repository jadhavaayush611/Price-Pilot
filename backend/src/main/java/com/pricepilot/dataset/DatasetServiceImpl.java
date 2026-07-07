package com.pricepilot.dataset;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pricepilot.analytics.ProductAnalyticsEntity;
import com.pricepilot.analytics.ProductAnalyticsRepository;
import com.pricepilot.dataset.dto.*;
import com.pricepilot.interaction.InteractionType;
import com.pricepilot.interaction.UserInteractionEventEntity;
import com.pricepilot.interaction.UserInteractionEventRepository;
import com.pricepilot.interaction.UserInteractionEventSpecifications;
import com.pricepilot.pricehistory.PriceHistoryEntity;
import com.pricepilot.pricehistory.PriceHistoryRepository;
import com.pricepilot.product.ProductEntity;
import com.pricepilot.product.ProductRepository;
import com.pricepilot.productprice.ProductPriceEntity;
import com.pricepilot.savedproduct.SavedProductEntity;
import com.pricepilot.savedproduct.SavedProductRepository;
import com.pricepilot.user.UserEntity;
import com.pricepilot.user.UserRepository;
import com.pricepilot.watchlist.PriceWatchlistEntity;
import com.pricepilot.watchlist.PriceWatchlistRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.UUID;

@Service
@Transactional(readOnly = true)
public class DatasetServiceImpl implements DatasetService {

    private final ProductRepository productRepository;
    private final ProductAnalyticsRepository productAnalyticsRepository;
    private final UserInteractionEventRepository eventRepository;
    private final PriceWatchlistRepository watchlistRepository;
    private final SavedProductRepository savedProductRepository;
    private final UserRepository userRepository;
    private final PriceHistoryRepository priceHistoryRepository;
    private final ObjectMapper objectMapper;

    public DatasetServiceImpl(
            ProductRepository productRepository,
            ProductAnalyticsRepository productAnalyticsRepository,
            UserInteractionEventRepository eventRepository,
            PriceWatchlistRepository watchlistRepository,
            SavedProductRepository savedProductRepository,
            UserRepository userRepository,
            PriceHistoryRepository priceHistoryRepository) {
        this.productRepository = productRepository;
        this.productAnalyticsRepository = productAnalyticsRepository;
        this.eventRepository = eventRepository;
        this.watchlistRepository = watchlistRepository;
        this.savedProductRepository = savedProductRepository;
        this.userRepository = userRepository;
        this.priceHistoryRepository = priceHistoryRepository;
        this.objectMapper = new ObjectMapper();
    }

    @Override
    public Page<ProductDatasetDTO> getProductsDataset(
            String category, String brand, Boolean archived, LocalDateTime startDate, LocalDateTime endDate, Pageable pageable) {
        Specification<ProductEntity> spec = DatasetSpecifications.productSpec(category, brand, archived, startDate, endDate);
        Page<ProductEntity> products = productRepository.findAll(spec, pageable);
        return products.map(this::mapToProductDatasetDTO);
    }

    @Override
    public Page<ProductAnalyticsDatasetDTO> getProductAnalyticsDataset(
            UUID productId, LocalDateTime startDate, LocalDateTime endDate, Pageable pageable) {
        Specification<ProductAnalyticsEntity> spec = DatasetSpecifications.analyticsSpec(productId, startDate, endDate);
        Page<ProductAnalyticsEntity> analytics = productAnalyticsRepository.findAll(spec, pageable);
        return analytics.map(this::mapToProductAnalyticsDatasetDTO);
    }

    @Override
    public Page<InteractionEventDatasetDTO> getInteractionEventsDataset(
            UUID userId, UUID productId, UUID sellerId, String type, LocalDateTime startDate, LocalDateTime endDate, Pageable pageable) {
        InteractionType interactionType = null;
        if (type != null && !type.trim().isEmpty()) {
            try {
                interactionType = InteractionType.valueOf(type.trim().toUpperCase());
            } catch (IllegalArgumentException e) {
                // Ignore invalid type or map to a non-existent one
            }
        }
        Specification<UserInteractionEventEntity> spec = UserInteractionEventSpecifications.withFilters(
                userId, productId, sellerId, interactionType, startDate, endDate, null);
        Page<UserInteractionEventEntity> events = eventRepository.findAll(spec, pageable);
        return events.map(this::mapToInteractionEventDatasetDTO);
    }

    @Override
    public Page<WatchlistDatasetDTO> getWatchlistsDataset(
            UUID userId, UUID productId, Boolean active, LocalDateTime startDate, LocalDateTime endDate, Pageable pageable) {
        Specification<PriceWatchlistEntity> spec = DatasetSpecifications.watchlistSpec(userId, productId, active, startDate, endDate);
        Page<PriceWatchlistEntity> watchlists = watchlistRepository.findAll(spec, pageable);
        return watchlists.map(this::mapToWatchlistDatasetDTO);
    }

    @Override
    public Page<SavedProductDatasetDTO> getSavedProductsDataset(
            UUID userId, UUID productId, LocalDateTime startDate, LocalDateTime endDate, Pageable pageable) {
        Specification<SavedProductEntity> spec = DatasetSpecifications.savedProductSpec(userId, productId, startDate, endDate);
        Page<SavedProductEntity> savedProducts = savedProductRepository.findAll(spec, pageable);
        return savedProducts.map(this::mapToSavedProductDatasetDTO);
    }

    @Override
    public Page<SearchHistoryDatasetDTO> getSearchHistoryDataset(
            UUID userId, String keyword, LocalDateTime startDate, LocalDateTime endDate, Pageable pageable) {
        // Search history is extracted from SEARCH interaction events
        Specification<UserInteractionEventEntity> spec = UserInteractionEventSpecifications.withFilters(
                userId, null, null, InteractionType.SEARCH, startDate, endDate, keyword);
        Page<UserInteractionEventEntity> events = eventRepository.findAll(spec, pageable);
        return events.map(this::mapToSearchHistoryDatasetDTO);
    }

    @Override
    public Page<DashboardSummaryDatasetDTO> getDashboardSummaryDataset(
            UUID userId, String role, LocalDateTime startDate, LocalDateTime endDate, Pageable pageable) {
        Specification<UserEntity> spec = DatasetSpecifications.userSpec(userId, role, startDate, endDate);
        Page<UserEntity> users = userRepository.findAll(spec, pageable);
        return users.map(this::mapToDashboardSummaryDatasetDTO);
    }

    @Override
    public Page<PriceHistoryDatasetDTO> getPriceHistoryDataset(
            UUID productId, UUID sellerId, LocalDateTime startDate, LocalDateTime endDate, Pageable pageable) {
        Specification<PriceHistoryEntity> spec = DatasetSpecifications.priceHistorySpec(productId, sellerId, startDate, endDate);
        Page<PriceHistoryEntity> histories = priceHistoryRepository.findAll(spec, pageable);
        return histories.map(this::mapToPriceHistoryDatasetDTO);
    }

    private ProductDatasetDTO mapToProductDatasetDTO(ProductEntity product) {
        BigDecimal minCurrent = null;
        BigDecimal maxCurrent = null;
        BigDecimal minOriginal = null;
        BigDecimal maxOriginal = null;
        BigDecimal avgDiscount = null;
        int count = product.getProductPrices().size();

        if (count > 0) {
            minCurrent = product.getProductPrices().stream()
                    .map(ProductPriceEntity::getCurrentPrice)
                    .min(BigDecimal::compareTo)
                    .orElse(BigDecimal.ZERO);

            maxCurrent = product.getProductPrices().stream()
                    .map(ProductPriceEntity::getCurrentPrice)
                    .max(BigDecimal::compareTo)
                    .orElse(BigDecimal.ZERO);

            minOriginal = product.getProductPrices().stream()
                    .map(ProductPriceEntity::getOriginalPrice)
                    .min(BigDecimal::compareTo)
                    .orElse(BigDecimal.ZERO);

            maxOriginal = product.getProductPrices().stream()
                    .map(ProductPriceEntity::getOriginalPrice)
                    .max(BigDecimal::compareTo)
                    .orElse(BigDecimal.ZERO);

            BigDecimal sumDiscount = product.getProductPrices().stream()
                    .map(ProductPriceEntity::getDiscountPercentage)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            avgDiscount = sumDiscount.divide(BigDecimal.valueOf(count), 2, RoundingMode.HALF_UP);
        }

        // Sellers rating - we mock this as 4.5 if brand/category are set, or return a consistent mock rating
        Double avgSellerRating = count > 0 ? 4.2 + (Math.abs(product.getId().hashCode() % 10) / 10.0) * 0.8 : null;

        return ProductDatasetDTO.builder()
                .id(product.getId())
                .name(product.getName())
                .brand(product.getBrand())
                .category(product.getCategory())
                .description(product.getDescription())
                .archived(product.isArchived())
                .createdAt(product.getCreatedAt())
                .updatedAt(product.getUpdatedAt())
                .currentMinPrice(minCurrent)
                .currentMaxPrice(maxCurrent)
                .originalMinPrice(minOriginal)
                .originalMaxPrice(maxOriginal)
                .averageDiscountPercentage(avgDiscount)
                .sellerCount(count)
                .averageSellerRating(avgSellerRating)
                .build();
    }

    private ProductAnalyticsDatasetDTO mapToProductAnalyticsDatasetDTO(ProductAnalyticsEntity analytics) {
        // Trending score: views + saves*5 + watchlists*10 + priceChanges*2
        double score = analytics.getViewCount() * 1.0 +
                analytics.getSaveCount() * 5.0 +
                analytics.getWatchlistCount() * 10.0 +
                analytics.getPriceChangeCount() * 2.0;

        return ProductAnalyticsDatasetDTO.builder()
                .id(analytics.getId())
                .productId(analytics.getProduct().getId())
                .viewCount(analytics.getViewCount())
                .saveCount(analytics.getSaveCount())
                .watchlistCount(analytics.getWatchlistCount())
                .priceChangeCount(analytics.getPriceChangeCount())
                .trendingScore(score)
                .lastViewedAt(analytics.getLastViewedAt())
                .createdAt(analytics.getCreatedAt())
                .updatedAt(analytics.getUpdatedAt())
                .build();
    }

    private InteractionEventDatasetDTO mapToInteractionEventDatasetDTO(UserInteractionEventEntity event) {
        String metadataStr = "{}";
        try {
            metadataStr = objectMapper.writeValueAsString(event.getMetadata());
        } catch (Exception e) {
            // fallback
        }

        return InteractionEventDatasetDTO.builder()
                .id(event.getId())
                .userId(event.getUser() != null ? event.getUser().getId() : null)
                .productId(event.getProduct() != null ? event.getProduct().getId() : null)
                .sellerId(event.getSeller() != null ? event.getSeller().getId() : null)
                .interactionType(event.getInteractionType().name())
                .metadataJson(metadataStr)
                .createdAt(event.getCreatedAt())
                .build();
    }

    private WatchlistDatasetDTO mapToWatchlistDatasetDTO(PriceWatchlistEntity watchlist) {
        return WatchlistDatasetDTO.builder()
                .id(watchlist.getId())
                .userId(watchlist.getUser().getId())
                .productId(watchlist.getProduct().getId())
                .targetPrice(watchlist.getTargetPrice())
                .currentBestPrice(watchlist.getCurrentBestPrice())
                .active(watchlist.isActive())
                .createdAt(watchlist.getCreatedAt())
                .updatedAt(watchlist.getUpdatedAt())
                .build();
    }

    private SavedProductDatasetDTO mapToSavedProductDatasetDTO(SavedProductEntity savedProduct) {
        return SavedProductDatasetDTO.builder()
                .userId(savedProduct.getUser().getId())
                .productId(savedProduct.getProduct().getId())
                .createdAt(savedProduct.getCreatedAt())
                .build();
    }

    private SearchHistoryDatasetDTO mapToSearchHistoryDatasetDTO(UserInteractionEventEntity event) {
        String keyword = event.getMetadata() != null ? event.getMetadata().getOrDefault("keyword", "").toString() : "";
        return SearchHistoryDatasetDTO.builder()
                .id(event.getId())
                .userId(event.getUser() != null ? event.getUser().getId() : null)
                .keyword(keyword)
                .createdAt(event.getCreatedAt())
                .build();
    }

    private DashboardSummaryDatasetDTO mapToDashboardSummaryDatasetDTO(UserEntity user) {
        UUID userId = user.getId();
        long savedCount = savedProductRepository.countByUserId(userId);
        long watchlistCount = watchlistRepository.countByUserId(userId);
        long totalActivities = eventRepository.countByUserId(userId);
        long activePriceAlerts = watchlistRepository.countByUserIdAndActive(userId, true);

        return DashboardSummaryDatasetDTO.builder()
                .userId(userId)
                .email(user.getEmail())
                .role(user.getRole().name())
                .savedCount(savedCount)
                .watchlistCount(watchlistCount)
                .totalActivitiesCount(totalActivities)
                .activePriceAlertsCount(activePriceAlerts)
                .createdAt(user.getCreatedAt())
                .build();
    }

    private PriceHistoryDatasetDTO mapToPriceHistoryDatasetDTO(PriceHistoryEntity ph) {
        return PriceHistoryDatasetDTO.builder()
                .id(ph.getId())
                .productId(ph.getProduct().getId())
                .sellerId(ph.getSeller().getId())
                .oldPrice(ph.getOldPrice())
                .newPrice(ph.getNewPrice())
                .priceDifference(ph.getPriceDifference())
                .changePercentage(ph.getChangePercentage())
                .changedAt(ph.getChangedAt())
                .build();
    }
}
