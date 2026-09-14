package com.pricepilot.intelligence.alternative;

import com.pricepilot.intelligence.alternative.model.*;
import com.pricepilot.intelligence.alternative.service.AlternativeFinderService;
import com.pricepilot.intelligence.semantic.model.EmbeddingVector;
import com.pricepilot.intelligence.semantic.service.EmbeddingService;
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
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc(addFilters = false)
@ActiveProfiles("test")
@Transactional
class AlternativeFinderIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private ProductPriceRepository productPriceRepository;

    @Autowired
    private SellerRepository sellerRepository;

    @Autowired
    private AlternativeFinderService alternativeFinderService;

    @Autowired
    private EmbeddingService embeddingService;

    private ProductEntity sonyHeadphones;
    private ProductEntity boseHeadphones;
    private ProductEntity ankerHeadphones;
    private SellerEntity amazonSeller;

    @BeforeEach
    void setUp() {
        productPriceRepository.deleteAll();
        productRepository.deleteAll();
        sellerRepository.deleteAll();

        amazonSeller = sellerRepository.save(SellerEntity.builder()
                .name("Amazon")
                .websiteUrl("https://amazon.com")
                .build());

        sonyHeadphones = productRepository.save(ProductEntity.builder()
                .name("Sony WH-1000XM5 Wireless Headphones")
                .brand("Sony")
                .category("Headphones")
                .description("Industry leading noise canceling headphones with dual processors")
                .build());

        boseHeadphones = productRepository.save(ProductEntity.builder()
                .name("Bose QuietComfort 45")
                .brand("Bose")
                .category("Headphones")
                .description("Iconic quiet comfort wireless noise cancelling headphones")
                .build());

        ankerHeadphones = productRepository.save(ProductEntity.builder()
                .name("Anker Soundcore Space Q45")
                .brand("Anker")
                .category("Headphones")
                .description("Affordable adaptive noise canceling headphones with long battery")
                .build());

        // Attach Prices
        productPriceRepository.save(ProductPriceEntity.builder()
                .product(sonyHeadphones)
                .seller(amazonSeller)
                .currentPrice(BigDecimal.valueOf(399.00))
                .originalPrice(BigDecimal.valueOf(399.00))
                .build());

        productPriceRepository.save(ProductPriceEntity.builder()
                .product(boseHeadphones)
                .seller(amazonSeller)
                .currentPrice(BigDecimal.valueOf(329.00))
                .originalPrice(BigDecimal.valueOf(329.00))
                .build());

        productPriceRepository.save(ProductPriceEntity.builder()
                .product(ankerHeadphones)
                .seller(amazonSeller)
                .currentPrice(BigDecimal.valueOf(149.00))
                .originalPrice(BigDecimal.valueOf(149.00))
                .build());
    }

    @Test
    @DisplayName("Integration: Find CHEAPER alternatives for Sony WH-1000XM5")
    void testProductDrivenCheaperAlternatives() throws Exception {
        mockMvc.perform(get("/api/v1/alternatives/product/" + sonyHeadphones.getId())
                        .param("type", "CHEAPER")
                        .param("limit", "5")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.executionMode").value("PRODUCT"))
                .andExpect(jsonPath("$.alternativeType").value("CHEAPER"))
                .andExpect(jsonPath("$.sourceProductContext.id").value(sonyHeadphones.getId().toString()))
                .andExpect(jsonPath("$.sourceProductContext.currentBestPrice").value(399.00))
                .andExpect(jsonPath("$.totalFound").isNotEmpty())
                .andExpect(jsonPath("$.content").isArray());
    }

    @Test
    @DisplayName("Integration: Natural-language endpoint parses cheaper alternative request")
    void testNaturalLanguageEndpoint() throws Exception {
        mockMvc.perform(get("/api/v1/alternatives/natural-language")
                        .param("query", "cheaper alternative to sony headphones")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.executionMode").value("QUERY"))
                .andExpect(jsonPath("$.alternativeType").value("CHEAPER"))
                .andExpect(jsonPath("$.content").isArray());
    }

    @Test
    @DisplayName("Integration: Query endpoint with structured AlternativeRequest POST")
    void testQueryPostEndpoint() throws Exception {
        String jsonPayload = """
                {
                    "query": "wireless headphones",
                    "type": "SIMILAR",
                    "category": "Headphones",
                    "limit": 5
                }
                """;

        mockMvc.perform(post("/api/v1/alternatives/query")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonPayload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.executionMode").value("QUERY"))
                .andExpect(jsonPath("$.alternativeType").value("SIMILAR"))
                .andExpect(jsonPath("$.content").isArray());
    }
}
