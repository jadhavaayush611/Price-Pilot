package com.pricepilot.intelligence.semantic.storage;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pricepilot.intelligence.semantic.exception.IncompatibleVectorDimensionException;
import com.pricepilot.intelligence.semantic.exception.VectorStoreException;
import com.pricepilot.intelligence.semantic.model.*;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.ZoneOffset;
import java.util.*;
import java.util.stream.Collectors;

/**
 * PostgreSQL / Relational Database implementation of the VectorStore abstraction.
 * Stores dense vector embeddings with metadata in the semantic_embeddings table
 * and performs in-memory vector similarity ranking with deterministic tie-breaking.
 */
@Component
public class JpaVectorStore implements VectorStore {

    private static final Logger log = LoggerFactory.getLogger(JpaVectorStore.class);
    private static final TypeReference<Map<String, String>> MAP_TYPE = new TypeReference<>() {};

    private final SemanticEmbeddingRepository repository;
    private final ObjectMapper objectMapper;

    // Micrometer metrics
    private final Counter upsertCounter;
    private final Counter searchCounter;
    private final Counter searchFailureCounter;
    private final Timer searchTimer;

    @org.springframework.beans.factory.annotation.Autowired
    public JpaVectorStore(
            SemanticEmbeddingRepository repository,
            @org.springframework.beans.factory.annotation.Autowired(required = false) ObjectMapper objectMapper,
            MeterRegistry meterRegistry) {
        this.repository = Objects.requireNonNull(repository, "repository cannot be null");
        this.objectMapper = objectMapper != null ? objectMapper : new ObjectMapper();

        this.upsertCounter = Counter.builder("pricepilot.semantic.vector.upsert.count")
                .description("Total number of semantic vector upsert operations")
                .register(meterRegistry);
        this.searchCounter = Counter.builder("pricepilot.semantic.vector.search.count")
                .description("Total number of semantic vector searches executed")
                .register(meterRegistry);
        this.searchFailureCounter = Counter.builder("pricepilot.semantic.vector.search.failures")
                .description("Total number of failed vector searches")
                .register(meterRegistry);
        this.searchTimer = Timer.builder("pricepilot.semantic.vector.search.latency")
                .description("Latency of semantic vector search queries")
                .register(meterRegistry);
    }

    @Override
    @Transactional
    public void upsert(EmbeddingRecord record) {
        Objects.requireNonNull(record, "EmbeddingRecord cannot be null");
        try {
            saveOrUpdateEntity(record);
            upsertCounter.increment();
        } catch (Exception e) {
            log.error("Failed to upsert vector embedding for entity: type={}, id={}",
                    record.getEntityType(), record.getEntityId(), e);
            throw new VectorStoreException("Failed to upsert vector embedding", e);
        }
    }

    @Override
    @Transactional
    public void batchUpsert(List<EmbeddingRecord> records) {
        if (records == null || records.isEmpty()) {
            return;
        }
        try {
            for (EmbeddingRecord record : records) {
                saveOrUpdateEntity(record);
            }
            upsertCounter.increment(records.size());
        } catch (Exception e) {
            log.error("Failed to batch upsert {} vector embeddings", records.size(), e);
            throw new VectorStoreException("Failed to batch upsert vector embeddings", e);
        }
    }

