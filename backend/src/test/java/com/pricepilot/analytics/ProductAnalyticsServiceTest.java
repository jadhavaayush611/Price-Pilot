package com.pricepilot.analytics;

import com.pricepilot.analytics.dto.ProductAnalyticsProjection;
import com.pricepilot.analytics.dto.ProductAnalyticsResponseDTO;
import com.pricepilot.exception.ResourceNotFoundException;
import com.pricepilot.product.ProductEntity;
import com.pricepilot.product.ProductRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class ProductAnalyticsServiceTest {

    @Mock
    private ProductAnalyticsRepository productAnalyticsRepository;

    @Mock
    private ProductRepository productRepository;

    @InjectMocks
    private ProductAnalyticsService productAnalyticsService;

    private UUID productId;
    private ProductEntity product;

    @BeforeEach
    void setUp() {
        productId = UUID.randomUUID();
        product = ProductEntity.builder()
                .name("Test Product")
                .brand("Test Brand")
                .category("Test Category")
                .build();
        product.setId(productId);
    }

    @Test
    void testCalculateTrendingScore() {
        double score = productAnalyticsService.calculateTrendingScore(10, 5, 2, 3);
        // score = 10 * 1 + 5 * 5 + 2 * 10 + 3 * 2 = 10 + 25 + 20 + 6 = 61.0
        assertEquals(61.0, score);
    }

    @Test
    void testTrackView_Success() {
        when(productAnalyticsRepository.incrementViewCount(eq(productId), any(LocalDateTime.class))).thenReturn(1);

        productAnalyticsService.trackView(productId);

        verify(productAnalyticsRepository, times(1)).incrementViewCount(eq(productId), any(LocalDateTime.class));
        verify(productAnalyticsRepository, never()).save(any(ProductAnalyticsEntity.class));
    }

    @Test
    void testTrackView_SuccessAfterInitialization() {
        when(productAnalyticsRepository.incrementViewCount(eq(productId), any(LocalDateTime.class)))
                .thenReturn(0) // First try
                .thenReturn(1); // Second try after initialization

        when(productAnalyticsRepository.findByProductId(productId)).thenReturn(Optional.empty());
        when(productRepository.findById(productId)).thenReturn(Optional.of(product));

        productAnalyticsService.trackView(productId);

        verify(productAnalyticsRepository, times(2)).incrementViewCount(eq(productId), any(LocalDateTime.class));
        verify(productAnalyticsRepository, times(1)).saveAndFlush(any(ProductAnalyticsEntity.class));
    }

    @Test
    void testGetProductAnalytics_Success() {
        ProductAnalyticsProjection projection = mock(ProductAnalyticsProjection.class);
        when(projection.getProductId()).thenReturn(productId);
        when(projection.getViewCount()).thenReturn(100L);
        when(projection.getSaveCount()).thenReturn(5L);
        when(projection.getWatchlistCount()).thenReturn(2L);
        when(projection.getPriceChangeCount()).thenReturn(4L);

        when(productRepository.existsById(productId)).thenReturn(true);
        when(productAnalyticsRepository.findProjectionByProductId(productId)).thenReturn(Optional.of(projection));

        ProductAnalyticsResponseDTO result = productAnalyticsService.getProductAnalytics(productId);

        assertNotNull(result);
        assertEquals(productId, result.getProductId());
        assertEquals(100, result.getViewCount());
        assertEquals(5, result.getSaveCount());
        assertEquals(2, result.getWatchlistCount());
        assertEquals(4, result.getPriceChangeCount());
        // trendingScore = 100 * 1 + 5 * 5 + 2 * 10 + 4 * 2 = 100 + 25 + 20 + 8 = 153.0
        assertEquals(153.0, result.getTrendingScore());
    }

    @Test
    void testGetProductAnalytics_ProductNotFoundThrowsException() {
        when(productRepository.existsById(productId)).thenReturn(false);

        assertThrows(ResourceNotFoundException.class, () -> {
            productAnalyticsService.getProductAnalytics(productId);
        });
    }
}
