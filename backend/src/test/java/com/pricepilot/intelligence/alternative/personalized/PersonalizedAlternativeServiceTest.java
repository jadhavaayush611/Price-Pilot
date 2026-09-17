package com.pricepilot.intelligence.alternative.personalized;

import com.pricepilot.intelligence.alternative.model.*;
import com.pricepilot.intelligence.alternative.scoring.AlternativeScoringStrategy;
import com.pricepilot.intelligence.alternative.scoring.DefaultAlternativeScoringStrategy;
import com.pricepilot.intelligence.analytics.PriceAnalyticsService;
import com.pricepilot.intelligence.discovery.hybrid.HybridSearchService;
import com.pricepilot.intelligence.discovery.intent.ShoppingQueryIntentValidator;
import com.pricepilot.intelligence.discovery.intent.ShoppingQueryInterpreter;
import com.pricepilot.intelligence.discovery.normalization.QueryNormalizer;
import com.pricepilot.intelligence.personalization.context.PersonalizationContext;
import com.pricepilot.intelligence.personalization.context.PersonalizationContextProvider;
import com.pricepilot.intelligence.personalization.evidence.DefaultPersonalizedEvidenceGenerator;
import com.pricepilot.intelligence.personalization.evidence.PersonalizedEvidenceGenerator;
import com.pricepilot.intelligence.personalization.preference.AvailabilityPreference;
import com.pricepilot.intelligence.personalization.preference.DealSensitivity;
import com.pricepilot.intelligence.personalization.preference.PriceSensitivity;
import com.pricepilot.intelligence.personalization.scoring.DefaultPersonalizedScoringStrategy;
import com.pricepilot.intelligence.personalization.scoring.PersonalizedScoringStrategy;
import com.pricepilot.intelligence.semantic.config.SemanticIntelligenceProperties;
import com.pricepilot.intelligence.semantic.contract.CanonicalProductTextBuilder;
import com.pricepilot.intelligence.semantic.service.EmbeddingService;
import com.pricepilot.intelligence.semantic.storage.VectorStore;
import com.pricepilot.product.ProductEntity;
import com.pricepilot.product.ProductRepository;
import com.pricepilot.productprice.ProductPriceEntity;
import com.pricepilot.productprice.ProductPriceRepository;
import com.pricepilot.seller.SellerEntity;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.access.AccessDeniedException;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class PersonalizedAlternativeServiceTest {

    @Mock
    private ProductRepository productRepository;
    @Mock
    private ProductPriceRepository productPriceRepository;
    @Mock
    private PriceAnalyticsService priceAnalyticsService;
    @Mock
    private EmbeddingService embeddingService;
    @Mock
    private VectorStore vectorStore;
    @Mock
    private CanonicalProductTextBuilder canonicalProductTextBuilder;
    @Mock
    private HybridSearchService hybridSearchService;
    @Mock
    private ShoppingQueryInterpreter shoppingQueryInterpreter;
    @Mock
    private ShoppingQueryIntentValidator intentValidator;
    @Mock
    private PersonalizationContextProvider personalizationContextProvider;

    private QueryNormalizer queryNormalizer;
    private SemanticIntelligenceProperties properties;
    private AlternativeScoringStrategy alternativeScoringStrategy;
    private PersonalizedScoringStrategy personalizedScoringStrategy;
    private PersonalizedEvidenceGenerator personalizedEvidenceGenerator;
    private PersonalizedAlternativeServiceImpl personalizedAlternativeService;

    private SellerEntity mockSeller;
    private UUID userId;

    @BeforeEach
    void setUp() {
        queryNormalizer = new QueryNormalizer();
        properties = new SemanticIntelligenceProperties();
        properties.setEnabled(false);

        alternativeScoringStrategy = new DefaultAlternativeScoringStrategy(queryNormalizer);
        personalizedScoringStrategy = new DefaultPersonalizedScoringStrategy();
        personalizedEvidenceGenerator = new DefaultPersonalizedEvidenceGenerator();

        personalizedAlternativeService = new PersonalizedAlternativeServiceImpl(
                productRepository,
                productPriceRepository,
                priceAnalyticsService,
                alternativeScoringStrategy,
                embeddingService,
                vectorStore,
                canonicalProductTextBuilder,
                hybridSearchService,
                shoppingQueryInterpreter,
                intentValidator,
                queryNormalizer,
                properties,
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

    @Test
    @DisplayName("Product-driven alternatives re-rank candidates based on user brand & category preferences")
    void testProductDrivenPersonalizedAlternatives() {
        UUID sourceId = UUID.randomUUID();
        ProductEntity sourceProduct = createProductEntity(sourceId, "Source Phone Pro", "Apple", "Electronics");
        ProductPriceEntity sourcePrice = createPriceEntity(sourceProduct, BigDecimal.valueOf(999.00), BigDecimal.ZERO);

        UUID candidateId1 = UUID.randomUUID();
        ProductEntity cand1 = createProductEntity(candidateId1, "Galaxy S24", "Samsung", "Electronics");
        ProductPriceEntity candPrice1 = createPriceEntity(cand1, BigDecimal.valueOf(899.00), BigDecimal.valueOf(10));

        UUID candidateId2 = UUID.randomUUID();
        ProductEntity cand2 = createProductEntity(candidateId2, "Pixel 8 Pro", "Google", "Electronics");
        ProductPriceEntity candPrice2 = createPriceEntity(cand2, BigDecimal.valueOf(899.00), BigDecimal.valueOf(10));

        when(productRepository.findById(sourceId)).thenReturn(Optional.of(sourceProduct));
        when(productRepository.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(cand1, cand2)));

        List<ProductPriceEntity> allPrices = List.of(sourcePrice, candPrice1, candPrice2);
        when(productPriceRepository.findPricesWithSellersByProductIds(anyList())).thenAnswer(invocation -> {
            List<UUID> requestedIds = invocation.getArgument(0);
            return allPrices.stream()
                    .filter(p -> p.getProduct() != null && requestedIds.contains(p.getProduct().getId()))
                    .toList();
        });

        // User preference: Strongly prefers Google brand
        PersonalizationContext context = PersonalizationContext.builder(userId)
                .addPreferredBrand("google")
                .addPreferredCategory("electronics")
                .priceSensitivity(PriceSensitivity.MEDIUM)
                .dealSensitivity(DealSensitivity.MEDIUM)
                .availabilityPreference(AvailabilityPreference.ALL)
                .build();

        when(personalizationContextProvider.getPersonalizationContext(userId)).thenReturn(context);

        AlternativeRequest req = AlternativeRequest.builder()
                .productId(sourceId)
                .type(AlternativeType.CHEAPER)
                .limit(10)
                .build();

        AlternativeResponseDTO response = personalizedAlternativeService.findPersonalizedAlternativesForProduct(sourceId, req, userId);

        assertThat(response).isNotNull();
        assertThat(response.getContent()).hasSize(2);

        // Google Pixel 8 Pro gets brand bonus and ranks #1 over Samsung Galaxy
        AlternativeProductDTO topResult = response.getContent().get(0);
        assertThat(topResult.getId()).isEqualTo(candidateId2);
        assertThat(topResult.getBrand()).isEqualTo("Google");
        assertThat(topResult.getPersonalizedScore()).isGreaterThan(topResult.getAlternativeScore());
        assertThat(topResult.getPersonalizationAdjustment()).isGreaterThan(0.0);
        assertThat(topResult.getPersonalizedEvidence()).isNotNull();
        assertThat(topResult.getPersonalizedEvidence().hasPersonalization()).isTrue();
    }

    @Test
    @DisplayName("Empty personalization context produces identical scores and zero adjustments")
    void testEmptyContextNeutrality() {
        UUID sourceId = UUID.randomUUID();
        ProductEntity sourceProduct = createProductEntity(sourceId, "Laptop Base", "Dell", "Computers");
        ProductPriceEntity sourcePrice = createPriceEntity(sourceProduct, BigDecimal.valueOf(1200.00), BigDecimal.ZERO);

        UUID candidateId = UUID.randomUUID();
        ProductEntity cand = createProductEntity(candidateId, "Laptop Alternative", "HP", "Computers");
        ProductPriceEntity candPrice = createPriceEntity(cand, BigDecimal.valueOf(950.00), BigDecimal.valueOf(15));

        when(productRepository.findById(sourceId)).thenReturn(Optional.of(sourceProduct));
        when(productRepository.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(cand)));

        List<ProductPriceEntity> allPrices = List.of(sourcePrice, candPrice);
        when(productPriceRepository.findPricesWithSellersByProductIds(anyList())).thenAnswer(invocation -> {
            List<UUID> requestedIds = invocation.getArgument(0);
            return allPrices.stream()
                    .filter(p -> p.getProduct() != null && requestedIds.contains(p.getProduct().getId()))
                    .toList();
        });

        PersonalizationContext emptyContext = PersonalizationContext.empty(userId);

        AlternativeRequest req = AlternativeRequest.builder()
                .productId(sourceId)
                .type(AlternativeType.CHEAPER)
                .limit(10)
                .build();

        AlternativeResponseDTO response = personalizedAlternativeService.findPersonalizedAlternativesForProduct(sourceId, req, emptyContext);

        assertThat(response.getContent()).hasSize(1);
        AlternativeProductDTO item = response.getContent().get(0);
        assertThat(item.getPersonalizationAdjustment()).isEqualTo(0.0);
        assertThat(item.getPersonalizedScore()).isEqualTo(item.getAlternativeScore());
    }

    @Test
    @DisplayName("Provider failure gracefully falls back to empty context and succeeds")
    void testProviderFailureGracefulFallback() {
        UUID sourceId = UUID.randomUUID();
        ProductEntity sourceProduct = createProductEntity(sourceId, "Headphones", "Sony", "Audio");
        ProductPriceEntity sourcePrice = createPriceEntity(sourceProduct, BigDecimal.valueOf(300.00), BigDecimal.ZERO);

        UUID candidateId = UUID.randomUUID();
        ProductEntity cand = createProductEntity(candidateId, "Bose Headphones", "Bose", "Audio");
        ProductPriceEntity candPrice = createPriceEntity(cand, BigDecimal.valueOf(250.00), BigDecimal.valueOf(10));

        when(productRepository.findById(sourceId)).thenReturn(Optional.of(sourceProduct));
        when(productRepository.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(cand)));

        List<ProductPriceEntity> allPrices = List.of(sourcePrice, candPrice);
        when(productPriceRepository.findPricesWithSellersByProductIds(anyList())).thenAnswer(invocation -> {
            List<UUID> requestedIds = invocation.getArgument(0);
            return allPrices.stream()
                    .filter(p -> p.getProduct() != null && requestedIds.contains(p.getProduct().getId()))
                    .toList();
        });

        when(personalizationContextProvider.getPersonalizationContext(userId))
                .thenThrow(new RuntimeException("Database timeout in preference subsystem"));

        AlternativeRequest req = AlternativeRequest.builder()
                .productId(sourceId)
                .type(AlternativeType.CHEAPER)
                .build();
        AlternativeResponseDTO response = personalizedAlternativeService.findPersonalizedAlternativesForProduct(sourceId, req, userId);

        assertThat(response).isNotNull();
        assertThat(response.getContent()).hasSize(1);
        assertThat(response.getContent().get(0).getPersonalizationAdjustment()).isEqualTo(0.0);
    }

    @Test
    @DisplayName("Unauthenticated request (userId == null) throws AccessDeniedException")
    void testUnauthenticatedRequestThrowsAccessDenied() {
        AlternativeRequest req = AlternativeRequest.builder().productId(UUID.randomUUID()).build();
        assertThatThrownBy(() -> personalizedAlternativeService.findPersonalizedAlternativesForProduct(UUID.randomUUID(), req, (UUID) null))
                .isInstanceOf(AccessDeniedException.class);
    }
}
