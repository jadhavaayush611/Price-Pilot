package com.pricepilot.intelligence.analytics;

import com.pricepilot.analytics.dto.ProductAnalyticsResponseDTO;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/**
 * REST controller for Shopping Intelligence price analytics endpoint.
 * Standardized API endpoint base path: /api/v1/intelligence/analytics
 */
@RestController
@RequestMapping({"/api/v1/intelligence/analytics", "/api/v1/analytics"})
@CrossOrigin(origins = "*")
public class IntelligenceAnalyticsController {

    private final PriceAnalyticsService priceAnalyticsService;

    public IntelligenceAnalyticsController(
            @Qualifier("intelligencePriceAnalyticsService") PriceAnalyticsService priceAnalyticsService) {
        this.priceAnalyticsService = priceAnalyticsService;
    }

    @GetMapping("/{productId}")
    public ResponseEntity<ProductAnalyticsResponseDTO> getProductAnalytics(@PathVariable UUID productId) {
        ProductAnalyticsResponseDTO analytics = priceAnalyticsService.getProductAnalytics(productId);
        return ResponseEntity.ok(analytics);
    }
}
