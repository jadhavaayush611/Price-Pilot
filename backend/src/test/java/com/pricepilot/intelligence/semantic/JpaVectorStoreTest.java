package com.pricepilot.intelligence.semantic;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pricepilot.intelligence.semantic.exception.IncompatibleVectorDimensionException;
import com.pricepilot.intelligence.semantic.model.*;
import com.pricepilot.intelligence.semantic.storage.JpaVectorStore;
import com.pricepilot.intelligence.semantic.storage.SemanticEmbeddingEntity;
import com.pricepilot.intelligence.semantic.storage.SemanticEmbeddingRepository;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.*;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class JpaVectorStoreTest {

    @Mock
    private SemanticEmbeddingRepository repository;

    private ObjectMapper objectMapper;
    private SimpleMeterRegistry meterRegistry;
    private JpaVectorStore vectorStore;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        meterRegistry = new SimpleMeterRegistry();
        vectorStore = new JpaVectorStore(repository, objectMapper, meterRegistry);
    }

    @Test
    @DisplayName("Should upsert new embedding entity when none exists")
    void testUpsertNewRecord() {
        when(repository.findByEntityTypeAndEntityIdAndModelNameAndModelVersion("PRODUCT", "p-1", "local-hash", "v1"))
                .thenReturn(Optional.empty());

        EmbeddingVector vector = EmbeddingVector.of(new float[]{0.6f, 0.8f});
        EmbeddingMetadata metadata = EmbeddingMetadata.builder()
                .entityType("PRODUCT")
                .entityId("p-1")
                .modelName("local-hash")
                .modelVersion("v1")
                .dimension(2)
                .attributes(Map.of("brand", "Sony"))
                .build();
        EmbeddingRecord record = EmbeddingRecord.builder()
                .id(UUID.randomUUID())
                .metadata(metadata)
                .vector(vector)
                .build();

        vectorStore.upsert(record);

        ArgumentCaptor<SemanticEmbeddingEntity> captor = ArgumentCaptor.forClass(SemanticEmbeddingEntity.class);
        verify(repository).save(captor.capture());

        SemanticEmbeddingEntity saved = captor.getValue();
        assertThat(saved.getEntityType()).isEqualTo("PRODUCT");
        assertThat(saved.getEntityId()).isEqualTo("p-1");
        assertThat(saved.getModelName()).isEqualTo("local-hash");
        assertThat(saved.getModelVersion()).isEqualTo("v1");
        assertThat(saved.getDimension()).isEqualTo(2);
        assertThat(saved.getVectorData()).isEqualTo("0.6,0.8");
        assertThat(saved.getMetadataJson()).contains("Sony");
        assertThat(meterRegistry.get("pricepilot.semantic.vector.upsert.count").counter().count()).isEqualTo(1.0);
    }

    @Test
    @DisplayName("Should update existing embedding entity when record already exists")
    void testUpsertExistingRecord() {
        SemanticEmbeddingEntity existing = SemanticEmbeddingEntity.builder()
                .entityType("PRODUCT")
                .entityId("p-1")
                .modelName("local-hash")
                .modelVersion("v1")
                .dimension(2)
                .vectorData("0.0,1.0")
                .metadataJson("{\"brand\":\"Old\"}")
                .build();
        existing.setId(UUID.randomUUID());

        when(repository.findByEntityTypeAndEntityIdAndModelNameAndModelVersion("PRODUCT", "p-1", "local-hash", "v1"))
                .thenReturn(Optional.of(existing));

        EmbeddingVector newVector = EmbeddingVector.of(new float[]{1.0f, 0.0f});
        EmbeddingMetadata metadata = EmbeddingMetadata.builder()
                .entityType("PRODUCT")
                .entityId("p-1")
                .modelName("local-hash")
                .modelVersion("v1")
                .dimension(2)
                .attributes(Map.of("brand", "NewBrand"))
                .build();
        EmbeddingRecord newRecord = EmbeddingRecord.builder()
                .id(existing.getId())
                .metadata(metadata)
                .vector(newVector)
                .build();

        vectorStore.upsert(newRecord);

        verify(repository).save(existing);
        assertThat(existing.getVectorData()).isEqualTo("1.0,0.0");
        assertThat(existing.getMetadataJson()).contains("NewBrand");
    }

    @Test
    @DisplayName("Should batch upsert multiple records")
    void testBatchUpsert() {
        EmbeddingMetadata m1 = EmbeddingMetadata.builder().entityType("PRODUCT").entityId("p-1").modelName("m").modelVersion("v1").dimension(2).build();
        EmbeddingMetadata m2 = EmbeddingMetadata.builder().entityType("PRODUCT").entityId("p-2").modelName("m").modelVersion("v1").dimension(2).build();

        EmbeddingRecord r1 = EmbeddingRecord.builder().metadata(m1).vector(EmbeddingVector.of(new float[]{1.0f, 0.0f})).build();
        EmbeddingRecord r2 = EmbeddingRecord.builder().metadata(m2).vector(EmbeddingVector.of(new float[]{0.0f, 1.0f})).build();

        when(repository.findByEntityTypeAndEntityIdAndModelNameAndModelVersion(anyString(), anyString(), anyString(), anyString()))
                .thenReturn(Optional.empty());

        vectorStore.batchUpsert(List.of(r1, r2));

        verify(repository, times(2)).save(any(SemanticEmbeddingEntity.class));
        assertThat(meterRegistry.get("pricepilot.semantic.vector.upsert.count").counter().count()).isEqualTo(2.0);
    }

    @Test
    @DisplayName("Should execute similarity search with deterministic ordering and score filtering")
    void testSimilaritySearch() {
        SemanticEmbeddingEntity e1 = SemanticEmbeddingEntity.builder()
                .entityType("PRODUCT").entityId("p-close").modelName("m").modelVersion("v1").dimension(2)
                .vectorData("0.8,0.6").metadataJson(null).build();
        e1.setCreatedAt(LocalDateTime.now());
        e1.setUpdatedAt(LocalDateTime.now());

        SemanticEmbeddingEntity e2 = SemanticEmbeddingEntity.builder()
                .entityType("PRODUCT").entityId("p-far").modelName("m").modelVersion("v1").dimension(2)
                .vectorData("-1.0,0.0").metadataJson(null).build();
        e2.setCreatedAt(LocalDateTime.now());
        e2.setUpdatedAt(LocalDateTime.now());

        SemanticEmbeddingEntity e3 = SemanticEmbeddingEntity.builder()
                .entityType("PRODUCT").entityId("p-exact").modelName("m").modelVersion("v1").dimension(2)
                .vectorData("1.0,0.0").metadataJson(null).build();
        e3.setCreatedAt(LocalDateTime.now());
        e3.setUpdatedAt(LocalDateTime.now());

        when(repository.findByEntityTypeAndModelNameAndModelVersion("PRODUCT", "m", "v1"))
                .thenReturn(List.of(e1, e2, e3));

        EmbeddingVector queryVector = EmbeddingVector.of(new float[]{1.0f, 0.0f});
        SimilaritySearchRequest request = SimilaritySearchRequest.builder()
                .queryVector(queryVector)
                .entityType("PRODUCT")
                .modelName("m")
                .modelVersion("v1")
                .minScore(0.0)
                .topK(2)
                .build();

        List<SimilaritySearchResult> results = vectorStore.similaritySearch(request);

        assertThat(results).hasSize(2);
        // Rank 1: p-exact (score = 1.0)
        assertThat(results.get(0).getEntityId()).isEqualTo("p-exact");
        assertThat(results.get(0).getScore()).isEqualTo(1.0, within(1e-6));

        // Rank 2: p-close (score = 0.8)
        assertThat(results.get(1).getEntityId()).isEqualTo("p-close");
        assertThat(results.get(1).getScore()).isEqualTo(0.8, within(1e-6));
    }

    @Test
    @DisplayName("Should enforce deterministic tie-breaking by entityId when scores are equal")
    void testDeterministicTieBreaking() {
        SemanticEmbeddingEntity e1 = SemanticEmbeddingEntity.builder()
                .entityType("PRODUCT").entityId("prod-zebra").modelName("m").modelVersion("v1").dimension(2)
                .vectorData("1.0,0.0").build();
        SemanticEmbeddingEntity e2 = SemanticEmbeddingEntity.builder()
                .entityType("PRODUCT").entityId("prod-apple").modelName("m").modelVersion("v1").dimension(2)
                .vectorData("1.0,0.0").build();

        when(repository.findByEntityTypeAndModelNameAndModelVersion("PRODUCT", "m", "v1"))
                .thenReturn(List.of(e1, e2));

        EmbeddingVector query = EmbeddingVector.of(new float[]{1.0f, 0.0f});
        SimilaritySearchRequest request = SimilaritySearchRequest.builder()
                .queryVector(query)
                .entityType("PRODUCT")
                .modelName("m")
                .modelVersion("v1")
                .topK(10)
                .build();

        List<SimilaritySearchResult> results = vectorStore.similaritySearch(request);

        assertThat(results).hasSize(2);
        // Scores are equal (1.0), so "prod-apple" must precede "prod-zebra"
        assertThat(results.get(0).getEntityId()).isEqualTo("prod-apple");
        assertThat(results.get(1).getEntityId()).isEqualTo("prod-zebra");
    }

    @Test
    @DisplayName("Should isolate model versions and reject dimension mismatch")
    void testModelVersionAndDimensionIsolation() {
        SemanticEmbeddingEntity badDimensionEntity = SemanticEmbeddingEntity.builder()
                .entityType("PRODUCT").entityId("p-bad").modelName("m").modelVersion("v1").dimension(4)
                .vectorData("1.0,0.0,0.0,0.0").build();

        when(repository.findByEntityTypeAndModelNameAndModelVersion("PRODUCT", "m", "v1"))
                .thenReturn(List.of(badDimensionEntity));

        EmbeddingVector query = EmbeddingVector.of(new float[]{1.0f, 0.0f});
        SimilaritySearchRequest request = SimilaritySearchRequest.builder()
                .queryVector(query)
                .entityType("PRODUCT")
                .modelName("m")
                .modelVersion("v1")
                .build();

        assertThatThrownBy(() -> vectorStore.similaritySearch(request))
                .isInstanceOf(IncompatibleVectorDimensionException.class);
    }

    @Test
    @DisplayName("Should filter search results by metadata attributes")
    void testAttributeFiltering() {
        SemanticEmbeddingEntity eSony = SemanticEmbeddingEntity.builder()
                .entityType("PRODUCT").entityId("p-sony").modelName("m").modelVersion("v1").dimension(2)
                .vectorData("1.0,0.0").metadataJson("{\"brand\":\"Sony\",\"category\":\"Audio\"}").build();

        SemanticEmbeddingEntity eBose = SemanticEmbeddingEntity.builder()
                .entityType("PRODUCT").entityId("p-bose").modelName("m").modelVersion("v1").dimension(2)
                .vectorData("1.0,0.0").metadataJson("{\"brand\":\"Bose\",\"category\":\"Audio\"}").build();

        when(repository.findByEntityTypeAndModelNameAndModelVersion("PRODUCT", "m", "v1"))
                .thenReturn(List.of(eSony, eBose));

        EmbeddingVector query = EmbeddingVector.of(new float[]{1.0f, 0.0f});
        SimilaritySearchRequest request = SimilaritySearchRequest.builder()
                .queryVector(query)
                .entityType("PRODUCT")
                .modelName("m")
                .modelVersion("v1")
                .addFilter("brand", "Sony")
                .build();

        List<SimilaritySearchResult> results = vectorStore.similaritySearch(request);

        assertThat(results).hasSize(1);
        assertThat(results.get(0).getEntityId()).isEqualTo("p-sony");
    }

    @Test
    @DisplayName("Should handle deletion and batch deletion")
    void testDeleteAndBatchDelete() {
        when(repository.deleteByEntityTypeAndEntityId("PRODUCT", "p-1")).thenReturn(1);
        when(repository.deleteByEntityTypeAndEntityId("PRODUCT", "p-unknown")).thenReturn(0);

        assertThat(vectorStore.delete("PRODUCT", "p-1")).isTrue();
        assertThat(vectorStore.delete("PRODUCT", "p-unknown")).isFalse();

        when(repository.deleteByEntityTypeAndEntityIdIn(eq("PRODUCT"), anyCollection())).thenReturn(5);
        assertThat(vectorStore.batchDelete("PRODUCT", List.of("p-1", "p-2", "p-3"))).isEqualTo(5);
    }
}
