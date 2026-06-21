package com.pricepilot.ai;

import com.pricepilot.product.dto.ProductSearchResultDTO;
import java.util.List;
import java.util.UUID;

/**
 * Interface for semantic vector search using pgvector or a vector database.
 * Will allow searching products based on high-dimensional text embeddings.
 */
public interface VectorSearchService {

    /**
     * Performs semantic search using query text embeddings.
     *
     * @param queryText The natural language search query.
     * @param limit The maximum number of results to return.
     * @return A list of semantically matching products.
     */
    List<ProductSearchResultDTO> semanticSearch(String queryText, int limit);

    /**
     * Generates embeddings for a product to sync with the vector database.
     *
     * @param productId The ID of the product.
     * @return The raw vector representation.
     */
    float[] generateProductEmbeddings(UUID productId);
}
