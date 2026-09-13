package com.pricepilot.intelligence.semantic.provider;

import com.pricepilot.intelligence.semantic.model.EmbeddingVector;
import com.pricepilot.intelligence.semantic.model.ProviderHealth;

import java.util.List;

/**
 * Interface contract for embedding providers that generate vector embeddings from raw text.
 * Implementations can be local deterministic models, on-device neural networks, or external service adapters.
 */
public interface EmbeddingProvider {

    /**
     * Generates a dense embedding vector for a single text input.
     *
     * @param text Raw untrusted text input to be embedded
     * @return Normalized dense EmbeddingVector
     */
    EmbeddingVector embed(String text);

    /**
     * Generates dense embedding vectors for a batch of text inputs.
     *
     * @param texts List of raw untrusted texts to be embedded
     * @return List of normalized dense EmbeddingVectors in matching order
     */
    List<EmbeddingVector> embedBatch(List<String> texts);

    /**
     * Returns the dimensionality of vectors produced by this provider.
     */
    int getDimension();

    /**
     * Returns the model identifier name.
     */
    String getModelName();

    /**
     * Returns the model version identifier.
     */
    String getModelVersion();

    /**
     * Checks health and availability of this provider.
     */
    ProviderHealth checkHealth();

    /**
     * Returns true if the provider is currently available to process requests.
     */
    boolean isAvailable();
}
