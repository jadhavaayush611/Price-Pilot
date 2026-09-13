package com.pricepilot.intelligence.semantic.pipeline;

/**
 * Service contract for batch processing and initial catalog indexing.
 * Provides chunked pagination to prevent out-of-memory errors on large product catalogs.
 */
public interface ProductBatchIndexingService {

    /**
     * Indexes all active non-archived products in the catalog using chunked pagination.
     *
     * @param chunkSize Number of products to process per chunk (e.g. 50 or 100)
     * @return BatchIndexingResult summary
     */
    BatchIndexingResult indexAllActiveProducts(int chunkSize);

    /**
     * Indexes a specific page chunk of active products.
     *
     * @param page Zero-based page index
     * @param pageSize Number of products per page
     * @return BatchIndexingResult summary for this page
     */
    BatchIndexingResult indexProductsPage(int page, int pageSize);
}
