package com.pricepilot.intelligence.discovery.intent;

import com.pricepilot.intelligence.discovery.dto.DiscoveryProductDTO;
import com.pricepilot.intelligence.discovery.dto.DiscoverySearchResponseDTO;
import com.pricepilot.intelligence.discovery.hybrid.HybridSearchRequest;
import com.pricepilot.intelligence.discovery.hybrid.HybridSearchService;
import com.pricepilot.intelligence.discovery.normalization.QueryNormalizer;
import com.pricepilot.product.ProductRepository;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class NaturalLanguageDiscoveryServiceTest {

    @Mock
    private HybridSearchService hybridSearchService;

    @Mock
    private ProductRepository productRepository;

    private ShoppingQueryInterpreter interpreter;
    private ShoppingQueryIntentValidator validator;
    private SimpleMeterRegistry meterRegistry;
    private NaturalLanguageDiscoveryServiceImpl nlDiscoveryService;

    @BeforeEach
    void setUp() {
        QueryNormalizer normalizer = new QueryNormalizer();
        interpreter = new DeterministicShoppingQueryInterpreter(normalizer);
        validator = new DefaultShoppingQueryIntentValidator();
        meterRegistry = new SimpleMeterRegistry();

        nlDiscoveryService = new NaturalLanguageDiscoveryServiceImpl(
                interpreter,
                validator,
                hybridSearchService,
                productRepository,
                meterRegistry
        );
    }

    @Test
    @DisplayName("End-to-End NL Search: converts query, validates intent, delegates to HybridSearchService")
    void testSuccessfulNaturalLanguageSearch() {
        String nlQuery = "wireless headphones under ₹10,000 with 4+ stars";

        DiscoveryProductDTO p1 = DiscoveryProductDTO.builder()
                .id(UUID.randomUUID())
                .name("Sony WH-CH520")
                .brand("Sony")
                .category("Headphones")
                .currentBestPrice(BigDecimal.valueOf(4999))
                .build();

        DiscoverySearchResponseDTO mockResponse = DiscoverySearchResponseDTO.builder()
                .content(List.of(p1))
                .totalElements(1)
                .totalPages(1)
                .page(0)
                .size(10)
                .build();

        when(hybridSearchService.search(any(HybridSearchRequest.class))).thenReturn(mockResponse);

        NaturalLanguageSearchRequest request = NaturalLanguageSearchRequest.builder()
                .query(nlQuery)
                .page(0)
                .size(10)
                .build();

        DiscoverySearchResponseDTO response = nlDiscoveryService.search(request);

        // Verify delegation with extracted constraints
        ArgumentCaptor<HybridSearchRequest> captor = ArgumentCaptor.forClass(HybridSearchRequest.class);
        verify(hybridSearchService).search(captor.capture());

        HybridSearchRequest sentRequest = captor.getValue();
        assertThat(sentRequest.getQuery()).isEqualTo("wireless headphones");
        assertThat(sentRequest.getCategory()).isEqualTo("Headphones");
        assertThat(sentRequest.getMaxPrice()).isEqualByComparingTo(BigDecimal.valueOf(10000));
        assertThat(sentRequest.getMinRating()).isEqualTo(4.0);

        // Verify response and metrics
        assertThat(response).isNotNull();
        assertThat(response.getContent()).hasSize(1);
        assertThat(response.getInterpretedQuery()).isNotNull();
        assertThat(response.getInterpretedQuery().getDetectedCategory()).isEqualTo("Headphones");
        assertThat(meterRegistry.get("pricepilot.nl.search.requests").counter().count()).isEqualTo(1.0);
        assertThat(meterRegistry.get("pricepilot.nl.search.success").counter().count()).isEqualTo(1.0);
    }

    @Test
    @DisplayName("Conflict Handling: Contradictory natural language query must return empty response with conflict note")
    void testConflictingQueryHandling() {
        String nlQuery = "laptop under ₹10,000 and above ₹20,000";

        when(productRepository.findDistinctCategories()).thenReturn(List.of("Laptop", "Headphones"));
        when(productRepository.findDistinctBrands()).thenReturn(List.of("Apple", "Dell"));

        NaturalLanguageSearchRequest request = NaturalLanguageSearchRequest.builder()
                .query(nlQuery)
                .build();

        DiscoverySearchResponseDTO response = nlDiscoveryService.search(request);

        assertThat(response.getContent()).isEmpty();
        assertThat(response.getTotalElements()).isEqualTo(0);
        assertThat(response.getInterpretedQuery().getInterpretationNotes()).anyMatch(n -> n.contains("exceeds") || n.contains("Conflict"));
        verify(hybridSearchService, never()).search(any(HybridSearchRequest.class));
        assertThat(meterRegistry.get("pricepilot.nl.search.conflicts").counter().count()).isEqualTo(1.0);
    }

    @Test
    @DisplayName("Query Intent Inspection: Can interpret query without executing retrieval")
    void testInterpretQueryInspection() {
        String nlQuery = "Bose noise cancelling earbuds under $300 in stock";
        ShoppingQueryIntent intent = nlDiscoveryService.interpretQuery(nlQuery);

        assertThat(intent.getBrand()).isEqualTo("Bose");
        assertThat(intent.getCategory()).isEqualTo("Headphones");
        assertThat(intent.getMaxPrice()).isEqualByComparingTo(BigDecimal.valueOf(300));
        assertThat(intent.getInStock()).isTrue();
        assertThat(intent.getSemanticQuery()).contains("noise cancelling earbuds");
    }
}
