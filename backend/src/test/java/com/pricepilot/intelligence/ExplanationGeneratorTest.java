package com.pricepilot.intelligence;

import com.pricepilot.ai.AiClient;
import com.pricepilot.ai.dto.AiExplainRequest;
import com.pricepilot.ai.dto.AiExplainResponse;
import com.pricepilot.ai.v2.RecommendationExplanation;
import com.pricepilot.intelligence.recommendation.dto.EvidenceItem;
import com.pricepilot.intelligence.recommendation.dto.EvidenceType;
import com.pricepilot.intelligence.recommendation.dto.RecommendationType;
import com.pricepilot.intelligence.recommendation.explanation.DeterministicExplanationGenerator;
import com.pricepilot.intelligence.recommendation.explanation.HybridAiExplanationGenerator;
import com.pricepilot.product.dto.ProductResponseDTO;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ExplanationGeneratorTest {

    private DeterministicExplanationGenerator deterministicGenerator;

    @Mock
    private AiClient aiClient;

    private HybridAiExplanationGenerator hybridGenerator;
    private SimpleMeterRegistry meterRegistry;

    private ProductResponseDTO sampleProduct;
    private UUID sampleId;

    @BeforeEach
    void setUp() {
        deterministicGenerator = new DeterministicExplanationGenerator();
        meterRegistry = new SimpleMeterRegistry();
        hybridGenerator = new HybridAiExplanationGenerator(aiClient, deterministicGenerator, meterRegistry);
        ReflectionTestUtils.setField(hybridGenerator, "aiEnabled", true);

        sampleId = UUID.randomUUID();
        sampleProduct = new ProductResponseDTO();
        sampleProduct.setId(sampleId);
        sampleProduct.setName("Pixel 9 Pro");
    }

    @Test
    @DisplayName("Deterministic generator builds evidence-grounded summary and trade-offs")
    void testDeterministicExplanationGeneration() {
        List<EvidenceItem> positive = List.of(
                new EvidenceItem(sampleId, "Pixel 9 Pro", EvidenceType.LOWEST_PRICE, "Lowest current price of $899", "PRICE", 899, 999, true, 0.95),
                new EvidenceItem(sampleId, "Pixel 9 Pro", EvidenceType.HIGHEST_RATING, "Highest customer rating of 4.9/5.0", "RATING", 4.9, 4.5, true, 0.90)
        );

        List<EvidenceItem> tradeOffs = List.of(
                new EvidenceItem(UUID.randomUUID(), "iPhone 15 Pro", EvidenceType.BETTER_SPECIFICATION, "iPhone 15 Pro has better battery life, but costs $100 more", "BATTERY", null, null, false, 0.8)
        );

        RecommendationExplanation exp = deterministicGenerator.generateExplanation(
                sampleProduct, List.of(sampleProduct), positive, tradeOffs, RecommendationType.BEST_OVERALL, 92.0, 0.88
        );

        assertNotNull(exp);
        assertEquals(sampleId, exp.productId());
        assertTrue(exp.summaryExplanation().contains("Pixel 9 Pro"));
        assertTrue(exp.summaryExplanation().contains("lowest current price"));
        assertTrue(exp.summaryExplanation().contains("highest customer rating"));
        assertEquals(2, exp.keyDecisionDrivers().size());
        assertEquals(1, exp.tradeOffs().size());
        assertEquals(0.88, exp.confidenceScore());
        assertEquals("DETERMINISTIC_RULE_BASED", exp.explanationStrategy());
    }

    @Test
    @DisplayName("Deterministic generator formats different recommendation goals appropriately")
    void testDeterministicRecommendationTypes() {
        List<EvidenceItem> positive = List.of(
                new EvidenceItem(sampleId, "Pixel 9 Pro", EvidenceType.LOWEST_PRICE, "Best price", "PRICE", 899, 999, true, 0.95)
        );

        RecommendationExplanation expValue = deterministicGenerator.generateExplanation(
                sampleProduct, List.of(sampleProduct), positive, List.of(), RecommendationType.BEST_VALUE, 90.0, 0.85
        );
        assertTrue(expValue.summaryExplanation().contains("best price-to-value choice"));

        RecommendationExplanation expRating = deterministicGenerator.generateExplanation(
                sampleProduct, List.of(sampleProduct), positive, List.of(), RecommendationType.HIGHEST_RATED, 90.0, 0.85
        );
        assertTrue(expRating.summaryExplanation().contains("top-rated selection"));

        RecommendationExplanation expDiscount = deterministicGenerator.generateExplanation(
                sampleProduct, List.of(sampleProduct), positive, List.of(), RecommendationType.BEST_DISCOUNT, 90.0, 0.85
        );
        assertTrue(expDiscount.summaryExplanation().contains("strongest discount deal"));
    }

    @Test
    @DisplayName("Hybrid generator returns valid AI response and sanitizes markup")
    void testHybridAiSuccessWithSanitization() {
        List<EvidenceItem> positive = List.of(
                new EvidenceItem(sampleId, "Pixel 9 Pro", EvidenceType.LOWEST_PRICE, "Lowest price", "PRICE", 899, 999, true, 0.95)
        );

        AiExplainResponse aiResponse = new AiExplainResponse(
                "<script>alert('xss')</script>Pixel 9 Pro is the top choice with lowest price.",
                List.of("<b>Lowest price</b> among sellers"),
                List.of("<img src=x onerror=alert(1)>Alternative costs more"),
                "PricePilot-LLM-v1"
        );

        when(aiClient.explain(any(AiExplainRequest.class))).thenReturn(aiResponse);

        RecommendationExplanation exp = hybridGenerator.generateExplanation(
                sampleProduct, List.of(sampleProduct), positive, List.of(), RecommendationType.BEST_OVERALL, 91.0, 0.87
        );

        assertNotNull(exp);
        assertEquals("AI_GATEWAY_HYBRID", exp.explanationStrategy());
        // Verify script tags stripped
        assertFalse(exp.summaryExplanation().contains("<script>"));
        assertTrue(exp.summaryExplanation().contains("Pixel 9 Pro is the top choice"));
        assertFalse(exp.keyDecisionDrivers().get(0).contains("<b>"));
        assertFalse(exp.tradeOffs().get(0).contains("<img"));
    }

    @Test
    @DisplayName("Hybrid generator falls back to deterministic on AI timeout / exception")
    void testHybridAiTimeoutFallback() {
        when(aiClient.explain(any(AiExplainRequest.class))).thenThrow(new RuntimeException("Connection timed out after 3000ms"));

        List<EvidenceItem> positive = List.of(
                new EvidenceItem(sampleId, "Pixel 9 Pro", EvidenceType.LOWEST_PRICE, "Lowest price", "PRICE", 899, 999, true, 0.95)
        );

        RecommendationExplanation exp = hybridGenerator.generateExplanation(
                sampleProduct, List.of(sampleProduct), positive, List.of(), RecommendationType.BEST_OVERALL, 91.0, 0.87
        );

        assertNotNull(exp);
        // Fallback strategy applied
        assertEquals("DETERMINISTIC_RULE_BASED", exp.explanationStrategy());
        assertTrue(exp.summaryExplanation().contains("Pixel 9 Pro"));
        // Preserves recommendation
        assertEquals(sampleId, exp.productId());
    }

    @Test
    @DisplayName("Hybrid generator falls back to deterministic on malformed empty AI response")
    void testHybridAiMalformedResponseFallback() {
        when(aiClient.explain(any(AiExplainRequest.class))).thenReturn(new AiExplainResponse("", List.of(), List.of(), "Model"));

        List<EvidenceItem> positive = List.of(
                new EvidenceItem(sampleId, "Pixel 9 Pro", EvidenceType.LOWEST_PRICE, "Lowest price", "PRICE", 899, 999, true, 0.95)
        );

        RecommendationExplanation exp = hybridGenerator.generateExplanation(
                sampleProduct, List.of(sampleProduct), positive, List.of(), RecommendationType.BEST_OVERALL, 91.0, 0.87
        );

        assertNotNull(exp);
        assertEquals("DETERMINISTIC_RULE_BASED", exp.explanationStrategy());
        assertTrue(exp.summaryExplanation().contains("Pixel 9 Pro"));
    }

    @Test
    @DisplayName("Hybrid generator uses deterministic directly when aiEnabled is false")
    void testHybridAiDisabledConfig() {
        ReflectionTestUtils.setField(hybridGenerator, "aiEnabled", false);

        RecommendationExplanation exp = hybridGenerator.generateExplanation(
                sampleProduct, List.of(sampleProduct), List.of(), List.of(), RecommendationType.BEST_OVERALL, 88.0, 0.80
        );

        assertNotNull(exp);
        assertEquals("DETERMINISTIC_RULE_BASED", exp.explanationStrategy());
        verifyNoInteractions(aiClient);
    }
}
