package com.pricepilot.analytics;

import com.pricepilot.analytics.dto.ProductAnalyticsResponseDTO;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/analytics")
@CrossOrigin(origins = "*")
public class ProductAnalyticsController {

    private final ProductAnalyticsService productAnalyticsService;

    public ProductAnalyticsController(ProductAnalyticsService productAnalyticsService) {
        this.productAnalyticsService = productAnalyticsService;
    }

    @GetMapping("/products/{productId}")
    public ResponseEntity<ProductAnalyticsResponseDTO> getProductAnalytics(@PathVariable UUID productId) {
        ProductAnalyticsResponseDTO analytics = productAnalyticsService.getProductAnalytics(productId);
        return ResponseEntity.ok(analytics);
    }
}
