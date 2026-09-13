package com.pricepilot.intelligence.semantic.pipeline;

import com.pricepilot.exception.ResourceNotFoundException;
import com.pricepilot.intelligence.semantic.config.SemanticIntelligenceProperties;
import com.pricepilot.intelligence.semantic.contract.CanonicalProductText;
import com.pricepilot.intelligence.semantic.contract.CanonicalProductTextBuilder;
import com.pricepilot.intelligence.semantic.exception.SemanticIntelligenceException;
import com.pricepilot.intelligence.semantic.model.EmbeddingRecord;
import com.pricepilot.intelligence.semantic.service.EmbeddingService;
import com.pricepilot.intelligence.semantic.storage.VectorStore;
import com.pricepilot.product.ProductEntity;
import com.pricepilot.product.ProductRepository;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * Production implementation of the ProductEmbeddingService.
 * Coordinates product text canonicalization, vector generation, and relational vector persistence.
 */
@Service
public class ProductEmbeddingServiceImpl implements ProductEmbeddingService {

    private static final Logger log = LoggerFactory.getLogger(ProductEmbeddingServiceImpl.class);
    private static final String ENTITY_TYPE_PRODUCT = "PRODUCT";

    private final CanonicalProductTextBuilder textBuilder;
    private final EmbeddingService embeddingService;
    private final VectorStore vectorStore;
    private final ProductRepository productRepository;
    private final SemanticIntelligenceProperties properties;

    // Observability Metrics
    private final Counter indexedCounter;
    private final Counter skippedCounter;
    private final Counter failureCounter;
    private final Counter deletionCounter;
    private final Timer indexingTimer;

    public ProductEmbeddingServiceImpl(
            CanonicalProductTextBuilder textBuilder,
            EmbeddingService embeddingService,
            VectorStore vectorStore,
            ProductRepository productRepository,
            SemanticIntelligenceProperties properties,
            MeterRegistry meterRegistry) {
        this.textBuilder = Objects.requireNonNull(textBuilder, "CanonicalProductTextBuilder cannot be null");
        this.embeddingService = Objects.requireNonNull(embeddingService, "EmbeddingService cannot be null");
        this.vectorStore = Objects.requireNonNull(vectorStore, "VectorStore cannot be null");
        this.productRepository = Objects.requireNonNull(productRepository, "ProductRepository cannot be null");
        this.properties = Objects.requireNonNull(properties, "SemanticIntelligenceProperties cannot be null");

        this.indexedCounter = Counter.builder("pricepilot.semantic.product.indexed.count")
                .description("Total number of products successfully indexed into vector store")
                .register(meterRegistry);
        this.skippedCounter = Counter.builder("pricepilot.semantic.product.skipped.count")
                .description("Total number of product indexing operations skipped (e.g. disabled or non-semantic update)")
                .register(meterRegistry);
        this.failureCounter = Counter.builder("pricepilot.semantic.product.failures")
                .description("Total number of product indexing failures")
                .register(meterRegistry);
        this.deletionCounter = Counter.builder("pricepilot.semantic.product.deletions")
                .description("Total number of product embeddings deleted")
                .register(meterRegistry);
        this.indexingTimer = Timer.builder("pricepilot.semantic.product.indexing.latency")
                .description("Latency of product embedding indexing operations")
                .register(meterRegistry);
    }

    @Override
    @Transactional
    public EmbeddingRecord indexProduct(ProductEntity product) {
        Objects.requireNonNull(product, "ProductEntity cannot be null");
        if (!properties.isEnabled()) {
            skippedCounter.increment();
            return null;
        }

        if (product.isArchived()) {
            log.debug("Skipping indexing for archived product id={}", product.getId());
            deleteProductEmbedding(product.getId());
            skippedCounter.increment();
            return null;
        }

        long startTime = System.nanoTime();
        try {
            CanonicalProductText canonical = textBuilder.build(product);
            String productIdStr = product.getId().toString();

            EmbeddingRecord record = embeddingService.createRecord(
                    ENTITY_TYPE_PRODUCT,
                    productIdStr,
                    canonical.getCanonicalText(),
                    canonical.getAttributes()
            );

            vectorStore.upsert(record);
            indexedCounter.increment();
            indexingTimer.record(System.nanoTime() - startTime, TimeUnit.NANOSECONDS);
            return record;
        } catch (Exception e) {
            failureCounter.increment();
            log.error("Failed to index product id={}", product.getId(), e);
            throw new SemanticIntelligenceException("Failed to index product: " + product.getId(), e);
        }
    }

