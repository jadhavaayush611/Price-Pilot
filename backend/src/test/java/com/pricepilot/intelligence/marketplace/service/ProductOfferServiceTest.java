package com.pricepilot.intelligence.marketplace.service;

import com.pricepilot.exception.ResourceNotFoundException;
import com.pricepilot.intelligence.marketplace.adapter.MarketplaceOfferAggregatorService;
import com.pricepilot.intelligence.marketplace.adapter.MarketplaceQueryContext;
import com.pricepilot.intelligence.marketplace.dto.NormalizedOfferDTO;
import com.pricepilot.intelligence.marketplace.model.ProvenanceType;
import com.pricepilot.intelligence.marketplace.model.VerificationStatus;
import com.pricepilot.product.ProductEntity;
import com.pricepilot.product.ProductRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("ProductOfferService Integration & Offer Aggregation Tests")
class ProductOfferServiceTest {

    @Mock
    private ProductRepository productRepository;

    @Mock
    private MarketplaceOfferAggregatorService aggregatorService;

    private ProductOfferServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new ProductOfferServiceImpl(productRepository, aggregatorService);
    }

    @Test
    @DisplayName("Returns empty list safely when productId is null")
    void testGetProductOffers_nullIdReturnsEmpty() {
        List<NormalizedOfferDTO> result = service.getProductOffers(null);
        assertNotNull(result);
        assertTrue(result.isEmpty());
        verifyNoInteractions(productRepository);
        verifyNoInteractions(aggregatorService);
    }

    @Test
    @DisplayName("Throws ResourceNotFoundException when product does not exist in repository")
    void testGetProductOffers_productNotFoundThrows() {
        UUID nonExistentId = UUID.randomUUID();
        when(productRepository.findById(nonExistentId)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> service.getProductOffers(nonExistentId));
        verify(productRepository).findById(nonExistentId);
        verifyNoInteractions(aggregatorService);
    }

    @Test
    @DisplayName("Constructs query context and queries aggregator when product exists")
    void testGetProductOffers_success() {
        UUID prodId = UUID.randomUUID();
        ProductEntity entity = ProductEntity.builder()
                .name("Pixel 8 Pro")
                .brand("Google")
                .category("Electronics")
                .build();
        entity.setId(prodId);

        when(productRepository.findById(prodId)).thenReturn(Optional.of(entity));

        NormalizedOfferDTO catalogOffer = NormalizedOfferDTO.builder()
                .id(UUID.randomUUID())
                .productId(prodId)
                .productTitle("Pixel 8 Pro")
                .pricing(com.pricepilot.intelligence.marketplace.dto.OfferPricingDTO.builder()
                        .basePrice(new BigDecimal("899.00"))
                        .currency("USD")
                        .build())
                .provenance(com.pricepilot.intelligence.marketplace.dto.OfferProvenanceDTO.builder()
                        .sourceType(ProvenanceType.DEMO)
                        .verificationStatus(VerificationStatus.UNVERIFIED)
                        .liveVerified(false)
                        .trustLabel("Catalog Offer")
                        .build())
                .isBestDeal(true)
                .build();

        NormalizedOfferDTO mockOffer = NormalizedOfferDTO.builder()
                .id(UUID.randomUUID())
                .productId(prodId)
                .productTitle("Pixel 8 Pro")
                .pricing(com.pricepilot.intelligence.marketplace.dto.OfferPricingDTO.builder()
                        .basePrice(new BigDecimal("949.00"))
                        .currency("USD")
                        .build())
                .provenance(com.pricepilot.intelligence.marketplace.dto.OfferProvenanceDTO.builder()
                        .sourceType(ProvenanceType.DEMO)
                        .verificationStatus(VerificationStatus.UNVERIFIED)
                        .liveVerified(false)
                        .trustLabel("Simulated Offer")
                        .build())
                .isBestDeal(false)
                .build();

        when(aggregatorService.aggregateOffers(any(MarketplaceQueryContext.class)))
                .thenReturn(List.of(catalogOffer, mockOffer));

        List<NormalizedOfferDTO> offers = service.getProductOffers(prodId);

        assertEquals(2, offers.size());
        assertEquals("Catalog Offer", offers.get(0).getProvenance().getTrustLabel());
        assertTrue(offers.get(0).getIsBestDeal());
        assertFalse(offers.get(0).getProvenance().isLiveVerified());

        assertEquals("Simulated Offer", offers.get(1).getProvenance().getTrustLabel());
        assertFalse(offers.get(1).getIsBestDeal());
        assertFalse(offers.get(1).getProvenance().isLiveVerified());

        ArgumentCaptor<MarketplaceQueryContext> contextCaptor = ArgumentCaptor.forClass(MarketplaceQueryContext.class);
        verify(aggregatorService).aggregateOffers(contextCaptor.capture());
        MarketplaceQueryContext capturedContext = contextCaptor.getValue();
        assertEquals(prodId, capturedContext.productId());
        assertEquals("Pixel 8 Pro", capturedContext.productName());
        assertEquals("Google", capturedContext.brand());
        assertEquals("Electronics", capturedContext.category());
    }

    @Test
    @DisplayName("Handles empty aggregator results when product has no offers safely")
    void testGetProductOffers_emptyAggregatorResults() {
        UUID prodId = UUID.randomUUID();
        ProductEntity entity = ProductEntity.builder().name("Item Without Offers").build();
        entity.setId(prodId);

        when(productRepository.findById(prodId)).thenReturn(Optional.of(entity));
        when(aggregatorService.aggregateOffers(any())).thenReturn(Collections.emptyList());

        List<NormalizedOfferDTO> offers = service.getProductOffers(prodId);
        assertNotNull(offers);
        assertTrue(offers.isEmpty());
    }
}
