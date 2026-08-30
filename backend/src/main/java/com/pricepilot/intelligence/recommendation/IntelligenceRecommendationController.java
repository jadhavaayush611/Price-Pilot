package com.pricepilot.intelligence.recommendation;

import com.pricepilot.intelligence.recommendation.dto.RecommendationCompareRequest;
import com.pricepilot.intelligence.recommendation.dto.RecommendationResponse;
import com.pricepilot.intelligence.recommendation.dto.RecommendationType;
import com.pricepilot.security.UserPrincipal;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/**
 * REST controller for Shopping Intelligence Recommendation Engine v2 endpoints.
 * Standardized API endpoint base paths:
 * - /api/v1/recommendations
 * - /api/v1/intelligence/recommendations
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
            @RequestParam(defaultValue = "10") int limit,
            @RequestParam(required = false) String type) {
        RecommendationType recType = RecommendationType.fromString(type);
        RecommendationResponse response = recommendationService.getRecommendationsForProduct(productId, limit, recType);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/compare")
    public ResponseEntity<RecommendationResponse> compareAndRecommend(
            @Valid @RequestBody RecommendationCompareRequest request,
            @AuthenticationPrincipal UserPrincipal principal) {
        UUID userId = principal != null ? principal.getId() : null;
        RecommendationResponse response = recommendationService.compareAndRecommend(request, userId);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/personalized")
    public ResponseEntity<RecommendationResponse> getPersonalizedRecommendations(
            @AuthenticationPrincipal UserPrincipal principal,
            @RequestParam(defaultValue = "10") int limit) {
        if (principal == null) {
            throw new AccessDeniedException("Authentication required for personalized recommendations");
        }
        RecommendationResponse response = recommendationService.getPersonalizedRecommendations(principal.getId(), limit);
        return ResponseEntity.ok(response);
    }
}
