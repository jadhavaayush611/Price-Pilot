package com.pricepilot.savedproduct;

import com.pricepilot.exception.DuplicateSaveException;
import com.pricepilot.exception.ProductArchivedException;
import com.pricepilot.exception.ResourceNotFoundException;
import com.pricepilot.product.ProductEntity;
import com.pricepilot.product.ProductRepository;
import com.pricepilot.productprice.ProductPriceRepository;
import com.pricepilot.productprice.dto.BestPriceProjection;
import com.pricepilot.savedproduct.dto.SavedProductResponseDTO;
import com.pricepilot.user.UserEntity;
import com.pricepilot.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class SavedProductServiceTest {

    @Mock
    private SavedProductRepository savedProductRepository;

    @Mock
    private ProductRepository productRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private ProductPriceRepository productPriceRepository;

    @Mock
    private com.pricepilot.analytics.ProductAnalyticsService productAnalyticsService;

    @Mock
    private com.pricepilot.interaction.UserInteractionEventService eventService;

    @Mock
    private com.pricepilot.recommendation.RecommendationCacheHelper cacheHelper;

    @InjectMocks
    private SavedProductService savedProductService;

    private UserEntity user;
    private ProductEntity activeProduct;
    private ProductEntity archivedProduct;

    @BeforeEach
    void setUp() {
        user = UserEntity.builder()
                .email("test@example.com")
                .firstName("John")
                .lastName("Doe")
                .build();
        user.setId(UUID.randomUUID());

        activeProduct = ProductEntity.builder()
                .name("Active Product")
                .brand("Brand A")
                .category("Category X")
                .archived(false)
                .build();
        activeProduct.setId(UUID.randomUUID());

        archivedProduct = ProductEntity.builder()
                .name("Archived Product")
                .brand("Brand B")
                .category("Category Y")
                .archived(true)
                .build();
        archivedProduct.setId(UUID.randomUUID());
    }

    @Test
    void testSaveProduct_Success() {
        when(userRepository.findByEmail("test@example.com")).thenReturn(Optional.of(user));
        when(productRepository.findById(activeProduct.getId())).thenReturn(Optional.of(activeProduct));
        when(savedProductRepository.existsById(any(SavedProductId.class))).thenReturn(false);

        savedProductService.saveProduct("test@example.com", activeProduct.getId());

        verify(savedProductRepository, times(1)).save(any(SavedProductEntity.class));
        verify(productAnalyticsService, times(1)).incrementSaveCount(activeProduct.getId());
    }

    @Test
    void testSaveProduct_ArchivedThrowsException() {
        when(userRepository.findByEmail("test@example.com")).thenReturn(Optional.of(user));
        when(productRepository.findById(archivedProduct.getId())).thenReturn(Optional.of(archivedProduct));

        ProductArchivedException ex = assertThrows(ProductArchivedException.class, () -> {
            savedProductService.saveProduct("test@example.com", archivedProduct.getId());
        });
        assertEquals("Cannot save an archived product", ex.getMessage());
        verify(savedProductRepository, never()).save(any(SavedProductEntity.class));
    }

    @Test
    void testSaveProduct_DuplicateThrowsException() {
        when(userRepository.findByEmail("test@example.com")).thenReturn(Optional.of(user));
        when(productRepository.findById(activeProduct.getId())).thenReturn(Optional.of(activeProduct));
        when(savedProductRepository.existsById(any(SavedProductId.class))).thenReturn(true);

        DuplicateSaveException ex = assertThrows(DuplicateSaveException.class, () -> {
            savedProductService.saveProduct("test@example.com", activeProduct.getId());
        });
        assertEquals("Product is already saved by this user", ex.getMessage());
        verify(savedProductRepository, never()).save(any(SavedProductEntity.class));
    }

    @Test
    void testRemoveProduct_Success() {
        when(userRepository.findByEmail("test@example.com")).thenReturn(Optional.of(user));
        when(savedProductRepository.existsById(any(SavedProductId.class))).thenReturn(true);

        savedProductService.removeProduct("test@example.com", activeProduct.getId());

        verify(savedProductRepository, times(1)).deleteById(any(SavedProductId.class));
        verify(productAnalyticsService, times(1)).decrementSaveCount(activeProduct.getId());
    }

    @Test
    void testRemoveProduct_NotFoundThrowsException() {
        when(userRepository.findByEmail("test@example.com")).thenReturn(Optional.of(user));
        when(savedProductRepository.existsById(any(SavedProductId.class))).thenReturn(false);

        ResourceNotFoundException ex = assertThrows(ResourceNotFoundException.class, () -> {
            savedProductService.removeProduct("test@example.com", activeProduct.getId());
        });
        assertEquals("Saved product association not found for this user", ex.getMessage());
        verify(savedProductRepository, never()).deleteById(any(SavedProductId.class));
    }

    @Test
    void testGetSavedProducts_SuccessWithActiveOnlyAndOptimizedQuery() {
        // Prepare list of saved entities
        SavedProductEntity sp = SavedProductEntity.builder()
                .id(new SavedProductId(user.getId(), activeProduct.getId()))
                .user(user)
                .product(activeProduct)
                .createdAt(LocalDateTime.now())
                .build();

        when(userRepository.findByEmail("test@example.com")).thenReturn(Optional.of(user));
        when(savedProductRepository.findAllByUserIdWithProduct(user.getId())).thenReturn(List.of(sp));

        // Mock projection
        BestPriceProjection projection = mock(BestPriceProjection.class);
        when(projection.getProductId()).thenReturn(activeProduct.getId());
        when(projection.getBestPrice()).thenReturn(new BigDecimal("199.99"));

        when(productPriceRepository.findBestPricesByProductIds(List.of(activeProduct.getId())))
                .thenReturn(List.of(projection));

        List<SavedProductResponseDTO> result = savedProductService.getSavedProducts("test@example.com");

        assertNotNull(result);
        assertEquals(1, result.size());
        SavedProductResponseDTO dto = result.get(0);
        assertEquals(activeProduct.getId(), dto.getProductId());
        assertEquals("Active Product", dto.getName());
        assertEquals("Brand A", dto.getBrand());
        assertEquals(new BigDecimal("199.99"), dto.getBestPrice());
    }
}
