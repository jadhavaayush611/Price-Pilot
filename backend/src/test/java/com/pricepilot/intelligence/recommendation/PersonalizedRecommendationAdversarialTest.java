package com.pricepilot.intelligence.recommendation;

import com.pricepilot.ai.v2.ExplanationGenerator;
import com.pricepilot.ai.v2.RecommendationExplanation;
import com.pricepilot.intelligence.comparison.scoring.ComparisonScoringStrategy;
import com.pricepilot.intelligence.personalization.context.*;
import com.pricepilot.intelligence.personalization.evidence.DefaultPersonalizedEvidenceGenerator;
import com.pricepilot.intelligence.personalization.evidence.PersonalizedEvidenceGenerator;
import com.pricepilot.intelligence.personalization.scoring.DefaultPersonalizedScoringStrategy;
import com.pricepilot.intelligence.personalization.scoring.PersonalizedScoringStrategy;
import com.pricepilot.intelligence.recommendation.confidence.ConfidenceCalculator;
import com.pricepilot.intelligence.recommendation.dto.EvidenceItem;
import com.pricepilot.intelligence.recommendation.dto.EvidenceType;
import com.pricepilot.intelligence.recommendation.dto.ProductScore;
import com.pricepilot.intelligence.recommendation.dto.RecommendationResponse;
import com.pricepilot.intelligence.recommendation.dto.RecommendationType;
import com.pricepilot.intelligence.recommendation.evidence.EvidenceExtractor;
import com.pricepilot.intelligence.recommendation.repository.RecommendationHistoryEventRepository;
import com.pricepilot.intelligence.recommendation.repository.RecommendationMetadataRepository;
import com.pricepilot.product.ProductRepository;
import com.pricepilot.product.ProductService;
import com.pricepilot.product.dto.ProductResponseDTO;
import com.pricepilot.productprice.dto.ProductPriceResponseDTO;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;

import java.math.BigDecimal;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * Adversarial and invariant verification test suite for Personalized Recommendations (Phase 6.8).
 */
@ExtendWith(MockitoExtension.class)
class PersonalizedRecommendationAdversarialTest {

    @Mock
    private ProductService productService;
    @Mock
    private ProductRepository productRepository;
    @Mock
    private RecommendationMetadataRepository metadataRepository;
    @Mock
    private RecommendationHistoryEventRepository historyRepository;
    @Mock
    private ComparisonScoringStrategy scoringStrategy;
    @Mock
    private ExplanationGenerator explanationGenerator;
    @Mock
    private PersonalizationContextProvider contextProvider;

    private PersonalizedScoringStrategy personalizedScoringStrategy;
    private PersonalizedEvidenceGenerator personalizedEvidenceGenerator;
    private DefaultRecommendationPipeline pipeline;
    private RecommendationServiceImpl recommendationService;

    private UUID userId;
    private UUID otherUserId;

    private ProductResponseDTO sonyHeadphones;
    private ProductResponseDTO boseHeadphones;
    private ProductResponseDTO sennheiserHeadphones;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();
        otherUserId = UUID.randomUUID();

        personalizedScoringStrategy = new DefaultPersonalizedScoringStrategy();
        personalizedEvidenceGenerator = new DefaultPersonalizedEvidenceGenerator();
        EvidenceExtractor evidenceExtractor = new EvidenceExtractor();
        ConfidenceCalculator confidenceCalculator = new ConfidenceCalculator();

        pipeline = new DefaultRecommendationPipeline(
                productService,
                scoringStrategy,
                evidenceExtractor,
                confidenceCalculator,
                explanationGenerator,
                historyRepository,
                new SimpleMeterRegistry(),
                personalizedScoringStrategy,
                personalizedEvidenceGenerator
        );

        recommendationService = new RecommendationServiceImpl(
                productService,
                pipeline,
                metadataRepository,
                contextProvider,
                productRepository
        );

        sonyHeadphones = ProductResponseDTO.builder()
                .id(UUID.fromString("11111111-aaaa-1111-aaaa-111111111111"))
                .name("Sony WH-1000XM5")
                .brand("Sony")
                .category("Audio")
                .prices(List.of(ProductPriceResponseDTO.builder().currentPrice(BigDecimal.valueOf(350)).build()))
                .build();

        boseHeadphones = ProductResponseDTO.builder()
                .id(UUID.fromString("22222222-bbbb-2222-bbbb-222222222222"))
                .name("Bose QuietComfort Ultra")
                .brand("Bose")
                .category("Audio")
                .prices(List.of(ProductPriceResponseDTO.builder().currentPrice(BigDecimal.valueOf(400)).build()))
                .build();

