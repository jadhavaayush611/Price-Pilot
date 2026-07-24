package com.pricepilot.intelligence;

import com.pricepilot.intelligence.comparison.ComparisonServiceImpl;
import com.pricepilot.intelligence.comparison.dto.ComparisonRequest;
import com.pricepilot.intelligence.comparison.dto.ComparisonResponse;
import com.pricepilot.intelligence.comparison.repository.ComparisonSessionRepository;
import com.pricepilot.intelligence.comparison.repository.SavedComparisonRepository;
import com.pricepilot.product.ProductRepository;
import com.pricepilot.product.ProductService;
import com.pricepilot.product.dto.ProductResponseDTO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ComparisonServiceTest {

    @Mock
    private ProductRepository productRepository;

    @Mock
    private ProductService productService;

    @Mock
    private ComparisonSessionRepository comparisonSessionRepository;

    @Mock
    private SavedComparisonRepository savedComparisonRepository;

    private ComparisonServiceImpl comparisonService;

    @BeforeEach
    void setUp() {
        comparisonService = new ComparisonServiceImpl(
                productRepository,
                productService,
                comparisonSessionRepository,
                savedComparisonRepository
        );
    }

    @Test
    @DisplayName("compareProducts should return comparison matrix with scores and rows")
    void testCompareProducts() {
        UUID id1 = UUID.randomUUID();
        UUID id2 = UUID.randomUUID();

        ProductResponseDTO p1 = ProductResponseDTO.builder()
                .id(id1)
                .name("Laptop Pro")
                .brand("BrandA")
                .category("Electronics")
                .description("High-end laptop")
                .imageUrl("http://img1")
                .prices(List.of())
                .build();

        ProductResponseDTO p2 = ProductResponseDTO.builder()
                .id(id2)
                .name("Laptop Air")
                .brand("BrandB")
                .category("Electronics")
                .description("Thin laptop")
                .imageUrl("http://img2")
                .prices(List.of())
                .build();

        when(productService.getProductById(id1)).thenReturn(p1);
        when(productService.getProductById(id2)).thenReturn(p2);

        ComparisonRequest request = new ComparisonRequest(List.of(id1, id2), "Electronics", List.of("Price", "Brand"), null, null);
        ComparisonResponse response = comparisonService.compareProducts(request);

        assertNotNull(response);
        assertEquals(2, response.getProducts().size());
        assertFalse(response.getRows().isEmpty());
        assertEquals(2, response.getScores().size());
        assertNotNull(response.getSummary());
    }

    @Test
    @DisplayName("compareProducts with empty IDs should return graceful empty response")
    void testCompareProductsEmpty() {
        ComparisonResponse response = comparisonService.compareProducts(List.of());
        assertNotNull(response);
        assertTrue(response.getProducts().isEmpty());
    }
}
