package com.pricepilot.intelligence.analytics;

import com.pricepilot.analytics.dto.ProductAnalyticsResponseDTO;

import java.util.UUID;

/**
 * Service interface for Shopping Intelligence price analytics engine.
 */
public interface PriceAnalyticsService {

    /**
     * Calculates comprehensive price analytics, volatility metrics, and deal ratings for a product.
     *
     * @param productId Target product ID.
     * @return Product analytics DTO.
     */
    ProductAnalyticsResponseDTO getProductAnalytics(UUID productId);
}
