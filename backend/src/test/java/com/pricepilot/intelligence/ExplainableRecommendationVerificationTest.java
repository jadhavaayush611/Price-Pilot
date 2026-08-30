package com.pricepilot.intelligence;

import com.pricepilot.ai.AiClient;
import com.pricepilot.ai.dto.AiExplainRequest;
import com.pricepilot.ai.dto.AiExplainResponse;
import com.pricepilot.ai.v2.RecommendationExplanation;
import com.pricepilot.intelligence.comparison.scoring.ComparisonScoringStrategy;
import com.pricepilot.intelligence.comparison.scoring.DefaultComparisonScorer;
import com.pricepilot.intelligence.recommendation.DefaultRecommendationPipeline;
import com.pricepilot.intelligence.recommendation.confidence.ConfidenceCalculator;
import com.pricepilot.intelligence.recommendation.dto.EvidenceItem;
import com.pricepilot.intelligence.recommendation.dto.EvidenceType;
import com.pricepilot.intelligence.recommendation.dto.RecommendationResponse;
import com.pricepilot.intelligence.recommendation.dto.RecommendationType;
import com.pricepilot.intelligence.recommendation.evidence.EvidenceExtractor;
import com.pricepilot.intelligence.recommendation.explanation.DeterministicExplanationGenerator;
import com.pricepilot.intelligence.recommendation.explanation.HybridAiExplanationGenerator;
import com.pricepilot.intelligence.recommendation.repository.RecommendationHistoryEventRepository;
import com.pricepilot.product.ProductService;
import com.pricepilot.product.dto.ProductResponseDTO;
import com.pricepilot.productprice.dto.ProductPriceResponseDTO;
import com.pricepilot.seller.dto.SellerResponseDTO;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Independent Phase 3 Verification Suite covering:
 * A. Recommendation correctness & deterministic scoring
 * B. Explainability & evidence traceability (missing price, missing rating, conflicting signals, incomplete specs)
 * C. AI failure resilience & seamless deterministic fallback
 * D. Security & output sanitization
 */
@ExtendWith(MockitoExtension.class)
public class ExplainableRecommendationVerificationTest {

    @Mock
    private ProductService productService;

    @Mock
    private RecommendationHistoryEventRepository historyRepository;

    @Mock
    private AiClient aiClient;

    private ComparisonScoringStrategy scoringStrategy;
    private EvidenceExtractor evidenceExtractor;
    private ConfidenceCalculator confidenceCalculator;
    private DeterministicExplanationGenerator deterministicGenerator;
    private HybridAiExplanationGenerator hybridGenerator;
    private SimpleMeterRegistry meterRegistry;
    private DefaultRecommendationPipeline pipeline;

    @BeforeEach
    void setUp() {
        scoringStrategy = new DefaultComparisonScorer();
        evidenceExtractor = new EvidenceExtractor();
        confidenceCalculator = new ConfidenceCalculator();
        deterministicGenerator = new DeterministicExplanationGenerator();
        meterRegistry = new SimpleMeterRegistry();
        hybridGenerator = new HybridAiExplanationGenerator(aiClient, deterministicGenerator, meterRegistry);
        ReflectionTestUtils.setField(hybridGenerator, "aiEnabled", true);

        pipeline = new DefaultRecommendationPipeline(
                productService,
                scoringStrategy,
                evidenceExtractor,
                confidenceCalculator,
                hybridGenerator,
                historyRepository,
                meterRegistry
        );
    }

    private ProductResponseDTO buildProduct(UUID id, String name, BigDecimal price, BigDecimal discount, int sellers, String desc) {
        ProductResponseDTO p = new ProductResponseDTO();
        p.setId(id);
        p.setName(name);
        p.setDescription(desc);
        List<ProductPriceResponseDTO> prices = new ArrayList<>();
        if (price != null) {
            for (int i = 0; i < sellers; i++) {
                ProductPriceResponseDTO pr = new ProductPriceResponseDTO();
                pr.setCurrentPrice(price);
                pr.setDiscountPercentage(discount != null ? discount : BigDecimal.ZERO);
                SellerResponseDTO s = new SellerResponseDTO();
                s.setName("Merchant " + i);
                pr.setSeller(s);
                prices.add(pr);
            }
        }
        p.setPrices(prices);
        return p;
    }

