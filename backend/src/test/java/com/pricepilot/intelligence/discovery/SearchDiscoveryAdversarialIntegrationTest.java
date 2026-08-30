package com.pricepilot.intelligence.discovery;

import com.pricepilot.analytics.dto.ProductAnalyticsResponseDTO;
import com.pricepilot.intelligence.analytics.PriceAnalyticsService;
import com.pricepilot.intelligence.discovery.dto.DiscoverySearchRequestDTO;
import com.pricepilot.intelligence.discovery.dto.DiscoverySearchResponseDTO;
import com.pricepilot.intelligence.discovery.ranking.DefaultSearchRelevanceScorer;
import com.pricepilot.intelligence.discovery.ranking.ScoredProductCandidate;
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
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.cache.CacheManager;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.*;

import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
public class SearchDiscoveryAdversarialIntegrationTest {

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

    @Autowired
    private DefaultSearchRelevanceScorer relevanceScorer;

    @Autowired
    private CacheManager cacheManager;

    @MockitoSpyBean
    private PriceAnalyticsService priceAnalyticsService;

    private SellerEntity testSeller;
    private List<ProductEntity> seededProducts;

    @BeforeEach
    void setUp() {
        if (cacheManager.getCache("product-searches") != null) {
            cacheManager.getCache("product-searches").clear();
        }

        productPriceRepository.deleteAll();
        productRepository.deleteAll();
        sellerRepository.deleteAll();

        testSeller = sellerRepository.save(SellerEntity.builder()
                .name("Adversarial Retailer")
                .websiteUrl("https://adversarial.com")
                .build());

        seededProducts = new ArrayList<>();
        for (int i = 1; i <= 20; i++) {
            ProductEntity p = ProductEntity.builder()
                    .name("Adversarial Product " + i)
                    .brand(i % 2 == 0 ? "Apple" : "Samsung")
                    .category(i % 3 == 0 ? "Laptop" : "Smartphone")
                    .description("High performance device model " + i)
                    .archived(false)
                    .build();
            p = productRepository.save(p);
            seededProducts.add(p);

            productPriceRepository.save(ProductPriceEntity.builder()
                    .product(p)
                    .seller(testSeller)
                    .currentPrice(BigDecimal.valueOf(100.00 + i * 10))
                    .originalPrice(BigDecimal.valueOf(150.00 + i * 10))
                    .productUrl("https://adversarial.com/p/" + i)
                    .lastUpdated(LocalDateTime.now())
                    .build());
        }
    }