        sennheiserHeadphones = ProductResponseDTO.builder()
                .id(UUID.fromString("33333333-cccc-3333-cccc-333333333333"))
                .name("Sennheiser Momentum 4")
                .brand("Sennheiser")
                .category("Audio")
                .prices(List.of(ProductPriceResponseDTO.builder().currentPrice(BigDecimal.valueOf(300)).build()))
                .build();
    }

    @Test
    @DisplayName("Empty context produces exactly neutral personalization adjustment (0.0) and preserves base ranking")
    void testEmptyContextNeutrality() {
        when(contextProvider.getPersonalizationContext(userId)).thenReturn(PersonalizationContext.empty(userId));
        when(productService.getTrendingProducts(any(Integer.class)))
                .thenReturn(List.of(sonyHeadphones, boseHeadphones, sennheiserHeadphones));

        Map<UUID, ProductScore> baseScores = new HashMap<>();
        baseScores.put(sonyHeadphones.getId(), new ProductScore(sonyHeadphones.getId(), sonyHeadphones.getName(), 90.0, 85.0, 95.0, 90.0, new HashMap<>(), "TOP"));
        baseScores.put(boseHeadphones.getId(), new ProductScore(boseHeadphones.getId(), boseHeadphones.getName(), 85.0, 80.0, 90.0, 85.0, new HashMap<>(), "TOP"));
        baseScores.put(sennheiserHeadphones.getId(), new ProductScore(sennheiserHeadphones.getId(), sennheiserHeadphones.getName(), 80.0, 90.0, 75.0, 80.0, new HashMap<>(), "TOP"));
        when(scoringStrategy.calculateScores(any())).thenReturn(baseScores);

        when(explanationGenerator.generateExplanation(any(), any(), any(), any(), any(), any(Double.class), any(Double.class)))
                .thenReturn(new RecommendationExplanation(sonyHeadphones.getId(), "Best in class audio.", List.of("Superior sound"), 0.95, "DETERMINISTIC"));

        RecommendationResponse response = recommendationService.getPersonalizedRecommendations(userId, 5);

        assertNotNull(response);
        assertEquals(sonyHeadphones.getId(), response.getRecommendedProduct().getId());
        assertEquals(90.0, response.getBaseScore());
        assertEquals(0.0, response.getPersonalizationContribution());
        assertEquals(90.0, response.getScore());
        assertTrue(response.getPersonalizationEvidence().isEmpty());
    }

    @Test
    @DisplayName("Brand preference boosts matching product above higher base-scoring candidate when within adjustment bounds")
    void testBrandPreferenceBoost() {
        // User prefers Bose
        PersonalizationContext context = PersonalizationContext.builder(userId)
                .addPreferredBrand("bose")
                .build();

        when(contextProvider.getPersonalizationContext(userId)).thenReturn(context);
        when(productService.getTrendingProducts(any(Integer.class)))
                .thenReturn(List.of(sonyHeadphones, boseHeadphones));

        Map<UUID, ProductScore> baseScores = new HashMap<>();
        // Sony base 88, Bose base 82. Bose brand match adds +10 -> Bose 92 > Sony 88
        baseScores.put(sonyHeadphones.getId(), new ProductScore(sonyHeadphones.getId(), sonyHeadphones.getName(), 88.0, 85.0, 90.0, 88.0, new HashMap<>(), "TOP"));
        baseScores.put(boseHeadphones.getId(), new ProductScore(boseHeadphones.getId(), boseHeadphones.getName(), 82.0, 80.0, 85.0, 82.0, new HashMap<>(), "TOP"));
        when(scoringStrategy.calculateScores(any())).thenReturn(baseScores);

        when(explanationGenerator.generateExplanation(any(), any(), any(), any(), any(), any(Double.class), any(Double.class)))
                .thenAnswer(inv -> new RecommendationExplanation(
                        ((ProductResponseDTO) inv.getArgument(0)).getId(),
                        "Top personalized pick.",
                        List.of(),
                        0.92,
                        "DETERMINISTIC"
                ));

        RecommendationResponse response = recommendationService.getPersonalizedRecommendations(userId, 5);

        assertNotNull(response);
        assertEquals(boseHeadphones.getId(), response.getRecommendedProduct().getId());
        assertEquals(82.0, response.getBaseScore());
        assertEquals(10.0, response.getPersonalizationContribution());
        assertEquals(92.0, response.getScore());
        assertFalse(response.getPersonalizationEvidence().isEmpty());
        assertTrue(response.getPersonalizationEvidence().stream().anyMatch(e -> e.getType() == EvidenceType.PREFERRED_BRAND));
    }

    @Test
    @DisplayName("Candidate input list order shuffling produces identical deterministic recommendation")
    void testInputShufflingDeterminism() {
        PersonalizationContext context = PersonalizationContext.builder(userId)
                .addPreferredBrand("sony")
                .build();

        Map<UUID, ProductScore> baseScores = new HashMap<>();
        baseScores.put(sonyHeadphones.getId(), new ProductScore(sonyHeadphones.getId(), sonyHeadphones.getName(), 80.0, 80.0, 80.0, 80.0, new HashMap<>(), "TOP"));
        baseScores.put(boseHeadphones.getId(), new ProductScore(boseHeadphones.getId(), boseHeadphones.getName(), 80.0, 80.0, 80.0, 80.0, new HashMap<>(), "TOP"));
        baseScores.put(sennheiserHeadphones.getId(), new ProductScore(sennheiserHeadphones.getId(), sennheiserHeadphones.getName(), 80.0, 80.0, 80.0, 80.0, new HashMap<>(), "TOP"));
        when(scoringStrategy.calculateScores(any())).thenReturn(baseScores);

        when(explanationGenerator.generateExplanation(any(), any(), any(), any(), any(), any(Double.class), any(Double.class)))
                .thenReturn(new RecommendationExplanation(sonyHeadphones.getId(), "Explanation", List.of(), 0.90, "DETERMINISTIC"));

        Map<String, Object> ctx = Map.of("personalized", true, "personalizationContext", context);

        RecommendationResponse r1 = pipeline.executeComparisonPipeline(
                List.of(sonyHeadphones, boseHeadphones, sennheiserHeadphones), RecommendationType.BEST_OVERALL, userId, ctx);
        RecommendationResponse r2 = pipeline.executeComparisonPipeline(
                List.of(sennheiserHeadphones, boseHeadphones, sonyHeadphones), RecommendationType.BEST_OVERALL, userId, ctx);
        RecommendationResponse r3 = pipeline.executeComparisonPipeline(
                List.of(boseHeadphones, sonyHeadphones, sennheiserHeadphones), RecommendationType.BEST_OVERALL, userId, ctx);

        assertEquals(r1.getRecommendedProduct().getId(), r2.getRecommendedProduct().getId());
        assertEquals(r1.getRecommendedProduct().getId(), r3.getRecommendedProduct().getId());
        assertEquals(sonyHeadphones.getId(), r1.getRecommendedProduct().getId());
    }

    @Test
    @DisplayName("Context provider runtime failure gracefully falls back to empty context")
    void testContextProviderFailureFallback() {
        when(contextProvider.getPersonalizationContext(userId)).thenThrow(new RuntimeException("Context service database timeout"));
        when(productService.getTrendingProducts(any(Integer.class)))
                .thenReturn(List.of(sonyHeadphones, boseHeadphones));

        Map<UUID, ProductScore> baseScores = Map.of(
                sonyHeadphones.getId(), new ProductScore(sonyHeadphones.getId(), sonyHeadphones.getName(), 90.0, 85.0, 95.0, 90.0, new HashMap<>(), "TOP"),
                boseHeadphones.getId(), new ProductScore(boseHeadphones.getId(), boseHeadphones.getName(), 85.0, 80.0, 90.0, 85.0, new HashMap<>(), "TOP")
        );
        when(scoringStrategy.calculateScores(any())).thenReturn(baseScores);
        when(explanationGenerator.generateExplanation(any(), any(), any(), any(), any(), any(Double.class), any(Double.class)))
                .thenReturn(new RecommendationExplanation(sonyHeadphones.getId(), "Top Sony.", List.of(), 0.90, "DETERMINISTIC"));

        // Must not throw, should fall back cleanly
        RecommendationResponse response = recommendationService.getPersonalizedRecommendations(userId, 5);

        assertNotNull(response);
        assertEquals(sonyHeadphones.getId(), response.getRecommendedProduct().getId());
        assertEquals(0.0, response.getPersonalizationContribution());
    }

    @Test
    @DisplayName("Security bounds: Null user ID throws AccessDeniedException")
    void testSecurityBounds() {
        assertThrows(AccessDeniedException.class, () ->
                recommendationService.getPersonalizedRecommendations(null, 5)
        );
    }
}
