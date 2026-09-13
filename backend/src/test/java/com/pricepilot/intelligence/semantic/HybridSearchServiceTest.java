package com.pricepilot.intelligence.semantic;

import com.pricepilot.intelligence.analytics.PriceAnalyticsService;
import com.pricepilot.intelligence.discovery.dto.DiscoverySearchResponseDTO;
import com.pricepilot.intelligence.discovery.hybrid.*;
import com.pricepilot.intelligence.discovery.interpretation.InterpretedQuery;
import com.pricepilot.intelligence.discovery.interpretation.QueryInterpreter;
import com.pricepilot.intelligence.discovery.normalization.QueryNormalizer;
import com.pricepilot.intelligence.discovery.ranking.DefaultSearchRelevanceScorer;
import com.pricepilot.intelligence.semantic.config.SemanticIntelligenceProperties;
import com.pricepilot.intelligence.semantic.model.EmbeddingMetadata;
import com.pricepilot.intelligence.semantic.model.EmbeddingVector;
import com.pricepilot.intelligence.semantic.model.SimilaritySearchRequest;
import com.pricepilot.intelligence.semantic.model.SimilaritySearchResult;
import com.pricepilot.intelligence.semantic.service.EmbeddingService;
import com.pricepilot.intelligence.semantic.storage.VectorStore;
import com.pricepilot.product.ProductEntity;
import com.pricepilot.product.ProductRepository;
import com.pricepilot.productprice.ProductPriceEntity;
import com.pricepilot.productprice.ProductPriceRepository;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class HybridSearchServiceTest {

    @Mock
    private ProductRepository productRepository;

    @Mock
    private ProductPriceRepository productPriceRepository;

    @Mock
    private PriceAnalyticsService priceAnalyticsService;

    @Mock
    private QueryInterpreter queryInterpreter;

    @Mock
    private EmbeddingService embeddingService;

    @Mock
    private VectorStore vectorStore;

    private DefaultSearchRelevanceScorer relevanceScorer;
    private HybridCandidateFusionStrategy fusionStrategy;
    private SemanticIntelligenceProperties properties;
    private SimpleMeterRegistry meterRegistry;
    private HybridSearchServiceImpl hybridSearchService;

    @BeforeEach
    void setUp() {
        QueryNormalizer queryNormalizer = new QueryNormalizer();
        relevanceScorer = new DefaultSearchRelevanceScorer(queryNormalizer);
        fusionStrategy = new ReciprocalRankFusionStrategy(60.0);

        properties = new SemanticIntelligenceProperties();
        properties.setEnabled(true);
        properties.setModelName("local-hash-embedding");
        properties.setModelVersion("v1");
        properties.setDimension(64);

        meterRegistry = new SimpleMeterRegistry();

        hybridSearchService = new HybridSearchServiceImpl(
                productRepository,
                productPriceRepository,
                priceAnalyticsService,
                queryInterpreter,
                relevanceScorer,
                embeddingService,
                vectorStore,
                fusionStrategy,
                properties,
                meterRegistry
        );
    }

    @Test
    @DisplayName("Should fuse structured and semantic candidates and enrich response")
    void testHybridSearchSuccessfulFlow() {
        String query = "noise cancelling headphones";
        InterpretedQuery interpreted = InterpretedQuery.builder()
                .originalQuery(query)
                .normalizedQuery(query)
                .searchTokens(List.of("noise", "cancelling", "headphones"))
                .cleanSearchTerms(query)
                .build();

        when(queryInterpreter.interpret(query)).thenReturn(interpreted);

        UUID p1Id = UUID.randomUUID();
        UUID p2Id = UUID.randomUUID();

        ProductEntity p1 = ProductEntity.builder().name("Sony WH-1000XM5").brand("Sony").category("Audio").build();
        p1.setId(p1Id);
        ProductEntity p2 = ProductEntity.builder().name("Bose QuietComfort 45").brand("Bose").category("Audio").build();
        p2.setId(p2Id);

        // Structured finds p1
        when(productRepository.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(p1)));

        // Semantic finds p2
        EmbeddingVector queryVector = EmbeddingVector.of(new float[64]);
        when(embeddingService.generateEmbedding(query)).thenReturn(queryVector);
        when(embeddingService.getModelName()).thenReturn("local-hash-embedding");
        when(embeddingService.getModelVersion()).thenReturn("v1");

        EmbeddingMetadata meta = new EmbeddingMetadata("PRODUCT", p2Id.toString(), "m", "v1", 64, Map.of(), Instant.now(), Instant.now());
        SimilaritySearchResult semResult = new SimilaritySearchResult("PRODUCT", p2Id.toString(), 0.90, meta, null);
        when(vectorStore.similaritySearch(any(SimilaritySearchRequest.class)))
                .thenReturn(List.of(semResult));

        when(productRepository.findAllByIdInWithPricesAndSellers(List.of(p2Id)))
                .thenReturn(List.of(p2));

        // Prices for both
        ProductPriceEntity price1 = ProductPriceEntity.builder().product(p1).currentPrice(BigDecimal.valueOf(299.99)).build();
        ProductPriceEntity price2 = ProductPriceEntity.builder().product(p2).currentPrice(BigDecimal.valueOf(279.99)).build();
        when(productPriceRepository.findPricesWithSellersByProductIds(anyList()))
                .thenReturn(List.of(price1, price2));

        HybridSearchRequest request = HybridSearchRequest.builder()
                .query(query)
                .page(0)
                .size(10)
                .build();

        DiscoverySearchResponseDTO response = hybridSearchService.search(request);

        assertThat(response).isNotNull();
        assertThat(response.getContent()).hasSize(2);
        assertThat(meterRegistry.get("pricepilot.hybrid.search.requests").counter().count()).isEqualTo(1.0);
    }

    @Test
    @DisplayName("Hard Filter Guarantee: Semantic candidate violating price constraint must be excluded")
    void testHardPriceFilterExclusion() {
        String query = "headphones";
        InterpretedQuery interpreted = InterpretedQuery.builder()
                .originalQuery(query)
                .searchTokens(List.of("headphones"))
                .cleanSearchTerms(query)
                .build();

        when(queryInterpreter.interpret(query)).thenReturn(interpreted);

        UUID pExpensiveId = UUID.randomUUID();
        ProductEntity pExpensive = ProductEntity.builder().name("Apple AirPods Max").category("Audio").build();
        pExpensive.setId(pExpensiveId);

        // Structured returns empty
        when(productRepository.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(Collections.emptyList()));

        // Semantic returns expensive headphone
        EmbeddingVector vec = EmbeddingVector.of(new float[64]);
        when(embeddingService.generateEmbedding(query)).thenReturn(vec);
        when(embeddingService.getModelName()).thenReturn("local-hash-embedding");
        when(embeddingService.getModelVersion()).thenReturn("v1");
        EmbeddingMetadata meta = new EmbeddingMetadata("PRODUCT", pExpensiveId.toString(), "m", "v1", 64, Map.of(), Instant.now(), Instant.now());
        when(vectorStore.similaritySearch(any(SimilaritySearchRequest.class)))
                .thenReturn(List.of(new SimilaritySearchResult("PRODUCT", pExpensiveId.toString(), 0.95, meta, null)));

        when(productRepository.findAllByIdInWithPricesAndSellers(List.of(pExpensiveId)))
                .thenReturn(List.of(pExpensive));

        // Price is $549
        ProductPriceEntity expensivePrice = ProductPriceEntity.builder().product(pExpensive).currentPrice(BigDecimal.valueOf(549.00)).build();
        when(productPriceRepository.findPricesWithSellersByProductIds(anyList()))
                .thenReturn(List.of(expensivePrice));

        // Hard filter maxPrice = $300
        HybridSearchRequest request = HybridSearchRequest.builder()
                .query(query)
                .maxPrice(BigDecimal.valueOf(300.00))
                .page(0)
                .size(10)
                .build();

        DiscoverySearchResponseDTO response = hybridSearchService.search(request);

        // The expensive product MUST be excluded by hard filter
        assertThat(response.getContent()).isEmpty();
    }

    @Test
    @DisplayName("Graceful Degradation: Semantic failure falls back cleanly to structured results")
    void testGracefulDegradationOnSemanticFailure() {
        String query = "laptop";
        InterpretedQuery interpreted = InterpretedQuery.builder()
                .originalQuery(query)
                .searchTokens(List.of("laptop"))
                .cleanSearchTerms(query)
                .build();

        when(queryInterpreter.interpret(query)).thenReturn(interpreted);

        UUID pId = UUID.randomUUID();
        ProductEntity p = ProductEntity.builder().name("Dell XPS 15").category("Laptops").build();
        p.setId(pId);

        when(productRepository.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(p)));

        // Embedding generation fails with an exception (e.g. timeout or unavailable)
        when(embeddingService.generateEmbedding(query))
                .thenThrow(new RuntimeException("Embedding model unavailable"));

        ProductPriceEntity price = ProductPriceEntity.builder().product(p).currentPrice(BigDecimal.valueOf(1499.00)).build();
        when(productPriceRepository.findPricesWithSellersByProductIds(anyList()))
                .thenReturn(List.of(price));

        HybridSearchRequest request = HybridSearchRequest.builder()
                .query(query)
                .page(0)
                .size(10)
                .build();

        DiscoverySearchResponseDTO response = hybridSearchService.search(request);

        assertThat(response.getContent()).hasSize(1);
        assertThat(response.getContent().get(0).getId()).isEqualTo(pId);
        assertThat(meterRegistry.get("pricepilot.hybrid.search.degraded").counter().count()).isEqualTo(1.0);
    }
}
