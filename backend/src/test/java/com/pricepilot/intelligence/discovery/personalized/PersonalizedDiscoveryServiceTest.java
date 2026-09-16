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
import org.springframework.security.access.AccessDeniedException;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("Phase 6.6 - Personalized Discovery Service Test Suite")
class PersonalizedDiscoveryServiceTest {

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

    private UUID userId;
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

        userId = UUID.randomUUID();
        mockSeller = SellerEntity.builder().name("Official Store").build();
        mockSeller.setId(UUID.randomUUID());
    }

    private ProductEntity createProductEntity(UUID id, String name, String brand, String category) {
        ProductEntity entity = ProductEntity.builder()
                .name(name)
                .brand(brand)
                .category(category)
                .description("Test product description")
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
    @DisplayName("Invariant 1: Empty Context Equivalence")
    class EmptyContextEquivalence {

        @Test
        @DisplayName("Empty personalization context produces identical candidate ranking to deterministic base score")
        void testEmptyContextProducesIdenticalRanking() {
            UUID id1 = UUID.fromString("00000000-0000-0000-0000-000000000001");
            UUID id2 = UUID.fromString("00000000-0000-0000-0000-000000000002");

            ProductEntity p1 = createProductEntity(id1, "Product 1", "BrandA", "Laptops");
            ProductEntity p2 = createProductEntity(id2, "Product 2", "BrandB", "Laptops");

            ProductPriceEntity price1 = createPriceEntity(p1, BigDecimal.valueOf(1000), BigDecimal.valueOf(10));
            ProductPriceEntity price2 = createPriceEntity(p2, BigDecimal.valueOf(1200), BigDecimal.valueOf(10));

            when(queryInterpreter.interpret(any())).thenReturn(InterpretedQuery.builder().searchTokens(List.of("laptop")).build());
            when(productRepository.findAll(any(Specification.class), any(Pageable.class)))
                    .thenReturn(new PageImpl<>(List.of(p1, p2)));
            when(productPriceRepository.findPricesWithSellersByProductIds(anyList()))
                    .thenReturn(List.of(price1, price2));

            // Scorer sets base scores: p2 has higher base score (85.0) than p1 (75.0)
            doAnswer(inv -> {
                ScoredProductCandidate cand = inv.getArgument(0);
                if (cand.getProduct().getId().equals(id2)) {
                    cand.setRelevanceScore(85.0);
                } else {
                    cand.setRelevanceScore(75.0);
                }
                return null;
            }).when(relevanceScorer).scoreCandidate(any(), any());

            PersonalizationContext emptyContext = PersonalizationContext.empty(userId);
            when(personalizationContextProvider.getPersonalizationContext(userId)).thenReturn(emptyContext);

            DiscoverySearchRequestDTO request = DiscoverySearchRequestDTO.builder().query("laptop").build();
            DiscoverySearchResponseDTO response = discoveryService.discover(request, userId);

            assertNotNull(response);
            assertEquals(2, response.getContent().size());

            // With empty context, adjustment is 0.0 -> p2 remains ranked #1 (85.0) and p1 ranked #2 (75.0)
            assertEquals(id2, response.getContent().get(0).getId());
            assertEquals(85.0, response.getContent().get(0).getPersonalizedScore());
            assertEquals(0.0, response.getContent().get(0).getPersonalizationAdjustment());

            assertEquals(id1, response.getContent().get(1).getId());
            assertEquals(75.0, response.getContent().get(1).getPersonalizedScore());
            assertEquals(0.0, response.getContent().get(1).getPersonalizationAdjustment());
        }
    }

    @Nested
    @DisplayName("Invariant 2: Explicit Preference Personalization")
    class ExplicitPreferenceRanking {

        @Test
        @DisplayName("Re-ranks eligible candidate matching preferred brand above competitor")
        void testPreferredBrandReRanking() {
            UUID appleId = UUID.randomUUID();
            UUID dellId = UUID.randomUUID();

            ProductEntity appleProduct = createProductEntity(appleId, "MacBook Air", "Apple", "Laptops");
            ProductEntity dellProduct = createProductEntity(dellId, "Dell XPS", "Dell", "Laptops");

            ProductPriceEntity applePrice = createPriceEntity(appleProduct, BigDecimal.valueOf(1000), BigDecimal.valueOf(10));
            ProductPriceEntity dellPrice = createPriceEntity(dellProduct, BigDecimal.valueOf(1000), BigDecimal.valueOf(10));

            when(queryInterpreter.interpret(any())).thenReturn(InterpretedQuery.builder().searchTokens(List.of("laptop")).build());
            when(productRepository.findAll(any(Specification.class), any(Pageable.class)))
                    .thenReturn(new PageImpl<>(List.of(dellProduct, appleProduct)));
            when(productPriceRepository.findPricesWithSellersByProductIds(anyList()))
                    .thenReturn(List.of(dellPrice, applePrice));

            // Dell has slightly higher base score (80.0) vs Apple (75.0)
            doAnswer(inv -> {
                ScoredProductCandidate cand = inv.getArgument(0);
                if (cand.getProduct().getId().equals(dellId)) {
                    cand.setRelevanceScore(80.0);
                } else {
                    cand.setRelevanceScore(75.0);
                }
                return null;
            }).when(relevanceScorer).scoreCandidate(any(), any());

            // User explicitly prefers "Apple" (+10.0 bonus)
            PersonalizationContext userContext = PersonalizationContext.builder(userId)
                    .addPreferredBrand("Apple")
                    .build();
            when(personalizationContextProvider.getPersonalizationContext(userId)).thenReturn(userContext);

            DiscoverySearchRequestDTO request = DiscoverySearchRequestDTO.builder().query("laptop").build();
            DiscoverySearchResponseDTO response = discoveryService.discover(request, userId);

            // Apple: 75.0 + 10.0 = 85.0 -> Ranks #1!
            // Dell: 80.0 + 0.0 = 80.0 -> Ranks #2!
            assertEquals(appleId, response.getContent().get(0).getId());
            assertEquals(85.0, response.getContent().get(0).getPersonalizedScore());
            assertEquals(10.0, response.getContent().get(0).getPersonalizationAdjustment());
            assertTrue(response.getContent().get(0).getDiscoveryBadges().contains("Personalized Match"));

            assertEquals(dellId, response.getContent().get(1).getId());
            assertEquals(80.0, response.getContent().get(1).getPersonalizedScore());
        }
    }

    @Nested
    @DisplayName("Invariant 3: Hard Constraints Inviolable")
    class HardConstraintsInviolable {

        @Test
        @DisplayName("Hard filters strictly applied at database retrieval level before personalization")
        void testHardFiltersPrecedePersonalization() {
            when(queryInterpreter.interpret(any())).thenReturn(InterpretedQuery.builder().searchTokens(List.of("phone")).build());
            // When database filter returns empty page (e.g. no products fit hard budget/category filter)
            when(productRepository.findAll(any(Specification.class), any(Pageable.class)))
                    .thenReturn(new PageImpl<>(Collections.emptyList()));

            PersonalizationContext userContext = PersonalizationContext.builder(userId)
                    .addPreferredBrand("Apple")
                    .build();
            when(personalizationContextProvider.getPersonalizationContext(userId)).thenReturn(userContext);

            DiscoverySearchRequestDTO request = DiscoverySearchRequestDTO.builder()
                    .query("phone")
                    .maxPrice(BigDecimal.valueOf(200)) // Hard filter: max price $200
                    .build();

            DiscoverySearchResponseDTO response = discoveryService.discover(request, userId);

            assertNotNull(response);
            assertTrue(response.getContent().isEmpty());
            assertEquals(0, response.getTotalElements());
            verify(productPriceRepository, never()).findPricesWithSellersByProductIds(anyList());
        }
    }

    @Nested
    @DisplayName("Invariant 4: Security & Authentication")
    class SecurityAndAuthentication {

        @Test
        @DisplayName("Throws AccessDeniedException when userId is null")
        void testUnauthenticatedAccessThrowsAccessDenied() {
            DiscoverySearchRequestDTO request = DiscoverySearchRequestDTO.builder().query("laptop").build();
            assertThrows(AccessDeniedException.class, () -> discoveryService.discover(request, (UUID) null));
        }
    }
}