    @Override
    @Transactional(readOnly = true)
    public List<SimilaritySearchResult> similaritySearch(SimilaritySearchRequest request) {
        Objects.requireNonNull(request, "SimilaritySearchRequest cannot be null");
        searchCounter.increment();

        return searchTimer.record(() -> {
            try {
                EmbeddingVector queryVector = request.getQueryVector();
                String entityType = request.getEntityType();
                String modelName = request.getModelName();
                String modelVersion = request.getModelVersion();

                List<SemanticEmbeddingEntity> entities;
                if (entityType != null && !entityType.isBlank()) {
                    entities = repository.findByEntityTypeAndModelNameAndModelVersion(
                            entityType.trim().toUpperCase(), modelName, modelVersion
                    );
                } else {
                    entities = repository.findByModelNameAndModelVersion(modelName, modelVersion);
                }

                if (entities.isEmpty()) {
                    return Collections.emptyList();
                }

                Map<String, String> requiredAttributes = request.getFilterAttributes();
                List<SimilaritySearchResult> candidates = new ArrayList<>();

                for (SemanticEmbeddingEntity entity : entities) {
                    if (entity.getDimension() != queryVector.dimension()) {
                        throw new IncompatibleVectorDimensionException(queryVector.dimension(), entity.getDimension());
                    }

                    Map<String, String> attributes = deserializeAttributes(entity.getMetadataJson());
                    if (!matchesFilter(attributes, requiredAttributes)) {
                        continue;
                    }

                    EmbeddingVector candidateVector = EmbeddingVector.parse(entity.getVectorData());
                    double score = queryVector.cosineSimilarity(candidateVector);

                    if (score >= request.getMinScore()) {
                        Instant created = entity.getCreatedAt() != null ? entity.getCreatedAt().toInstant(ZoneOffset.UTC) : Instant.now();
                        Instant updated = entity.getUpdatedAt() != null ? entity.getUpdatedAt().toInstant(ZoneOffset.UTC) : created;

                        EmbeddingMetadata metadata = new EmbeddingMetadata(
                                entity.getEntityType(),
                                entity.getEntityId(),
                                entity.getModelName(),
                                entity.getModelVersion(),
                                entity.getDimension(),
                                attributes,
                                created,
                                updated
                        );

                        candidates.add(new SimilaritySearchResult(
                                entity.getEntityType(),
                                entity.getEntityId(),
                                score,
                                metadata,
                                candidateVector
                        ));
                    }
                }

                // Sort descending by score, tie-break by entityId ascending
                candidates.sort(Comparator.naturalOrder());

                if (candidates.size() > request.getTopK()) {
                    return candidates.subList(0, request.getTopK());
                }
                return candidates;
            } catch (IncompatibleVectorDimensionException ivde) {
                searchFailureCounter.increment();
                throw ivde;
            } catch (Exception e) {
                searchFailureCounter.increment();
                log.error("Similarity search execution failed for model={}:{}", request.getModelName(), request.getModelVersion(), e);
                throw new VectorStoreException("Similarity search execution failed", e);
            }
        });
    }

    @Override
    @Transactional
    public boolean delete(String entityType, String entityId) {
        Objects.requireNonNull(entityType, "entityType cannot be null");
        Objects.requireNonNull(entityId, "entityId cannot be null");
        try {
            int deleted = repository.deleteByEntityTypeAndEntityId(entityType.trim().toUpperCase(), entityId.trim());
            return deleted > 0;
        } catch (Exception e) {
            log.error("Failed to delete vector embedding for type={}, id={}", entityType, entityId, e);
            throw new VectorStoreException("Failed to delete vector embedding", e);
        }
    }

