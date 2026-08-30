package com.pricepilot.intelligence;

import com.pricepilot.analytics.dto.ProductAnalyticsResponseDTO;
import com.pricepilot.intelligence.analytics.IntelligenceAnalyticsController;
import com.pricepilot.intelligence.analytics.PriceAnalyticsService;
import com.pricepilot.intelligence.comparison.ComparisonController;
import com.pricepilot.intelligence.comparison.ComparisonService;
import com.pricepilot.intelligence.comparison.dto.ComparisonRequest;
import com.pricepilot.intelligence.comparison.dto.ComparisonResponse;
import com.pricepilot.intelligence.comparison.dto.SavedComparisonResponseDTO;
import com.pricepilot.intelligence.recommendation.IntelligenceRecommendationController;
import com.pricepilot.intelligence.recommendation.RecommendationService;
import com.pricepilot.intelligence.recommendation.dto.RecommendationResponse;
import com.pricepilot.security.UserPrincipal;
import com.pricepilot.user.Role;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
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

    private UUID testUserId;

    @BeforeEach
    void setUp() {
        comparisonController = new ComparisonController(comparisonService);
        recommendationController = new IntelligenceRecommendationController(recommendationService);
        analyticsController = new IntelligenceAnalyticsController(priceAnalyticsService);
        testUserId = UUID.randomUUID();

        UserPrincipal principal = new UserPrincipal(testUserId, "test@example.com", "password", Role.USER, true, false);
        UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities());
        SecurityContextHolder.getContext().setAuthentication(auth);
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
    @DisplayName("POST /api/v1/compare/save saves comparison for authenticated user")
    void testSaveComparisonContract() {
        UUID savedId = UUID.randomUUID();
        SavedComparisonResponseDTO savedDTO = new SavedComparisonResponseDTO(
                savedId, testUserId, UUID.randomUUID(), "Saved Set", List.of(UUID.randomUUID()), "Note", LocalDateTime.now(), List.of()
        );

        ComparisonRequest request = new ComparisonRequest(List.of(UUID.randomUUID()), "Electronics", List.of(), testUserId, null, "Saved Set", "Note");
        when(comparisonService.saveComparisonSession(eq(testUserId), any(ComparisonRequest.class))).thenReturn(savedDTO);

        ResponseEntity<SavedComparisonResponseDTO> response = comparisonController.saveComparison(request);

        assertEquals(HttpStatus.CREATED, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(savedId, response.getBody().getId());
    }

    @Test
    @DisplayName("GET /api/v1/compare/saved returns paginated, sorted, and filtered saved comparisons")
    void testGetSavedComparisonsContract() {
        SavedComparisonResponseDTO dto = new SavedComparisonResponseDTO(
                UUID.randomUUID(), testUserId, UUID.randomUUID(), "Saved Set", List.of(), "Notes", LocalDateTime.now(), List.of()
        );
        Page<SavedComparisonResponseDTO> page = new PageImpl<>(List.of(dto));

        when(comparisonService.getSavedComparisons(eq(testUserId), eq(0), eq(10), eq("name"), eq("asc"), eq("Set"))).thenReturn(page);

        ResponseEntity<Page<SavedComparisonResponseDTO>> response = comparisonController.getSavedComparisons(0, 10, "name", "asc", "Set");

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(1, response.getBody().getTotalElements());
    }

    @Test
    @DisplayName("DELETE /api/v1/compare/{sessionId} deletes comparison")
    void testDeleteComparisonContract() {
        UUID sessionId = UUID.randomUUID();
        doNothing().when(comparisonService).deleteSavedComparison(testUserId, sessionId);

        ResponseEntity<Void> response = comparisonController.deleteComparison(sessionId);

        assertEquals(HttpStatus.NO_CONTENT, response.getStatusCode());
    }

    @Test
    @DisplayName("POST /api/v1/compare/save throws AccessDeniedException when unauthenticated")
    void testSaveComparisonUnauthenticated() {
        SecurityContextHolder.clearContext();
        ComparisonRequest request = new ComparisonRequest(List.of(UUID.randomUUID()), "Tech", List.of(), null, null);

        assertThrows(AccessDeniedException.class, () -> comparisonController.saveComparison(request));
    }

    @Test
    @DisplayName("GET /api/v1/recommendations/{productId} returns RecommendationResponse with explainable fields")
    void testGetProductRecommendations() {
        UUID prodId = UUID.randomUUID();
        RecommendationResponse mockResponse = new RecommendationResponse(
                prodId, null, List.of(), null, "BEST_OVERALL", 91.0, 0.88,
                "Product A is recommended because...", List.of("Lowest price"), List.of(),
                List.of(), List.of(), "DEFAULT_COMPARISON_SCORER", "DETERMINISTIC_RULE_BASED", LocalDateTime.now()
        );

        when(recommendationService.getRecommendationsForProduct(eq(prodId), eq(5), any())).thenReturn(mockResponse);

        ResponseEntity<RecommendationResponse> response = recommendationController.getRecommendationsForProduct(prodId, 5, "BEST_OVERALL");

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("BEST_OVERALL", response.getBody().getRecommendationType());
        assertEquals(0.88, response.getBody().getConfidence());
        assertFalse(response.getBody().getSupportingFactors().isEmpty());
    }

    @Test
    @DisplayName("POST /api/v1/recommendations/compare processes 2-5 products and returns explainable response")
    void testCompareRecommendationsContract() {
        UUID id1 = UUID.randomUUID();
        UUID id2 = UUID.randomUUID();
        com.pricepilot.intelligence.recommendation.dto.RecommendationCompareRequest req =
                new com.pricepilot.intelligence.recommendation.dto.RecommendationCompareRequest(List.of(id1, id2), "BEST_VALUE");

        RecommendationResponse mockResponse = new RecommendationResponse(
                null, testUserId, List.of(), null, "BEST_VALUE", 89.0, 0.84,
                "Best value choice...", List.of("Lower price"), List.of(),
                List.of(), List.of(), "DEFAULT_COMPARISON_SCORER", "DETERMINISTIC_RULE_BASED", LocalDateTime.now()
        );

        when(recommendationService.compareAndRecommend(any(), eq(testUserId))).thenReturn(mockResponse);

        UserPrincipal principal = new UserPrincipal(testUserId, "test@example.com", "pass", Role.USER, true, false);
        ResponseEntity<RecommendationResponse> response = recommendationController.compareAndRecommend(req, principal);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("BEST_VALUE", response.getBody().getRecommendationType());
    }

    @Test
    @DisplayName("GET /api/v1/recommendations/personalized succeeds when authenticated")
    void testPersonalizedRecommendationsAuthenticated() {
        RecommendationResponse mockResponse = new RecommendationResponse(
                null, testUserId, List.of(), null, "BEST_OVERALL", 93.0, 0.86,
                "Personalized recommendation...", List.of(), List.of(),
                List.of(), List.of(), "DEFAULT_COMPARISON_SCORER", "DETERMINISTIC_RULE_BASED", LocalDateTime.now()
        );

        when(recommendationService.getPersonalizedRecommendations(eq(testUserId), eq(10))).thenReturn(mockResponse);

        UserPrincipal principal = new UserPrincipal(testUserId, "test@example.com", "pass", Role.USER, true, false);
        ResponseEntity<RecommendationResponse> response = recommendationController.getPersonalizedRecommendations(principal, 10);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(testUserId, response.getBody().getUserId());
    }

    @Test
    @DisplayName("GET /api/v1/recommendations/personalized throws AccessDeniedException when unauthenticated")
    void testPersonalizedRecommendationsUnauthenticated() {
        assertThrows(AccessDeniedException.class, () -> recommendationController.getPersonalizedRecommendations(null, 10));
    }
}
