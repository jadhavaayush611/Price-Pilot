package com.pricepilot.intelligence.discovery;

import com.pricepilot.intelligence.discovery.dto.DiscoverySearchRequestDTO;
import com.pricepilot.intelligence.discovery.dto.DiscoverySearchResponseDTO;
import com.pricepilot.intelligence.discovery.service.SearchDiscoveryService;
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
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
public class SearchDiscoveryIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private ProductPriceRepository productPriceRepository;

    @Autowired
    private SellerRepository sellerRepository;

    @Autowired
    private SearchDiscoveryService discoveryService;

    private SellerEntity seller;
    private ProductEntity iphone;
    private ProductEntity macbook;
    private ProductEntity galaxy;

    @BeforeEach
    void setUp() {
        productPriceRepository.deleteAll();
        productRepository.deleteAll();
        sellerRepository.deleteAll();

        seller = sellerRepository.save(SellerEntity.builder()
                .name("BestBuy")
                .websiteUrl("https://bestbuy.com")
                .build());

        iphone = productRepository.save(ProductEntity.builder()
                .name("Apple iPhone 15 Pro")
                .brand("Apple")
                .category("Smartphone")
                .description("Titanium body smartphone with A17 Pro chip")
                .archived(false)
                .build());

        productPriceRepository.save(ProductPriceEntity.builder()
                .product(iphone)
                .seller(seller)
                .currentPrice(BigDecimal.valueOf(999.00))
                .originalPrice(BigDecimal.valueOf(1099.00))
                .productUrl("https://bestbuy.com/iphone")
                .lastUpdated(LocalDateTime.now())
                .build());

        macbook = productRepository.save(ProductEntity.builder()
                .name("Apple MacBook Pro 14 M3")
                .brand("Apple")
                .category("Laptop")
                .description("Powerful Apple silicon laptop")
                .archived(false)
                .build());

        productPriceRepository.save(ProductPriceEntity.builder()
                .product(macbook)
                .seller(seller)
                .currentPrice(BigDecimal.valueOf(1599.00))
                .originalPrice(BigDecimal.valueOf(1799.00))
                .productUrl("https://bestbuy.com/macbook")
                .lastUpdated(LocalDateTime.now())
                .build());

        galaxy = productRepository.save(ProductEntity.builder()
                .name("Samsung Galaxy S24 Ultra")
                .brand("Samsung")
                .category("Smartphone")
                .description("Galaxy AI and high resolution camera")
                .archived(false)
                .build());

        productPriceRepository.save(ProductPriceEntity.builder()
                .product(galaxy)
                .seller(seller)
                .currentPrice(BigDecimal.valueOf(1299.00))
                .originalPrice(BigDecimal.valueOf(1299.00))
                .productUrl("https://bestbuy.com/galaxy")
                .lastUpdated(LocalDateTime.now())
                .build());
    }

    @Test
    @DisplayName("Discovery pipeline interprets intent 'iphone under 1200' and ranks iPhone first")
    void testQueryInterpretationAndDiscoveryRanking() throws Exception {
        mockMvc.perform(get("/api/v1/discovery/products")
                        .param("query", "iphone under 1200")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.content[0].name", is("Apple iPhone 15 Pro")))
                .andExpect(jsonPath("$.content[0].currentBestPrice", is(999.0)))
                .andExpect(jsonPath("$.content[0].discoveryBadges", hasItem("Best Match")))
                .andExpect(jsonPath("$.interpretedQuery.detectedCategory", is("Smartphone")))
                .andExpect(jsonPath("$.interpretedQuery.maxPrice", is(1200)));
    }

    @Test
    @DisplayName("Search with explicit brand and category filters")
    void testSearchWithExplicitFilters() throws Exception {
        mockMvc.perform(get("/api/v1/discovery/products")
                        .param("brand", "Apple")
                        .param("category", "Laptop")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.content[0].name", is("Apple MacBook Pro 14 M3")));
    }

    @Test
    @DisplayName("Search with price-asc sort returns cheaper iPhone before Galaxy")
    void testSearchPriceAscSort() throws Exception {
        mockMvc.perform(get("/api/v1/discovery/products")
                        .param("category", "Smartphone")
                        .param("sort", "price-asc")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(2)))
                .andExpect(jsonPath("$.content[0].name", is("Apple iPhone 15 Pro"))) // 999.00
                .andExpect(jsonPath("$.content[1].name", is("Samsung Galaxy S24 Ultra"))); // 1299.00
    }

    @Test
    @DisplayName("Performance Benchmark: Scaling to 60 products executes within bounded latency")
    void testScalingPerformanceBenchmark() {
        // Bulk insert 60 additional products
        List<ProductEntity> bulkProds = new ArrayList<>();
        for (int i = 0; i < 60; i++) {
            bulkProds.add(ProductEntity.builder()
                    .name("Benchmark Device " + i)
                    .brand(i % 2 == 0 ? "Apple" : "Samsung")
                    .category(i % 3 == 0 ? "Smartphone" : "Laptop")
                    .description("Test device for benchmark")
                    .archived(false)
                    .build());
        }
        bulkProds = productRepository.saveAll(bulkProds);

        List<ProductPriceEntity> bulkPrices = new ArrayList<>();
        for (int i = 0; i < 60; i++) {
            bulkPrices.add(ProductPriceEntity.builder()
                    .product(bulkProds.get(i))
                    .seller(seller)
                    .currentPrice(BigDecimal.valueOf(100.00 + i))
                    .originalPrice(BigDecimal.valueOf(120.00 + i))
                    .productUrl("https://example.com/" + i)
                    .lastUpdated(LocalDateTime.now())
                    .build());
        }
        productPriceRepository.saveAll(bulkPrices);

        long start = System.currentTimeMillis();
        DiscoverySearchResponseDTO response = discoveryService.searchAndDiscover(
                DiscoverySearchRequestDTO.builder()
                        .query("Benchmark")
                        .page(0)
                        .size(10)
                        .build()
        );
        long latencyMs = System.currentTimeMillis() - start;

        assertNotNull(response);
        assertEquals(10, response.getContent().size(), "Page size should be bounded to 10");
        assertTrue(response.getTotalElements() >= 60, "Total elements reflects bulk set");
        assertTrue(latencyMs < 1000, "Discovery search latency should be < 1000ms, actual: " + latencyMs + "ms");
    }

    @Test
    @DisplayName("Invalid sort property is rejected with 400 Bad Request")
    void testInvalidSortRejected() throws Exception {
        mockMvc.perform(get("/api/v1/discovery/products")
                        .param("sort", "injectionField,asc")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status", is(400)))
                .andExpect(jsonPath("$.message", containsString("Invalid sort property: injectionField")));
    }

    @Test
    @DisplayName("Backwards compatibility: GET /api/v1/search continues to function")
    void testBackwardsCompatibilitySearch() throws Exception {
        mockMvc.perform(get("/api/v1/search")
                        .param("category", "Smartphone")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(2)))
                .andExpect(jsonPath("$.content[*].name", hasItems("Apple iPhone 15 Pro", "Samsung Galaxy S24 Ultra")));
    }

    @Test
    @DisplayName("Backwards compatibility: GET /api/v1/products continues to function")
    void testBackwardsCompatibilityProducts() throws Exception {
        mockMvc.perform(get("/api/v1/products")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(3)));
    }

    @Test
    @DisplayName("Suggestions endpoint returns autocomplete results")
    void testAutocompleteSuggestions() throws Exception {
        mockMvc.perform(get("/api/v1/discovery/suggestions")
                        .param("query", "App")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", not(empty())))
                .andExpect(jsonPath("$[0].text", is("Apple")));
    }
}
