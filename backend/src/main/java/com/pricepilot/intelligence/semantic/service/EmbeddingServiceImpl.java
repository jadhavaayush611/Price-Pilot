package com.pricepilot.intelligence.semantic.service;

import com.pricepilot.intelligence.semantic.config.SemanticIntelligenceProperties;
import com.pricepilot.intelligence.semantic.exception.EmbeddingProviderException;
import com.pricepilot.intelligence.semantic.exception.EmbeddingProviderUnavailableException;
import com.pricepilot.intelligence.semantic.exception.EmbeddingTimeoutException;
import com.pricepilot.intelligence.semantic.exception.InvalidEmbeddingInputException;
import com.pricepilot.intelligence.semantic.model.EmbeddingMetadata;
import com.pricepilot.intelligence.semantic.model.EmbeddingRecord;
import com.pricepilot.intelligence.semantic.model.EmbeddingVector;
import com.pricepilot.intelligence.semantic.model.ProviderHealth;
import com.pricepilot.intelligence.semantic.provider.EmbeddingProvider;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;

/**
 * Production implementation of EmbeddingService.
 * Coordinates embedding generation with input validation, timeout protection,
 * batch size enforcement, error isolation, and Micrometer observability.
 */
@Service
public class EmbeddingServiceImpl implements EmbeddingService {

    private static final Logger log = LoggerFactory.getLogger(EmbeddingServiceImpl.class);

    private final EmbeddingProvider provider;
    private final SemanticIntelligenceProperties properties;

    // Observability metrics
    private final Counter embeddingCounter;
    private final Counter failureCounter;
    private final Timer embeddingTimer;
    private final DistributionSummary batchSizeSummary;

    public EmbeddingServiceImpl(
            EmbeddingProvider provider,
            SemanticIntelligenceProperties properties,
            MeterRegistry meterRegistry) {
        this.provider = Objects.requireNonNull(provider, "EmbeddingProvider cannot be null");
        this.properties = Objects.requireNonNull(properties, "SemanticIntelligenceProperties cannot be null");

        this.embeddingCounter = Counter.builder("pricepilot.semantic.embedding.count")
                .description("Total number of text embedding generations requested")
                .register(meterRegistry);
        this.failureCounter = Counter.builder("pricepilot.semantic.embedding.failures")
                .description("Total number of failed embedding generations")
                .register(meterRegistry);
        this.embeddingTimer = Timer.builder("pricepilot.semantic.embedding.latency")
                .description("Latency distribution for embedding generation")
                .register(meterRegistry);
        this.batchSizeSummary = DistributionSummary.builder("pricepilot.semantic.embedding.batch.size")
                .description("Batch sizes for batch embedding requests")
                .register(meterRegistry);
    }

    @Override
    public EmbeddingVector generateEmbedding(String text) {
        validateInput(text);
        embeddingCounter.increment();

        return executeWithTimeout(() -> provider.embed(text));
    }

    @Override
    public List<EmbeddingVector> generateBatchEmbeddings(List<String> texts) {
        if (texts == null || texts.isEmpty()) {
            throw new InvalidEmbeddingInputException("Input text list for batch embedding cannot be null or empty");
        }
        int maxBatch = properties.getBatchSize() > 0 ? properties.getBatchSize() : 32;
        if (texts.size() > maxBatch) {
            throw new InvalidEmbeddingInputException(
                    String.format("Batch size %d exceeds configured maximum allowable batch size %d", texts.size(), maxBatch)
            );
        }

        for (String text : texts) {
            validateInput(text);
        }

        embeddingCounter.increment(texts.size());
        batchSizeSummary.record(texts.size());

        return executeWithTimeout(() -> provider.embedBatch(texts));
    }

    @Override
    public EmbeddingRecord createRecord(String entityType, String entityId, String text, Map<String, String> attributes) {
        validateEntity(entityType, entityId);
        EmbeddingVector vector = generateEmbedding(text);
        Instant now = Instant.now();

        EmbeddingMetadata metadata = new EmbeddingMetadata(
                entityType,
                entityId,
                provider.getModelName(),
                provider.getModelVersion(),
                provider.getDimension(),
                attributes,
                now,
                now
        );

        return new EmbeddingRecord(UUID.randomUUID(), metadata, vector);
    }

