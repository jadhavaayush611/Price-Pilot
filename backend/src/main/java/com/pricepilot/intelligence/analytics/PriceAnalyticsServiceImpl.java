package com.pricepilot.intelligence.analytics;

import com.pricepilot.analytics.dto.ProductAnalyticsResponseDTO;
import com.pricepilot.analytics.ProductAnalyticsService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Foundation implementation of PriceAnalyticsService for Shopping Intelligence module.
 */
@Service("intelligencePriceAnalyticsService")
public class PriceAnalyticsServiceImpl implements PriceAnalyticsService {

    private final ProductAnalyticsService coreProductAnalyticsService;

    public PriceAnalyticsServiceImpl(ProductAnalyticsService coreProductAnalyticsService) {
        this.coreProductAnalyticsService = coreProductAnalyticsService;
    }

    @Override
    @Transactional(readOnly = true)
    public ProductAnalyticsResponseDTO getProductAnalytics(UUID productId) {
        return coreProductAnalyticsService.getProductAnalytics(productId);
    }
}
