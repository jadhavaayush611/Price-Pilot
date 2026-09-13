package com.pricepilot.intelligence.semantic.pipeline;

import com.pricepilot.intelligence.semantic.contract.CanonicalProductTextBuilder;
import com.pricepilot.product.event.ProductCreatedEvent;
import com.pricepilot.product.event.ProductDeletedEvent;
import com.pricepilot.product.event.ProductUpdatedEvent;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.Objects;

/**
 * Event-driven incremental indexing listener.
 * Automatically synchronizes product semantic embeddings on creation, update, and deletion lifecycle events.
 * Intelligently skips re-embedding when only volatile data (prices, sellers, discounts) changes.
 */
@Component
public class ProductSemanticEventListener {

    private static final Logger log = LoggerFactory.getLogger(ProductSemanticEventListener.class);

    private final ProductEmbeddingService productEmbeddingService;
    private final CanonicalProductTextBuilder textBuilder;

    private final Counter eventReceivedCounter;
    private final Counter eventReindexedCounter;
    private final Counter eventSkippedCounter;

    public ProductSemanticEventListener(
            ProductEmbeddingService productEmbeddingService,
            CanonicalProductTextBuilder textBuilder,
            MeterRegistry meterRegistry) {
        this.productEmbeddingService = Objects.requireNonNull(productEmbeddingService, "ProductEmbeddingService cannot be null");
        this.textBuilder = Objects.requireNonNull(textBuilder, "CanonicalProductTextBuilder cannot be null");

        this.eventReceivedCounter = Counter.builder("pricepilot.semantic.product.events.received")
                .description("Total number of product lifecycle events received")
                .register(meterRegistry);
        this.eventReindexedCounter = Counter.builder("pricepilot.semantic.product.events.reindexed")
                .description("Total number of products re-indexed triggered by lifecycle events")
                .register(meterRegistry);
        this.eventSkippedCounter = Counter.builder("pricepilot.semantic.product.events.skipped")
                .description("Total number of lifecycle events skipped (non-semantic updates)")
                .register(meterRegistry);
    }

    @EventListener
    public void onProductCreated(ProductCreatedEvent event) {
        eventReceivedCounter.increment();
        if (event == null || event.productId() == null) return;

        if (event.archived()) {
            eventSkippedCounter.increment();
            return;
        }

        try {
            productEmbeddingService.indexProductById(event.productId());
            eventReindexedCounter.increment();
        } catch (Exception e) {
            log.error("Failed to index newly created product id={}", event.productId(), e);
        }
    }

    @EventListener
    public void onProductUpdated(ProductUpdatedEvent event) {
        eventReceivedCounter.increment();
        if (event == null || event.productId() == null) return;

        // 1. If archived state changed to archived -> delete embedding
        if (!event.oldArchived() && event.newArchived()) {
            productEmbeddingService.deleteProductEmbedding(event.productId());
            eventReindexedCounter.increment();
            return;
        }

        // 2. If unarchived -> index product
        if (event.oldArchived() && !event.newArchived()) {
            productEmbeddingService.indexProductById(event.productId());
            eventReindexedCounter.increment();
            return;
        }

        // 3. If archived is true and stayed true -> skip
        if (event.newArchived()) {
            eventSkippedCounter.increment();
            return;
        }

        // 4. Check if semantic fields changed
        boolean semanticChanged = textBuilder.hasSemanticDifference(
                event.oldName(), event.oldBrand(), event.oldCategory(), event.oldDescription(),
                event.newName(), event.newBrand(), event.newCategory(), event.newDescription()
        );

        if (!semanticChanged) {
            // Price, discount, stock, rating or image changed - DO NOT re-embed!
            log.debug("Skipping semantic re-indexing for product id={} (no semantic changes)", event.productId());
            eventSkippedCounter.increment();
            return;
        }

        // 5. Re-index due to actual semantic text change
        try {
            productEmbeddingService.indexProductById(event.productId());
            eventReindexedCounter.increment();
            log.info("Successfully re-indexed product id={} after semantic modification", event.productId());
        } catch (Exception e) {
            log.error("Failed to re-index modified product id={}", event.productId(), e);
        }
    }

    @EventListener
    public void onProductDeleted(ProductDeletedEvent event) {
        eventReceivedCounter.increment();
        if (event == null || event.productId() == null) return;

        try {
            productEmbeddingService.deleteProductEmbedding(event.productId());
            eventReindexedCounter.increment();
        } catch (Exception e) {
            log.error("Failed to clean up embedding for deleted product id={}", event.productId(), e);
        }
    }
}
