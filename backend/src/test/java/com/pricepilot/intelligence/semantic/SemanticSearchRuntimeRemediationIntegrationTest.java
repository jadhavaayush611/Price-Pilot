package com.pricepilot.intelligence.semantic;

import com.pricepilot.intelligence.discovery.dto.DiscoverySearchRequestDTO;
import com.pricepilot.intelligence.discovery.dto.DiscoverySearchResponseDTO;
import com.pricepilot.intelligence.discovery.hybrid.HybridSearchRequest;
import com.pricepilot.intelligence.discovery.hybrid.HybridSearchService;
import com.pricepilot.intelligence.discovery.intent.NaturalLanguageDiscoveryService;
import com.pricepilot.intelligence.discovery.intent.NaturalLanguageSearchRequest;
import com.pricepilot.intelligence.discovery.service.SearchDiscoveryService;
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
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Regression Test Suite for Semantic Search Runtime Remediation.
 *
 * Verifies that descriptive natural-language shopping queries retrieve relevant semantic candidates
 * while preserving exact category, brand, and hard constraint filtering.
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
public class SemanticSearchRuntimeRemediationIntegrationTest {

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private SellerRepository sellerRepository;

    @Autowired
    private ProductPriceRepository productPriceRepository;

    @Autowired
    private ProductEmbeddingService productEmbeddingService;

    @Autowired
    private HybridSearchService hybridSearchService;

    @Autowired
    private NaturalLanguageDiscoveryService naturalLanguageDiscoveryService;

    @Autowired
    private SearchDiscoveryService searchDiscoveryService;

    @BeforeEach
    void setUp() {
        productPriceRepository.deleteAll();
        productRepository.deleteAll();
        sellerRepository.deleteAll();

        SellerEntity seller = sellerRepository.save(SellerEntity.builder()
                .name("Official Electronics Store")
                .websiteUrl("https://official.example.com")
                .build());

        // 18 Headphone products matching DatabaseSeeder specification
        String[][] headphoneData = {
                {"Sony WH-1000XM5", "Sony", "Headphones", "Industry leading active noise canceling over-ear headphones.", "399.00"},
                {"Bose QuietComfort Ultra", "Bose", "Headphones", "Noise canceling headphones with spatial audio technology.", "429.00"},
                {"Apple AirPods Max", "Apple", "Headphones", "Premium over-ear headphones with custom spatial audio and mesh headband.", "549.00"},
                {"Sennheiser Momentum 4", "Sennheiser", "Headphones", "Audiophile grade headphones with incredible 60-hour battery life.", "379.00"},
                {"Beats Studio Pro", "Beats", "Headphones", "Over-ear headphones with customized spatial audio and USB-C audio support.", "349.00"},
                {"Sony WH-1000XM4", "Sony", "Headphones", "Classic noise canceling headphones with foldaway design.", "299.00"},
                {"Shure AONIC 50 Gen 2", "Shure", "Headphones", "Studio quality sound with customizable ANC settings.", "349.00"},
                {"Bowers & Wilkins Px7 S2e", "Bowers & Wilkins", "Headphones", "Luxurious build quality and highly detailed acoustic performance.", "399.00"},
                {"Focal Bathys", "Focal", "Headphones", "Hi-fi active noise canceling headphones with built-in DAC mode.", "799.00"},
                {"Audio-Technica ATH-M50xBT2", "Audio-Technica", "Headphones", "Wireless studio monitor headphones with legendary audio performance.", "199.00"},
                {"Sennheiser HD 600", "Sennheiser", "Headphones", "Legendary open-back audiophile reference headphones.", "399.00"},
                {"Bose QuietComfort", "Bose", "Headphones", "Classic noise canceling comfort in a new design.", "349.00"},
                {"Sony WH-CH720N", "Sony", "Headphones", "Lightweight entry-level noise canceling headphones.", "149.00"},
                {"Sennheiser Accentum", "Sennheiser", "Headphones", "Premium sound ANC headphones with 50h battery life.", "179.00"},
                {"Beats Solo 4", "Beats", "Headphones", "On-ear headphones with ultra-long battery life.", "199.00"},
                {"Audio-Technica ATH-M55x", "Audio-Technica", "Headphones", "Professional studio tracking headphones.", "169.00"},
                {"JBL Tour One M2", "JBL", "Headphones", "Adaptive noise canceling wireless over-ear headphones.", "299.00"},
                {"Bowers & Wilkins Px8", "Bowers & Wilkins", "Headphones", "Flagship luxury noise canceling headphones.", "699.00"}
        };

        // 2 Non-headphone products to test taxonomy isolation
        String[][] otherData = {
                {"Apple MacBook Pro 16 M3", "Apple", "Laptop", "Professional workstation laptop.", "2499.00"},
                {"Samsung Galaxy S24 Ultra", "Samsung", "Smartphone", "Premium smartphone with AI features.", "1299.00"}
        };

        List<ProductEntity> products = new ArrayList<>();
        for (String[] data : headphoneData) {
            ProductEntity p = productRepository.save(ProductEntity.builder()
                    .name(data[0])
                    .brand(data[1])
                    .category(data[2])
                    .description(data[3])
                    .archived(false)
                    .build());
            productPriceRepository.save(ProductPriceEntity.builder()
                    .product(p)
                    .seller(seller)
                    .currentPrice(new BigDecimal(data[4]))
                    .originalPrice(new BigDecimal(data[4]))
                    .build());
            products.add(p);
        }

        for (String[] data : otherData) {
            ProductEntity p = productRepository.save(ProductEntity.builder()
                    .name(data[0])
                    .brand(data[1])
                    .category(data[2])
                    .description(data[3])
                    .archived(false)
                    .build());
            productPriceRepository.save(ProductPriceEntity.builder()
                    .product(p)
                    .seller(seller)
                    .currentPrice(new BigDecimal(data[4]))
                    .originalPrice(new BigDecimal(data[4]))
                    .build());
            products.add(p);
        }

        productEmbeddingService.indexProductBatch(products);
    }

