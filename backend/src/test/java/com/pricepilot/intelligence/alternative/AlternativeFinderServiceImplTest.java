package com.pricepilot.intelligence.alternative;

import com.pricepilot.exception.ResourceNotFoundException;
import com.pricepilot.intelligence.alternative.model.*;
import com.pricepilot.intelligence.alternative.scoring.DefaultAlternativeScoringStrategy;
import com.pricepilot.intelligence.alternative.service.AlternativeFinderServiceImpl;
import com.pricepilot.intelligence.analytics.PriceAnalyticsService;
import com.pricepilot.intelligence.discovery.dto.DiscoveryProductDTO;
import com.pricepilot.intelligence.discovery.dto.DiscoverySearchResponseDTO;
import com.pricepilot.intelligence.discovery.hybrid.HybridSearchRequest;
import com.pricepilot.intelligence.discovery.hybrid.HybridSearchService;
import com.pricepilot.intelligence.discovery.intent.DefaultShoppingQueryIntentValidator;
import com.pricepilot.intelligence.discovery.intent.DeterministicShoppingQueryInterpreter;
import com.pricepilot.intelligence.discovery.normalization.QueryNormalizer;
import com.pricepilot.intelligence.semantic.config.SemanticIntelligenceProperties;
import com.pricepilot.intelligence.semantic.contract.CanonicalProductTextBuilder;
import com.pricepilot.intelligence.semantic.model.EmbeddingVector;
import com.pricepilot.intelligence.semantic.model.SimilaritySearchRequest;
import com.pricepilot.intelligence.semantic.model.SimilaritySearchResult;
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
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AlternativeFinderServiceImplTest {

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
    private HybridSearchService hybridSearchService;

    private QueryNormalizer queryNormalizer;
    private DefaultAlternativeScoringStrategy scoringStrategy;
    private CanonicalProductTextBuilder canonicalProductTextBuilder;
    private DeterministicShoppingQueryInterpreter shoppingQueryInterpreter;
    private DefaultShoppingQueryIntentValidator intentValidator;
    private SemanticIntelligenceProperties properties;
    private SimpleMeterRegistry meterRegistry;

    private AlternativeFinderServiceImpl service;

    @BeforeEach
    void setUp() {
        queryNormalizer = new QueryNormalizer();
        scoringStrategy = new DefaultAlternativeScoringStrategy(queryNormalizer);
        canonicalProductTextBuilder = new CanonicalProductTextBuilder(2048);
        shoppingQueryInterpreter = new DeterministicShoppingQueryInterpreter(queryNormalizer);
        intentValidator = new DefaultShoppingQueryIntentValidator();
        properties = new SemanticIntelligenceProperties();
        properties.setEnabled(true);
        meterRegistry = new SimpleMeterRegistry();

        when(embeddingService.getModelName()).thenReturn("deterministic-hash-v1");
        when(embeddingService.getModelVersion()).thenReturn("1.0.0");
        when(embeddingService.generateEmbedding(anyString())).thenReturn(EmbeddingVector.of(new float[]{0.1f, 0.2f}));

        service = new AlternativeFinderServiceImpl(
                productRepository,
                productPriceRepository,
                priceAnalyticsService,
                scoringStrategy,
                embeddingService,
                vectorStore,
                canonicalProductTextBuilder,
                hybridSearchService,
                shoppingQueryInterpreter,
                intentValidator,
                queryNormalizer,
                properties,
                meterRegistry
        );
    }

    @Test
    @DisplayName("Product-driven: Successfully finds cheaper alternatives, excludes source product")
    void testFindAlternativesForProductCheaper() {
        UUID sourceId = UUID.randomUUID();
        ProductEntity sourceProduct = ProductEntity.builder()
                .name("Sony WH-1000XM5")
                .brand("Sony")
                .category("Headphones")
                .description("Flagship noise canceling headphones")
                .build();
        sourceProduct.setId(sourceId);

        SellerEntity seller = SellerEntity.builder().name("BestBuy").build();

        ProductPriceEntity srcPrice = ProductPriceEntity.builder()
                .product(sourceProduct)
                .seller(seller)
                .currentPrice(BigDecimal.valueOf(399.99))
                .originalPrice(BigDecimal.valueOf(399.99))
                .discountPercentage(BigDecimal.ZERO)
                .build();

        UUID cand1Id = UUID.randomUUID();
        ProductEntity cand1 = ProductEntity.builder()
                .name("Anker Soundcore Space Q45")
                .brand("Anker")
                .category("Headphones")
                .description("Adaptive noise canceling wireless headphones")
                .build();
        cand1.setId(cand1Id);

        ProductPriceEntity cand1Price = ProductPriceEntity.builder()
                .product(cand1)
                .seller(seller)
                .currentPrice(BigDecimal.valueOf(149.99))
                .originalPrice(BigDecimal.valueOf(149.99))
                .discountPercentage(BigDecimal.ZERO)
                .build();

        when(productRepository.findById(sourceId)).thenReturn(Optional.of(sourceProduct));
        when(productPriceRepository.findPricesWithSellersByProductIds(List.of(sourceId))).thenReturn(List.of(srcPrice));

        // Vector store returns cand1
        SimilaritySearchResult simResult = new SimilaritySearchResult("PRODUCT", cand1Id.toString(), 0.85, null, null);
        when(vectorStore.similaritySearch(any(SimilaritySearchRequest.class))).thenReturn(List.of(simResult));
        when(productRepository.findAllByIdInWithPricesAndSellers(List.of(cand1Id))).thenReturn(List.of(cand1));

        // Structured fallback returns empty
        when(productRepository.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(Collections.emptyList()));

        when(productPriceRepository.findPricesWithSellersByProductIds(List.of(cand1Id))).thenReturn(List.of(cand1Price));

        AlternativeRequest request = AlternativeRequest.builder()
                .productId(sourceId)
                .type(AlternativeType.CHEAPER)
                .limit(10)
                .build();

        AlternativeResponseDTO response = service.findAlternativesForProduct(sourceId, request);

        assertThat(response).isNotNull();
        assertThat(response.getExecutionMode()).isEqualTo(AlternativeMode.PRODUCT);
        assertThat(response.getSourceProductContext()).isNotNull();
        assertThat(response.getSourceProductContext().getId()).isEqualTo(sourceId);
        assertThat(response.getContent()).hasSize(1);

        AlternativeProductDTO alt = response.getContent().get(0);
        assertThat(alt.getId()).isEqualTo(cand1Id);
        assertThat(alt.getCurrentBestPrice()).isEqualByComparingTo(BigDecimal.valueOf(149.99));
        assertThat(alt.getPriceDifference()).isLessThan(BigDecimal.ZERO);
        assertThat(alt.getReasonCodes()).contains(AlternativeReasonCode.LOWER_PRICE);
    }

    @Test
    @DisplayName("Product-driven: Throws ResourceNotFoundException if source product does not exist")
    void testSourceProductNotFound() {
        UUID unknownId = UUID.randomUUID();
        when(productRepository.findById(unknownId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.findAlternativesForProduct(unknownId, new AlternativeRequest()))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Product not found");
    }

    @Test
    @DisplayName("Query-driven: Interprets query and retrieves alternatives via HybridSearch")
    void testFindAlternativesForQuery() {
        UUID candId = UUID.randomUUID();
        DiscoveryProductDTO discProduct = DiscoveryProductDTO.builder()
                .id(candId)
                .name("MacBook Air M2")
                .brand("Apple")
                .category("Laptops")
                .currentBestPrice(BigDecimal.valueOf(999.00))
                .originalPrice(BigDecimal.valueOf(1199.00))
                .discountPercentage(BigDecimal.valueOf(16.68))
                .relevanceScore(85.0)
                .build();

        DiscoverySearchResponseDTO discResponse = DiscoverySearchResponseDTO.builder()
                .content(List.of(discProduct))
                .totalElements(1)
                .availableCategories(List.of("Laptops"))
                .availableBrands(List.of("Apple"))
                .build();

        when(hybridSearchService.search(any(HybridSearchRequest.class))).thenReturn(discResponse);

        ProductEntity entity = ProductEntity.builder()
                .name("MacBook Air M2")
                .brand("Apple")
                .category("Laptops")
                .build();
        entity.setId(candId);

        when(productRepository.findAllByIdInWithPricesAndSellers(List.of(candId))).thenReturn(List.of(entity));
        when(productPriceRepository.findPricesWithSellersByProductIds(List.of(candId))).thenReturn(Collections.emptyList());

        AlternativeRequest request = AlternativeRequest.builder()
                .query("laptop under $1200")
                .type(AlternativeType.BETTER_VALUE)
                .build();

        AlternativeResponseDTO response = service.findAlternativesForQuery(request);

        assertThat(response).isNotNull();
        assertThat(response.getExecutionMode()).isEqualTo(AlternativeMode.QUERY);
        assertThat(response.getContent()).hasSize(1);
        assertThat(response.getContent().get(0).getId()).isEqualTo(candId);
    }

    @Test
    @DisplayName("Natural Language query: Auto-detects CHEAPER type from keywords")
    void testFindAlternativesForNaturalLanguageKeywords() {
        UUID candId = UUID.randomUUID();
        DiscoveryProductDTO discProduct = DiscoveryProductDTO.builder()
                .id(candId)
                .name("Soundcore Q30")
                .brand("Anker")
                .category("Headphones")
                .currentBestPrice(BigDecimal.valueOf(79.99))
                .build();

        DiscoverySearchResponseDTO discResponse = DiscoverySearchResponseDTO.builder()
                .content(List.of(discProduct))
                .totalElements(1)
                .build();

        when(hybridSearchService.search(any(HybridSearchRequest.class))).thenReturn(discResponse);

        ProductEntity entity = ProductEntity.builder()
                .name("Soundcore Q30")
                .category("Headphones")
                .build();
        entity.setId(candId);

        when(productRepository.findAllByIdInWithPricesAndSellers(List.of(candId))).thenReturn(List.of(entity));

        AlternativeResponseDTO response = service.findAlternativesForNaturalLanguage("cheaper alternative to bose quietcomfort", null, 5);

        assertThat(response).isNotNull();
        assertThat(response.getAlternativeType()).isEqualTo(AlternativeType.CHEAPER);
    }
}
