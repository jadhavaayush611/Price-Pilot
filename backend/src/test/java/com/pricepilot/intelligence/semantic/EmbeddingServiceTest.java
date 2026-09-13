package com.pricepilot.intelligence.semantic;

import com.pricepilot.intelligence.semantic.config.SemanticIntelligenceProperties;
import com.pricepilot.intelligence.semantic.exception.EmbeddingProviderUnavailableException;
import com.pricepilot.intelligence.semantic.exception.InvalidEmbeddingInputException;
import com.pricepilot.intelligence.semantic.model.EmbeddingRecord;
import com.pricepilot.intelligence.semantic.model.EmbeddingVector;
import com.pricepilot.intelligence.semantic.provider.LocalDeterministicEmbeddingProvider;
import com.pricepilot.intelligence.semantic.service.EmbeddingServiceImpl;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.*;

class EmbeddingServiceTest {

    private LocalDeterministicEmbeddingProvider provider;
    private SemanticIntelligenceProperties properties;
    private SimpleMeterRegistry meterRegistry;
    private EmbeddingServiceImpl embeddingService;

    @BeforeEach
    void setUp() {
        properties = new SemanticIntelligenceProperties();
        properties.setModelName("local-hash-embedding");
        properties.setModelVersion("v1");
        properties.setDimension(64);
        properties.setBatchSize(4);
        properties.setMaxInputLength(500);
        properties.setTimeoutMs(5000);

        provider = new LocalDeterministicEmbeddingProvider(properties);
        meterRegistry = new SimpleMeterRegistry();
        embeddingService = new EmbeddingServiceImpl(provider, properties, meterRegistry);
    }

    @Test
    @DisplayName("Should generate single embedding and update metric counter")
    void testGenerateEmbedding() {
        EmbeddingVector vector = embeddingService.generateEmbedding("MacBook Pro M3 Max 16-inch");

        assertThat(vector).isNotNull();
        assertThat(vector.dimension()).isEqualTo(64);
        assertThat(meterRegistry.get("pricepilot.semantic.embedding.count").counter().count()).isEqualTo(1.0);
    }

    @Test
    @DisplayName("Should generate batch embeddings and enforce batch size limit")
    void testGenerateBatchEmbeddings() {
        List<String> validBatch = List.of("Item 1", "Item 2", "Item 3");
        List<EmbeddingVector> vectors = embeddingService.generateBatchEmbeddings(validBatch);

        assertThat(vectors).hasSize(3);
        assertThat(meterRegistry.get("pricepilot.semantic.embedding.count").counter().count()).isEqualTo(3.0);

        List<String> oversizedBatch = List.of("1", "2", "3", "4", "5");
        assertThatThrownBy(() -> embeddingService.generateBatchEmbeddings(oversizedBatch))
                .isInstanceOf(InvalidEmbeddingInputException.class)
                .hasMessageContaining("exceeds configured maximum allowable batch size");
    }

    @Test
    @DisplayName("Should create single EmbeddingRecord with complete metadata")
    void testCreateRecord() {
        Map<String, String> attrs = Map.of("category", "Electronics", "brand", "Apple");
        EmbeddingRecord record = embeddingService.createRecord(
                "PRODUCT", "prod-12345", "Apple iPhone 15 Pro", attrs
        );

        assertThat(record).isNotNull();
        assertThat(record.getId()).isNotNull();
        assertThat(record.getEntityType()).isEqualTo("PRODUCT");
        assertThat(record.getEntityId()).isEqualTo("prod-12345");
        assertThat(record.getModelName()).isEqualTo("local-hash-embedding");
        assertThat(record.getModelVersion()).isEqualTo("v1");
        assertThat(record.getDimension()).isEqualTo(64);
        assertThat(record.getMetadata().getAttributes()).containsEntry("category", "Electronics");
        assertThat(record.getVector().dimension()).isEqualTo(64);
    }

    @Test
    @DisplayName("Should create batch records preserving ordering and metadata")
    void testCreateBatchRecords() {
        Map<String, String> entityIdToText = new LinkedHashMap<>();
        entityIdToText.put("p1", "Sony WH-1000XM5");
        entityIdToText.put("p2", "Bose QuietComfort Ultra");

        Map<String, Map<String, String>> attributes = Map.of(
                "p1", Map.of("brand", "Sony"),
                "p2", Map.of("brand", "Bose")
        );

        List<EmbeddingRecord> records = embeddingService.createBatchRecords("PRODUCT", entityIdToText, attributes);

        assertThat(records).hasSize(2);
        assertThat(records.get(0).getEntityId()).isEqualTo("p1");
        assertThat(records.get(0).getMetadata().getAttributes()).containsEntry("brand", "Sony");
        assertThat(records.get(1).getEntityId()).isEqualTo("p2");
        assertThat(records.get(1).getMetadata().getAttributes()).containsEntry("brand", "Bose");
    }

    @Test
    @DisplayName("Should reject empty text or null entity identifiers")
    void testValidation() {
        assertThatThrownBy(() -> embeddingService.generateEmbedding(""))
                .isInstanceOf(InvalidEmbeddingInputException.class);

        assertThatThrownBy(() -> embeddingService.createRecord("", "id-1", "Valid text", Map.of()))
                .isInstanceOf(InvalidEmbeddingInputException.class);

        assertThatThrownBy(() -> embeddingService.createRecord("PRODUCT", "", "Valid text", Map.of()))
                .isInstanceOf(InvalidEmbeddingInputException.class);
    }

    @Test
    @DisplayName("Should record failure metric when provider is unavailable")
    void testProviderFailureMetric() {
        provider.setAvailable(false);

        assertThatThrownBy(() -> embeddingService.generateEmbedding("Test query"))
                .isInstanceOf(EmbeddingProviderUnavailableException.class);

        assertThat(meterRegistry.get("pricepilot.semantic.embedding.failures").counter().count()).isEqualTo(1.0);
    }
}
