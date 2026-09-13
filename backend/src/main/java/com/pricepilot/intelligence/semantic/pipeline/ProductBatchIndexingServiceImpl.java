package com.pricepilot.intelligence.semantic.pipeline;

import com.pricepilot.intelligence.semantic.config.SemanticIntelligenceProperties;
import com.pricepilot.product.ProductEntity;
import com.pricepilot.product.ProductRepository;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Objects;

/**
 * Production implementation of ProductBatchIndexingService.
 * Executes chunked catalog pagination to safely index large product inventories with bounded memory.
 */
@Service
public class ProductBatchIndexingServiceImpl implements ProductBatchIndexingService {

    private static final Logger log = LoggerFactory.getLogger(ProductBatchIndexingServiceImpl.class);

    private final ProductRepository productRepository;
    private final ProductEmbeddingService productEmbeddingService;
    private final SemanticIntelligenceProperties properties;

    private final Counter batchExecutionCounter;
    private final DistributionSummary batchChunkSummary;

    public ProductBatchIndexingServiceImpl(
            ProductRepository productRepository,
            ProductEmbeddingService productEmbeddingService,
            SemanticIntelligenceProperties properties,
            MeterRegistry meterRegistry) {
        this.productRepository = Objects.requireNonNull(productRepository, "ProductRepository cannot be null");
        this.productEmbeddingService = Objects.requireNonNull(productEmbeddingService, "ProductEmbeddingService cannot be null");
        this.properties = Objects.requireNonNull(properties, "SemanticIntelligenceProperties cannot be null");

        this.batchExecutionCounter = Counter.builder("pricepilot.semantic.product.batch.executions")
                .description("Total number of product batch indexing executions")
                .register(meterRegistry);
        this.batchChunkSummary = DistributionSummary.builder("pricepilot.semantic.product.batch.chunk.size")
                .description("Distribution of product batch indexing chunk sizes")
                .register(meterRegistry);
    }

    @Override
    public BatchIndexingResult indexAllActiveProducts(int chunkSize) {
        int effectiveChunkSize = chunkSize > 0 ? chunkSize : 50;
        batchExecutionCounter.increment();
        batchChunkSummary.record(effectiveChunkSize);

        if (!properties.isEnabled()) {
            log.warn("Batch indexing requested while semantic intelligence is disabled");
            return new BatchIndexingResult(0, 0, 0, 0, 0);
        }

        long start = System.currentTimeMillis();
        int totalProcessed = 0;
        int totalIndexed = 0;
        int totalSkipped = 0;
        int totalFailed = 0;

        int page = 0;
        Page<ProductEntity> productPage;

        log.info("Starting catalog-wide product embedding indexing with chunk size {}", effectiveChunkSize);

        do {
            try {
                PageRequest pageRequest = PageRequest.of(page, effectiveChunkSize, Sort.by("id"));
                productPage = productRepository.findByArchivedFalse(pageRequest);

                List<ProductEntity> content = productPage.getContent();
                if (!content.isEmpty()) {
                    totalProcessed += content.size();
                    try {
                        var records = productEmbeddingService.indexProductBatch(content);
                        totalIndexed += records.size();
                        totalSkipped += (content.size() - records.size());
                    } catch (Exception e) {
                        log.error("Failed to index batch on page {}", page, e);
                        // Isolate errors per single product in this failed chunk
                        for (ProductEntity p : content) {
                            try {
                                var r = productEmbeddingService.indexProduct(p);
                                if (r != null) totalIndexed++; else totalSkipped++;
                            } catch (Exception ex) {
                                totalFailed++;
                                log.warn("Failed single fallback indexing for product id={}", p.getId(), ex);
                            }
                        }
                    }
                }
            } catch (Exception e) {
                log.error("Failed to retrieve product page {} during batch indexing", page, e);
                break;
            }
            page++;
        } while (productPage.hasNext());

        long duration = System.currentTimeMillis() - start;
        log.info("Catalog indexing complete: processed={}, indexed={}, skipped={}, failed={}, duration={}ms",
                totalProcessed, totalIndexed, totalSkipped, totalFailed, duration);

        return new BatchIndexingResult(totalProcessed, totalIndexed, totalSkipped, totalFailed, duration);
    }

    @Override
    public BatchIndexingResult indexProductsPage(int page, int pageSize) {
        int effectivePageSize = pageSize > 0 ? pageSize : 50;
        int effectivePage = Math.max(0, page);

        long start = System.currentTimeMillis();
        PageRequest pageRequest = PageRequest.of(effectivePage, effectivePageSize, Sort.by("id"));
        Page<ProductEntity> productPage = productRepository.findByArchivedFalse(pageRequest);

        List<ProductEntity> content = productPage.getContent();
        int totalProcessed = content.size();
        int totalIndexed = 0;
        int totalSkipped = 0;
        int totalFailed = 0;

        if (!content.isEmpty()) {
            try {
                var records = productEmbeddingService.indexProductBatch(content);
                totalIndexed = records.size();
                totalSkipped = totalProcessed - totalIndexed;
            } catch (Exception e) {
                log.error("Failed to index page {}", effectivePage, e);
                for (ProductEntity p : content) {
                    try {
                        var r = productEmbeddingService.indexProduct(p);
                        if (r != null) totalIndexed++; else totalSkipped++;
                    } catch (Exception ex) {
                        totalFailed++;
                    }
                }
            }
        }

        long duration = System.currentTimeMillis() - start;
        return new BatchIndexingResult(totalProcessed, totalIndexed, totalSkipped, totalFailed, duration);
    }
}
