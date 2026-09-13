package com.pricepilot.intelligence.semantic.pipeline;

import com.pricepilot.intelligence.semantic.model.EmbeddingRecord;
import com.pricepilot.product.ProductEntity;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Service contract for generating and persisting product semantic vector embeddings.
 */
public interface ProductEmbeddingService {

    /**
     * Indexes a single ProductEntity into the vector store.
     * Idempotently creates or updates the embedding.
     */
    EmbeddingRecord indexProduct(ProductEntity product);

    /**
     * Indexes a product by its ID by fetching it from the database.
     */
    EmbeddingRecord indexProductById(UUID productId);

    /**
     * Indexes a batch of ProductEntity instances into the vector store.
     * Generates embeddings in batch and persists them with error isolation.
     */
    List<EmbeddingRecord> indexProductBatch(List<ProductEntity> products);

    /**
     * Removes a product's embedding from the vector store upon deletion/archival.
     */
    boolean deleteProductEmbedding(UUID productId);

    /**
     * Checks if a product embedding exists for the current active model and version.
     */
    boolean isProductIndexed(UUID productId);

    /**
     * Retrieves an existing product embedding record if available.
     */
    Optional<EmbeddingRecord> getProductEmbedding(UUID productId);
}