    @Override
    @Transactional
    public EmbeddingRecord indexProductById(UUID productId) {
        Objects.requireNonNull(productId, "productId cannot be null");
        ProductEntity product = productRepository.findById(productId)
                .orElseThrow(() -> new ResourceNotFoundException("Product not found with id: " + productId));
        return indexProduct(product);
    }

    @Override
    @Transactional
    public List<EmbeddingRecord> indexProductBatch(List<ProductEntity> products) {
        if (products == null || products.isEmpty()) {
            return Collections.emptyList();
        }
        if (!properties.isEnabled()) {
            skippedCounter.increment(products.size());
            return Collections.emptyList();
        }

        long startTime = System.nanoTime();
        try {
            // Filter out archived products and delete their embeddings
            List<ProductEntity> activeProducts = new ArrayList<>(products.size());
            List<String> archivedIdsToDelete = new ArrayList<>();

            for (ProductEntity p : products) {
                if (p == null || p.getId() == null) continue;
                if (p.isArchived()) {
                    archivedIdsToDelete.add(p.getId().toString());
                    skippedCounter.increment();
                } else {
                    activeProducts.add(p);
                }
            }

            if (!archivedIdsToDelete.isEmpty()) {
                vectorStore.batchDelete(ENTITY_TYPE_PRODUCT, archivedIdsToDelete);
                deletionCounter.increment(archivedIdsToDelete.size());
            }

            if (activeProducts.isEmpty()) {
                return Collections.emptyList();
            }

            Map<String, String> entityIdToText = new LinkedHashMap<>();
            Map<String, Map<String, String>> attributesByEntityId = new LinkedHashMap<>();

            for (ProductEntity product : activeProducts) {
                try {
                    CanonicalProductText canonical = textBuilder.build(product);
                    String idStr = product.getId().toString();
                    entityIdToText.put(idStr, canonical.getCanonicalText());
                    attributesByEntityId.put(idStr, canonical.getAttributes());
                } catch (Exception e) {
                    failureCounter.increment();
                    log.warn("Failed to canonicalize product id={}, skipping in batch", product.getId(), e);
                }
            }

            if (entityIdToText.isEmpty()) {
                return Collections.emptyList();
            }

            // Batch embedding generation via EmbeddingService (respects batch bounds)
            List<EmbeddingRecord> records = new ArrayList<>(entityIdToText.size());
            List<String> allIds = new ArrayList<>(entityIdToText.keySet());
            int batchSize = properties.getBatchSize() > 0 ? properties.getBatchSize() : 32;

            for (int i = 0; i < allIds.size(); i += batchSize) {
                int end = Math.min(i + batchSize, allIds.size());
                List<String> chunkIds = allIds.subList(i, end);

                Map<String, String> chunkTexts = new LinkedHashMap<>();
                Map<String, Map<String, String>> chunkAttrs = new LinkedHashMap<>();
                for (String id : chunkIds) {
                    chunkTexts.put(id, entityIdToText.get(id));
                    chunkAttrs.put(id, attributesByEntityId.get(id));
                }

                List<EmbeddingRecord> chunkRecords = embeddingService.createBatchRecords(
                        ENTITY_TYPE_PRODUCT, chunkTexts, chunkAttrs
                );
                vectorStore.batchUpsert(chunkRecords);
                records.addAll(chunkRecords);
            }

            indexedCounter.increment(records.size());
            indexingTimer.record(System.nanoTime() - startTime, TimeUnit.NANOSECONDS);
            return records;
        } catch (Exception e) {
            failureCounter.increment();
            log.error("Failed to batch index {} products", products.size(), e);
            throw new SemanticIntelligenceException("Failed to batch index products", e);
        }
    }

    @Override
    @Transactional
    public boolean deleteProductEmbedding(UUID productId) {
        if (productId == null) return false;
        try {
            boolean deleted = vectorStore.delete(ENTITY_TYPE_PRODUCT, productId.toString());
            if (deleted) {
                deletionCounter.increment();
            }
            return deleted;
        } catch (Exception e) {
            log.error("Failed to delete product embedding for id={}", productId, e);
            return false;
        }
    }

    @Override
    @Transactional(readOnly = true)
    public boolean isProductIndexed(UUID productId) {
        if (productId == null) return false;
        return vectorStore.exists(
                ENTITY_TYPE_PRODUCT,
                productId.toString(),
                embeddingService.getModelName(),
                embeddingService.getModelVersion()
        );
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<EmbeddingRecord> getProductEmbedding(UUID productId) {
        if (productId == null) return Optional.empty();
        return vectorStore.get(
                ENTITY_TYPE_PRODUCT,
                productId.toString(),
                embeddingService.getModelName(),
                embeddingService.getModelVersion()
        );
    }
}
