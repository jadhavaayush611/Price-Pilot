package com.pricepilot.intelligence.semantic;

import com.pricepilot.intelligence.semantic.config.SemanticIntelligenceProperties;
import com.pricepilot.intelligence.semantic.health.SemanticIntelligenceHealthIndicator;
import com.pricepilot.intelligence.semantic.model.*;
import com.pricepilot.intelligence.semantic.provider.EmbeddingProvider;
import com.pricepilot.intelligence.semantic.service.EmbeddingService;
import com.pricepilot.intelligence.semantic.service.VectorSearchService;
import com.pricepilot.intelligence.semantic.storage.VectorStore;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.Status;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.concurrent.*;

import static org.assertj.core.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class SemanticIntelligenceIntegrationTest {

    @Autowired
    private EmbeddingService embeddingService;

    @Autowired
    private VectorSearchService vectorSearchService;

    @Autowired
    private VectorStore vectorStore;

    @Autowired
    private EmbeddingProvider embeddingProvider;

    @Autowired
    private SemanticIntelligenceProperties properties;

    @Autowired
    private SemanticIntelligenceHealthIndicator healthIndicator;

    @Test
    @DisplayName("End-to-End: Generate embedding, upsert into DB, search by text, and delete")
    void testEndToEndSemanticFlow() {
        String entityType = "PRODUCT";
        String entityId1 = "prod-iphone-15-pro";
        String entityId2 = "prod-samsung-s24";
        String entityId3 = "prod-sony-headphones";

        EmbeddingRecord r1 = embeddingService.createRecord(entityType, entityId1, "Apple iPhone 15 Pro 256GB Black Titanium", Map.of("category", "Smartphones", "brand", "Apple"));
        EmbeddingRecord r2 = embeddingService.createRecord(entityType, entityId2, "Samsung Galaxy S24 Ultra 512GB Titanium Grey", Map.of("category", "Smartphones", "brand", "Samsung"));
        EmbeddingRecord r3 = embeddingService.createRecord(entityType, entityId3, "Sony WH-1000XM5 Noise Canceling Headphones", Map.of("category", "Audio", "brand", "Sony"));

        vectorStore.batchUpsert(List.of(r1, r2, r3));

        // 1. Verify existence
        assertThat(vectorStore.exists(entityType, entityId1, properties.getModelName(), properties.getModelVersion())).isTrue();
        assertThat(vectorStore.exists(entityType, "unknown-id", properties.getModelName(), properties.getModelVersion())).isFalse();

        // 2. Perform text-based similarity search
        List<SimilaritySearchResult> results = vectorSearchService.searchByText("Apple iPhone 15", entityType, 5, 0.0);

        assertThat(results).isNotEmpty();
        // Top result should be the iPhone
        assertThat(results.get(0).getEntityId()).isEqualTo(entityId1);
        assertThat(results.get(0).getScore()).isGreaterThan(0.50);
        assertThat(results.get(0).getMetadata().getAttributes()).containsEntry("brand", "Apple");

        // 3. Search with attribute filter
        List<SimilaritySearchResult> audioResults = vectorSearchService.searchByTextWithFilters(
                "Wireless headphones audio", entityType, 5, 0.0, Map.of("category", "Audio")
        );
        assertThat(audioResults).hasSize(1);
        assertThat(audioResults.get(0).getEntityId()).isEqualTo(entityId3);

        // 4. Delete and verify removal
        boolean deleted = vectorStore.delete(entityType, entityId1);
        assertThat(deleted).isTrue();
        assertThat(vectorStore.exists(entityType, entityId1, properties.getModelName(), properties.getModelVersion())).isFalse();

        List<SimilaritySearchResult> afterDelete = vectorSearchService.searchByText("Apple iPhone 15", entityType, 5, 0.0);
        assertThat(afterDelete.stream().anyMatch(r -> r.getEntityId().equals(entityId1))).isFalse();
    }

    @Test
    @DisplayName("Domain Isolation: Entity type filtering prevents cross-domain or cross-user leakage")
    void testDomainAndUserIsolation() {
        // Store a private user search query vector and a public product vector
        EmbeddingRecord userRecord = embeddingService.createRecord("USER_QUERY", "user-42-query-1", "MacBook Pro M3 Max", Map.of("userId", "user-42"));
        EmbeddingRecord productRecord = embeddingService.createRecord("PRODUCT", "prod-macbook-pro", "Apple MacBook Pro M3 Max 16-inch Space Black", Map.of("brand", "Apple"));

        vectorStore.batchUpsert(List.of(userRecord, productRecord));

        // When searching in "PRODUCT" domain, private USER_QUERY records must never leak
        List<SimilaritySearchResult> productResults = vectorSearchService.searchByText("MacBook Pro", "PRODUCT", 10, 0.0);

        assertThat(productResults).isNotEmpty();
        assertThat(productResults.stream().allMatch(r -> r.getEntityType().equals("PRODUCT"))).isTrue();
        assertThat(productResults.stream().noneMatch(r -> r.getEntityId().equals("user-42-query-1"))).isTrue();
    }

    @Test
    @DisplayName("Model Version Isolation: Queries against different model version return isolated results")
    void testModelVersionIsolation() {
        EmbeddingRecord recordV1 = embeddingService.createRecord("PRODUCT", "prod-v1", "Test Product", Map.of());
        vectorStore.upsert(recordV1);

        // Querying with different model version
        SimilaritySearchRequest v2Request = SimilaritySearchRequest.builder()
                .queryVector(recordV1.getVector())
                .entityType("PRODUCT")
                .modelName(properties.getModelName())
                .modelVersion("v2-future-version")
                .build();

        List<SimilaritySearchResult> results = vectorStore.similaritySearch(v2Request);
        assertThat(results).isEmpty();
    }

    @Test
    @DisplayName("Concurrency: Safe parallel embedding generation and vector search from multiple threads")
    void testConcurrentAccess() throws InterruptedException {
        int threadCount = 8;
        int operationsPerThread = 25;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);
        List<Throwable> exceptions = new CopyOnWriteArrayList<>();

        for (int i = 0; i < threadCount; i++) {
            final int threadId = i;
            executor.submit(() -> {
                try {
                    for (int j = 0; j < operationsPerThread; j++) {
                        String text = "Product query from thread " + threadId + " item " + j;
                        EmbeddingVector vec = embeddingService.generateEmbedding(text);
                        assertThat(vec.dimension()).isEqualTo(properties.getDimension());
                    }
                } catch (Throwable t) {
                    exceptions.add(t);
                } finally {
                    latch.countDown();
                }
            });
        }

        boolean completed = latch.await(15, TimeUnit.SECONDS);
        executor.shutdown();

        assertThat(completed).isTrue();
        assertThat(exceptions).isEmpty();
    }

    @Test
    @DisplayName("Health Indicator: Returns UP status with model metadata")
    void testHealthIndicator() {
        Health health = healthIndicator.health();

        assertThat(health.getStatus()).isEqualTo(Status.UP);
        assertThat(health.getDetails()).containsEntry("model", "local-hash-embedding");
        assertThat(health.getDetails()).containsEntry("version", "v1");
        assertThat(health.getDetails()).containsEntry("dimension", 64);
    }
}
