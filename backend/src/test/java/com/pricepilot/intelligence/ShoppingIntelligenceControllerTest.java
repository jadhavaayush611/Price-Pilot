package com.pricepilot.intelligence;

import com.pricepilot.analytics.dto.ProductAnalyticsResponseDTO;
import com.pricepilot.intelligence.analytics.IntelligenceAnalyticsController;
import com.pricepilot.intelligence.analytics.PriceAnalyticsService;
import com.pricepilot.intelligence.comparison.ComparisonController;
import com.pricepilot.intelligence.comparison.ComparisonService;
import com.pricepilot.intelligence.comparison.dto.ComparisonRequest;
import com.pricepilot.intelligence.comparison.dto.ComparisonResponse;
import com.pricepilot.intelligence.recommendation.IntelligenceRecommendationController;
import com.pricepilot.intelligence.recommendation.RecommendationService;
import com.pricepilot.intelligence.recommendation.dto.RecommendationResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ShoppingIntelligenceControllerTest {

    @Mock
    private ComparisonService comparisonService;

    @Mock
    private RecommendationService recommendationService;

    @Mock
    private PriceAnalyticsService priceAnalyticsService;

    private ComparisonController comparisonController;
    private IntelligenceRecommendationController recommendationController;
    private IntelligenceAnalyticsController analyticsController;

    @BeforeEach
    void setUp() {
        comparisonController = new ComparisonController(comparisonService);
        recommendationController = new IntelligenceRecommendationController(recommendationService);
        analyticsController = new IntelligenceAnalyticsController(priceAnalyticsService);
    }

    @Test
    @DisplayName("GET /api/v1/compare should invoke service and return ComparisonResponse")
    void testGetComparisonContract() {
        UUID comparisonId = UUID.randomUUID();
        ComparisonResponse mockResponse = new ComparisonResponse(
                comparisonId, List.of(), List.of(), Map.of(), "GET /api/v1/compare summary", LocalDateTime.now()
        );

        when(comparisonService.compareProducts(any(List.class))).thenReturn(mockResponse);

        ResponseEntity<ComparisonResponse> response = comparisonController.getComparison(null, null);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("GET /api/v1/compare summary", response.getBody().getSummary());
    }

    @Test
    @DisplayName("POST /api/v1/compare should process request and return ComparisonResponse")
    void testPostComparisonContract() {
        UUID comparisonId = UUID.randomUUID();
        ComparisonResponse mockResponse = new ComparisonResponse(
                comparisonId, List.of(), List.of(), Map.of(), "POST /api/v1/compare summary", LocalDateTime.now()
        );

        ComparisonRequest request = new ComparisonRequest(List.of(UUID.randomUUID()), "Tech", List.of("Specs"), null, null);
        when(comparisonService.compareProducts(any(ComparisonRequest.class))).thenReturn(mockResponse);

        ResponseEntity<ComparisonResponse> response = comparisonController.createComparison(request);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("POST /api/v1/compare summary", response.getBody().getSummary());
    }

    @Test
    @DisplayName("GET /api/v1/recommendations/{productId} should return RecommendationResponse")
    void testRecommendationContract() {
        UUID productId = UUID.randomUUID();
        RecommendationResponse mockResponse = new RecommendationResponse(
                productId, null, List.of(), List.of(), "Engine v2 Explanation", "V2_PIPELINE", LocalDateTime.now()
        );

        when(recommendationService.getRecommendationsForProduct(eq(productId), eq(10))).thenReturn(mockResponse);

        ResponseEntity<RecommendationResponse> response = recommendationController.getRecommendationsForProduct(productId, 10);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("V2_PIPELINE", response.getBody().getStrategyUsed());
    }

    @Test
    @DisplayName("GET /api/v1/analytics/{productId} should return ProductAnalyticsResponseDTO")
    void testAnalyticsContract() {
        UUID productId = UUID.randomUUID();
        ProductAnalyticsResponseDTO mockAnalytics = new ProductAnalyticsResponseDTO(
                productId,
                100L,
                25L,
                12L,
                5L,
                88.5
        );

        when(priceAnalyticsService.getProductAnalytics(productId)).thenReturn(mockAnalytics);

        ResponseEntity<ProductAnalyticsResponseDTO> response = analyticsController.getProductAnalytics(productId);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(productId, response.getBody().getProductId());
        assertEquals(100L, response.getBody().getViewCount());
    }
}
