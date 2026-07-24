package com.pricepilot.intelligence.recommendation;

import com.pricepilot.intelligence.recommendation.dto.RecommendationResponse;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/**
 * REST controller for Shopping Intelligence Recommendation Engine v2 endpoints.
 * Standardized API endpoint base path: /api/v1/intelligence/recommendations
 */
@RestController
@RequestMapping({"/api/v1/intelligence/recommendations", "/api/v1/recommendations"})
@CrossOrigin(origins = "*")
public class IntelligenceRecommendationController {

    private final RecommendationService recommendationService;

    public IntelligenceRecommendationController(
            @Qualifier("intelligenceRecommendationService") RecommendationService recommendationService) {
        this.recommendationService = recommendationService;
    }

    @GetMapping("/{productId}")
    public ResponseEntity<RecommendationResponse> getRecommendationsForProduct(
            @PathVariable UUID productId,
            @RequestParam(defaultValue = "10") int limit) {
        RecommendationResponse response = recommendationService.getRecommendationsForProduct(productId, limit);
        return ResponseEntity.ok(response);
    }
}
