package com.pricepilot.productprice;

import com.pricepilot.exception.ResourceNotFoundException;
import com.pricepilot.pricehistory.PriceHistoryService;
import com.pricepilot.product.ProductEntity;
import com.pricepilot.product.ProductRepository;
import com.pricepilot.seller.SellerEntity;
import com.pricepilot.seller.SellerRepository;
import com.pricepilot.productprice.dto.ProductPriceRequestDTO;
import com.pricepilot.productprice.dto.ProductPriceResponseDTO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class ProductPriceServiceTest {

    @Mock
    private ProductPriceRepository productPriceRepository;

    @Mock
    private ProductRepository productRepository;

    @Mock
    private SellerRepository sellerRepository;

    @Mock
    private PriceHistoryService priceHistoryService;

    @InjectMocks
    private ProductPriceService productPriceService;

    private UUID priceId;
    private ProductEntity product;
    private SellerEntity seller;
    private ProductPriceEntity productPrice;
    private ProductPriceRequestDTO updateRequest;

    @BeforeEach
    void setUp() {
        priceId = UUID.randomUUID();

        product = ProductEntity.builder()
                .name("iPhone 15 Pro")
                .brand("Apple")
                .category("Electronics")
                .build();
        product.setId(UUID.randomUUID());

        seller = SellerEntity.builder()
                .name("Amazon")
                .websiteUrl("https://amazon.com")
                .build();
        seller.setId(UUID.randomUUID());

        productPrice = ProductPriceEntity.builder()
                .currentPrice(new BigDecimal("67999.00"))
                .originalPrice(new BigDecimal("67999.00"))
                .discountPercentage(BigDecimal.ZERO)
                .productUrl("https://amazon.com/iphone15")
                .product(product)
                .seller(seller)
                .build();
        productPrice.setId(priceId);

        updateRequest = ProductPriceRequestDTO.builder()
                .productId(product.getId())
                .sellerId(seller.getId())
                .currentPrice(new BigDecimal("64999.00"))
                .originalPrice(new BigDecimal("67999.00"))
                .productUrl("https://amazon.com/iphone15")
                .build();
    }

    @Test
    void testUpdateProductPrice_whenPriceChanges_shouldRecordHistory() {
        when(productPriceRepository.findById(priceId)).thenReturn(Optional.of(productPrice));
        when(productRepository.findById(product.getId())).thenReturn(Optional.of(product));
        when(sellerRepository.findById(seller.getId())).thenReturn(Optional.of(seller));
        when(productPriceRepository.save(any(ProductPriceEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

        ProductPriceResponseDTO response = productPriceService.updateProductPrice(priceId, updateRequest);

        assertNotNull(response);
        assertEquals(new BigDecimal("64999.00"), response.getCurrentPrice());

        // Verify PriceHistoryService was called
        verify(priceHistoryService, times(1)).recordPriceHistory(
                eq(product), eq(seller), eq(new BigDecimal("67999.00")), eq(new BigDecimal("64999.00"))
        );
    }

    @Test
    void testUpdateProductPrice_whenPriceDoesNotChange_shouldNotRecordHistory() {
        // Set request price equal to current price
        updateRequest.setCurrentPrice(new BigDecimal("67999.00"));

        when(productPriceRepository.findById(priceId)).thenReturn(Optional.of(productPrice));
        when(productRepository.findById(product.getId())).thenReturn(Optional.of(product));
        when(sellerRepository.findById(seller.getId())).thenReturn(Optional.of(seller));
        when(productPriceRepository.save(any(ProductPriceEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

        ProductPriceResponseDTO response = productPriceService.updateProductPrice(priceId, updateRequest);

        assertNotNull(response);
        assertEquals(new BigDecimal("67999.00"), response.getCurrentPrice());

        // Verify PriceHistoryService was never called since price is same
        verify(priceHistoryService, never()).recordPriceHistory(any(), any(), any(), any());
    }

    @Test
    void testUpdateProductPrice_whenProductPriceNotFound_shouldThrowException() {
        when(productPriceRepository.findById(priceId)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> {
            productPriceService.updateProductPrice(priceId, updateRequest);
        });

        verify(priceHistoryService, never()).recordPriceHistory(any(), any(), any(), any());
    }
}
