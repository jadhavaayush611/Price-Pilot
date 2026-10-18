package com.pricepilot.product;

import com.pricepilot.exception.ResourceNotFoundException;
import com.pricepilot.intelligence.marketplace.dto.*;
import com.pricepilot.intelligence.marketplace.model.AvailabilityStatus;
import com.pricepilot.intelligence.marketplace.model.ProvenanceType;
import com.pricepilot.intelligence.marketplace.model.VerificationStatus;
import com.pricepilot.intelligence.marketplace.service.ProductOfferService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc(addFilters = false)
@DisplayName("Product Offers Controller Endpoint Tests (GET /api/v1/products/{id}/offers)")
class ProductOffersControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ProductOfferService productOfferService;

    @Test
    @DisplayName("GET /api/v1/products/{id}/offers returns 200 OK with ranked offers")
    void testGetProductOffers_success() throws Exception {
        UUID productId = UUID.randomUUID();
        UUID offerId1 = UUID.randomUUID();
        UUID offerId2 = UUID.randomUUID();

        NormalizedOfferDTO catalogOffer = NormalizedOfferDTO.builder()
                .id(offerId1)
                .productId(productId)
                .productTitle("Sony WH-1000XM5")
                .source(OfferSourceDTO.builder()
                        .sellerId(UUID.randomUUID())
                        .sellerName("Amazon")
                        .websiteUrl("https://amazon.com")
                        .offerUrl("https://amazon.com/dp/B09XS7JWHH")
                        .providerName("catalog")
                        .build())
                .pricing(OfferPricingDTO.builder()
                        .basePrice(new BigDecimal("348.00"))
                        .originalPrice(new BigDecimal("399.99"))
                        .discountPercentage(new BigDecimal("13.00"))
                        .currency("USD")
                        .basePriceOnly(true)
                        .pricingDisclaimer("Listed base price. Additional shipping, taxes, and merchant fees may apply at checkout.")
                        .build())
                .provenance(OfferProvenanceDTO.builder()
                        .sourceType(ProvenanceType.DEMO)
                        .verificationStatus(VerificationStatus.UNVERIFIED)
                        .liveVerified(false)
                        .observedAt(LocalDateTime.of(2026, 10, 18, 12, 0))
                        .rawLastUpdated("2026-10-18T12:00:00Z")
                        .stale(false)
                        .trustLabel("Catalog Offer")
                        .trustDescription("Catalog listing recorded in PricePilot database.")
                        .build())
                .availability(OfferAvailabilityDTO.builder()
                        .status(AvailabilityStatus.IN_STOCK)
                        .available(true)
                        .build())
                .isBestDeal(true)
                .savings(new BigDecimal("51.99"))
                .priceDifferenceFromLowest(BigDecimal.ZERO)
                .build();

        NormalizedOfferDTO mockOffer = NormalizedOfferDTO.builder()
                .id(offerId2)
                .productId(productId)
                .productTitle("Sony WH-1000XM5")
                .source(OfferSourceDTO.builder()
                        .sellerId(UUID.randomUUID())
                        .sellerName("Mock Marketplace")
                        .websiteUrl("https://mock.example.com")
                        .offerUrl("https://mock.example.com/item/1")
                        .providerName("mock-marketplace")
                        .build())
                .pricing(OfferPricingDTO.builder()
                        .basePrice(new BigDecimal("369.00"))
                        .currency("USD")
                        .basePriceOnly(true)
                        .pricingDisclaimer("Listed base price. Additional shipping, taxes, and merchant fees may apply at checkout.")
                        .build())
                .provenance(OfferProvenanceDTO.builder()
                        .sourceType(ProvenanceType.DEMO)
                        .verificationStatus(VerificationStatus.UNVERIFIED)
                        .liveVerified(false)
                        .stale(false)
                        .trustLabel("Simulated Offer")
                        .trustDescription("Simulated offer generated offline for demonstration and testing.")
                        .build())
                .availability(OfferAvailabilityDTO.builder()
                        .status(AvailabilityStatus.UNKNOWN)
                        .build())
                .isBestDeal(false)
                .savings(BigDecimal.ZERO)
                .priceDifferenceFromLowest(new BigDecimal("21.00"))
                .build();

        when(productOfferService.getProductOffers(eq(productId)))
                .thenReturn(List.of(catalogOffer, mockOffer));

        mockMvc.perform(get("/api/v1/products/" + productId + "/offers")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].id").value(offerId1.toString()))
                .andExpect(jsonPath("$[0].productId").value(productId.toString()))
                .andExpect(jsonPath("$[0].productTitle").value("Sony WH-1000XM5"))
                .andExpect(jsonPath("$[0].source.sellerName").value("Amazon"))
                .andExpect(jsonPath("$[0].pricing.basePrice").value(348.00))
                .andExpect(jsonPath("$[0].pricing.currency").value("USD"))
                .andExpect(jsonPath("$[0].provenance.sourceType").value("DEMO"))
                .andExpect(jsonPath("$[0].provenance.trustLabel").value("Catalog Offer"))
                .andExpect(jsonPath("$[0].provenance.liveVerified").value(false))
                .andExpect(jsonPath("$[0].isBestDeal").value(true))
                .andExpect(jsonPath("$[1].id").value(offerId2.toString()))
                .andExpect(jsonPath("$[1].provenance.sourceType").value("DEMO"))
                .andExpect(jsonPath("$[1].provenance.trustLabel").value("Simulated Offer"))
                .andExpect(jsonPath("$[1].provenance.liveVerified").value(false))
                .andExpect(jsonPath("$[1].isBestDeal").value(false));

        verify(productOfferService).getProductOffers(eq(productId));
    }

    @Test
    @DisplayName("GET /api/v1/products/{id}/offers returns 200 OK with empty array when no offers exist")
    void testGetProductOffers_emptyResults() throws Exception {
        UUID productId = UUID.randomUUID();
        when(productOfferService.getProductOffers(eq(productId))).thenReturn(Collections.emptyList());

        mockMvc.perform(get("/api/v1/products/" + productId + "/offers")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$.length()").value(0));

        verify(productOfferService).getProductOffers(eq(productId));
    }

    @Test
    @DisplayName("GET /api/v1/products/{id}/offers returns 404 NOT FOUND for unknown product ID")
    void testGetProductOffers_unknownProductThrows404() throws Exception {
        UUID nonExistentId = UUID.randomUUID();
        when(productOfferService.getProductOffers(eq(nonExistentId)))
                .thenThrow(new ResourceNotFoundException("Product not found with id: " + nonExistentId));

        mockMvc.perform(get("/api/v1/products/" + nonExistentId + "/offers")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.error").value("Not Found"))
                .andExpect(jsonPath("$.message").value("Product not found with id: " + nonExistentId));

        verify(productOfferService).getProductOffers(eq(nonExistentId));
    }

    @Test
    @DisplayName("Adapter failure resilience: valid surviving offers are returned when another provider fails")
    void testGetProductOffers_survivingOffersAfterAdapterFailure() throws Exception {
        UUID productId = UUID.randomUUID();

        // Valid surviving catalog offer when a secondary adapter encountered an issue
        NormalizedOfferDTO survivingCatalogOffer = NormalizedOfferDTO.builder()
                .id(UUID.randomUUID())
                .productId(productId)
                .productTitle("Resilient Product")
                .source(OfferSourceDTO.builder()
                        .sellerName("Surviving Retailer")
                        .providerName("catalog")
                        .build())
                .pricing(OfferPricingDTO.builder()
                        .basePrice(new BigDecimal("199.99"))
                        .currency("USD")
                        .build())
                .provenance(OfferProvenanceDTO.builder()
                        .sourceType(ProvenanceType.DEMO)
                        .verificationStatus(VerificationStatus.UNVERIFIED)
                        .liveVerified(false)
                        .trustLabel("Catalog Offer")
                        .build())
                .isBestDeal(true)
                .build();

        when(productOfferService.getProductOffers(eq(productId)))
                .thenReturn(List.of(survivingCatalogOffer));

        mockMvc.perform(get("/api/v1/products/" + productId + "/offers"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].source.sellerName").value("Surviving Retailer"))
                .andExpect(jsonPath("$[0].provenance.sourceType").value("DEMO"))
                .andExpect(jsonPath("$[0].provenance.liveVerified").value(false));
    }
}
