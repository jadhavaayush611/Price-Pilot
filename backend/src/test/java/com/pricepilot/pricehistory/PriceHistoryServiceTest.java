package com.pricepilot.pricehistory;

import com.pricepilot.pricehistory.dto.PriceHistoryResponseDTO;
import com.pricepilot.product.ProductEntity;
import com.pricepilot.seller.SellerEntity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class PriceHistoryServiceTest {

    @Mock
    private PriceHistoryRepository priceHistoryRepository;

    @InjectMocks
    private PriceHistoryService priceHistoryService;

    private ProductEntity product;
    private SellerEntity seller;
    private PriceHistoryEntity priceHistory;

    @BeforeEach
    void setUp() {
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

        priceHistory = PriceHistoryEntity.builder()
                .product(product)
                .seller(seller)
                .oldPrice(new BigDecimal("67999.00"))
                .newPrice(new BigDecimal("64999.00"))
                .priceDifference(new BigDecimal("-3000.00"))
                .changePercentage(new BigDecimal("-4.41"))
                .changedAt(LocalDateTime.now())
                .build();
        priceHistory.setId(UUID.randomUUID());
    }

    @Test
    void testRecordPriceHistory_whenPricesAreDifferent_shouldSave() {
        BigDecimal oldPrice = new BigDecimal("100.00");
        BigDecimal newPrice = new BigDecimal("90.00");

        priceHistoryService.recordPriceHistory(product, seller, oldPrice, newPrice);

        ArgumentCaptor<PriceHistoryEntity> captor = ArgumentCaptor.forClass(PriceHistoryEntity.class);
        verify(priceHistoryRepository, times(1)).save(captor.capture());

        PriceHistoryEntity saved = captor.getValue();
        assertEquals(product, saved.getProduct());
        assertEquals(seller, saved.getSeller());
        assertEquals(oldPrice, saved.getOldPrice());
        assertEquals(newPrice, saved.getNewPrice());
        assertEquals(new BigDecimal("-10.00"), saved.getPriceDifference());
        assertEquals(new BigDecimal("-10.00"), saved.getChangePercentage());
        assertNotNull(saved.getChangedAt());
    }

    @Test
    void testRecordPriceHistory_whenPricesAreSame_shouldNotSave() {
        BigDecimal price = new BigDecimal("100.00");

        priceHistoryService.recordPriceHistory(product, seller, price, price);

        verify(priceHistoryRepository, never()).save(any(PriceHistoryEntity.class));
    }

    @Test
    void testGetAllPriceHistory() {
        Pageable pageable = PageRequest.of(0, 10);
        Page<PriceHistoryEntity> page = new PageImpl<>(List.of(priceHistory), pageable, 1);
        when(priceHistoryRepository.findAllWithRelations(pageable)).thenReturn(page);

        Page<PriceHistoryResponseDTO> result = priceHistoryService.getAllPriceHistory(pageable);

        assertNotNull(result);
        assertEquals(1, result.getTotalElements());
        assertEquals(priceHistory.getId(), result.getContent().get(0).getId());
        verify(priceHistoryRepository, times(1)).findAllWithRelations(pageable);
    }

    @Test
    void testGetPriceHistoryByProduct() {
        UUID productId = product.getId();
        Pageable pageable = PageRequest.of(0, 10);
        Page<PriceHistoryEntity> page = new PageImpl<>(List.of(priceHistory), pageable, 1);
        when(priceHistoryRepository.findByProductIdWithRelations(productId, pageable)).thenReturn(page);

        Page<PriceHistoryResponseDTO> result = priceHistoryService.getPriceHistoryByProduct(productId, pageable);

        assertNotNull(result);
        assertEquals(1, result.getTotalElements());
        verify(priceHistoryRepository, times(1)).findByProductIdWithRelations(productId, pageable);
    }

    @Test
    void testGetPriceHistoryBySeller() {
        UUID sellerId = seller.getId();
        Pageable pageable = PageRequest.of(0, 10);
        Page<PriceHistoryEntity> page = new PageImpl<>(List.of(priceHistory), pageable, 1);
        when(priceHistoryRepository.findBySellerIdWithRelations(sellerId, pageable)).thenReturn(page);

        Page<PriceHistoryResponseDTO> result = priceHistoryService.getPriceHistoryBySeller(sellerId, pageable);

        assertNotNull(result);
        assertEquals(1, result.getTotalElements());
        verify(priceHistoryRepository, times(1)).findBySellerIdWithRelations(sellerId, pageable);
    }

    @Test
    void testGetLargestPriceDrops() {
        when(priceHistoryRepository.findLargestPriceDrops(any(Pageable.class))).thenReturn(List.of(priceHistory));

        List<PriceHistoryResponseDTO> drops = priceHistoryService.getLargestPriceDrops(5);

        assertNotNull(drops);
        assertEquals(1, drops.size());
        verify(priceHistoryRepository, times(1)).findLargestPriceDrops(any(Pageable.class));
    }

    @Test
    void testGetLargestPriceIncreases() {
        when(priceHistoryRepository.findLargestPriceIncreases(any(Pageable.class))).thenReturn(Collections.emptyList());

        List<PriceHistoryResponseDTO> increases = priceHistoryService.getLargestPriceIncreases(5);

        assertNotNull(increases);
        assertTrue(increases.isEmpty());
        verify(priceHistoryRepository, times(1)).findLargestPriceIncreases(any(Pageable.class));
    }

    @Test
    void testGetRecentPriceChanges() {
        when(priceHistoryRepository.findRecentPriceChanges(any(Pageable.class))).thenReturn(List.of(priceHistory));

        List<PriceHistoryResponseDTO> recent = priceHistoryService.getRecentPriceChanges(5);

        assertNotNull(recent);
        assertEquals(1, recent.size());
        verify(priceHistoryRepository, times(1)).findRecentPriceChanges(any(Pageable.class));
    }
}
