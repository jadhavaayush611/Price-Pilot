package com.pricepilot.intelligence.personalization;

import com.pricepilot.ai.v2.ExplanationGenerator;
import com.pricepilot.ai.v2.RecommendationExplanation;
import com.pricepilot.intelligence.comparison.scoring.ComparisonScoringStrategy;
import com.pricepilot.intelligence.personalization.preference.AvailabilityPreference;
import com.pricepilot.intelligence.personalization.preference.DealSensitivity;
import com.pricepilot.intelligence.personalization.preference.PriceSensitivity;
import com.pricepilot.intelligence.personalization.preference.UserShoppingPreferenceEntity;
import com.pricepilot.intelligence.personalization.preference.UserShoppingPreferenceService;
import com.pricepilot.intelligence.personalization.scoring.DefaultPersonalizationScorer;
import com.pricepilot.intelligence.personalization.scoring.PersonalizationScorer;
import com.pricepilot.intelligence.personalization.signals.BehavioralSignalService;
import com.pricepilot.intelligence.personalization.signals.UserShoppingSignals;
import com.pricepilot.intelligence.recommendation.DefaultRecommendationPipeline;
import com.pricepilot.intelligence.recommendation.RecommendationServiceImpl;
import com.pricepilot.intelligence.recommendation.confidence.ConfidenceCalculator;
import com.pricepilot.intelligence.recommendation.dto.EvidenceType;
import com.pricepilot.intelligence.recommendation.dto.ProductScore;
import com.pricepilot.intelligence.recommendation.dto.RecommendationResponse;
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

@ExtendWith(MockitoExtension.class)
class PersonalizedRecommendationIntegrationTest {

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
    private UserShoppingPreferenceService preferenceService;
    @Mock
    private BehavioralSignalService behavioralSignalService;

    private PersonalizationScorer personalizationScorer;
    private DefaultRecommendationPipeline pipeline;
    private RecommendationServiceImpl recommendationService;

    private UUID userA;
    private UUID userB;

    private ProductResponseDTO applePhone;
    private ProductResponseDTO samsungPhone;

    @BeforeEach
    void setUp() {
        userA = UUID.randomUUID();
        userB = UUID.randomUUID();

        personalizationScorer = new DefaultPersonalizationScorer();
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
                personalizationScorer
        );

        recommendationService = new RecommendationServiceImpl(
                productService,
                pipeline,
                metadataRepository,
                preferenceService,
                behavioralSignalService,
                productRepository
        );

        // Mock candidates
        applePhone = ProductResponseDTO.builder()
                .id(UUID.fromString("11111111-1111-1111-1111-111111111111"))
                .name("iPhone 15 Pro")
                .brand("Apple")
                .category("Smartphones")
                .prices(List.of(ProductPriceResponseDTO.builder().currentPrice(BigDecimal.valueOf(999)).build()))
                .build();

