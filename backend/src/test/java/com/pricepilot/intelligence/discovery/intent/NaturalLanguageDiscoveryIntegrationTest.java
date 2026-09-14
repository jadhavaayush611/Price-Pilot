package com.pricepilot.intelligence.discovery.intent;

import com.pricepilot.intelligence.discovery.dto.DiscoverySearchResponseDTO;
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

import java.math.BigDecimal;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class NaturalLanguageDiscoveryIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private ProductPriceRepository productPriceRepository;

    @Autowired
    private SellerRepository sellerRepository;

    private SellerEntity testSeller;

    @BeforeEach
    void setUp() {
        productPriceRepository.deleteAll();
        productRepository.deleteAll();
        sellerRepository.deleteAll();

        testSeller = sellerRepository.save(SellerEntity.builder()
                .name("ElectroStore")
                .websiteUrl("https://electrostore.com")
                .build());

        // Create affordable headphones
        ProductEntity p1 = productRepository.save(ProductEntity.builder()
                .name("Sony WH-1000XM4 Wireless Noise Cancelling Headphones")
                .brand("Sony")
                .category("Headphones")
                .description("Industry-leading noise canceling with Dual Noise Sensor technology.")
                .archived(false)
                .build());

        productPriceRepository.save(ProductPriceEntity.builder()
                .product(p1)
                .seller(testSeller)
                .currentPrice(BigDecimal.valueOf(199.99))
                .originalPrice(BigDecimal.valueOf(349.99))
                .discountPercentage(BigDecimal.valueOf(42.8))
                .build());

        // Create expensive laptop
        ProductEntity p2 = productRepository.save(ProductEntity.builder()
                .name("Apple MacBook Pro 16 M3 Max")
                .brand("Apple")
                .category("Laptop")
                .description("Extreme performance laptop for creators.")
                .archived(false)
                .build());

        productPriceRepository.save(ProductPriceEntity.builder()
                .product(p2)
                .seller(testSeller)
                .currentPrice(BigDecimal.valueOf(3499.00))
                .originalPrice(BigDecimal.valueOf(3499.00))
                .discountPercentage(BigDecimal.ZERO)
                .build());
    }

    @Test
    @DisplayName("Integration: GET /api/v1/discovery/natural-language executes end-to-end shopping query")
    void testNaturalLanguageEndpoint() throws Exception {
        mockMvc.perform(get("/api/v1/discovery/natural-language")
                        .param("q", "Sony wireless headphones under 300")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(greaterThanOrEqualTo(1))))
                .andExpect(jsonPath("$.content[0].name", containsString("Sony WH-1000XM4")))
                .andExpect(jsonPath("$.interpretedQuery.detectedBrand", is("Sony")))
                .andExpect(jsonPath("$.interpretedQuery.detectedCategory", is("Headphones")))
                .andExpect(jsonPath("$.interpretedQuery.maxPrice", is(300)));
    }

    @Test
    @DisplayName("Integration: GET /api/v1/discovery/intent previews parsed intent")
    void testIntentEndpoint() throws Exception {
        mockMvc.perform(get("/api/v1/discovery/intent")
                        .param("q", "Apple MacBook Pro under 4000")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.category", is("Laptop")))
                .andExpect(jsonPath("$.brand", is("Apple")))
                .andExpect(jsonPath("$.maxPrice", is(4000)));
    }

    @Test
    @DisplayName("Integration: GET /api/v1/discovery/products?nl=true routes through natural language service")
    void testProductsEndpointWithNlFlag() throws Exception {
        mockMvc.perform(get("/api/v1/discovery/products")
                        .param("q", "wireless headphones under 250")
                        .param("nl", "true")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.content[0].category", is("Headphones")));
    }
}
