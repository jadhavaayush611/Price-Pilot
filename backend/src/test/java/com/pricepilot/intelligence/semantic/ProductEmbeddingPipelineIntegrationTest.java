package com.pricepilot.intelligence.semantic;

import com.pricepilot.intelligence.semantic.config.SemanticIntelligenceProperties;
import com.pricepilot.intelligence.semantic.contract.CanonicalProductTextBuilder;
import com.pricepilot.intelligence.semantic.model.EmbeddingRecord;
import com.pricepilot.intelligence.semantic.pipeline.BatchIndexingResult;
import com.pricepilot.intelligence.semantic.pipeline.CatalogIndexingService;
import com.pricepilot.intelligence.semantic.pipeline.ProductEmbeddingService;
import com.pricepilot.intelligence.semantic.storage.VectorStore;
import com.pricepilot.product.ProductEntity;
import com.pricepilot.product.ProductRepository;
import com.pricepilot.product.ProductService;
import com.pricepilot.product.dto.ProductRequestDTO;
import com.pricepilot.product.dto.ProductResponseDTO;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class ProductEmbeddingPipelineIntegrationTest {

    @Autowired
    private ProductService productService;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private ProductEmbeddingService productEmbeddingService;

    @Autowired
    private CatalogIndexingService catalogIndexingService;

    @Autowired
    private VectorStore vectorStore;

    @Autowired
    private SemanticIntelligenceProperties properties;

    @Test
    @DisplayName("End-to-End: Product creation triggers indexing, updates re-index, and deletion removes embedding")
    void testProductLifecycleEmbeddingIntegration() {
        // 1. Create a new product via ProductService
        ProductRequestDTO request = ProductRequestDTO.builder()
                .name("Bose QuietComfort Ultra")
                .brand("Bose")
                .category("Headphones")
                .description("Spatial audio noise cancelling headphones with custom sound calibration.")
                .archived(false)
                .build();

        ProductResponseDTO created = productService.createProduct(request);
        UUID productId = created.getId();

        // Verify product was automatically indexed in VectorStore
        boolean isIndexed = productEmbeddingService.isProductIndexed(productId);
        assertThat(isIndexed).isTrue();

        Optional<EmbeddingRecord> recordOpt = productEmbeddingService.getProductEmbedding(productId);
        assertThat(recordOpt).isPresent();
        EmbeddingRecord record = recordOpt.get();
        assertThat(record.getEntityType()).isEqualTo("PRODUCT");
        assertThat(record.getEntityId()).isEqualTo(productId.toString());
        assertThat(record.getDimension()).isEqualTo(properties.getDimension());
        assertThat(record.getMetadata().getAttributes()).containsEntry("canonicalVersion", "product-semantic-v1");
        assertThat(record.getMetadata().getAttributes()).containsEntry("brand", "Bose");
        assertThat(record.getMetadata().getAttributes()).containsEntry("category", "Headphones");

        // 2. Update product with new semantic name
        ProductRequestDTO updateRequest = ProductRequestDTO.builder()
                .name("Bose QuietComfort Ultra (2nd Gen)")
                .brand("Bose")
                .category("Headphones")
                .description("Spatial audio noise cancelling headphones with custom sound calibration.")
                .archived(false)
                .build();

        productService.updateProduct(productId, updateRequest);

        Optional<EmbeddingRecord> updatedRecordOpt = productEmbeddingService.getProductEmbedding(productId);
        assertThat(updatedRecordOpt).isPresent();
        // Vector and hash updated
        assertThat(updatedRecordOpt.get().getMetadata().getAttributes().get("semanticHash"))
                .isNotEqualTo(record.getMetadata().getAttributes().get("semanticHash"));

        // 3. Delete product and verify removal from vector store
        productService.deleteProduct(productId);

        assertThat(productEmbeddingService.isProductIndexed(productId)).isFalse();
    }

    @Test
    @DisplayName("Batch & Catalog Indexing: Processes active catalog and skips archived products")
    void testCatalogBatchIndexing() {
        ProductEntity active1 = productRepository.save(ProductEntity.builder()
                .name("Product Active 1").brand("Brand A").category("Electronics").archived(false).build());
        ProductEntity active2 = productRepository.save(ProductEntity.builder()
                .name("Product Active 2").brand("Brand B").category("Electronics").archived(false).build());
        ProductEntity archived = productRepository.save(ProductEntity.builder()
                .name("Product Archived").brand("Brand C").category("Electronics").archived(true).build());

        BatchIndexingResult result = catalogIndexingService.reindexCatalog(10);

        assertThat(result.isSuccessful()).isTrue();
        assertThat(result.getTotalProcessed()).isGreaterThanOrEqualTo(2);
        assertThat(productEmbeddingService.isProductIndexed(active1.getId())).isTrue();
        assertThat(productEmbeddingService.isProductIndexed(active2.getId())).isTrue();
        assertThat(productEmbeddingService.isProductIndexed(archived.getId())).isFalse();
    }
}