    @Test
    @DisplayName("Query 1: 'ANC headphones with strong bass' returns relevant headphone candidates")
    void testQuery1_AncHeadphonesWithStrongBass() {
        DiscoverySearchResponseDTO response = searchDiscoveryService.searchAndDiscover(
                DiscoverySearchRequestDTO.builder().query("ANC headphones with strong bass").page(0).size(10).build()
        );

        assertThat(response).isNotNull();
        assertThat(response.getContent()).isNotEmpty();
        assertThat(response.getContent()).allMatch(p -> "Headphones".equalsIgnoreCase(p.getCategory()));
        assertThat(response.getContent().stream().anyMatch(p -> p.getName().contains("WH-1000") || p.getName().contains("QuietComfort") || p.getName().contains("Momentum"))).isTrue();
    }

    @Test
    @DisplayName("Query 2: 'wireless headphones with deep bass' returns relevant headphone candidates")
    void testQuery2_WirelessHeadphonesWithDeepBass() {
        DiscoverySearchResponseDTO response = searchDiscoveryService.searchAndDiscover(
                DiscoverySearchRequestDTO.builder().query("wireless headphones with deep bass").page(0).size(10).build()
        );

        assertThat(response).isNotNull();
        assertThat(response.getContent()).isNotEmpty();
        assertThat(response.getContent()).allMatch(p -> "Headphones".equalsIgnoreCase(p.getCategory()));
    }