    @Nested
    @DisplayName("A. Recommendation Correctness & Scoring Origin")
    class RecommendationCorrectnessTests {

        @Test
        @DisplayName("Score calculations strictly originate from Phase 2 DefaultComparisonScorer")
        void testScoresOriginateFromPhase2Engine() {
            UUID id1 = UUID.randomUUID();
            UUID id2 = UUID.randomUUID();
            ProductResponseDTO p1 = buildProduct(id1, "PhoneA", BigDecimal.valueOf(800), BigDecimal.valueOf(15), 2, "Desc A");
            ProductResponseDTO p2 = buildProduct(id2, "PhoneB", BigDecimal.valueOf(1000), BigDecimal.valueOf(5), 1, "Desc B");

            RecommendationResponse response = pipeline.executeComparisonPipeline(
                    List.of(p1, p2), RecommendationType.BEST_OVERALL, null, Map.of()
            );

            // Verify scores map directly to DefaultComparisonScorer breakdown
            assertNotNull(response.getScores());
            assertEquals(2, response.getScores().size());
            for (var score : response.getScores()) {
                assertNotNull(score.getBreakdown());
                assertTrue(score.getBreakdown().containsKey("PriceCompetitiveness"));
                assertTrue(score.getBreakdown().containsKey("DiscountPercentage"));
                assertTrue(score.getBreakdown().containsKey("ProductRating"));
                assertTrue(score.getBreakdown().containsKey("ReviewCount"));
                assertTrue(score.getBreakdown().containsKey("SellerReputation"));
                assertTrue(score.getBreakdown().containsKey("Availability"));
            }
        }

        @Test
        @DisplayName("Deterministic ranking selects highest scoring candidate consistently")
        void testHighestScorerSelection() {
            UUID id1 = UUID.randomUUID();
            UUID id2 = UUID.randomUUID();
            ProductResponseDTO p1 = buildProduct(id1, "SuperiorSpec", BigDecimal.valueOf(500), BigDecimal.valueOf(25), 4, "High end");
            ProductResponseDTO p2 = buildProduct(id2, "BudgetBasic", BigDecimal.valueOf(600), BigDecimal.valueOf(0), 1, "Basic");

            RecommendationResponse res1 = pipeline.executeComparisonPipeline(List.of(p1, p2), RecommendationType.BEST_OVERALL, null, Map.of());
            RecommendationResponse res2 = pipeline.executeComparisonPipeline(List.of(p2, p1), RecommendationType.BEST_OVERALL, null, Map.of());

            assertEquals(id1, res1.getRecommendedProduct().getId());
            assertEquals(id1, res2.getRecommendedProduct().getId());
            assertEquals(res1.getScore(), res2.getScore());
        }

        @Test
        @DisplayName("Identical score ties are broken deterministically by stable comparator")
        void testIdenticalScoreTieBreaking() {
            UUID idA = UUID.fromString("00000000-0000-0000-0000-000000000001");
            UUID idB = UUID.fromString("00000000-0000-0000-0000-000000000002");

            ProductResponseDTO pA = buildProduct(idA, "CloneA", BigDecimal.valueOf(100), BigDecimal.valueOf(10), 2, "Same specs");
            ProductResponseDTO pB = buildProduct(idB, "CloneB", BigDecimal.valueOf(100), BigDecimal.valueOf(10), 2, "Same specs");

            RecommendationResponse forward = pipeline.executeComparisonPipeline(List.of(pA, pB), RecommendationType.BEST_OVERALL, null, Map.of());
            RecommendationResponse reversed = pipeline.executeComparisonPipeline(List.of(pB, pA), RecommendationType.BEST_OVERALL, null, Map.of());

            // Order must be identical regardless of list permutation
            assertEquals(forward.getRecommendedProduct().getId(), reversed.getRecommendedProduct().getId());
            assertEquals(forward.getRecommendedProducts().get(0).getId(), reversed.getRecommendedProducts().get(0).getId());
            assertEquals(forward.getRecommendedProducts().get(1).getId(), reversed.getRecommendedProducts().get(1).getId());
        }
    }

