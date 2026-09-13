package com.pricepilot.intelligence.semantic;

import com.pricepilot.intelligence.semantic.config.SemanticIntelligenceProperties;
import com.pricepilot.intelligence.semantic.model.EmbeddingMetadata;
import com.pricepilot.intelligence.semantic.model.EmbeddingRecord;
import com.pricepilot.intelligence.semantic.model.EmbeddingVector;
import com.pricepilot.intelligence.semantic.pipeline.BatchIndexingResult;
import com.pricepilot.intelligence.semantic.pipeline.ProductBatchIndexingServiceImpl;
import com.pricepilot.intelligence.semantic.pipeline.ProductEmbeddingService;
import com.pricepilot.product.ProductEntity;
import com.pricepilot.product.ProductRepository;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ProductBatchIndexingServiceTest {

    @Mock
    private ProductRepository productRepository;

    @Mock
    private ProductEmbeddingService productEmbeddingService;

    private SemanticIntelligenceProperties properties;
    private SimpleMeterRegistry meterRegistry;
    private ProductBatchIndexingServiceImpl batchIndexingService;

    @BeforeEach
    void setUp() {
        properties = new SemanticIntelligenceProperties();
        properties.setEnabled(true);
        meterRegistry = new SimpleMeterRegistry();
        batchIndexingService = new ProductBatchIndexingServiceImpl(
                productRepository, productEmbeddingService, properties, meterRegistry
        );
    }

    @Test
    @DisplayName("Should paginate through active products and index in batches")
    void testIndexAllActiveProducts() {
        UUID id1 = UUID.randomUUID();
        UUID id2 = UUID.randomUUID();
        ProductEntity p1 = ProductEntity.builder().name("P1").category("C1").build();
        p1.setId(id1);
        ProductEntity p2 = ProductEntity.builder().name("P2").category("C2").build();
        p2.setId(id2);

        when(productRepository.findByArchivedFalse(any(PageRequest.class)))
                .thenReturn(new PageImpl<>(List.of(p1, p2), PageRequest.of(0, 10), 2));

        EmbeddingRecord r1 = new EmbeddingRecord(UUID.randomUUID(), new EmbeddingMetadata("PRODUCT", id1.toString(), "m", "v1", 64, Map.of(), Instant.now(), Instant.now()), EmbeddingVector.of(new float[64]));
        EmbeddingRecord r2 = new EmbeddingRecord(UUID.randomUUID(), new EmbeddingMetadata("PRODUCT", id2.toString(), "m", "v1", 64, Map.of(), Instant.now(), Instant.now()), EmbeddingVector.of(new float[64]));

        when(productEmbeddingService.indexProductBatch(anyList()))
                .thenReturn(List.of(r1, r2));

        BatchIndexingResult result = batchIndexingService.indexAllActiveProducts(10);

        assertThat(result.isSuccessful()).isTrue();
        assertThat(result.getTotalProcessed()).isEqualTo(2);
        assertThat(result.getTotalIndexed()).isEqualTo(2);
        assertThat(result.getTotalFailed()).isEqualTo(0);
        assertThat(meterRegistry.get("pricepilot.semantic.product.batch.executions").counter().count()).isEqualTo(1.0);
    }

    @Test
    @DisplayName("Should isolate errors per product when batch embedding fails")
    void testBatchErrorIsolationFallback() {
        ProductEntity pGood = ProductEntity.builder().name("Good").category("C1").build();
        pGood.setId(UUID.randomUUID());
        ProductEntity pBad = ProductEntity.builder().name("Bad").category("C2").build();
        pBad.setId(UUID.randomUUID());

        when(productRepository.findByArchivedFalse(any(PageRequest.class)))
                .thenReturn(new PageImpl<>(List.of(pGood, pBad), PageRequest.of(0, 10), 2));

        // Batch fails
        when(productEmbeddingService.indexProductBatch(anyList()))
                .thenThrow(new RuntimeException("Batch error"));

        // Single fallbacks
        EmbeddingRecord rGood = new EmbeddingRecord(UUID.randomUUID(), new EmbeddingMetadata("PRODUCT", pGood.getId().toString(), "m", "v1", 64, Map.of(), Instant.now(), Instant.now()), EmbeddingVector.of(new float[64]));
        when(productEmbeddingService.indexProduct(pGood)).thenReturn(rGood);
        when(productEmbeddingService.indexProduct(pBad)).thenThrow(new RuntimeException("Invalid item"));

        BatchIndexingResult result = batchIndexingService.indexAllActiveProducts(10);

        assertThat(result.getTotalProcessed()).isEqualTo(2);
        assertThat(result.getTotalIndexed()).isEqualTo(1);
        assertThat(result.getTotalFailed()).isEqualTo(1);
    }
}
