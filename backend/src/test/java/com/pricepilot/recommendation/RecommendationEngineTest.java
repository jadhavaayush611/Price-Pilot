package com.pricepilot.recommendation;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pricepilot.analytics.ProductAnalyticsEntity;
import com.pricepilot.product.ProductEntity;
import com.pricepilot.product.ProductRepository;
import com.pricepilot.product.dto.ProductResponseDTO;
import com.pricepilot.productprice.ProductPriceRepository;
import com.pricepilot.recommendation.dto.RecommendationProfile;
import com.pricepilot.recommendation.dto.ScoredProduct;
import com.pricepilot.recommendation.engine.*;
import com.pricepilot.recommendation.explainability.RecommendationExplainer;
import com.pricepilot.savedproduct.SavedProductEntity;
import com.pricepilot.savedproduct.SavedProductRepository;
import com.pricepilot.watchlist.PriceWatchlistEntity;
import com.pricepilot.watchlist.PriceWatchlistRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class RecommendationEngineTest {

    @Mock
    private ProductRepository productRepository;
    @Mock
    private ProductPriceRepository productPriceRepository;
    @Mock
    private SavedProductRepository savedProductRepository;
    @Mock
    private PriceWatchlistRepository watchlistRepository;
    @Mock
    private RecommendationProfileService profileService;

    private RecommendationExplainer explainer;
    private RuleBasedRecommendationEngine ruleBasedEngine;
    private PopularityRecommendationEngine popularityEngine;
    private ContentBasedRecommendationEngine contentEngine;
    private CollaborativeFilteringEngine collaborativeEngine;
    private HybridRecommendationEngine hybridEngine;
    @Mock
    private com.pricepilot.ai.AiGatewayService aiGatewayService;

    private RecommendationServiceImpl recommendationService;

    private UUID userId;
    private List<ProductEntity> candidates;
    private RecommendationProfile profile;

    @BeforeEach
    void setUp() {
        explainer = new RecommendationExplainer();
        ruleBasedEngine = new RuleBasedRecommendationEngine(explainer);
        popularityEngine = new PopularityRecommendationEngine(explainer);
        contentEngine = new ContentBasedRecommendationEngine(explainer);
        collaborativeEngine = new CollaborativeFilteringEngine(explainer, savedProductRepository, watchlistRepository);
        hybridEngine = new HybridRecommendationEngine(popularityEngine, contentEngine, collaborativeEngine, explainer);

        // Inject default weights for Hybrid Engine tests
        ReflectionTestUtils.setField(hybridEngine, "wPopularity", 0.20);
        ReflectionTestUtils.setField(hybridEngine, "wContent", 0.35);
        ReflectionTestUtils.setField(hybridEngine, "wCollaborative", 0.45);

        recommendationService = new RecommendationServiceImpl(
                productRepository, productPriceRepository, savedProductRepository, watchlistRepository, profileService,
                ruleBasedEngine, popularityEngine, contentEngine, collaborativeEngine, hybridEngine, aiGatewayService
        );

        userId = UUID.randomUUID();

        // Setup candidate products
        ProductEntity p1 = new ProductEntity();
        p1.setId(UUID.randomUUID());
        p1.setName("Laptop");
        p1.setCategory("Electronics");
        p1.setBrand("Dell");
        p1.setArchived(false);
        ProductAnalyticsEntity a1 = new ProductAnalyticsEntity();
        a1.setViewCount(150L);
        a1.setSaveCount(10L);
        a1.setWatchlistCount(5L);
        p1.setAnalytics(a1);

        ProductEntity p2 = new ProductEntity();
        p2.setId(UUID.randomUUID());
        p2.setName("Headphones");
        p2.setCategory("Electronics");
        p2.setBrand("Sony");
        p2.setArchived(false);
        ProductAnalyticsEntity a2 = new ProductAnalyticsEntity();
        a2.setViewCount(50L);
        a2.setSaveCount(2L);
        a2.setWatchlistCount(1L);
        p2.setAnalytics(a2);

        candidates = Arrays.asList(p1, p2);

        // Setup profile
        profile = new RecommendationProfile();
        Map<String, Double> preferredCategories = new HashMap<>();
        preferredCategories.put("Electronics", 1.0);
        profile.setPreferredCategories(preferredCategories);
        profile.setPreferredBrands(new HashMap<>());
        profile.setPreferredSellers(new HashMap<>());
    }

    @Test
    void testRuleBasedEngineRecommendation() {
        List<ScoredProduct> recommendations = ruleBasedEngine.recommend(
                userId, candidates, profile, Collections.emptyList(), Collections.emptyList(), 10
        );

        assertNotNull(recommendations);
        assertEquals(2, recommendations.size());
        assertEquals("Laptop", recommendations.get(0).getProduct().getName());
        assertFalse(recommendations.get(0).getReasons().isEmpty());
    }

    @Test
    void testPopularityEngineRecommendation() {
        List<ScoredProduct> recommendations = popularityEngine.recommend(
                userId, candidates, profile, Collections.emptyList(), Collections.emptyList(), 10
        );

        assertNotNull(recommendations);
        assertEquals(2, recommendations.size());
        assertTrue(recommendations.get(0).getScore() > recommendations.get(1).getScore());
    }

    @Test
    void testContentBasedEngineRecommendation() {
        List<ScoredProduct> recommendations = contentEngine.recommend(
                userId, candidates, profile, Collections.emptyList(), Collections.emptyList(), 10
        );

        assertNotNull(recommendations);
        assertEquals(2, recommendations.size());
        assertEquals("Electronics", recommendations.get(0).getProduct().getCategory());
    }

    @Test
    void testHybridEngineRecommendation() {
        List<ScoredProduct> recommendations = hybridEngine.recommend(
                userId, candidates, profile, Collections.emptyList(), Collections.emptyList(), 10
        );

        assertNotNull(recommendations);
        assertEquals(2, recommendations.size());
    }

    @Test
    void testStrategySwitchingViaConfiguration() {
        // Set strategy to POPULARITY
        ReflectionTestUtils.setField(recommendationService, "strategy", "POPULARITY");
        RecommendationEngine engine = recommendationService.getActiveEngine();
        assertEquals("Popularity", engine.getAlgorithmName());

        // Set strategy to CONTENT
        ReflectionTestUtils.setField(recommendationService, "strategy", "CONTENT");
        engine = recommendationService.getActiveEngine();
        assertEquals("Content", engine.getAlgorithmName());

        // Set strategy to HYBRID
        ReflectionTestUtils.setField(recommendationService, "strategy", "HYBRID");
        engine = recommendationService.getActiveEngine();
        assertEquals("Hybrid", engine.getAlgorithmName());

        // Fallback strategy
        ReflectionTestUtils.setField(recommendationService, "strategy", "INVALID_NAME");
        engine = recommendationService.getActiveEngine();
        assertEquals("RuleBased", engine.getAlgorithmName());
    }

    @Test
    void testExplainabilitySerialization() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        ProductResponseDTO dto = new ProductResponseDTO();
        dto.setId(UUID.randomUUID());
        dto.setName("Test Product");
        dto.setRecommendationAlgorithm("Hybrid");
        dto.setRecommendationScore(0.95);
        dto.setRecommendationReasons(Arrays.asList("Popular item", "Matches Electronics preference"));

        String json = mapper.writeValueAsString(dto);
        assertTrue(json.contains("\"recommendationScore\":0.95"));
        assertTrue(json.contains("\"recommendationAlgorithm\":\"Hybrid\""));
        assertTrue(json.contains("\"recommendationReasons\":[\"Popular item\",\"Matches Electronics preference\"]"));
    }
}
