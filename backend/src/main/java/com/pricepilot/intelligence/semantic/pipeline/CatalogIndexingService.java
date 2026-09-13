package com.pricepilot.intelligence.semantic.pipeline;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.Objects;

/**
 * Administrative and operational service for triggering catalog embedding re-synchronization.
 * Does NOT run automatically on every application startup.
 */
@Service
public class CatalogIndexingService {

    private static final Logger log = LoggerFactory.getLogger(CatalogIndexingService.class);

    private final ProductBatchIndexingService batchIndexingService;

    public CatalogIndexingService(ProductBatchIndexingService batchIndexingService) {
        this.batchIndexingService = Objects.requireNonNull(batchIndexingService, "ProductBatchIndexingService cannot be null");
    }

    /**
     * Executes a full re-indexing of all active products in the catalog.
     *
     * @param chunkSize Batch size per page chunk (default 50)
     * @return BatchIndexingResult summary
     */
    public BatchIndexingResult reindexCatalog(int chunkSize) {
        log.info("Administrative catalog re-indexing initiated with chunk size {}", chunkSize);
        return batchIndexingService.indexAllActiveProducts(chunkSize);
    }
}
