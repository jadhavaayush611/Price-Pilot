package com.pricepilot.intelligence.semantic.service;

import com.pricepilot.intelligence.semantic.model.EmbeddingMetadata;
import com.pricepilot.intelligence.semantic.model.EmbeddingRecord;
import com.pricepilot.intelligence.semantic.model.EmbeddingVector;
import com.pricepilot.intelligence.semantic.model.ProviderHealth;

import java.util.List;
import java.util.Map;

/**
 * Service contract for generating dense embeddings and creating structured domain records.
 */
public interface EmbeddingService {

    /**
     * Generates a normalized dense vector for single text input.
     */
    EmbeddingVector generateEmbedding(String text);

    /**
     * Generates normalized dense vectors for a batch of text inputs.
     */
    List<EmbeddingVector> generateBatchEmbeddings(List<String> texts);

    /**
     * Creates an EmbeddingRecord containing generated vector and metadata for a specific entity.
     */
    EmbeddingRecord createRecord(String entityType, String entityId, String text, Map<String, String> attributes);

    /**
     * Creates a batch of EmbeddingRecords.
     */
    List<EmbeddingRecord> createBatchRecords(
            String entityType,
            Map<String, String> entityIdToText,
            Map<String, Map<String, String>> attributesByEntityId
    );

    /**
     * Returns provider health status.
     */
    ProviderHealth getHealth();

    /**
     * Returns active model dimensionality.
     */
    int getDimension();

    /**
     * Returns active model name.
     */
    String getModelName();

    /**
     * Returns active model version.
     */
    String getModelVersion();
}
