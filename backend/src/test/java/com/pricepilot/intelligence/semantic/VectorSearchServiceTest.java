package com.pricepilot.intelligence.semantic;

import com.pricepilot.intelligence.semantic.config.SemanticIntelligenceProperties;
import com.pricepilot.intelligence.semantic.exception.EmbeddingProviderUnavailableException;
import com.pricepilot.intelligence.semantic.model.*;
import com.pricepilot.intelligence.semantic.provider.LocalDeterministicEmbeddingProvider;
import com.pricepilot.intelligence.semantic.service.EmbeddingServiceImpl;
import com.pricepilot.intelligence.semantic.service.VectorSearchServiceImpl;
import com.pricepilot.intelligence.semantic.storage.VectorStore;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class VectorSearchServiceTest {

    @Mock
    private VectorStore vectorStore;

    private LocalDeterministicEmbeddingProvider provider;
    private EmbeddingServiceImpl embeddingService;
    private SemanticIntelligenceProperties properties;
    private VectorSearchServiceImpl searchService;

    @BeforeEach
    void setUp() {
        properties = new SemanticIntelligenceProperties();
        properties.setModelName("local-hash-embedding");
        properties.setModelVersion("v1");
        properties.setDimension(64);
        properties.setEnabled(true);

        provider = new LocalDeterministicEmbeddingProvider(properties);
        SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
        embeddingService = new EmbeddingServiceImpl(provider, properties, meterRegistry);
        searchService = new VectorSearchServiceImpl(embeddingService, vectorStore, properties);
    }

    @Test
    @DisplayName("Should embed text query and execute similarity search via VectorStore")
    void testSearchByText() {
        EmbeddingMetadata metadata = new EmbeddingMetadata("PRODUCT", "p-100", "local-hash-embedding", "v1", 64, Map.of(), Instant.now(), Instant.now());
        SimilaritySearchResult expected = new SimilaritySearchResult("PRODUCT", "p-100", 0.95, metadata, null);

        when(vectorStore.similaritySearch(any(SimilaritySearchRequest.class)))
                .thenReturn(List.of(expected));

        List<SimilaritySearchResult> results = searchService.searchByText("Apple iPhone 15 Pro", "PRODUCT", 5, 0.5);

        assertThat(results).hasSize(1);
        assertThat(results.get(0).getEntityId()).isEqualTo("p-100");

        ArgumentCaptor<SimilaritySearchRequest> captor = ArgumentCaptor.forClass(SimilaritySearchRequest.class);
        verify(vectorStore).similaritySearch(captor.capture());

        SimilaritySearchRequest captured = captor.getValue();
        assertThat(captured.getEntityType()).isEqualTo("PRODUCT");
        assertThat(captured.getTopK()).isEqualTo(5);
        assertThat(captured.getMinScore()).isEqualTo(0.5);
        assertThat(captured.getQueryVector().dimension()).isEqualTo(64);
    }

    @Test
    @DisplayName("Should return empty list without querying vector store when disabled")
    void testDisabledSearch() {
        properties.setEnabled(false);

        List<SimilaritySearchResult> results = searchService.searchByText("Apple iPhone 15 Pro", "PRODUCT", 5, 0.5);

        assertThat(results).isEmpty();
        verifyNoInteractions(vectorStore);
    }

    @Test
    @DisplayName("Should throw EmbeddingProviderUnavailableException and NOT fabricate false matches when provider is down")
    void testProviderUnavailableThrows() {
        provider.setAvailable(false);

        assertThatThrownBy(() -> searchService.searchByText("Apple iPhone 15 Pro", "PRODUCT", 5, 0.5))
                .isInstanceOf(EmbeddingProviderUnavailableException.class);

        verifyNoInteractions(vectorStore);
    }
}
