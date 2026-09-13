package com.pricepilot.intelligence.semantic;

import com.pricepilot.intelligence.semantic.config.SemanticIntelligenceProperties;
import com.pricepilot.intelligence.semantic.contract.CanonicalProductTextBuilder;
import com.pricepilot.intelligence.semantic.model.EmbeddingMetadata;
import com.pricepilot.intelligence.semantic.model.EmbeddingRecord;
import com.pricepilot.intelligence.semantic.model.EmbeddingVector;
import com.pricepilot.intelligence.semantic.pipeline.ProductEmbeddingServiceImpl;
import com.pricepilot.intelligence.semantic.service.EmbeddingService;
import com.pricepilot.intelligence.semantic.storage.VectorStore;
import com.pricepilot.product.ProductEntity;
import com.pricepilot.product.ProductRepository;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.*;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ProductEmbeddingServiceTest {

    @Mock
    private EmbeddingService embeddingService;

    @Mock
    private VectorStore vectorStore;

    @Mock
    private ProductRepository productRepository;

    private CanonicalProductTextBuilder textBuilder;
    private SemanticIntelligenceProperties properties;
    private SimpleMeterRegistry meterRegistry;
    private ProductEmbeddingServiceImpl productEmbeddingService;

    @BeforeEach
    void setUp() {
        textBuilder = new CanonicalProductTextBuilder(2048);
        properties = new SemanticIntelligenceProperties();
        properties.setEnabled(true);
        properties.setModelName("local-hash-embedding");
        properties.setModelVersion("v1");
        properties.setDimension(64);
        properties.setBatchSize(32);

        meterRegistry = new SimpleMeterRegistry();
        productEmbeddingService = new ProductEmbeddingServiceImpl(
                textBuilder, embeddingService, vectorStore, productRepository, properties, meterRegistry
        );
    }

    @Test
    @DisplayName("Should canonicalize and index a single active product")
    void testIndexSingleProduct() {
        UUID productId = UUID.randomUUID();
        ProductEntity product = ProductEntity.builder()
                .name("MacBook Pro M3 Max")
                .brand("Apple")
                .category("Laptops")
                .description("16-inch Space Black")
                .archived(false)
                .build();
        product.setId(productId);

        EmbeddingVector vector = EmbeddingVector.of(new float[64]);
        EmbeddingMetadata metadata = new EmbeddingMetadata("PRODUCT", productId.toString(), "local-hash", "v1", 64, Map.of(), Instant.now(), Instant.now());
        EmbeddingRecord record = new EmbeddingRecord(UUID.randomUUID(), metadata, vector);

        when(embeddingService.createRecord(eq("PRODUCT"), eq(productId.toString()), anyString(), anyMap()))
                .thenReturn(record);

        EmbeddingRecord result = productEmbeddingService.indexProduct(product);

        assertThat(result).isNotNull();
        assertThat(result.getEntityId()).isEqualTo(productId.toString());
        verify(vectorStore).upsert(record);
        assertThat(meterRegistry.get("pricepilot.semantic.product.indexed.count").counter().count()).isEqualTo(1.0);
    }

    @Test
    @DisplayName("Should skip and delete embedding for archived product")
    void testIndexArchivedProduct() {
        UUID productId = UUID.randomUUID();
        ProductEntity product = ProductEntity.builder()
                .name("Discontinued Item")
                .category("Electronics")
                .archived(true)
                .build();
        product.setId(productId);

        when(vectorStore.delete("PRODUCT", productId.toString())).thenReturn(true);

        EmbeddingRecord result = productEmbeddingService.indexProduct(product);

        assertThat(result).isNull();
        verify(vectorStore).delete("PRODUCT", productId.toString());
        verify(embeddingService, never()).createRecord(anyString(), anyString(), anyString(), anyMap());
        assertThat(meterRegistry.get("pricepilot.semantic.product.skipped.count").counter().count()).isEqualTo(1.0);
    }

    @Test
    @DisplayName("Should batch index multiple products and delete archived products in batch")
    void testIndexProductBatch() {
        UUID p1Id = UUID.randomUUID();
        UUID p2Id = UUID.randomUUID();
        UUID pArchivedId = UUID.randomUUID();

        ProductEntity p1 = ProductEntity.builder().name("Item 1").category("Cat1").archived(false).build();
        p1.setId(p1Id);
        ProductEntity p2 = ProductEntity.builder().name("Item 2").category("Cat2").archived(false).build();
        p2.setId(p2Id);
        ProductEntity pArchived = ProductEntity.builder().name("Archived").category("Cat3").archived(true).build();
        pArchived.setId(pArchivedId);

        EmbeddingVector vec = EmbeddingVector.of(new float[64]);
        EmbeddingMetadata m1 = new EmbeddingMetadata("PRODUCT", p1Id.toString(), "m", "v1", 64, Map.of(), Instant.now(), Instant.now());
        EmbeddingMetadata m2 = new EmbeddingMetadata("PRODUCT", p2Id.toString(), "m", "v1", 64, Map.of(), Instant.now(), Instant.now());
        EmbeddingRecord r1 = new EmbeddingRecord(UUID.randomUUID(), m1, vec);
        EmbeddingRecord r2 = new EmbeddingRecord(UUID.randomUUID(), m2, vec);

        when(embeddingService.createBatchRecords(eq("PRODUCT"), anyMap(), anyMap()))
                .thenReturn(List.of(r1, r2));

        List<EmbeddingRecord> results = productEmbeddingService.indexProductBatch(List.of(p1, p2, pArchived));

        assertThat(results).hasSize(2);
        verify(vectorStore).batchDelete("PRODUCT", List.of(pArchivedId.toString()));
        verify(vectorStore).batchUpsert(List.of(r1, r2));
        assertThat(meterRegistry.get("pricepilot.semantic.product.indexed.count").counter().count()).isEqualTo(2.0);
    }

    @Test
    @DisplayName("Should return empty list and skip when semantic intelligence is disabled")
    void testDisabledBehavior() {
        properties.setEnabled(false);

        ProductEntity product = ProductEntity.builder().name("Item").category("Cat").build();
        product.setId(UUID.randomUUID());

        EmbeddingRecord record = productEmbeddingService.indexProduct(product);
        assertThat(record).isNull();
        verifyNoInteractions(vectorStore);
        verifyNoInteractions(embeddingService);
    }
}
