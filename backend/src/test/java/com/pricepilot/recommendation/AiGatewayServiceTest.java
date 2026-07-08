package com.pricepilot.recommendation;

import com.pricepilot.ai.AiClient;
import com.pricepilot.ai.AiGatewayServiceImpl;
import com.pricepilot.ai.dto.AiPredictResponse;
import com.pricepilot.ai.dto.ScoredRecommendation;
import com.pricepilot.interaction.UserInteractionEventRepository;
import com.pricepilot.product.ProductEntity;
import com.pricepilot.recommendation.dto.RecommendationProfile;
import com.pricepilot.recommendation.dto.ScoredProduct;
import com.pricepilot.recommendation.engine.RuleBasedRecommendationEngine;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class AiGatewayServiceTest {

    @Mock
    private AiClient aiClient;

    @Mock
    private RuleBasedRecommendationEngine ruleBasedEngine;

    @Mock
    private UserInteractionEventRepository eventRepository;

    private AiGatewayServiceImpl aiGatewayService;

    private UUID userId;
    private ProductEntity candidateProduct;
    private List<ProductEntity> candidates;
    private RecommendationProfile profile;

    @BeforeEach
    public void setUp() {
        aiGatewayService = new AiGatewayServiceImpl(aiClient, ruleBasedEngine, eventRepository);
        ReflectionTestUtils.setField(aiGatewayService, "aiEnabled", true);

        userId = UUID.randomUUID();
        
        candidateProduct = new ProductEntity();
        candidateProduct.setId(UUID.randomUUID());
        candidateProduct.setCategory("Electronics");
        candidateProduct.setBrand("BrandX");
        candidateProduct.setProductPrices(new ArrayList<>());
        
        candidates = List.of(candidateProduct);
        profile = RecommendationProfile.builder()
                .preferredCategories(Map.of("Electronics", 1.0))
                .preferredBrands(Map.of("BrandX", 1.0))
                .build();
    }

    @Test
    public void testRecommend_Success() {
        // Arrange
        List<ScoredRecommendation> recs = List.of(
                ScoredRecommendation.builder()
                        .productId(candidateProduct.getId().toString())
                        .score(0.92)
                        .reasons(List.of("Matches category preference"))
                        .build()
        );
        AiPredictResponse predictResponse = AiPredictResponse.builder()
                .algorithm("Hybrid")
                .score(0.92)
                .recommendations(recs)
                .build();

        when(aiClient.predict(any())).thenReturn(predictResponse);

        // Act
        List<ScoredProduct> result = aiGatewayService.recommend(
                userId, candidates, profile, Collections.emptyList(), Collections.emptyList(), "Hybrid", 10
        );

        // Assert
        assertNotNull(result);
        assertEquals(1, result.size());
        assertEquals(candidateProduct.getId(), result.get(0).getProduct().getId());
        assertEquals(0.92, result.get(0).getScore());
        assertEquals("Hybrid", result.get(0).getAlgorithm());
        verify(aiClient, times(1)).predict(any());
        verifyNoInteractions(ruleBasedEngine);
    }

    @Test
    public void testRecommend_AiDisabled() {
        // Arrange
        ReflectionTestUtils.setField(aiGatewayService, "aiEnabled", false);
        
        List<ScoredProduct> ruleBasedResult = List.of(
                new ScoredProduct(candidateProduct, 0.5, List.of("Rule-based fallback"))
        );
        when(ruleBasedEngine.recommend(any(), any(), any(), any(), any(), anyInt()))
                .thenReturn(ruleBasedResult);

        // Act
        List<ScoredProduct> result = aiGatewayService.recommend(
                userId, candidates, profile, Collections.emptyList(), Collections.emptyList(), "Hybrid", 10
        );

        // Assert
        assertNotNull(result);
        assertEquals(1, result.size());
        assertEquals("Rule-Based", result.get(0).getAlgorithm());
        verifyNoInteractions(aiClient);
        verify(ruleBasedEngine, times(1)).recommend(any(), any(), any(), any(), any(), anyInt());
    }

    @Test
    public void testRecommend_FastApiFailure_FallbackToRuleBased() {
        // Arrange
        when(aiClient.predict(any())).thenThrow(new RuntimeException("Connection timed out"));

        List<ScoredProduct> ruleBasedResult = List.of(
                new ScoredProduct(candidateProduct, 0.5, List.of("Rule-based fallback"))
        );
        when(ruleBasedEngine.recommend(any(), any(), any(), any(), any(), anyInt()))
                .thenReturn(ruleBasedResult);

        // Act
        List<ScoredProduct> result = aiGatewayService.recommend(
                userId, candidates, profile, Collections.emptyList(), Collections.emptyList(), "Hybrid", 10
        );

        // Assert
        assertNotNull(result);
        assertEquals(1, result.size());
        assertEquals("Rule-Based", result.get(0).getAlgorithm());
        verify(aiClient, times(1)).predict(any());
        verify(ruleBasedEngine, times(1)).recommend(any(), any(), any(), any(), any(), anyInt());
    }
}