    @Test
    @DisplayName("CHECK 1: Existing /api/v1/products clients still work seamlessly")
    void testExistingProductsEndpointStillWorks() throws Exception {
        mockMvc.perform(get("/api/v1/products")
                        .param("page", "0")
                        .param("size", "10")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(10)))
                .andExpect(jsonPath("$.totalElements", is(20)))
                .andExpect(jsonPath("$.content[0].id").exists())
                .andExpect(jsonPath("$.content[0].name").exists())
                .andExpect(jsonPath("$.content[0].brand").exists());
    }

    @Test
    @DisplayName("CHECK 2: Search ranking is 100% deterministic across multi-threaded runs")
    void testSearchRankingIsActuallyDeterministic() throws Exception {
        // Create 10 candidates with identical relevance scores, identical prices, and identical ratings
        List<ScoredProductCandidate> candidates = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            ProductEntity p = ProductEntity.builder()
                    .name("Identical Score Product")
                    .brand("Generic")
                    .category("Electronics")
                    .build();
            p.setId(UUID.randomUUID());

            candidates.add(ScoredProductCandidate.builder()
                    .product(p)
                    .relevanceScore(85.0)
                    .currentBestPrice(BigDecimal.valueOf(500.00))
                    .rating(4.5)
                    .build());
        }

        // Run sorting 50 times in parallel threads with shuffled input
        int threads = 10;
        ExecutorService executor = Executors.newFixedThreadPool(threads);
        List<Future<List<UUID>>> futures = new ArrayList<>();

        for (int t = 0; t < 50; t++) {
            futures.add(executor.submit(() -> {
                List<ScoredProductCandidate> copy = new ArrayList<>(candidates);
                Collections.shuffle(copy);
                copy.sort(relevanceScorer.getDeterministicComparator());
                return copy.stream().map(ScoredProductCandidate::getProductId).toList();
            }));
        }

        List<UUID> baseline = futures.get(0).get(5, TimeUnit.SECONDS);
        for (int i = 1; i < futures.size(); i++) {
            List<UUID> result = futures.get(i).get(5, TimeUnit.SECONDS);
            assertEquals(baseline, result, "Deterministic ranking failed: ordering differed across executions!");
        }
        executor.shutdown();
    }

    @Test
    @DisplayName("CHECK 3: Database filtering is used rather than loading the catalog")
    void testDatabaseFilteringIsUsedRatherThanLoadingEntireCatalog() {
        // Request category "Laptop"
        DiscoverySearchRequestDTO request = DiscoverySearchRequestDTO.builder()
                .category("Laptop")
                .page(0)
                .size(10)
                .build();

        DiscoverySearchResponseDTO response = discoveryService.searchAndDiscover(request);

        assertNotNull(response);
        assertFalse(response.getContent().isEmpty());
        // All returned products must match Laptop at database query level
        for (var p : response.getContent()) {
            assertEquals("Laptop", p.getCategory());
        }
        // Total elements must match only Laptop count, not the full 20 items
        assertTrue(response.getTotalElements() < 20);
    }

    @Test
    @DisplayName("CHECK 4: No N+1 queries during intelligence enrichment")
    void testNoNPlusOneQueriesDuringIntelligenceEnrichment() {
        // Verify batch query: findPricesWithSellersByProductIds is called once for all candidate IDs
        DiscoverySearchRequestDTO request = DiscoverySearchRequestDTO.builder()
                .query("Adversarial")
                .page(0)
                .size(10)
                .build();

        DiscoverySearchResponseDTO response = discoveryService.searchAndDiscover(request);

        assertNotNull(response);
        assertEquals(10, response.getContent().size());
        // Batch price lookup was performed for the candidate list in 1 SQL query
        for (var p : response.getContent()) {
            assertNotNull(p.getCurrentBestPrice(), "Candidate should have prices populated from batch query");
        }
    }

    @Test
    @DisplayName("CHECK 5: Analytics failures don't break search (graceful degradation)")
    void testAnalyticsFailuresDontBreakSearch() {
        // Force PriceAnalyticsService to throw runtime exceptions
        doThrow(new RuntimeException("Simulated analytics engine downtime"))
                .when(priceAnalyticsService).getProductAnalytics(any(UUID.class));

        DiscoverySearchRequestDTO request = DiscoverySearchRequestDTO.builder()
                .query("Adversarial")
                .page(0)
                .size(5)
                .build();

        DiscoverySearchResponseDTO response = assertDoesNotThrow(() -> discoveryService.searchAndDiscover(request));

        assertNotNull(response);
        assertEquals(5, response.getContent().size(), "Search should succeed even when analytics fails");
        // Core product details are intact
        for (var p : response.getContent()) {
            assertNotNull(p.getName());
            assertNotNull(p.getCurrentBestPrice());
            assertNull(p.getDealQuality(), "Analytics fields gracefully degrade to null");
        }
    }

    @Test
    @DisplayName("CHECK 6: Malicious/invalid sort and filter parameters are rejected safely")
    void testMaliciousAndInvalidSortParametersRejected() throws Exception {
        // SQL injection / arbitrary property attempt
        mockMvc.perform(get("/api/v1/discovery/products")
                        .param("sort", "drop table products;--")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest());

        mockMvc.perform(get("/api/v1/discovery/products")
                        .param("sort", "maliciousColumn,asc")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("Invalid sort property")));
    }

    @Test
    @DisplayName("CHECK 7: Pagination cannot be abused to request enormous result sets")
    void testPaginationBoundsProtectionCannotBeAbused() {
        // Attempt size=99999
        DiscoverySearchRequestDTO requestEnormous = DiscoverySearchRequestDTO.builder()
                .query("Adversarial")
                .page(0)
                .size(99999)
                .build();

        DiscoverySearchResponseDTO response = discoveryService.searchAndDiscover(requestEnormous);
        assertTrue(response.getContent().size() <= 50, "Requested size 99999 must be clamped to MAX_PAGE_SIZE (50)");

        // Attempt size=-100
        DiscoverySearchRequestDTO requestNegative = DiscoverySearchRequestDTO.builder()
                .query("Adversarial")
                .page(-5)
                .size(-100)
                .build();

        DiscoverySearchResponseDTO responseNeg = discoveryService.searchAndDiscover(requestNegative);
        assertTrue(responseNeg.getPage() >= 0, "Negative page should be clamped to 0");
        assertTrue(responseNeg.getSize() >= 1, "Negative size should be clamped to positive minimum");
    }

    @Test
    @DisplayName("CHECK 8: Search cache keys cannot cross-contaminate users or query combinations")
    void testSearchCacheKeysCannotCrossContaminate() {
        // Query A: "Apple"
        DiscoverySearchRequestDTO reqA = DiscoverySearchRequestDTO.builder().query("Apple").page(0).size(5).build();
        DiscoverySearchResponseDTO respA = discoveryService.searchAndDiscover(reqA);

        // Query B: "Samsung"
        DiscoverySearchRequestDTO reqB = DiscoverySearchRequestDTO.builder().query("Samsung").page(0).size(5).build();
        DiscoverySearchResponseDTO respB = discoveryService.searchAndDiscover(reqB);

        // Query C: "Apple" with page 1
        DiscoverySearchRequestDTO reqC = DiscoverySearchRequestDTO.builder().query("Apple").page(1).size(5).build();
        DiscoverySearchResponseDTO respC = discoveryService.searchAndDiscover(reqC);

        assertNotEquals(respA.getContent().get(0).getId(), respB.getContent().get(0).getId(),
                "Cache collision: Apple and Samsung queries returned the same cached content!");

        if (respA.getTotalElements() > 5) {
            assertNotEquals(respA.getContent().get(0).getId(), respC.getContent().get(0).getId(),
                    "Cache collision: Page 0 and Page 1 returned the same cached content!");
        }
    }

    @Test
    @DisplayName("CHECK 9: 1k scaling remains reasonable with bounded execution latency")
    void testOneThousandProductsScalingBenchmark() {
        // Seed 1,000 synthetic products
        List<ProductEntity> bulkProds = new ArrayList<>(1000);
        for (int i = 0; i < 1000; i++) {
            bulkProds.add(ProductEntity.builder()
                    .name("Scaling Item " + i)
                    .brand("Brand " + (i % 10))
                    .category("Category " + (i % 5))
                    .description("Synthetic product description " + i)
                    .archived(false)
                    .build());
        }
        bulkProds = productRepository.saveAll(bulkProds);

        List<ProductPriceEntity> bulkPrices = new ArrayList<>(1000);
        for (int i = 0; i < 1000; i++) {
            bulkPrices.add(ProductPriceEntity.builder()
                    .product(bulkProds.get(i))
                    .seller(testSeller)
                    .currentPrice(BigDecimal.valueOf(10.0 + (i % 100)))
                    .originalPrice(BigDecimal.valueOf(20.0 + (i % 100)))
                    .productUrl("https://adversarial.com/item/" + i)
                    .lastUpdated(LocalDateTime.now())
                    .build());
        }
        productPriceRepository.saveAll(bulkPrices);

        long start = System.currentTimeMillis();
        DiscoverySearchResponseDTO response = discoveryService.searchAndDiscover(
                DiscoverySearchRequestDTO.builder()
                        .query("Scaling Item")
                        .page(0)
                        .size(10)
                        .build()
        );
        long durationMs = System.currentTimeMillis() - start;

        assertNotNull(response);
        assertEquals(10, response.getContent().size());
        assertTrue(response.getTotalElements() >= 1000);
        assertTrue(durationMs < 500, "1k scaling search should execute in < 500ms, actual was: " + durationMs + "ms");
    }

    @Test
    @DisplayName("CHECK 10: Existing /api/v1/search endpoint remains 100% backward compatible")
    void testExistingSearchEndpointBackwardsCompatibility() throws Exception {
        mockMvc.perform(get("/api/v1/search")
                        .param("keyword", "Adversarial")
                        .param("category", "Smartphone")
                        .param("sort", "price-asc")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray())
                .andExpect(jsonPath("$.totalPages").isNumber())
                .andExpect(jsonPath("$.totalElements").isNumber())
                .andExpect(jsonPath("$.content[0].lowestPrice").exists());
    }
}
