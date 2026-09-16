package com.pricepilot.intelligence.discovery.personalized;

import com.pricepilot.intelligence.analytics.PriceAnalyticsService;
import com.pricepilot.intelligence.discovery.dto.DiscoveryProductDTO;
import com.pricepilot.intelligence.discovery.dto.DiscoverySearchRequestDTO;
import com.pricepilot.intelligence.discovery.dto.DiscoverySearchResponseDTO;
import com.pricepilot.intelligence.discovery.interpretation.InterpretedQuery;
import com.pricepilot.intelligence.discovery.interpretation.QueryInterpreter;
import com.pricepilot.intelligence.discovery.ranking.DefaultSearchRelevanceScorer;
import com.pricepilot.intelligence.discovery.ranking.ScoredProductCandidate;
import com.pricepilot.intelligence.personalization.context.PersonalizationContext;
import com.pricepilot.intelligence.personalization.context.PersonalizationContextProvider;
import com.pricepilot.intelligence.personalization.evidence.DefaultPersonalizedEvidenceGenerator;
import com.pricepilot.intelligence.personalization.evidence.PersonalizedEvidenceGenerator;
import com.pricepilot.intelligence.personalization.scoring.DefaultPersonalizedScoringStrategy;
import com.pricepilot.intelligence.personalization.scoring.PersonalizedScoringStrategy;
import com.pricepilot.product.ProductEntity;
import com.pricepilot.product.ProductRepository;
import com.pricepilot.productprice.ProductPriceEntity;
import com.pricepilot.productprice.ProductPriceRepository;
import com.pricepilot.seller.SellerEntity;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("Phase 6.6 - Personalized Discovery Adversarial & Resilience Test Suite")
class PersonalizedDiscoveryAdversarialTest {

    @Mock
    private ProductRepository productRepository;

    @Mock
    private ProductPriceRepository productPriceRepository;

    @Mock
    private PriceAnalyticsService priceAnalyticsService;

    @Mock
    private QueryInterpreter queryInterpreter;

    @Mock
    private DefaultSearchRelevanceScorer relevanceScorer;

    @Mock
    private PersonalizationContextProvider personalizationContextProvider;

    private PersonalizedScoringStrategy personalizedScoringStrategy;
    private PersonalizedEvidenceGenerator personalizedEvidenceGenerator;
    private PersonalizedDiscoveryServiceImpl discoveryService;

    private SellerEntity mockSeller;

    @BeforeEach
    void setUp() {
        personalizedScoringStrategy = new DefaultPersonalizedScoringStrategy();
        personalizedEvidenceGenerator = new DefaultPersonalizedEvidenceGenerator();

        discoveryService = new PersonalizedDiscoveryServiceImpl(
                productRepository,
                productPriceRepository,
                priceAnalyticsService,
                queryInterpreter,
                relevanceScorer,
                personalizationContextProvider,
                personalizedScoringStrategy,
                personalizedEvidenceGenerator,
                new SimpleMeterRegistry()
        );

        mockSeller = SellerEntity.builder().name("Official Store").build();
        mockSeller.setId(UUID.randomUUID());
    }

    private ProductEntity createProductEntity(UUID id, String name, String brand, String category) {
        ProductEntity entity = ProductEntity.builder()
                .name(name)
                .brand(brand)
                .category(category)
                .description("Test description")
                .imageUrl("https://example.com/img.png")
                .archived(false)
                .build();
        entity.setId(id);
        entity.setCreatedAt(LocalDateTime.now());
        entity.setUpdatedAt(LocalDateTime.now());
        return entity;
    }

    private ProductPriceEntity createPriceEntity(ProductEntity product, BigDecimal price, BigDecimal discount) {
        ProductPriceEntity priceEntity = ProductPriceEntity.builder()
                .product(product)
                .seller(mockSeller)
                .currentPrice(price)
                .originalPrice(price.add(BigDecimal.valueOf(50)))
                .discountPercentage(discount)
                .productUrl("https://example.com/product")
                .build();
        priceEntity.setId(UUID.randomUUID());
        return priceEntity;
    }

    @Nested
    @DisplayName("Provider Resilience & Graceful Fallback")
    class ProviderResilience {

        @Test
        @DisplayName("Gracefully falls back to empty context and base discovery when provider throws RuntimeException")
        void testProviderFailureGracefulFallback() {
            UUID userId = UUID.randomUUID();
            UUID prodId = UUID.randomUUID();
            ProductEntity product = createProductEntity(prodId, "Flagship Phone", "TechBrand", "Smartphones");
            ProductPriceEntity price = createPriceEntity(product, BigDecimal.valueOf(800), BigDecimal.valueOf(15));

            when(queryInterpreter.interpret(any())).thenReturn(InterpretedQuery.builder().searchTokens(List.of("phone")).build());
            when(productRepository.findAll(any(Specification.class), any(Pageable.class)))
                    .thenReturn(new PageImpl<>(List.of(product)));
            when(productPriceRepository.findPricesWithSellersByProductIds(anyList()))
                    .thenReturn(List.of(price));

            doAnswer(inv -> {
                ScoredProductCandidate cand = inv.getArgument(0);
                cand.setRelevanceScore(78.0);
                return null;
            }).when(relevanceScorer).scoreCandidate(any(), any());

            // Provider throws Redis/database connection exception
            when(personalizationContextProvider.getPersonalizationContext(userId))
                    .thenThrow(new RuntimeException("Redis connection timeout"));

            DiscoverySearchRequestDTO request = DiscoverySearchRequestDTO.builder().query("phone").build();
            DiscoverySearchResponseDTO response = discoveryService.discover(request, userId);

            // Discovery must succeed without throwing exception!
            assertNotNull(response);
            assertEquals(1, response.getContent().size());
            DiscoveryProductDTO result = response.getContent().get(0);
            assertEquals(prodId, result.getId());
            assertEquals(78.0, result.getPersonalizedScore());
            assertEquals(0.0, result.getPersonalizationAdjustment());
            assertFalse(result.getPersonalizedEvidence().hasPersonalization());
        }
    }