    @Test
    @DisplayName("Query 3: 'noise cancelling headphones for bass-heavy music' returns relevant headphone candidates")
    void testQuery3_NoiseCancellingHeadphonesForBassHeavyMusic() {
        DiscoverySearchResponseDTO response = searchDiscoveryService.searchAndDiscover(
                DiscoverySearchRequestDTO.builder().query("noise cancelling headphones for bass-heavy music").page(0).size(10).build()
        );

        assertThat(response).isNotNull();
        assertThat(response.getContent()).isNotEmpty();
        assertThat(response.getContent()).allMatch(p -> "Headphones".equalsIgnoreCase(p.getCategory()));
    }

    @Test
    @DisplayName("Query 4: 'headphones with powerful bass and noise cancellation' returns relevant headphone candidates")
    void testQuery4_HeadphonesWithPowerfulBassAndNoiseCancellation() {
        DiscoverySearchResponseDTO response = searchDiscoveryService.searchAndDiscover(
                DiscoverySearchRequestDTO.builder().query("headphones with powerful bass and noise cancellation").page(0).size(10).build()
        );

        assertThat(response).isNotNull();
        assertThat(response.getContent()).isNotEmpty();
        assertThat(response.getContent()).allMatch(p -> "Headphones".equalsIgnoreCase(p.getCategory()));
    }

    @Test
    @DisplayName("Query 5: 'Sony headphones' returns exactly 3 Sony headphone products")
    void testQuery5_SonyHeadphones() {
        DiscoverySearchResponseDTO response = searchDiscoveryService.searchAndDiscover(
                DiscoverySearchRequestDTO.builder().query("Sony headphones").page(0).size(10).build()
        );

        assertThat(response).isNotNull();
        assertThat(response.getContent()).hasSize(3);
        assertThat(response.getContent()).allMatch(p -> "Sony".equalsIgnoreCase(p.getBrand()) && "Headphones".equalsIgnoreCase(p.getCategory()));
    }

    @Test
    @DisplayName("Query 6: 'headphones' returns all 18 headphone products across pages")
    void testQuery6_Headphones() {
        DiscoverySearchResponseDTO page1 = searchDiscoveryService.searchAndDiscover(
                DiscoverySearchRequestDTO.builder().query("headphones").page(0).size(10).build()
        );

        assertThat(page1).isNotNull();
        assertThat(page1.getTotalElements()).isEqualTo(18);
        assertThat(page1.getContent()).hasSize(10);
        assertThat(page1.getContent()).allMatch(p -> "Headphones".equalsIgnoreCase(p.getCategory()));

        DiscoverySearchResponseDTO page2 = searchDiscoveryService.searchAndDiscover(
                DiscoverySearchRequestDTO.builder().query("headphones").page(1).size(10).build()
        );
        assertThat(page2.getContent()).hasSize(8);
        assertThat(page2.getContent()).allMatch(p -> "Headphones".equalsIgnoreCase(p.getCategory()));
    }

    @Test
    @DisplayName("Hard Price Filter: 'wireless headphones under $250' excludes items above $250")
    void testHardPriceFilterWithSemanticQuery() {
        DiscoverySearchResponseDTO response = searchDiscoveryService.searchAndDiscover(
                DiscoverySearchRequestDTO.builder().query("wireless headphones under $250").page(0).size(10).build()
        );

        assertThat(response).isNotNull();
        assertThat(response.getContent()).isNotEmpty();
        assertThat(response.getContent()).allMatch(p -> p.getCurrentBestPrice().compareTo(BigDecimal.valueOf(250.00)) <= 0);
    }

    @Test
    @DisplayName("Natural Language Service End-to-End: 'ANC headphones with strong bass' delegates and returns candidates")
    void testNlDiscoveryServiceDirectly() {
        DiscoverySearchResponseDTO response = naturalLanguageDiscoveryService.search(
                NaturalLanguageSearchRequest.builder().query("ANC headphones with strong bass").page(0).size(10).build()
        );

        assertThat(response).isNotNull();
        assertThat(response.getContent()).isNotEmpty();
        assertThat(response.getContent()).allMatch(p -> "Headphones".equalsIgnoreCase(p.getCategory()));
    }
}
