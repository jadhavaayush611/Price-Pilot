package com.pricepilot.intelligence.semantic;

import com.pricepilot.intelligence.semantic.contract.CanonicalProductTextBuilder;
import com.pricepilot.intelligence.semantic.pipeline.ProductEmbeddingService;
import com.pricepilot.intelligence.semantic.pipeline.ProductSemanticEventListener;
import com.pricepilot.product.event.ProductCreatedEvent;
import com.pricepilot.product.event.ProductDeletedEvent;
import com.pricepilot.product.event.ProductUpdatedEvent;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ProductSemanticEventListenerTest {

    @Mock
    private ProductEmbeddingService productEmbeddingService;

    private CanonicalProductTextBuilder textBuilder;
    private SimpleMeterRegistry meterRegistry;
    private ProductSemanticEventListener listener;

    @BeforeEach
    void setUp() {
        textBuilder = new CanonicalProductTextBuilder(2048);
        meterRegistry = new SimpleMeterRegistry();
        listener = new ProductSemanticEventListener(productEmbeddingService, textBuilder, meterRegistry);
    }

    @Test
    @DisplayName("Should trigger indexing when active product is created")
    void testOnProductCreated() {
        UUID productId = UUID.randomUUID();
        ProductCreatedEvent event = new ProductCreatedEvent(
                productId, "iPhone 15", "Apple", "Phones", "New phone", false
        );

        listener.onProductCreated(event);

        verify(productEmbeddingService).indexProductById(productId);
        assertThat(meterRegistry.get("pricepilot.semantic.product.events.reindexed").counter().count()).isEqualTo(1.0);
    }

    @Test
    @DisplayName("Should skip indexing when archived product is created")
    void testOnArchivedProductCreated() {
        UUID productId = UUID.randomUUID();
        ProductCreatedEvent event = new ProductCreatedEvent(
                productId, "Old Phone", "Apple", "Phones", "Archived phone", true
        );

        listener.onProductCreated(event);

        verifyNoInteractions(productEmbeddingService);
        assertThat(meterRegistry.get("pricepilot.semantic.product.events.skipped").counter().count()).isEqualTo(1.0);
    }

    @Test
    @DisplayName("Should re-index when semantic fields change on update")
    void testOnSemanticUpdate() {
        UUID productId = UUID.randomUUID();
        ProductUpdatedEvent event = new ProductUpdatedEvent(
                productId,
                "iPhone 14", "Apple", "Phones", "Desc 1", false,
                "iPhone 15", "Apple", "Phones", "Desc 1", false // name changed
        );

        listener.onProductUpdated(event);

        verify(productEmbeddingService).indexProductById(productId);
        assertThat(meterRegistry.get("pricepilot.semantic.product.events.reindexed").counter().count()).isEqualTo(1.0);
    }

    @Test
    @DisplayName("Should SKIP re-indexing when only non-semantic fields change on update")
    void testOnNonSemanticUpdate() {
        UUID productId = UUID.randomUUID();
        // Exact same semantic fields (name, brand, category, description), only volatile price/stock/images changed in product
        ProductUpdatedEvent event = new ProductUpdatedEvent(
                productId,
                "iPhone 15", "Apple", "Phones", "Same Description", false,
                "iPhone 15", "Apple", "Phones", "Same Description", false
        );

        listener.onProductUpdated(event);

        verifyNoInteractions(productEmbeddingService);
        assertThat(meterRegistry.get("pricepilot.semantic.product.events.skipped").counter().count()).isEqualTo(1.0);
    }

    @Test
    @DisplayName("Should delete embedding when product is transitioned to archived")
    void testOnArchivedTransition() {
        UUID productId = UUID.randomUUID();
        ProductUpdatedEvent event = new ProductUpdatedEvent(
                productId,
                "iPhone 15", "Apple", "Phones", "Desc", false,
                "iPhone 15", "Apple", "Phones", "Desc", true // archived transitioned false -> true
        );

        listener.onProductUpdated(event);

        verify(productEmbeddingService).deleteProductEmbedding(productId);
        verify(productEmbeddingService, never()).indexProductById(productId);
    }

    @Test
    @DisplayName("Should delete embedding on ProductDeletedEvent")
    void testOnProductDeleted() {
        UUID productId = UUID.randomUUID();
        ProductDeletedEvent event = new ProductDeletedEvent(productId);

        listener.onProductDeleted(event);

        verify(productEmbeddingService).deleteProductEmbedding(productId);
    }
}
