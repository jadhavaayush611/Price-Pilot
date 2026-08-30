package com.pricepilot.intelligence.analytics;

import com.pricepilot.analytics.ProductAnalyticsService;
import com.pricepilot.analytics.dto.ProductAnalyticsResponseDTO;
import com.pricepilot.intelligence.analytics.engine.AdvancedPriceAnalyticsEngine;
import com.pricepilot.intelligence.analytics.model.*;
import com.pricepilot.pricehistory.PriceHistoryEntity;
import com.pricepilot.pricehistory.PriceHistoryRepository;
import com.pricepilot.product.ProductService;
import com.pricepilot.product.dto.ProductResponseDTO;
import com.pricepilot.productprice.dto.ProductPriceResponseDTO;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PriceAnalyticsServiceImplTest {

    @Mock
    private ProductAnalyticsService coreProductAnalyticsService;

    @Mock
    private ProductService productService;

    @Mock
    private PriceHistoryRepository priceHistoryRepository;

    @Mock
    private AdvancedPriceAnalyticsEngine priceAnalyticsEngine;

    private SimpleMeterRegistry meterRegistry;
    private PriceAnalyticsServiceImpl service;

    private UUID productId;

    @BeforeEach
    void setUp() {
        meterRegistry = new SimpleMeterRegistry();
        service = new PriceAnalyticsServiceImpl(
                coreProductAnalyticsService,
                productService,
                priceHistoryRepository,
                priceAnalyticsEngine,
                meterRegistry
        );
        productId = UUID.randomUUID();
    }

    @Test
    @DisplayName("getProductAnalytics aggregates core engagement and advanced price intelligence")
    void testGetProductAnalyticsSuccess() {
        ProductResponseDTO product = new ProductResponseDTO();
        product.setId(productId);
        ProductPriceResponseDTO pr = new ProductPriceResponseDTO();
        pr.setCurrentPrice(BigDecimal.valueOf(199.99));
        product.setPrices(List.of(pr));

        ProductAnalyticsResponseDTO engagement = ProductAnalyticsResponseDTO.builder()
                .productId(productId)
                .viewCount(100)
                .saveCount(20)
                .watchlistCount(15)
                .priceChangeCount(4)
                .trendingScore(75.0)
                .build();

        PriceStatistics stats = PriceStatistics.builder()
                .currentPrice(BigDecimal.valueOf(199.99))
                .historicalMin(BigDecimal.valueOf(180.00))
                .historicalMax(BigDecimal.valueOf(250.00))
                .historicalAvg(BigDecimal.valueOf(210.00))
                .historicalMedian(BigDecimal.valueOf(205.00))
                .priceRange(BigDecimal.valueOf(70.00))
                .observationCount(5)
                .historicalLowDistance(BigDecimal.valueOf(19.99))
                .historicalAverageDistance(BigDecimal.valueOf(-10.01))
                .build();

        AdvancedPriceAnalyticsEngine.AnalysisResult mockResult = new AdvancedPriceAnalyticsEngine.AnalysisResult(
                stats,
                new com.pricepilot.intelligence.analytics.engine.StatisticalPriceAnalyzer.VolatilityResult(0.04, PriceVolatility.LOW),
                new com.pricepilot.intelligence.analytics.engine.PriceTrendAnalyzer.TrendResult(PriceTrend.FALLING, -4.5),
                new com.pricepilot.intelligence.analytics.engine.PricePositionAnalyzer.PositionResult(75.0, DealQuality.GOOD_DEAL),
                new com.pricepilot.intelligence.analytics.engine.PurchaseSignalGenerator.SignalResult(PurchaseSignal.GOOD_TIME, "Good time to buy", List.of("Below average")),
                List.of(),
                List.of(HistoricalPricePoint.builder().price(BigDecimal.valueOf(199.99)).timestamp(LocalDateTime.now()).build())
        );

        when(productService.getProductById(productId)).thenReturn(product);
        when(coreProductAnalyticsService.getProductAnalytics(productId)).thenReturn(engagement);
        when(priceHistoryRepository.findByProductIdChronological(productId)).thenReturn(List.of());
        when(priceAnalyticsEngine.analyze(any(), any())).thenReturn(mockResult);

        ProductAnalyticsResponseDTO result = service.getProductAnalytics(productId);

        assertNotNull(result);
        assertEquals(productId, result.getProductId());
        // Engagement metrics preserved
        assertEquals(100, result.getViewCount());
        assertEquals(20, result.getSaveCount());
        // Advanced price intelligence
        assertEquals(BigDecimal.valueOf(199.99), result.getCurrentPrice());
        assertEquals(BigDecimal.valueOf(180.00), result.getHistoricalMin());
        assertEquals(PriceVolatility.LOW, result.getVolatility());
        assertEquals(PriceTrend.FALLING, result.getTrend());
        assertEquals(DealQuality.GOOD_DEAL, result.getDealQuality());
        assertEquals(PurchaseSignal.GOOD_TIME, result.getPurchaseSignal());
        assertFalse(result.getSupportingEvidence().isEmpty());
    }

    @Test
    @DisplayName("Throws IllegalArgumentException when productId is null")
    void testNullProductId() {
        assertThrows(IllegalArgumentException.class, () -> service.getProductAnalytics(null));
    }
}