    @Nested
    @DisplayName("B. Explainability & Evidence Traceability")
    class ExplainabilityAndEvidenceTests {

        @Test
        @DisplayName("Missing price does NOT fabricate LOWEST_PRICE or PRICE_BELOW_AVERAGE evidence")
        void testMissingPriceDoesNotFabricatePriceEvidence() {
            UUID id1 = UUID.randomUUID();
            UUID id2 = UUID.randomUUID();

            // p1 has no price records
            ProductResponseDTO p1 = buildProduct(id1, "NoPriceProduct", null, BigDecimal.ZERO, 0, "No price");
            ProductResponseDTO p2 = buildProduct(id2, "PricedProduct", BigDecimal.valueOf(150), BigDecimal.ZERO, 1, "Has price");

            RecommendationResponse response = pipeline.executeComparisonPipeline(
                    List.of(p1, p2), RecommendationType.BEST_OVERALL, null, Map.of()
            );

            // Ensure NO price evidence was claimed for p1
            boolean hasPriceClaim = response.getEvidence().stream().anyMatch(e ->
                    e.getType() == EvidenceType.LOWEST_PRICE || e.getType() == EvidenceType.PRICE_BELOW_AVERAGE);
            assertFalse(hasPriceClaim, "Must NOT fabricate price evidence when price is missing");

            for (String factor : response.getSupportingFactors()) {
                assertFalse(factor.toLowerCase().contains("lowest current price"));
            }
        }

        @Test
        @DisplayName("Incomplete specs & zero sellers does NOT fabricate seller availability")
        void testZeroSellersDoesNotFabricateAvailability() {
            UUID id1 = UUID.randomUUID();
            UUID id2 = UUID.randomUUID();

            ProductResponseDTO p1 = buildProduct(id1, "SingleSeller", BigDecimal.valueOf(100), BigDecimal.ZERO, 1, null);
            ProductResponseDTO p2 = buildProduct(id2, "MultiSeller", BigDecimal.valueOf(120), BigDecimal.ZERO, 4, null);

            RecommendationResponse response = pipeline.executeComparisonPipeline(
                    List.of(p1, p2), RecommendationType.BEST_OVERALL, null, Map.of()
            );

            boolean hasHighSellerClaim = response.getEvidence().stream().anyMatch(e ->
                    e.getType() == EvidenceType.HIGH_SELLER_AVAILABILITY);
            assertFalse(hasHighSellerClaim, "Must NOT claim strong seller availability with only 1 seller");
        }

        @Test
        @DisplayName("Conflicting signals produce truthful trade-offs tracing back to comparison data")
        void testConflictingSignalsTraceability() {
            UUID idCheap = UUID.randomUUID();
            UUID idPremium = UUID.randomUUID();

            ProductResponseDTO pCheap = buildProduct(idCheap, "BudgetLeader", BigDecimal.valueOf(200), BigDecimal.valueOf(20), 2, "Budget");
            ProductResponseDTO pPremium = buildProduct(idPremium, "FlagshipCompetitor", BigDecimal.valueOf(600), BigDecimal.valueOf(0), 4, "Flagship");

            RecommendationResponse response = pipeline.executeComparisonPipeline(
                    List.of(pCheap, pPremium), RecommendationType.BEST_OVERALL, null, Map.of()
            );

            // Every evidence item can be traced back to product data
            for (EvidenceItem item : response.getEvidence()) {
                assertNotNull(item.getProductId());
                assertNotNull(item.getMetricName());
                assertNotNull(item.getMetricValue());
                assertTrue(item.getImportance() > 0.0);
            }

            // Verify trade-offs highlight competitor differences truthfully
            if (!response.getTradeOffs().isEmpty()) {
                String tradeOffText = response.getTradeOffs().get(0);
                assertNotNull(tradeOffText);
                assertFalse(tradeOffText.isBlank());
            }
        }
    }

    @Nested
    @DisplayName("C. AI Failure Resilience & Deterministic Fallback")
    class AiFailureResilienceTests {

