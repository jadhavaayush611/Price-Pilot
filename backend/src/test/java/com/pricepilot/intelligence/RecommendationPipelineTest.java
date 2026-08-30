package com.pricepilot.intelligence;

import com.pricepilot.ai.v2.RecommendationExplanation;
import com.pricepilot.intelligence.comparison.scoring.ComparisonScoringStrategy;
import com.pricepilot.intelligence.comparison.scoring.DefaultComparisonScorer;
import com.pricepilot.intelligence.recommendation.DefaultRecommendationPipeline;
import com.pricepilot.intelligence.recommendation.confidence.ConfidenceCalculator;
import com.pricepilot.intelligence.recommendation.dto.EvidenceItem;
import com.pricepilot.intelligence.recommendation.dto.ProductScore;
import com.pricepilot.intelligence.recommendation.dto.RecommendationResponse;
import com.pricepilot.intelligence.recommendation.dto.RecommendationType;
import com.pricepilot.intelligence.recommendation.entity.RecommendationHistoryEventEntity;
import com.pricepilot.intelligence.recommendation.evidence.EvidenceExtractor;
import com.pricepilot.intelligence.recommendation.explanation.DeterministicExplanationGenerator;
import com.pricepilot.intelligence.recommendation.repository.RecommendationHistoryEventRepository;
import com.pricepilot.product.ProductService;
import com.pricepilot.product.dto.ProductResponseDTO;
import com.pricepilot.productprice.dto.ProductPriceResponseDTO;
import com.pricepilot.seller.dto.SellerResponseDTO;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RecommendationPipelineTest {

    @Mock
    private ProductService productService;

    @Mock
    private RecommendationHistoryEventRepository historyRepository;

    private ComparisonScoringStrategy scoringStrategy;
    private EvidenceExtractor evidenceExtractor;
    private ConfidenceCalculator confidenceCalculator;
    private DeterministicExplanationGenerator explanationGenerator;
    private SimpleMeterRegistry meterRegistry;

    private DefaultRecommendationPipeline pipeline;

    @BeforeEach
    void setUp() {
        scoringStrategy = new DefaultComparisonScorer();
        evidenceExtractor = new EvidenceExtractor();
        confidenceCalculator = new ConfidenceCalculator();
        explanationGenerator = new DeterministicExplanationGenerator();
        meterRegistry = new SimpleMeterRegistry();

        pipeline = new DefaultRecommendationPipeline(
                productService,
                scoringStrategy,
                evidenceExtractor,
                confidenceCalculator,
                explanationGenerator,
                historyRepository,
                meterRegistry
        );
    }

    private ProductResponseDTO createProduct(UUID id, String name, BigDecimal price, BigDecimal discount, int sellerCount) {
        ProductResponseDTO p = new ProductResponseDTO();
        p.setId(id);
        p.setName(name);
        p.setDescription("Description for " + name);
        List<ProductPriceResponseDTO> prices = new ArrayList<>();
        for (int i = 0; i < sellerCount; i++) {
            ProductPriceResponseDTO pr = new ProductPriceResponseDTO();
            pr.setCurrentPrice(price);
            pr.setDiscountPercentage(discount);
            SellerResponseDTO s = new SellerResponseDTO();
            s.setName("Seller " + i);
            pr.setSeller(s);
            prices.add(pr);
        }
        p.setPrices(prices);
        return p;
    }

    @Test
    @DisplayName("Ranks candidates and selects Best Overall recommendation")
    void testBestOverallRanking() {
        UUID id1 = UUID.randomUUID();
        UUID id2 = UUID.randomUUID();
        ProductResponseDTO p1 = createProduct(id1, "OverallWinner", BigDecimal.valueOf(100), BigDecimal.valueOf(25), 3);
        ProductResponseDTO p2 = createProduct(id2, "RunnerUp", BigDecimal.valueOf(300), BigDecimal.valueOf(5), 1);

        RecommendationResponse response = pipeline.executeComparisonPipeline(
                List.of(p2, p1), RecommendationType.BEST_OVERALL, null, Map.of()
        );

        assertNotNull(response);
        assertEquals(id1, response.getRecommendedProduct().getId());
        assertEquals("BEST_OVERALL", response.getRecommendationType());
        assertTrue(response.getScore() > 0.0);
        assertTrue(response.getConfidence() > 0.50);
        assertNotNull(response.getExplanation());
        assertTrue(response.getExplanation().contains("OverallWinner"));
        assertFalse(response.getSupportingFactors().isEmpty());
        assertEquals("DEFAULT_COMPARISON_SCORER", response.getScoringStrategy());
        assertEquals("DETERMINISTIC_RULE_BASED", response.getExplanationStrategy());

        // Verify history logged
        ArgumentCaptor<RecommendationHistoryEventEntity> captor = ArgumentCaptor.forClass(RecommendationHistoryEventEntity.class);
        verify(historyRepository).save(captor.capture());
        assertEquals(id1, captor.getValue().getRecommendedProductId());
        assertEquals("BEST_OVERALL", captor.getValue().getRecommendationType());
    }

    @Test
    @DisplayName("Ranks candidates for Best Value prioritizing price/value score")
    void testBestValueRanking() {
        UUID idExpensive = UUID.randomUUID();
        UUID idCheap = UUID.randomUUID();

        ProductResponseDTO pExpensive = createProduct(idExpensive, "ExpensiveFlagship", BigDecimal.valueOf(1200), BigDecimal.ZERO, 3);
        ProductResponseDTO pCheap = createProduct(idCheap, "BudgetKing", BigDecimal.valueOf(200), BigDecimal.valueOf(15), 3);

        RecommendationResponse response = pipeline.executeComparisonPipeline(
                List.of(pExpensive, pCheap), RecommendationType.BEST_VALUE, null, Map.of()
        );

        assertEquals(idCheap, response.getRecommendedProduct().getId());
        assertEquals("BEST_VALUE", response.getRecommendationType());
        assertTrue(response.getExplanation().contains("BudgetKing"));
    }

    @Test
    @DisplayName("Ranks candidates for Best Discount prioritizing discount percentage")
    void testBestDiscountRanking() {
        UUID idNoDiscount = UUID.randomUUID();
        UUID idBigDiscount = UUID.randomUUID();

        ProductResponseDTO p1 = createProduct(idNoDiscount, "StandardPrice", BigDecimal.valueOf(100), BigDecimal.ZERO, 2);
        ProductResponseDTO p2 = createProduct(idBigDiscount, "HeavyDiscount", BigDecimal.valueOf(120), BigDecimal.valueOf(40), 2);

        RecommendationResponse response = pipeline.executeComparisonPipeline(
                List.of(p1, p2), RecommendationType.BEST_DISCOUNT, null, Map.of()
        );

        assertEquals(idBigDiscount, response.getRecommendedProduct().getId());
        assertEquals("BEST_DISCOUNT", response.getRecommendationType());
        assertTrue(response.getExplanation().contains("HeavyDiscount"));
    }

    @Test
    @DisplayName("Deterministic tie breaking produces consistent ranking on score ties")
    void testDeterministicTieBreaking() {
        UUID id1 = UUID.fromString("11111111-1111-1111-1111-111111111111");
        UUID id2 = UUID.fromString("22222222-2222-2222-2222-222222222222");

        // Identical attributes and identical price
        ProductResponseDTO p1 = createProduct(id1, "TwinA", BigDecimal.valueOf(100), BigDecimal.valueOf(10), 2);
        ProductResponseDTO p2 = createProduct(id2, "TwinB", BigDecimal.valueOf(100), BigDecimal.valueOf(10), 2);

        RecommendationResponse resOrder1 = pipeline.executeComparisonPipeline(List.of(p1, p2), RecommendationType.BEST_OVERALL, null, Map.of());
        RecommendationResponse resOrder2 = pipeline.executeComparisonPipeline(List.of(p2, p1), RecommendationType.BEST_OVERALL, null, Map.of());

        assertEquals(resOrder1.getRecommendedProduct().getId(), resOrder2.getRecommendedProduct().getId(),
                "Deterministic tie-breaker must produce identical recommendation regardless of input list order");
    }

    @Test
    @DisplayName("executePipeline retrieves target product and trending candidates")
    void testExecutePipelineFlow() {
        UUID targetId = UUID.randomUUID();
        UUID trendId = UUID.randomUUID();

        ProductResponseDTO target = createProduct(targetId, "TargetProduct", BigDecimal.valueOf(150), BigDecimal.valueOf(10), 2);
        ProductResponseDTO trend = createProduct(trendId, "TrendProduct", BigDecimal.valueOf(120), BigDecimal.valueOf(20), 3);

        when(productService.getProductById(targetId)).thenReturn(target);
        when(productService.getTrendingProducts(anyInt())).thenReturn(List.of(trend));

        RecommendationResponse response = pipeline.executePipeline(targetId, null, 5, Map.of("recommendationType", "BEST_OVERALL"));

        assertNotNull(response);
        assertEquals(targetId, response.getTargetProductId());
        assertNotNull(response.getRecommendedProduct());
        assertFalse(response.getRecommendedProducts().isEmpty());
    }
}