    @Override
    @Transactional
    public int batchDelete(String entityType, Collection<String> entityIds) {
        Objects.requireNonNull(entityType, "entityType cannot be null");
        if (entityIds == null || entityIds.isEmpty()) {
            return 0;
        }
        try {
            return repository.deleteByEntityTypeAndEntityIdIn(entityType.trim().toUpperCase(), entityIds);
        } catch (Exception e) {
            log.error("Failed to batch delete vector embeddings for type={}, idsCount={}", entityType, entityIds.size(), e);
            throw new VectorStoreException("Failed to batch delete vector embeddings", e);
        }
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<EmbeddingRecord> get(String entityType, String entityId, String modelName, String modelVersion) {
        Objects.requireNonNull(entityType, "entityType cannot be null");
        Objects.requireNonNull(entityId, "entityId cannot be null");
        Objects.requireNonNull(modelName, "modelName cannot be null");
        Objects.requireNonNull(modelVersion, "modelVersion cannot be null");

        return repository.findByEntityTypeAndEntityIdAndModelNameAndModelVersion(
                entityType.trim().toUpperCase(), entityId.trim(), modelName.trim(), modelVersion.trim()
        ).map(this::mapEntityToRecord);
    }

    @Override
    @Transactional(readOnly = true)
    public boolean exists(String entityType, String entityId, String modelName, String modelVersion) {
        Objects.requireNonNull(entityType, "entityType cannot be null");
        Objects.requireNonNull(entityId, "entityId cannot be null");
        Objects.requireNonNull(modelName, "modelName cannot be null");
        Objects.requireNonNull(modelVersion, "modelVersion cannot be null");

        return repository.existsByEntityTypeAndEntityIdAndModelNameAndModelVersion(
                entityType.trim().toUpperCase(), entityId.trim(), modelName.trim(), modelVersion.trim()
        );
    }

    @Override
    @Transactional(readOnly = true)
    public long countByModel(String modelName, String modelVersion) {
        Objects.requireNonNull(modelName, "modelName cannot be null");
        Objects.requireNonNull(modelVersion, "modelVersion cannot be null");
        return repository.countByModelNameAndModelVersion(modelName.trim(), modelVersion.trim());
    }

    @Override
    @Transactional
    public void deleteAllByModel(String modelName, String modelVersion) {
        Objects.requireNonNull(modelName, "modelName cannot be null");
        Objects.requireNonNull(modelVersion, "modelVersion cannot be null");
        try {
            repository.deleteByModelNameAndModelVersion(modelName.trim(), modelVersion.trim());
        } catch (Exception e) {
            log.error("Failed to delete embeddings for model={}:{}", modelName, modelVersion, e);
            throw new VectorStoreException("Failed to delete embeddings for model", e);
        }
    }

    private void saveOrUpdateEntity(EmbeddingRecord record) {
        Optional<SemanticEmbeddingEntity> existingOpt = repository.findByEntityTypeAndEntityIdAndModelNameAndModelVersion(
                record.getEntityType(),
                record.getEntityId(),
                record.getModelName(),
                record.getModelVersion()
        );

        String serializedVector = record.getVector().serialize();
        String serializedMetadata = serializeAttributes(record.getMetadata().getAttributes());

        if (existingOpt.isPresent()) {
            SemanticEmbeddingEntity existing = existingOpt.get();
            existing.setDimension(record.getDimension());
            existing.setVectorData(serializedVector);
            existing.setMetadataJson(serializedMetadata);
            repository.save(existing);
        } else {
            SemanticEmbeddingEntity newEntity = SemanticEmbeddingEntity.builder()
                    .entityType(record.getEntityType())
                    .entityId(record.getEntityId())
                    .modelName(record.getModelName())
                    .modelVersion(record.getModelVersion())
                    .dimension(record.getDimension())
                    .vectorData(serializedVector)
                    .metadataJson(serializedMetadata)
                    .build();
            repository.save(newEntity);
        }
    }

    private EmbeddingRecord mapEntityToRecord(SemanticEmbeddingEntity entity) {
        EmbeddingVector vector = EmbeddingVector.parse(entity.getVectorData());
        Map<String, String> attributes = deserializeAttributes(entity.getMetadataJson());
        Instant created = entity.getCreatedAt() != null ? entity.getCreatedAt().toInstant(ZoneOffset.UTC) : Instant.now();
        Instant updated = entity.getUpdatedAt() != null ? entity.getUpdatedAt().toInstant(ZoneOffset.UTC) : created;

        EmbeddingMetadata metadata = new EmbeddingMetadata(
                entity.getEntityType(),
                entity.getEntityId(),
                entity.getModelName(),
                entity.getModelVersion(),
                entity.getDimension(),
                attributes,
                created,
                updated
        );

        return new EmbeddingRecord(entity.getId(), metadata, vector);
    }

    private String serializeAttributes(Map<String, String> attributes) {
        if (attributes == null || attributes.isEmpty()) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(attributes);
        } catch (Exception e) {
            log.warn("Failed to serialize embedding metadata attributes to JSON", e);
            return null;
        }
    }

    private Map<String, String> deserializeAttributes(String json) {
        if (json == null || json.isBlank()) {
            return Collections.emptyMap();
        }
        try {
            return objectMapper.readValue(json, MAP_TYPE);
        } catch (Exception e) {
            log.warn("Failed to deserialize embedding metadata JSON", e);
            return Collections.emptyMap();
        }
    }

    private boolean matchesFilter(Map<String, String> entityAttributes, Map<String, String> requiredFilter) {
        if (requiredFilter == null || requiredFilter.isEmpty()) {
            return true;
        }
        for (Map.Entry<String, String> filterEntry : requiredFilter.entrySet()) {
            String value = entityAttributes.get(filterEntry.getKey());
            if (value == null || !value.equals(filterEntry.getValue())) {
                return false;
            }
        }
        return true;
    }
}
