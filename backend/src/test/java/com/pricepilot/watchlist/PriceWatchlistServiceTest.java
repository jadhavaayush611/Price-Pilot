package com.pricepilot.watchlist;

import com.pricepilot.exception.DuplicateWatchlistException;
import com.pricepilot.exception.ResourceNotFoundException;
import com.pricepilot.product.ProductEntity;
import com.pricepilot.product.ProductRepository;
import com.pricepilot.productprice.ProductPriceRepository;
import com.pricepilot.user.UserEntity;
import com.pricepilot.user.UserRepository;
import com.pricepilot.watchlist.dto.CreateWatchlistRequestDTO;
import com.pricepilot.watchlist.dto.UpdateWatchlistRequestDTO;
import com.pricepilot.watchlist.dto.WatchlistResponseDTO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class PriceWatchlistServiceTest {

    @Mock
    private PriceWatchlistRepository watchlistRepository;

    @Mock
    private ProductRepository productRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private ProductPriceRepository productPriceRepository;

    @InjectMocks
    private PriceWatchlistService watchlistService;

    private UserEntity user;
    private UserEntity otherUser;
    private ProductEntity activeProduct;
    private ProductEntity archivedProduct;
    private PriceWatchlistEntity watchlist;

    @BeforeEach
    void setUp() {
        user = UserEntity.builder()
                .email("test@example.com")
                .firstName("John")
                .lastName("Doe")
                .build();
        user.setId(UUID.randomUUID());

        otherUser = UserEntity.builder()
                .email("other@example.com")
                .firstName("Jane")
                .lastName("Doe")
                .build();
        otherUser.setId(UUID.randomUUID());

        activeProduct = ProductEntity.builder()
                .name("iPhone 15")
                .brand("Apple")
                .category("Electronics")
                .archived(false)
                .build();
        activeProduct.setId(UUID.randomUUID());

        archivedProduct = ProductEntity.builder()
                .name("Old Product")
                .brand("Generic")
                .category("Stuff")
                .archived(true)
                .build();
        archivedProduct.setId(UUID.randomUUID());

        watchlist = PriceWatchlistEntity.builder()
                .user(user)
                .product(activeProduct)
                .targetPrice(new BigDecimal("60000.00"))
                .currentBestPrice(new BigDecimal("67999.00"))
                .active(true)
                .build();
        watchlist.setId(UUID.randomUUID());
        watchlist.setCreatedAt(LocalDateTime.now());
        watchlist.setUpdatedAt(LocalDateTime.now());
    }

    @Test
    void testCreateWatchlist_Success() {
        CreateWatchlistRequestDTO request = CreateWatchlistRequestDTO.builder()
                .productId(activeProduct.getId())
                .targetPrice(new BigDecimal("60000.00"))
                .build();

        when(userRepository.findByEmail("test@example.com")).thenReturn(Optional.of(user));
        when(productRepository.findById(activeProduct.getId())).thenReturn(Optional.of(activeProduct));
        when(watchlistRepository.existsByUserIdAndProductId(user.getId(), activeProduct.getId())).thenReturn(false);
        when(productPriceRepository.findBestPriceByProductId(activeProduct.getId())).thenReturn(Optional.of(new BigDecimal("67999.00")));
        when(watchlistRepository.save(any(PriceWatchlistEntity.class))).thenReturn(watchlist);

        WatchlistResponseDTO response = watchlistService.createWatchlist("test@example.com", request);

        assertNotNull(response);
        assertEquals(watchlist.getId(), response.getId());
        assertEquals(activeProduct.getId(), response.getProductId());
        assertEquals("iPhone 15", response.getProductName());
        assertEquals(new BigDecimal("60000.00"), response.getTargetPrice());
        assertEquals(new BigDecimal("67999.00"), response.getCurrentBestPrice());
        assertEquals(new BigDecimal("7999.00"), response.getPriceDifference());
        assertTrue(response.isActive());
    }

    @Test
    void testCreateWatchlist_ProductArchivedThrowsException() {
        CreateWatchlistRequestDTO request = CreateWatchlistRequestDTO.builder()
                .productId(archivedProduct.getId())
                .targetPrice(new BigDecimal("60000.00"))
                .build();

        when(userRepository.findByEmail("test@example.com")).thenReturn(Optional.of(user));
        when(productRepository.findById(archivedProduct.getId())).thenReturn(Optional.of(archivedProduct));

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> {
            watchlistService.createWatchlist("test@example.com", request);
        });
        assertEquals("Product must be active", ex.getMessage());
        verify(watchlistRepository, never()).save(any(PriceWatchlistEntity.class));
    }

    @Test
    void testCreateWatchlist_DuplicateThrowsException() {
        CreateWatchlistRequestDTO request = CreateWatchlistRequestDTO.builder()
                .productId(activeProduct.getId())
                .targetPrice(new BigDecimal("60000.00"))
                .build();

        when(userRepository.findByEmail("test@example.com")).thenReturn(Optional.of(user));
        when(productRepository.findById(activeProduct.getId())).thenReturn(Optional.of(activeProduct));
        when(watchlistRepository.existsByUserIdAndProductId(user.getId(), activeProduct.getId())).thenReturn(true);

        DuplicateWatchlistException ex = assertThrows(DuplicateWatchlistException.class, () -> {
            watchlistService.createWatchlist("test@example.com", request);
        });
        assertEquals("You are already watching this product", ex.getMessage());
        verify(watchlistRepository, never()).save(any(PriceWatchlistEntity.class));
    }

    @Test
    void testCreateWatchlist_TargetPriceGreaterThanBestPriceThrowsException() {
        CreateWatchlistRequestDTO request = CreateWatchlistRequestDTO.builder()
                .productId(activeProduct.getId())
                .targetPrice(new BigDecimal("70000.00"))
                .build();

        when(userRepository.findByEmail("test@example.com")).thenReturn(Optional.of(user));
        when(productRepository.findById(activeProduct.getId())).thenReturn(Optional.of(activeProduct));
        when(watchlistRepository.existsByUserIdAndProductId(user.getId(), activeProduct.getId())).thenReturn(false);
        when(productPriceRepository.findBestPriceByProductId(activeProduct.getId())).thenReturn(Optional.of(new BigDecimal("67999.00")));

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> {
            watchlistService.createWatchlist("test@example.com", request);
        });
        assertTrue(ex.getMessage().contains("Target price must be less than the current best price"));
        verify(watchlistRepository, never()).save(any(PriceWatchlistEntity.class));
    }

    @Test
    void testUpdateWatchlist_Success() {
        UpdateWatchlistRequestDTO request = UpdateWatchlistRequestDTO.builder()
                .targetPrice(new BigDecimal("55000.00"))
                .active(false)
                .build();

        when(watchlistRepository.findByIdWithRelations(watchlist.getId())).thenReturn(Optional.of(watchlist));
        when(productPriceRepository.findBestPriceByProductId(activeProduct.getId())).thenReturn(Optional.of(new BigDecimal("67999.00")));
        when(watchlistRepository.save(any(PriceWatchlistEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

        WatchlistResponseDTO response = watchlistService.updateWatchlist("test@example.com", watchlist.getId(), request);

        assertNotNull(response);
        assertEquals(new BigDecimal("55000.00"), response.getTargetPrice());
        assertFalse(response.isActive());
    }

    @Test
    void testUpdateWatchlist_ForbiddenForDifferentUser() {
        UpdateWatchlistRequestDTO request = UpdateWatchlistRequestDTO.builder()
                .targetPrice(new BigDecimal("55000.00"))
                .build();

        when(watchlistRepository.findByIdWithRelations(watchlist.getId())).thenReturn(Optional.of(watchlist));

        assertThrows(AccessDeniedException.class, () -> {
            watchlistService.updateWatchlist("other@example.com", watchlist.getId(), request);
        });
        verify(watchlistRepository, never()).save(any(PriceWatchlistEntity.class));
    }

    @Test
    void testDeleteWatchlist_Success() {
        when(watchlistRepository.findByIdWithRelations(watchlist.getId())).thenReturn(Optional.of(watchlist));

        watchlistService.deleteWatchlist("test@example.com", watchlist.getId());

        verify(watchlistRepository, times(1)).delete(watchlist);
    }

    @Test
    void testDeleteWatchlist_ForbiddenForDifferentUser() {
        when(watchlistRepository.findByIdWithRelations(watchlist.getId())).thenReturn(Optional.of(watchlist));

        assertThrows(AccessDeniedException.class, () -> {
            watchlistService.deleteWatchlist("other@example.com", watchlist.getId());
        });
        verify(watchlistRepository, never()).delete(any(PriceWatchlistEntity.class));
    }

    @Test
    void testGetAllWatchlists_Success() {
        when(userRepository.findByEmail("test@example.com")).thenReturn(Optional.of(user));
        when(watchlistRepository.findAllByUserIdWithProduct(user.getId())).thenReturn(List.of(watchlist));

        List<WatchlistResponseDTO> response = watchlistService.getAllWatchlists("test@example.com");

        assertNotNull(response);
        assertEquals(1, response.size());
        assertEquals(watchlist.getId(), response.get(0).getId());
    }
}
