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

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class PersonalizedAlternativeAdversarialTest {

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
    @DisplayName("Adversarial: Invalid CHEAPER candidate cannot be rescued by strong brand affinity")
    void testInvalidCheaperCandidateCannotBeRescuedByPersonalization() {
        UUID sourceId = UUID.randomUUID();
        ProductEntity sourceProduct = createProductEntity(sourceId, "Source Product", "BrandA", "Electronics");
        ProductPriceEntity sourcePrice = createPriceEntity(sourceProduct, BigDecimal.valueOf(500.00), BigDecimal.ZERO);

        // Candidate is MORE expensive ($600 > $500), so it is objectively invalid for CHEAPER
        UUID candId = UUID.randomUUID();
        ProductEntity cand = createProductEntity(candId, "Expensive Candidate", "LovedBrand", "Electronics");
        ProductPriceEntity candPrice = createPriceEntity(cand, BigDecimal.valueOf(600.00), BigDecimal.ZERO);

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

        // User has maximum brand affinity for LovedBrand (+35.0 potential boost)
        PersonalizationContext context = PersonalizationContext.builder(UUID.randomUUID())
                .addPreferredBrand("lovedbrand")
                .addPreferredCategory("electronics")
                .build();

        AlternativeRequest req = AlternativeRequest.builder()
                .productId(sourceId)
                .type(AlternativeType.CHEAPER)
                .build();

        AlternativeResponseDTO response = personalizedAlternativeService.findPersonalizedAlternativesForProduct(sourceId, req, context);

        // Even with strong brand affinity, the candidate MUST NOT be returned because it violates CHEAPER qualification
        assertThat(response.getContent()).isEmpty();
        assertThat(response.getTotalFound()).isEqualTo(0);
    }

    @Test
    @DisplayName("Adversarial: Invalid BUDGET_FALLBACK candidate cannot be rescued by budget preference")
    void testInvalidBudgetFallbackCannotBeRescued() {
        UUID sourceId = UUID.randomUUID();
        ProductEntity sourceProduct = createProductEntity(sourceId, "Flagship Phone", "BrandA", "Phones");
        ProductPriceEntity sourcePrice = createPriceEntity(sourceProduct, BigDecimal.valueOf(1000.00), BigDecimal.ZERO);

        // Candidate is $900 (only 10% savings, fails 20% savings threshold for BUDGET_FALLBACK)
        UUID candId = UUID.randomUUID();
        ProductEntity cand = createProductEntity(candId, "Slightly Cheaper Phone", "PreferredBrand", "Phones");
        ProductPriceEntity candPrice = createPriceEntity(cand, BigDecimal.valueOf(900.00), BigDecimal.ZERO);

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

        PersonalizationContext context = PersonalizationContext.builder(UUID.randomUUID())
                .addPreferredBrand("preferredbrand")
                .minBudget(BigDecimal.valueOf(500.00))
                .maxBudget(BigDecimal.valueOf(950.00))
                .build();

        AlternativeRequest req = AlternativeRequest.builder()
                .productId(sourceId)
                .type(AlternativeType.BUDGET_FALLBACK)
                .build();

        AlternativeResponseDTO response = personalizedAlternativeService.findPersonalizedAlternativesForProduct(sourceId, req, context);

        assertThat(response.getContent()).isEmpty();
    }

    @Test
    @DisplayName("Adversarial: Hard max price filter excludes candidate before qualification & scoring")
    void testHardMaxPriceConstraintExcludesCandidate() {
        UUID sourceId = UUID.randomUUID();
        ProductEntity sourceProduct = createProductEntity(sourceId, "Baseline Product", "BrandA", "Laptops");
        ProductPriceEntity sourcePrice = createPriceEntity(sourceProduct, BigDecimal.valueOf(1200.00), BigDecimal.ZERO);

        // Candidate price is $1100, but request has hard maxPrice = $1000
        UUID candId = UUID.randomUUID();
        ProductEntity cand = createProductEntity(candId, "Candidate Product", "Apple", "Laptops");
        ProductPriceEntity candPrice = createPriceEntity(cand, BigDecimal.valueOf(1100.00), BigDecimal.ZERO);

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

        PersonalizationContext context = PersonalizationContext.builder(UUID.randomUUID())
                .addPreferredBrand("apple")
                .addPreferredCategory("laptops")
                .build();

        AlternativeRequest req = AlternativeRequest.builder()
                .productId(sourceId)
                .type(AlternativeType.SIMILAR)
                .maxPrice(BigDecimal.valueOf(1000.00))
                .build();

        AlternativeResponseDTO response = personalizedAlternativeService.findPersonalizedAlternativesForProduct(sourceId, req, context);

        assertThat(response.getContent()).isEmpty();
    }

    @Test
    @DisplayName("Adversarial: Source product self-exclusion is strictly maintained")
    void testSourceProductSelfExclusion() {
        UUID sourceId = UUID.randomUUID();
        ProductEntity sourceProduct = createProductEntity(sourceId, "Target Phone", "BrandA", "Phones");
        ProductPriceEntity sourcePrice = createPriceEntity(sourceProduct, BigDecimal.valueOf(799.00), BigDecimal.ZERO);

        when(productRepository.findById(sourceId)).thenReturn(Optional.of(sourceProduct));
        when(productRepository.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(sourceProduct)));

        List<ProductPriceEntity> allPrices = List.of(sourcePrice);
        when(productPriceRepository.findPricesWithSellersByProductIds(anyList())).thenAnswer(invocation -> {
            List<UUID> requestedIds = invocation.getArgument(0);
            return allPrices.stream()
                    .filter(p -> p.getProduct() != null && requestedIds.contains(p.getProduct().getId()))
                    .toList();
        });

        PersonalizationContext context = PersonalizationContext.builder(UUID.randomUUID())
                .addPreferredBrand("branda")
                .build();

        AlternativeRequest req = AlternativeRequest.builder()
                .productId(sourceId)
                .type(AlternativeType.SIMILAR)
                .build();

        AlternativeResponseDTO response = personalizedAlternativeService.findPersonalizedAlternativesForProduct(sourceId, req, context);

        assertThat(response.getContent()).isEmpty();
    }

    @Test
    @DisplayName("Adversarial: User isolation ensures User A and User B receive isolated rankings without state leakage")
    void testUserIsolation() {
        UUID sourceId = UUID.randomUUID();
        ProductEntity sourceProduct = createProductEntity(sourceId, "Base Audio", "Generic", "Audio");
        ProductPriceEntity sourcePrice = createPriceEntity(sourceProduct, BigDecimal.valueOf(200.00), BigDecimal.ZERO);

        UUID sonyId = UUID.randomUUID();
        ProductEntity sonyCand = createProductEntity(sonyId, "Sony XM5", "Sony", "Audio");
        ProductPriceEntity sonyPrice = createPriceEntity(sonyCand, BigDecimal.valueOf(180.00), BigDecimal.valueOf(10));

        UUID boseId = UUID.randomUUID();
        ProductEntity boseCand = createProductEntity(boseId, "Bose QC45", "Bose", "Audio");
        ProductPriceEntity bosePrice = createPriceEntity(boseCand, BigDecimal.valueOf(180.00), BigDecimal.valueOf(10));

        when(productRepository.findById(sourceId)).thenReturn(Optional.of(sourceProduct));
        when(productRepository.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(sonyCand, boseCand)));

        List<ProductPriceEntity> allPrices = List.of(sourcePrice, sonyPrice, bosePrice);
        when(productPriceRepository.findPricesWithSellersByProductIds(anyList())).thenAnswer(invocation -> {
            List<UUID> requestedIds = invocation.getArgument(0);
            return allPrices.stream()
                    .filter(p -> p.getProduct() != null && requestedIds.contains(p.getProduct().getId()))
                    .toList();
        });

        PersonalizationContext contextUserA = PersonalizationContext.builder(UUID.randomUUID())
                .addPreferredBrand("sony")
                .addPreferredCategory("audio")
                .build();

        PersonalizationContext contextUserB = PersonalizationContext.builder(UUID.randomUUID())
                .addPreferredBrand("bose")
                .addPreferredCategory("audio")
                .build();

        AlternativeRequest req = AlternativeRequest.builder()
                .productId(sourceId)
                .type(AlternativeType.CHEAPER)
                .build();

        AlternativeResponseDTO resA = personalizedAlternativeService.findPersonalizedAlternativesForProduct(sourceId, req, contextUserA);
        AlternativeResponseDTO resB = personalizedAlternativeService.findPersonalizedAlternativesForProduct(sourceId, req, contextUserB);

        assertThat(resA.getContent().get(0).getBrand()).isEqualTo("Sony");
        assertThat(resB.getContent().get(0).getBrand()).isEqualTo("Bose");
    }

    @Test
    @DisplayName("Adversarial: Product domain facts are immutable and unmutated after personalization")
    void testProductDomainFactsImmutability() {
        UUID sourceId = UUID.randomUUID();
        ProductEntity sourceProduct = createProductEntity(sourceId, "Base Product", "BrandA", "CategoryA");
        ProductPriceEntity sourcePrice = createPriceEntity(sourceProduct, BigDecimal.valueOf(100.00), BigDecimal.ZERO);

        UUID candId = UUID.randomUUID();
        ProductEntity cand = createProductEntity(candId, "Alternative Product", "BrandB", "CategoryA");
        ProductPriceEntity candPrice = createPriceEntity(cand, BigDecimal.valueOf(80.00), BigDecimal.valueOf(20));

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

        PersonalizationContext context = PersonalizationContext.builder(UUID.randomUUID())
                .addPreferredBrand("brandb")
                .addPreferredCategory("categorya")
                .build();

        AlternativeRequest req = AlternativeRequest.builder()
                .productId(sourceId)
                .type(AlternativeType.CHEAPER)
                .build();

        AlternativeResponseDTO response = personalizedAlternativeService.findPersonalizedAlternativesForProduct(sourceId, req, context);

        assertThat(response.getContent()).isNotEmpty();
        AlternativeProductDTO dto = response.getContent().get(0);
        assertThat(dto.getId()).isEqualTo(candId);
        assertThat(dto.getName()).isEqualTo("Alternative Product");
        assertThat(dto.getBrand()).isEqualTo("BrandB");
        assertThat(dto.getCategory()).isEqualTo("CategoryA");
        assertThat(dto.getCurrentBestPrice()).isEqualByComparingTo(BigDecimal.valueOf(80.00));
        assertThat(dto.getDiscountPercentage()).isEqualByComparingTo(BigDecimal.valueOf(20));
    }
}
