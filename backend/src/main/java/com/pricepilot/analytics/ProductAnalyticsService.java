package com.pricepilot.analytics;

import com.pricepilot.analytics.dto.ProductAnalyticsProjection;
import com.pricepilot.analytics.dto.ProductAnalyticsResponseDTO;
import com.pricepilot.exception.ResourceNotFoundException;
import com.pricepilot.product.ProductEntity;
import com.pricepilot.product.ProductRepository;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;

@Service
public class ProductAnalyticsService {

    private final ProductAnalyticsRepository productAnalyticsRepository;
    private final ProductRepository productRepository;

    public ProductAnalyticsService(
            ProductAnalyticsRepository productAnalyticsRepository,
            ProductRepository productRepository) {
        this.productAnalyticsRepository = productAnalyticsRepository;
        this.productRepository = productRepository;
    }

    /**
     * Helper to initialize the analytics record if it does not exist (backward compatibility).
     */
    @Transactional
    public void initializeAnalyticsIfAbsent(UUID productId) {
        if (productAnalyticsRepository.findByProductId(productId).isEmpty()) {
            ProductEntity product = productRepository.findById(productId)
                    .orElseThrow(() -> new ResourceNotFoundException("Product not found with id: " + productId));
            
            ProductAnalyticsEntity analytics = ProductAnalyticsEntity.builder()
                    .product(product)
                    .viewCount(0L)
                    .saveCount(0L)
                    .watchlistCount(0L)
                    .priceChangeCount(0L)
                    .build();
            try {
                productAnalyticsRepository.saveAndFlush(analytics);
            } catch (Exception e) {
                // Ignore constraint violation in case of concurrent writes
            }
        }
    }

    @Async
    @Transactional
    public void trackView(UUID productId) {
        LocalDateTime now = LocalDateTime.now();
        int updated = productAnalyticsRepository.incrementViewCount(productId, now);
        if (updated == 0) {
            initializeAnalyticsIfAbsent(productId);
            productAnalyticsRepository.incrementViewCount(productId, now);
        }
    }

    @Transactional
    public void incrementSaveCount(UUID productId) {
        int updated = productAnalyticsRepository.incrementSaveCount(productId, LocalDateTime.now());
        if (updated == 0) {
            initializeAnalyticsIfAbsent(productId);
            productAnalyticsRepository.incrementSaveCount(productId, LocalDateTime.now());
        }
    }

    @Transactional
    public void decrementSaveCount(UUID productId) {
        int updated = productAnalyticsRepository.decrementSaveCount(productId, LocalDateTime.now());
        if (updated == 0) {
            initializeAnalyticsIfAbsent(productId);
            productAnalyticsRepository.decrementSaveCount(productId, LocalDateTime.now());
        }
    }

    @Transactional
    public void incrementWatchlistCount(UUID productId) {
        int updated = productAnalyticsRepository.incrementWatchlistCount(productId, LocalDateTime.now());
        if (updated == 0) {
            initializeAnalyticsIfAbsent(productId);
            productAnalyticsRepository.incrementWatchlistCount(productId, LocalDateTime.now());
        }
    }

    @Transactional
    public void decrementWatchlistCount(UUID productId) {
        int updated = productAnalyticsRepository.decrementWatchlistCount(productId, LocalDateTime.now());
        if (updated == 0) {
            initializeAnalyticsIfAbsent(productId);
            productAnalyticsRepository.decrementWatchlistCount(productId, LocalDateTime.now());
        }
    }

    @Transactional
    public void incrementPriceChangeCount(UUID productId) {
        int updated = productAnalyticsRepository.incrementPriceChangeCount(productId, LocalDateTime.now());
        if (updated == 0) {
            initializeAnalyticsIfAbsent(productId);
            productAnalyticsRepository.incrementPriceChangeCount(productId, LocalDateTime.now());
        }
    }

    @Transactional(readOnly = true)
    public ProductAnalyticsResponseDTO getProductAnalytics(UUID productId) {
        // Ensure product exists
        if (!productRepository.existsById(productId)) {
            throw new ResourceNotFoundException("Product not found with id: " + productId);
        }

        ProductAnalyticsProjection projection = productAnalyticsRepository.findProjectionByProductId(productId)
                .orElseGet(() -> {
                    // Initialize and fetch again if legacy product
                    initializeAnalyticsIfAbsent(productId);
                    return productAnalyticsRepository.findProjectionByProductId(productId)
                            .orElseThrow(() -> new ResourceNotFoundException("Analytics not found for product: " + productId));
                });

        double score = calculateTrendingScore(
                projection.getViewCount(),
                projection.getSaveCount(),
                projection.getWatchlistCount(),
                projection.getPriceChangeCount()
        );

        return ProductAnalyticsResponseDTO.builder()
                .productId(projection.getProductId())
                .viewCount(projection.getViewCount())
                .saveCount(projection.getSaveCount())
                .watchlistCount(projection.getWatchlistCount())
                .priceChangeCount(projection.getPriceChangeCount())
                .trendingScore(score)
                .build();
    }

    public double calculateTrendingScore(long views, long saves, long watchlists, long priceChanges) {
        return (views * 1.0) + (saves * 5.0) + (watchlists * 10.0) + (priceChanges * 2.0);
    }
}