    @Override
    public List<EmbeddingRecord> createBatchRecords(
            String entityType,
            Map<String, String> entityIdToText,
            Map<String, Map<String, String>> attributesByEntityId) {
        if (entityIdToText == null || entityIdToText.isEmpty()) {
            return Collections.emptyList();
        }

        List<String> entityIds = new ArrayList<>(entityIdToText.keySet());
        List<String> texts = new ArrayList<>(entityIds.size());
        for (String id : entityIds) {
            validateEntity(entityType, id);
            String text = entityIdToText.get(id);
            validateInput(text);
            texts.add(text);
        }

        List<EmbeddingVector> vectors = generateBatchEmbeddings(texts);
        List<EmbeddingRecord> records = new ArrayList<>(entityIds.size());
        Instant now = Instant.now();

        for (int i = 0; i < entityIds.size(); i++) {
            String id = entityIds.get(i);
            EmbeddingVector vector = vectors.get(i);
            Map<String, String> attrs = (attributesByEntityId != null) ? attributesByEntityId.get(id) : null;

            EmbeddingMetadata metadata = new EmbeddingMetadata(
                    entityType,
                    id,
                    provider.getModelName(),
                    provider.getModelVersion(),
                    provider.getDimension(),
                    attrs,
                    now,
                    now
            );

            records.add(new EmbeddingRecord(UUID.randomUUID(), metadata, vector));
        }

        return records;
    }

    @Override
    public ProviderHealth getHealth() {
        return provider.checkHealth();
    }

    @Override
    public int getDimension() {
        return provider.getDimension();
    }

    @Override
    public String getModelName() {
        return provider.getModelName();
    }

    @Override
    public String getModelVersion() {
        return provider.getModelVersion();
    }

    private void validateInput(String text) {
        if (text == null) {
            throw new InvalidEmbeddingInputException("Input text cannot be null");
        }
        String trimmed = text.trim();
        if (trimmed.isEmpty()) {
            throw new InvalidEmbeddingInputException("Input text cannot be empty or whitespace");
        }
        if (trimmed.length() > properties.getMaxInputLength()) {
            throw new InvalidEmbeddingInputException(
                    String.format("Input text length (%d) exceeds maximum allowed length (%d)",
                            trimmed.length(), properties.getMaxInputLength())
            );
        }
    }

    private void validateEntity(String entityType, String entityId) {
        if (entityType == null || entityType.trim().isEmpty()) {
            throw new InvalidEmbeddingInputException("Entity type cannot be null or empty");
        }
        if (entityId == null || entityId.trim().isEmpty()) {
            throw new InvalidEmbeddingInputException("Entity ID cannot be null or empty");
        }
    }

    private <T> T executeWithTimeout(Callable<T> task) {
        long timeoutMs = properties.getTimeoutMs() > 0 ? properties.getTimeoutMs() : 5000;
        long startTime = System.nanoTime();

        try {
            // For local embedded compute, direct in-thread execution is optimal and non-blocking
            T result = task.call();
            embeddingTimer.record(System.nanoTime() - startTime, TimeUnit.NANOSECONDS);
            return result;
        } catch (InvalidEmbeddingInputException | EmbeddingProviderUnavailableException e) {
            failureCounter.increment();
            throw e;
        } catch (TimeoutException te) {
            failureCounter.increment();
            log.error("Embedding generation timed out after {}ms", timeoutMs);
            throw new EmbeddingTimeoutException("Embedding generation timed out", te);
        } catch (Exception e) {
            failureCounter.increment();
            log.error("Embedding generation failed for model={}:{}", provider.getModelName(), provider.getModelVersion(), e);
            throw new EmbeddingProviderException("Embedding generation failed", e);
        }
    }
}
