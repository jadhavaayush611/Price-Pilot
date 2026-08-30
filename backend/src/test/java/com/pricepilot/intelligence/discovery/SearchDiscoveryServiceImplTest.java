package com.pricepilot.intelligence.discovery;

import com.pricepilot.analytics.dto.ProductAnalyticsResponseDTO;
import com.pricepilot.intelligence.analytics.PriceAnalyticsService;
import com.pricepilot.intelligence.analytics.model.DealQuality;
import com.pricepilot.intelligence.analytics.model.PriceTrend;
import com.pricepilot.intelligence.analytics.model.PurchaseSignal;
import com.pricepilot.intelligence.discovery.dto.DiscoveryProductDTO;
import com.pricepilot.intelligence.discovery.dto.DiscoverySearchRequestDTO;
import com.pricepilot.intelligence.discovery.dto.DiscoverySearchResponseDTO;
import com.pricepilot.intelligence.discovery.dto.SearchSuggestionDTO;
import com.pricepilot.intelligence.discovery.interpretation.QueryInterpreter;
import com.pricepilot.intelligence.discovery.normalization.QueryNormalizer;
import com.pricepilot.intelligence.discovery.ranking.DefaultSearchRelevanceScorer;
import com.pricepilot.intelligence.discovery.service.SearchDiscoveryServiceImpl;
import com.pricepilot.product.ProductEntity;
import com.pricepilot.product.ProductRepository;
import com.pricepilot.productprice.ProductPriceEntity;
import com.pricepilot.productprice.ProductPriceRepository;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class SearchDiscoveryServiceImplTest {

    @Mock
    private ProductRepository productRepository;

    @Mock
    private ProductPriceRepository productPriceRepository;

    @Mock
    private PriceAnalyticsService priceAnalyticsService;

    private QueryNormalizer queryNormalizer;
    private QueryInterpreter queryInterpreter;
    private DefaultSearchRelevanceScorer relevanceScorer;
    private SearchDiscoveryServiceImpl discoveryService;

    private ProductEntity product1;
    private ProductEntity product2;

    @BeforeEach
    void setUp() {
        queryNormalizer = new QueryNormalizer();
        queryInterpreter = new QueryInterpreter(queryNormalizer);
        relevanceScorer = new DefaultSearchRelevanceScorer(queryNormalizer);

        discoveryService = new SearchDiscoveryServiceImpl(
                productRepository,
                productPriceRepository,
                priceAnalyticsService,
                queryNormalizer,
                queryInterpreter,
                relevanceScorer,
                new SimpleMeterRegistry()
        );

        product1 = ProductEntity.builder()
                .name("Apple iPhone 15 Pro Max")
                .brand("Apple")
                .category("Smartphone")
                .description("Titanium flagship phone")
                .build();
        product1.setId(UUID.randomUUID());

        product2 = ProductEntity.builder()
                .name("Samsung Galaxy S24 Ultra")
                .brand("Samsung")
                .category("Smartphone")
                .description("Flagship with S-Pen")
                .build();
        product2.setId(UUID.randomUUID());
    }

    @Test
    @DisplayName("Executes intelligent discovery search and enriches with Phase 4 intelligence")
    void testSearchAndDiscoverSuccessful() {
        DiscoverySearchRequestDTO request = DiscoverySearchRequestDTO.builder()
                .query("iphone under 1200")
                .page(0)
                .size(10)
                .build();

        when(productRepository.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(product1, product2)));

        ProductPriceEntity price1 = ProductPriceEntity.builder()
                .product(product1)
                .currentPrice(BigDecimal.valueOf(1149.00))
                .originalPrice(BigDecimal.valueOf(1199.00))
                .discountPercentage(BigDecimal.valueOf(4.1))
                .build();

        ProductPriceEntity price2 = ProductPriceEntity.builder()
                .product(product2)
                .currentPrice(BigDecimal.valueOf(1199.00))
                .originalPrice(BigDecimal.valueOf(1299.00))
                .discountPercentage(BigDecimal.valueOf(7.6))
                .build();

        when(productPriceRepository.findPricesWithSellersByProductIds(any()))
                .thenReturn(List.of(price1, price2));

        when(priceAnalyticsService.getProductAnalytics(product1.getId()))
                .thenReturn(ProductAnalyticsResponseDTO.builder()
                        .dealQuality(DealQuality.EXCELLENT_DEAL)
                        .trend(PriceTrend.FALLING)
                        .purchaseSignal(PurchaseSignal.BUY_NOW)
                        .historicalMin(BigDecimal.valueOf(1149.00))
                        .build());

        DiscoverySearchResponseDTO response = discoveryService.searchAndDiscover(request);

        assertNotNull(response);
        assertEquals(2, response.getContent().size());

        // Top candidate is iPhone due to exact query keyword match
        DiscoveryProductDTO top = response.getContent().get(0);
        assertEquals("Apple iPhone 15 Pro Max", top.getName());
        assertEquals(BigDecimal.valueOf(1149.00), top.getCurrentBestPrice());
        assertEquals(DealQuality.EXCELLENT_DEAL, top.getDealQuality());
        assertEquals(PriceTrend.FALLING, top.getPriceTrend());
        assertEquals(PurchaseSignal.BUY_NOW, top.getPurchaseSignal());
        assertTrue(Boolean.TRUE.equals(top.getIsHistoricalLow()));
        assertTrue(top.getDiscoveryBadges().contains("Best Match"));
        assertTrue(top.getDiscoveryBadges().contains("Lowest Historical Price"));
    }

    @Test
    @DisplayName("Rejects invalid sort parameters with IllegalArgumentException")
    void testInvalidSortParameterRejected() {
        DiscoverySearchRequestDTO request = DiscoverySearchRequestDTO.builder()
                .query("laptop")
                .sort("injectionField,asc")
                .build();

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () ->
                discoveryService.searchAndDiscover(request));

        assertTrue(ex.getMessage().contains("Invalid sort property"));
    }

    @Test
    @DisplayName("Handles empty search results cleanly")
    void testEmptySearchResults() {
        DiscoverySearchRequestDTO request = DiscoverySearchRequestDTO.builder()
                .query("nonexistent item 9999")
                .build();

        when(productRepository.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(Collections.emptyList()));
        when(productRepository.findDistinctCategories()).thenReturn(List.of("Electronics"));
        when(productRepository.findDistinctBrands()).thenReturn(List.of("Apple"));

        DiscoverySearchResponseDTO response = discoveryService.searchAndDiscover(request);

        assertNotNull(response);
        assertTrue(response.getContent().isEmpty());
        assertEquals(0, response.getTotalElements());
        assertEquals(0, response.getTotalPages());
    }

    @Test
    @DisplayName("Graceful degradation: analytics failure on candidate does not crash search")
    void testAnalyticsFailureGracefulDegradation() {
        DiscoverySearchRequestDTO request = DiscoverySearchRequestDTO.builder()
                .query("iphone")
                .build();

        when(productRepository.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(product1)));
        when(productPriceRepository.findPricesWithSellersByProductIds(any()))
                .thenReturn(Collections.emptyList());
        when(priceAnalyticsService.getProductAnalytics(product1.getId()))
                .thenThrow(new RuntimeException("Analytics engine timeout"));

        DiscoverySearchResponseDTO response = discoveryService.searchAndDiscover(request);

        assertNotNull(response);
        assertEquals(1, response.getContent().size());
        assertEquals("Apple iPhone 15 Pro Max", response.getContent().get(0).getName());
        assertNull(response.getContent().get(0).getDealQuality());
    }

    @Test
    @DisplayName("Returns autocomplete suggestions from brands, categories, and products")
    void testGetSuggestions() {
        when(productRepository.findDistinctBrands()).thenReturn(List.of("Apple", "Asus"));
        when(productRepository.findDistinctCategories()).thenReturn(List.of("Audio"));
        ProductEntity airpods = ProductEntity.builder().name("AirPods Pro").category("Audio").brand("Apple").build();
        airpods.setId(UUID.randomUUID());
        when(productRepository.findByNameStartingWithIgnoreCase(eq("a"), any(Pageable.class)))
                .thenReturn(List.of(airpods));

        List<SearchSuggestionDTO> suggestions = discoveryService.getSuggestions("a", 6);

        assertNotNull(suggestions);
        assertFalse(suggestions.isEmpty());
        assertTrue(suggestions.stream().anyMatch(s -> s.getText().equalsIgnoreCase("Apple")));
        assertTrue(suggestions.stream().anyMatch(s -> s.getText().equalsIgnoreCase("AirPods Pro")));
    }
}
