package com.pricepilot.intelligence.semantic;

import com.pricepilot.intelligence.discovery.dto.DiscoverySearchResponseDTO;
import com.pricepilot.intelligence.discovery.hybrid.HybridSearchRequest;
import com.pricepilot.intelligence.discovery.hybrid.HybridSearchService;
import com.pricepilot.intelligence.semantic.pipeline.ProductEmbeddingService;
import com.pricepilot.product.ProductEntity;
import com.pricepilot.product.ProductRepository;
import com.pricepilot.productprice.ProductPriceEntity;
import com.pricepilot.productprice.ProductPriceRepository;
import com.pricepilot.seller.SellerEntity;
import com.pricepilot.seller.SellerRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class HybridSearchIntegrationTest {

    @Autowired
    private HybridSearchService hybridSearchService;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private ProductPriceRepository productPriceRepository;

    @Autowired
    private SellerRepository sellerRepository;

    @Autowired
    private ProductEmbeddingService productEmbeddingService;

    private ProductEntity p1;
    private ProductEntity p2;
    private ProductEntity p3;
    private SellerEntity seller;

    @BeforeEach
    void setUp() {
        seller = sellerRepository.save(SellerEntity.builder()
                .name("Official Electronics Store")
                .websiteUrl("https://store.example.com")
                .build());

        p1 = productRepository.save(ProductEntity.builder()
                .name("Sony WH-1000XM5 Wireless Headphones")
                .brand("Sony")
                .category("Audio")
                .description("Industry leading noise cancellation with two processors and 8 microphones.")
                .archived(false)
                .build());

        p2 = productRepository.save(ProductEntity.builder()
                .name("Apple MacBook Pro M3 Max")
                .brand("Apple")
                .category("Laptops")
                .description("16-inch high performance workstation laptop.")
                .archived(false)
                .build());

        p3 = productRepository.save(ProductEntity.builder()
                .name("Nike Air Zoom Pegasus 40")
                .brand("Nike")
                .category("Footwear")
                .description("Responsive running shoe with breathable engineered mesh.")
                .archived(false)
                .build());

        // Add prices
        productPriceRepository.save(ProductPriceEntity.builder()
                .product(p1).seller(seller).currentPrice(BigDecimal.valueOf(399.00)).originalPrice(BigDecimal.valueOf(449.00)).discountPercentage(BigDecimal.valueOf(11)).build());
        productPriceRepository.save(ProductPriceEntity.builder()
                .product(p2).seller(seller).currentPrice(BigDecimal.valueOf(2499.00)).originalPrice(BigDecimal.valueOf(2499.00)).discountPercentage(BigDecimal.ZERO).build());
        productPriceRepository.save(ProductPriceEntity.builder()
                .product(p3).seller(seller).currentPrice(BigDecimal.valueOf(130.00)).originalPrice(BigDecimal.valueOf(130.00)).discountPercentage(BigDecimal.ZERO).build());

        // Index in VectorStore
        productEmbeddingService.indexProductBatch(List.of(p1, p2, p3));
    }

    @Test
    @DisplayName("Should retrieve and rank matching product via hybrid retrieval")
    void testHybridSearchIntegration() {
        HybridSearchRequest request = HybridSearchRequest.builder()
                .query("Sony noise cancelling")
                .page(0)
                .size(10)
                .build();

        DiscoverySearchResponseDTO response = hybridSearchService.search(request);

        assertThat(response).isNotNull();
        assertThat(response.getContent()).isNotEmpty();
        assertThat(response.getContent().get(0).getId()).isEqualTo(p1.getId());
        assertThat(response.getContent().get(0).getName()).contains("Sony");
    }

    @Test
    @DisplayName("Hard Filter Enforcement: Max price filter excludes semantically similar expensive items")
    void testHardPriceFilterEnforcementInIntegration() {
        // Query matching both Sony headphones ($399) and MacBook ($2499)
        // Apply maxPrice = $200
        HybridSearchRequest request = HybridSearchRequest.builder()
                .query("Wireless electronic device")
                .maxPrice(BigDecimal.valueOf(200.00))
                .page(0)
                .size(10)
                .build();

        DiscoverySearchResponseDTO response = hybridSearchService.search(request);

        // Sony ($399) and MacBook ($2499) must both be rejected by price filter
        assertThat(response.getContent().stream().noneMatch(p -> p.getId().equals(p1.getId()))).isTrue();
        assertThat(response.getContent().stream().noneMatch(p -> p.getId().equals(p2.getId()))).isTrue();
    }

    @Test
    @DisplayName("Category Filter Enforcement: Category filter excludes non-matching categories")
    void testCategoryFilterEnforcement() {
        HybridSearchRequest request = HybridSearchRequest.builder()
                .query("Nike shoe")
                .category("Audio") // Filter set to Audio
                .page(0)
                .size(10)
                .build();

        DiscoverySearchResponseDTO response = hybridSearchService.search(request);

        // Nike (Footwear) must not be returned in Audio category search
        assertThat(response.getContent().stream().noneMatch(p -> p.getId().equals(p3.getId()))).isTrue();
    }
}
