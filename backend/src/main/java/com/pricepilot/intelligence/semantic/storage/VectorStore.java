package com.pricepilot.intelligence.semantic.storage;

import com.pricepilot.intelligence.semantic.model.EmbeddingRecord;
import com.pricepilot.intelligence.semantic.model.SimilaritySearchRequest;
import com.pricepilot.intelligence.semantic.model.SimilaritySearchResult;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * High-level storage abstraction for persisting, deleting, and querying semantic vector embeddings.
 */
public interface VectorStore {

    /**
     * Upserts an embedding record. If an embedding for the entity, model, and version exists, it is updated.
     */
    void upsert(EmbeddingRecord record);

    /**
     * Batch upserts multiple embedding records.
     */
    void batchUpsert(List<EmbeddingRecord> records);

    /**
     * Executes similarity search using cosine distance/similarity against stored embeddings.
     */
    List<SimilaritySearchResult> similaritySearch(SimilaritySearchRequest request);

    /**
     * Deletes embedding records for an entity by entity type and entity ID.
     *
     * @return true if an embedding existed and was deleted, false otherwise
     */
    boolean delete(String entityType, String entityId);

    /**
     * Batch deletes embedding records for multiple entity IDs of a given entity type.
     *
     * @return number of deleted records
     */
    int batchDelete(String entityType, Collection<String> entityIds);

    /**
     * Retrieves an embedding record for an entity by model name and version.
     */
    Optional<EmbeddingRecord> get(String entityType, String entityId, String modelName, String modelVersion);

    /**
     * Checks if an embedding exists for the given entity, model name, and model version.
     */
    boolean exists(String entityType, String entityId, String modelName, String modelVersion);

    /**
     * Counts the total number of stored vectors for a specific model and version.
     */
    long countByModel(String modelName, String modelVersion);

    /**
     * Deletes all embeddings belonging to a specific model and version.
     */
    void deleteAllByModel(String modelName, String modelVersion);
}