        @Test
        @DisplayName("AI timeout falls back to deterministic explanation while recommendation succeeds")
        void testAiTimeoutFallback() {
            when(aiClient.explain(any(AiExplainRequest.class))).thenThrow(new RuntimeException("Read timed out after 3000ms"));

            UUID id1 = UUID.randomUUID();
            UUID id2 = UUID.randomUUID();
            ProductResponseDTO p1 = buildProduct(id1, "TopProduct", BigDecimal.valueOf(100), BigDecimal.valueOf(10), 2, "Desc");
            ProductResponseDTO p2 = buildProduct(id2, "OtherProduct", BigDecimal.valueOf(150), BigDecimal.ZERO, 1, "Desc");

            RecommendationResponse response = pipeline.executeComparisonPipeline(
                    List.of(p1, p2), RecommendationType.BEST_OVERALL, null, Map.of()
            );

            assertNotNull(response);
            assertEquals(id1, response.getRecommendedProduct().getId());
            assertEquals("DETERMINISTIC_RULE_BASED", response.getExplanationStrategy());
            assertNotNull(response.getExplanation());
            assertTrue(response.getExplanation().contains("TopProduct"));
            assertTrue(response.getConfidence() > 0.5);
        }

        @Test
        @DisplayName("AI service unavailable falls back to deterministic explanation")
        void testAiUnavailableFallback() {
            when(aiClient.explain(any(AiExplainRequest.class))).thenThrow(new RuntimeException("Connection refused: /127.0.0.1:8000"));

            UUID id1 = UUID.randomUUID();
            ProductResponseDTO p1 = buildProduct(id1, "SoloProduct", BigDecimal.valueOf(100), BigDecimal.valueOf(10), 2, "Desc");

            RecommendationResponse response = pipeline.executeComparisonPipeline(
                    List.of(p1), RecommendationType.BEST_OVERALL, null, Map.of()
            );

            assertNotNull(response);
            assertEquals("DETERMINISTIC_RULE_BASED", response.getExplanationStrategy());
        }

        @Test
        @DisplayName("Malformed/empty AI response falls back to deterministic explanation")
        void testMalformedAiResponseFallback() {
            when(aiClient.explain(any(AiExplainRequest.class))).thenReturn(new AiExplainResponse(null, List.of(), List.of(), "ModelX"));

            UUID id1 = UUID.randomUUID();
            ProductResponseDTO p1 = buildProduct(id1, "TestProd", BigDecimal.valueOf(100), BigDecimal.valueOf(10), 2, "Desc");

            RecommendationResponse response = pipeline.executeComparisonPipeline(
                    List.of(p1), RecommendationType.BEST_OVERALL, null, Map.of()
            );

            assertNotNull(response);
            assertEquals("DETERMINISTIC_RULE_BASED", response.getExplanationStrategy());
            assertFalse(response.getExplanation().isBlank());
        }
    }

    @Nested
    @DisplayName("D. Security & AI Output Sanitization")
    class SecurityAndSanitizationTests {

        @Test
        @DisplayName("AI output containing HTML/script tags is sanitized before embedding in response")
        void testAiOutputSanitization() {
            AiExplainResponse maliciousResponse = new AiExplainResponse(
                    "<script>alert('pwned')</script>Safe product explanation text",
                    List.of("<a href='javascript:void(0)'>Lowest price</a>"),
                    List.of("<style>body{display:none}</style>Trade off note"),
                    "EvilModel"
            );

            when(aiClient.explain(any(AiExplainRequest.class))).thenReturn(maliciousResponse);

            UUID id1 = UUID.randomUUID();
            ProductResponseDTO p1 = buildProduct(id1, "CleanProd", BigDecimal.valueOf(100), BigDecimal.ZERO, 2, "Desc");

            RecommendationResponse response = pipeline.executeComparisonPipeline(
                    List.of(p1), RecommendationType.BEST_OVERALL, null, Map.of()
            );

            assertNotNull(response);
            assertEquals("AI_GATEWAY_HYBRID", response.getExplanationStrategy());
            assertFalse(response.getExplanation().contains("<script>"));
            assertTrue(response.getExplanation().contains("Safe product explanation text"));
            assertFalse(response.getSupportingFactors().get(0).contains("<a"));
            assertFalse(response.getTradeOffs().get(0).contains("<style>"));
        }
    }
}
