package com.pricepilot.product;

import com.pricepilot.product.dto.ProductSearchResultDTO;
import com.pricepilot.product.dto.PageResponse;
import com.pricepilot.productprice.ProductPriceEntity;
import com.pricepilot.productprice.ProductPriceRepository;
import com.pricepilot.seller.SellerEntity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.jpa.domain.Specification;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class ProductServiceTest {

    @Mock
    private ProductRepository productRepository;

    @Mock
    private ProductPriceRepository productPriceRepository;

    @InjectMocks
    private ProductService productService;

    private ProductEntity product1;
    private ProductEntity product2;
    private List<ProductEntity> products;

    @BeforeEach
    void setUp() {
        product1 = ProductEntity.builder()
                .name("iPhone 15 Pro")
                .brand("Apple")
                .category("Electronics")
                .description("Latest iPhone model")
                .imageUrl("iphone15.jpg")
                .build();
        product1.setId(UUID.randomUUID());

        product2 = ProductEntity.builder()
                .name("Galaxy S24")
                .brand("Samsung")
                .category("Electronics")
                .description("Flagship Android phone")
                .imageUrl("galaxy24.jpg")
                .build();
        product2.setId(UUID.randomUUID());

        products = Arrays.asList(product1, product2);
    }

    @Test
    void testSearchProducts_Success() {
        // Arrange
        Page<ProductEntity> page = new PageImpl<>(products);
        when(productRepository.findAll(any(Specification.class), any(PageRequest.class))).thenReturn(page);

        SellerEntity seller = SellerEntity.builder().name("Amazon").websiteUrl("amazon.com").logoUrl("logo.png").build();
        seller.setId(UUID.randomUUID());

        ProductPriceEntity price1 = ProductPriceEntity.builder()
                .product(product1)
                .seller(seller)
                .currentPrice(new BigDecimal("999.00"))
                .originalPrice(new BigDecimal("1099.00"))
                .discountPercentage(new BigDecimal("9.10"))
                .lastUpdated(LocalDateTime.now())
                .build();
        price1.setId(UUID.randomUUID());

        ProductPriceEntity price2 = ProductPriceEntity.builder()
                .product(product2)
                .seller(seller)
                .currentPrice(new BigDecimal("899.00"))
                .originalPrice(new BigDecimal("899.00"))
                .discountPercentage(BigDecimal.ZERO)
                .lastUpdated(LocalDateTime.now())
                .build();
        price2.setId(UUID.randomUUID());

        when(productPriceRepository.findPricesWithSellersByProductIds(anyList()))
                .thenReturn(Arrays.asList(price1, price2));

        // Act
        PageResponse<ProductSearchResultDTO> results = productService.searchProducts("phone", "Electronics", "All", 0, 10, "price-asc");

        // Assert
        assertNotNull(results);
        assertEquals(2, results.getContent().size());
        assertEquals(0, results.getNumber());
        assertEquals(2, results.getSize());
        assertEquals(2L, results.getTotalElements());
        assertEquals(1, results.getTotalPages());

        ProductSearchResultDTO dto1 = results.getContent().get(0);
        assertEquals("iPhone 15 Pro", dto1.getName());
        assertEquals(new BigDecimal("999.00"), dto1.getLowestPrice());
        assertEquals(1, dto1.getPrices().size());
        assertEquals("Amazon", dto1.getPrices().get(0).getSeller().getName());

        verify(productRepository, times(1)).findAll(any(Specification.class), any(PageRequest.class));
        verify(productPriceRepository, times(1)).findPricesWithSellersByProductIds(anyList());
    }

    @Test
    void testGetProductById_Success() {
        // Arrange
        UUID productId = product1.getId();
        
        SellerEntity seller = SellerEntity.builder().name("Amazon").websiteUrl("amazon.com").logoUrl("logo.png").build();
        seller.setId(UUID.randomUUID());

        ProductPriceEntity price = ProductPriceEntity.builder()
                .product(product1)
                .seller(seller)
                .currentPrice(new BigDecimal("999.00"))
                .originalPrice(new BigDecimal("1099.00"))
                .discountPercentage(new BigDecimal("9.10"))
                .lastUpdated(LocalDateTime.now())
                .build();
        price.setId(UUID.randomUUID());
        
        product1.setProductPrices(Arrays.asList(price));

        when(productRepository.findByIdWithPricesAndSellers(productId)).thenReturn(java.util.Optional.of(product1));

        // Act
        com.pricepilot.product.dto.ProductResponseDTO result = productService.getProductById(productId);

        // Assert
        assertNotNull(result);
        assertEquals("iPhone 15 Pro", result.getName());
        assertNotNull(result.getPrices());
        assertEquals(1, result.getPrices().size());
        assertEquals(new BigDecimal("999.00"), result.getPrices().get(0).getCurrentPrice());
        assertEquals("Amazon", result.getPrices().get(0).getSeller().getName());

        verify(productRepository, times(1)).findByIdWithPricesAndSellers(productId);
    }
}