        samsungPhone = ProductResponseDTO.builder()
                .id(UUID.fromString("22222222-2222-2222-2222-222222222222"))
                .name("Galaxy S24")
                .brand("Samsung")
                .category("Smartphones")
                .prices(List.of(ProductPriceResponseDTO.builder().currentPrice(BigDecimal.valueOf(799)).build()))
                .build();
    }

    @Test
    @DisplayName("Throws AccessDeniedException when userId is null")
    void testAuthenticationRequired() {
        assertThrows(AccessDeniedException.class, () ->
                recommendationService.getPersonalizedRecommendations(null, 5)
        );
    }

    @Test
    @DisplayName("Cold start user receives valid recommendations without preferences or signals")
    void testColdStartPersonalizedRecommendations() {
        when(preferenceService.getPreferenceEntity(userA)).thenReturn(Optional.empty());
        when(behavioralSignalService.extractSignals(userA)).thenReturn(UserShoppingSignals.builder().userId(userA).build());
        when(productService.getTrendingProducts(any(Integer.class))).thenReturn(List.of(applePhone, samsungPhone));

        // Base objective scoring (equal scores)
        Map<UUID, ProductScore> baseScores = Map.of(
                applePhone.getId(), new ProductScore(applePhone.getId(), applePhone.getName(), 85.0, 80.0, 90.0, 85.0, new HashMap<>(), "TOP"),
                samsungPhone.getId(), new ProductScore(samsungPhone.getId(), samsungPhone.getName(), 85.0, 85.0, 85.0, 85.0, new HashMap<>(), "TOP")
        );
        when(scoringStrategy.calculateScores(any())).thenReturn(baseScores);
        when(explanationGenerator.generateExplanation(any(), any(), any(), any(), any(), any(Double.class), any(Double.class)))
                .thenReturn(new RecommendationExplanation(applePhone.getId(), "Great balance of performance.", List.of("Competitive price"), 0.90, "DETERMINISTIC"));

        RecommendationResponse response = recommendationService.getPersonalizedRecommendations(userA, 5);

        assertNotNull(response);
        assertEquals(userA, response.getUserId());
        assertNotNull(response.getRecommendedProduct());
        assertEquals(85.0, response.getBaseScore());
        assertEquals(0.0, response.getPersonalizationContribution());
    }

    @Test
    @DisplayName("Strict user isolation: User A's Apple preference ranks Apple top; User B's Samsung preference ranks Samsung top")
    void testCrossUserPreferenceIsolation() {
        // User A prefers Apple
        UserShoppingPreferenceEntity prefA = UserShoppingPreferenceEntity.builder()
                .userId(userA)
                .preferredBrands(Set.of("Apple"))
                .dealSensitivity(DealSensitivity.MEDIUM)
                .priceSensitivity(PriceSensitivity.MEDIUM)
                .availabilityPreference(AvailabilityPreference.ALL)
                .build();

        // User B prefers Samsung
        UserShoppingPreferenceEntity prefB = UserShoppingPreferenceEntity.builder()
                .userId(userB)
                .preferredBrands(Set.of("Samsung"))
                .dealSensitivity(DealSensitivity.MEDIUM)
                .priceSensitivity(PriceSensitivity.MEDIUM)
                .availabilityPreference(AvailabilityPreference.ALL)
                .build();

        when(productService.getTrendingProducts(any(Integer.class))).thenReturn(List.of(applePhone, samsungPhone));

        when(scoringStrategy.calculateScores(any())).thenAnswer(inv -> {
            Map<UUID, ProductScore> map = new HashMap<>();
            map.put(applePhone.getId(), new ProductScore(applePhone.getId(), applePhone.getName(), 80.0, 75.0, 85.0, 80.0, new HashMap<>(), "TOP"));
            map.put(samsungPhone.getId(), new ProductScore(samsungPhone.getId(), samsungPhone.getName(), 80.0, 75.0, 85.0, 80.0, new HashMap<>(), "TOP"));
            return map;
        });
        when(explanationGenerator.generateExplanation(any(), any(), any(), any(), any(), any(Double.class), any(Double.class)))
                .thenAnswer(inv -> new RecommendationExplanation(
                        ((ProductResponseDTO) inv.getArgument(0)).getId(),
                        "Top choice for your setup.",
                        List.of(),
                        0.90,
                        "DETERMINISTIC"
                ));

        // 1. Evaluate User A
        when(preferenceService.getPreferenceEntity(userA)).thenReturn(Optional.of(prefA));
        when(behavioralSignalService.extractSignals(userA)).thenReturn(UserShoppingSignals.builder().userId(userA).build());
        RecommendationResponse responseA = recommendationService.getPersonalizedRecommendations(userA, 5);

        assertEquals(applePhone.getId(), responseA.getRecommendedProduct().getId());
        assertTrue(responseA.getPersonalizationContribution() > 0);
        assertTrue(responseA.getPersonalizationEvidence().stream().anyMatch(e -> e.getType() == EvidenceType.PREFERRED_BRAND));

        // 2. Evaluate User B
        when(preferenceService.getPreferenceEntity(userB)).thenReturn(Optional.of(prefB));
        when(behavioralSignalService.extractSignals(userB)).thenReturn(UserShoppingSignals.builder().userId(userB).build());
        RecommendationResponse responseB = recommendationService.getPersonalizedRecommendations(userB, 5);

        assertEquals(samsungPhone.getId(), responseB.getRecommendedProduct().getId());
        assertTrue(responseB.getPersonalizationContribution() > 0);
        assertTrue(responseB.getPersonalizationEvidence().stream().anyMatch(e -> e.getType() == EvidenceType.PREFERRED_BRAND));
    }
}