    @Nested
    @DisplayName("User Isolation")
    class UserIsolation {

        @Test
        @DisplayName("User A (Apple preferred) and User B (Sony preferred) receive isolated personalized rankings")
        void testCrossUserIsolation() {
            UUID userA = UUID.randomUUID();
            UUID userB = UUID.randomUUID();

            UUID idApple = UUID.randomUUID();
            UUID idSony = UUID.randomUUID();

            ProductEntity appleProduct = createProductEntity(idApple, "Apple Headset", "Apple", "Audio");
            ProductEntity sonyProduct = createProductEntity(idSony, "Sony Headset", "Sony", "Audio");

            ProductPriceEntity priceApple = createPriceEntity(appleProduct, BigDecimal.valueOf(300), BigDecimal.valueOf(5));
            ProductPriceEntity priceSony = createPriceEntity(sonyProduct, BigDecimal.valueOf(300), BigDecimal.valueOf(5));

            when(queryInterpreter.interpret(any())).thenReturn(InterpretedQuery.builder().searchTokens(List.of("headset")).build());
            when(productRepository.findAll(any(Specification.class), any(Pageable.class)))
                    .thenReturn(new PageImpl<>(List.of(appleProduct, sonyProduct)));
            when(productPriceRepository.findPricesWithSellersByProductIds(anyList()))
                    .thenReturn(List.of(priceApple, priceSony));

            doAnswer(inv -> {
                ScoredProductCandidate cand = inv.getArgument(0);
                cand.setRelevanceScore(75.0); // Both have identical 75.0 base score
                return null;
            }).when(relevanceScorer).scoreCandidate(any(), any());

            PersonalizationContext contextA = PersonalizationContext.builder(userA).addPreferredBrand("Apple").build();
            PersonalizationContext contextB = PersonalizationContext.builder(userB).addPreferredBrand("Sony").build();

            when(personalizationContextProvider.getPersonalizationContext(userA)).thenReturn(contextA);
            when(personalizationContextProvider.getPersonalizationContext(userB)).thenReturn(contextB);

            DiscoverySearchRequestDTO request = DiscoverySearchRequestDTO.builder().query("headset").build();

            // Run for User A
            DiscoverySearchResponseDTO responseA = discoveryService.discover(request, userA);
            // Run for User B
            DiscoverySearchResponseDTO responseB = discoveryService.discover(request, userB);

            // User A gets Apple ranked #1 (75 + 10 = 85)
            assertEquals(idApple, responseA.getContent().get(0).getId());
            assertEquals(85.0, responseA.getContent().get(0).getPersonalizedScore());

            // User B gets Sony ranked #1 (75 + 10 = 85)
            assertEquals(idSony, responseB.getContent().get(0).getId());
            assertEquals(85.0, responseB.getContent().get(0).getPersonalizedScore());
        }
    }

    @Nested
    @DisplayName("Product Facts Non-Mutation")
    class ProductFactsNonMutation {

        @Test
        @DisplayName("Personalization does not mutate entity attributes or prices")
        void testProductFactsArePreserved() {
            UUID userId = UUID.randomUUID();
            UUID prodId = UUID.randomUUID();
            ProductEntity product = createProductEntity(prodId, "Immutable Monitor", "Dell", "Monitors");
            ProductPriceEntity price = createPriceEntity(product, BigDecimal.valueOf(450), BigDecimal.valueOf(20));

            String originalName = product.getName();
            String originalBrand = product.getBrand();
            BigDecimal originalPrice = price.getCurrentPrice();
            BigDecimal originalDiscount = price.getDiscountPercentage();

            when(queryInterpreter.interpret(any())).thenReturn(InterpretedQuery.builder().searchTokens(List.of("monitor")).build());
            when(productRepository.findAll(any(Specification.class), any(Pageable.class)))
                    .thenReturn(new PageImpl<>(List.of(product)));
            when(productPriceRepository.findPricesWithSellersByProductIds(anyList()))
                    .thenReturn(List.of(price));

            doAnswer(inv -> {
                ScoredProductCandidate cand = inv.getArgument(0);
                cand.setRelevanceScore(70.0);
                return null;
            }).when(relevanceScorer).scoreCandidate(any(), any());

            PersonalizationContext context = PersonalizationContext.builder(userId).addPreferredBrand("Dell").build();
            when(personalizationContextProvider.getPersonalizationContext(userId)).thenReturn(context);

            DiscoverySearchRequestDTO request = DiscoverySearchRequestDTO.builder().query("monitor").build();
            discoveryService.discover(request, userId);

            // Verify entity remains unmodified
            assertEquals(originalName, product.getName());
            assertEquals(originalBrand, product.getBrand());
            assertEquals(originalPrice, price.getCurrentPrice());
            assertEquals(originalDiscount, price.getDiscountPercentage());
        }
    }
}
