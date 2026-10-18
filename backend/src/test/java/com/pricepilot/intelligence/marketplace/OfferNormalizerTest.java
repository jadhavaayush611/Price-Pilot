package com.pricepilot.intelligence.marketplace;

import com.pricepilot.intelligence.marketplace.dto.NormalizedOfferDTO;
import com.pricepilot.intelligence.marketplace.model.AvailabilityStatus;
import com.pricepilot.intelligence.marketplace.model.ProvenanceType;
import com.pricepilot.intelligence.marketplace.model.VerificationStatus;
import com.pricepilot.intelligence.marketplace.normalizer.OfferNormalizer;
import com.pricepilot.product.dto.ProductResponseDTO;
import com.pricepilot.productprice.dto.ProductPriceResponseDTO;
import com.pricepilot.seller.dto.SellerResponseDTO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Backend OfferNormalizer & Marketplace Model Tests")
class OfferNormalizerTest {

    private OfferNormalizer normalizer;

    @BeforeEach
    void setUp() {
        normalizer = new OfferNormalizer();
    }

    @Test
    @DisplayName("Normalizes product price DTO without fabricating checkout fees")
    void testNormalize_preservesBasePriceAndSeparatesFees() {
        UUID priceId = UUID.randomUUID();
        UUID prodId = UUID.randomUUID();
        UUID sellerId = UUID.randomUUID();

        ProductPriceResponseDTO dto = ProductPriceResponseDTO.builder()
                .id(priceId)
                .currentPrice(new BigDecimal("604.98"))
                .originalPrice(new BigDecimal("699.00"))
                .discountPercentage(new BigDecimal("13.45"))
                .productUrl("https://example.com/phone")
                .lastUpdated(LocalDateTime.now())
                .product(ProductResponseDTO.builder().id(prodId).name("Nothing Phone 2").build())
                .seller(SellerResponseDTO.builder().id(sellerId).name("Retailer A").websiteUrl("https://example.com").build())
                .build();

        NormalizedOfferDTO offer = normalizer.normalize(dto);

        assertNotNull(offer);
        assertEquals(priceId, offer.getId());
        assertEquals(prodId, offer.getProductId());
        assertEquals("Nothing Phone 2", offer.getProductTitle());
        assertEquals("Retailer A", offer.getSource().getSellerName());
        assertEquals(new BigDecimal("604.98"), offer.getPricing().getBasePrice());
        assertEquals(new BigDecimal("699.00"), offer.getPricing().getOriginalPrice());
        assertEquals("USD", offer.getPricing().getCurrency());

        // Marketplace Contract: Missing fees MUST NOT be inferred as 0
        assertNull(offer.getPricing().getShippingPrice());
        assertNull(offer.getPricing().getTaxesAndFees());
        assertNull(offer.getPricing().getTotalPrice());
        assertTrue(offer.getPricing().isBasePriceOnly());
    }

    @Test
    @DisplayName("Marks seed data as DEMO and UNVERIFIED, never claiming live verification")
    void testNormalize_provenanceIntegrity() {
        ProductPriceResponseDTO dto = ProductPriceResponseDTO.builder()
                .id(UUID.randomUUID())
                .currentPrice(new BigDecimal("299.00"))
                .lastUpdated(LocalDateTime.now().minusHours(2))
                .seller(SellerResponseDTO.builder().name("Demo Store").build())
                .build();

        NormalizedOfferDTO offer = normalizer.normalize(dto);

        assertEquals(ProvenanceType.DEMO, offer.getProvenance().getSourceType());
        assertEquals(VerificationStatus.UNVERIFIED, offer.getProvenance().getVerificationStatus());
        assertFalse(offer.getProvenance().isLiveVerified());
        assertEquals("Catalog Offer", offer.getProvenance().getTrustLabel());
    }

    @Test
    @DisplayName("Flags stale observations when last updated exceeds 7 days without falsely claiming verified source")
    void testNormalize_stalenessDetection() {
        ProductPriceResponseDTO dto = ProductPriceResponseDTO.builder()
                .id(UUID.randomUUID())
                .currentPrice(new BigDecimal("299.00"))
                .lastUpdated(LocalDateTime.now().minusDays(10))
                .seller(SellerResponseDTO.builder().name("Demo Store").build())
                .build();

        NormalizedOfferDTO offer = normalizer.normalize(dto);

        assertTrue(offer.getProvenance().isStale());
        assertEquals(ProvenanceType.DEMO, offer.getProvenance().getSourceType());
        // Stale status does not imply a verified source: unverified demo seed data remains unverified
        assertEquals(VerificationStatus.UNVERIFIED, offer.getProvenance().getVerificationStatus());
        assertEquals("Catalog Offer (Stale)", offer.getProvenance().getTrustLabel());
    }

    @Test
    @DisplayName("Future timestamps are not flagged as stale")
    void testNormalize_futureTimestamp() {
        ProductPriceResponseDTO dto = ProductPriceResponseDTO.builder()
                .id(UUID.randomUUID())
                .currentPrice(new BigDecimal("299.00"))
                .lastUpdated(LocalDateTime.now().plusDays(2))
                .seller(SellerResponseDTO.builder().name("Store Future").build())
                .build();

        NormalizedOfferDTO offer = normalizer.normalize(dto);

        assertFalse(offer.getProvenance().isStale());
        assertEquals("Catalog Offer", offer.getProvenance().getTrustLabel());
    }

    @Test
    @DisplayName("Missing timestamp does not crash and produces non-stale observation")
    void testNormalize_missingTimestamp() {
        ProductPriceResponseDTO dto = ProductPriceResponseDTO.builder()
                .id(UUID.randomUUID())
                .currentPrice(new BigDecimal("299.00"))
                .lastUpdated(null)
                .build();

        NormalizedOfferDTO offer = normalizer.normalize(dto);

        assertNull(offer.getProvenance().getObservedAt());
        assertFalse(offer.getProvenance().isStale());
    }

    @Test
    @DisplayName("Does not fabricate stock state when unavailable in source")
    void testNormalize_stockUnknown() {
        ProductPriceResponseDTO dto = ProductPriceResponseDTO.builder()
                .id(UUID.randomUUID())
                .currentPrice(new BigDecimal("299.00"))
                .lastUpdated(LocalDateTime.now())
                .build();

        NormalizedOfferDTO offer = normalizer.normalize(dto);

        assertEquals(AvailabilityStatus.UNKNOWN, offer.getAvailability().getStatus());
        assertNull(offer.getAvailability().getAvailable());
    }

    @Test
    @DisplayName("Normalizes competing offers list, ranks lowest as best deal, and computes savings")
    void testNormalizeOffers_rankingAndSavings() {
        ProductPriceResponseDTO p1 = ProductPriceResponseDTO.builder()
                .id(UUID.randomUUID())
                .currentPrice(new BigDecimal("620.00"))
                .originalPrice(new BigDecimal("699.00"))
                .discountPercentage(new BigDecimal("11.30"))
                .lastUpdated(LocalDateTime.now())
                .seller(SellerResponseDTO.builder().name("Merchant B").build())
                .build();

        ProductPriceResponseDTO p2 = ProductPriceResponseDTO.builder()
                .id(UUID.randomUUID())
                .currentPrice(new BigDecimal("604.98"))
                .originalPrice(new BigDecimal("699.00"))
                .discountPercentage(new BigDecimal("13.45"))
                .lastUpdated(LocalDateTime.now())
                .seller(SellerResponseDTO.builder().name("Merchant A").build())
                .build();

        List<NormalizedOfferDTO> offers = normalizer.normalizeOffers(List.of(p1, p2), "Nothing Phone 2");

        assertEquals(2, offers.size());

        // First offer MUST be the lowest base price ($604.98)
        NormalizedOfferDTO bestDeal = offers.get(0);
        assertEquals(new BigDecimal("604.98"), bestDeal.getPricing().getBasePrice());
        assertTrue(bestDeal.getIsBestDeal());
        assertEquals(0, bestDeal.getPriceDifferenceFromLowest().compareTo(BigDecimal.ZERO));
        assertEquals(new BigDecimal("94.02"), bestDeal.getSavings());

        // Second offer ($620.00)
        NormalizedOfferDTO second = offers.get(1);
        assertEquals(new BigDecimal("620.00"), second.getPricing().getBasePrice());
        assertFalse(second.getIsBestDeal());
        assertEquals(new BigDecimal("15.02"), second.getPriceDifferenceFromLowest());
    }

    @Test
    @DisplayName("Resolves ties deterministically: by discount percentage then seller name")
    void testNormalizeOffers_tieBreakingParity() {
        // Equal base price $500, but different discounts
        ProductPriceResponseDTO o1 = ProductPriceResponseDTO.builder()
                .id(UUID.randomUUID())
                .currentPrice(new BigDecimal("500.00"))
                .originalPrice(new BigDecimal("550.00")) // ~9% discount
                .discountPercentage(new BigDecimal("9.09"))
                .seller(SellerResponseDTO.builder().name("Zeta Store").build())
                .build();

        ProductPriceResponseDTO o2 = ProductPriceResponseDTO.builder()
                .id(UUID.randomUUID())
                .currentPrice(new BigDecimal("500.00"))
                .originalPrice(new BigDecimal("600.00")) // ~16.67% discount
                .discountPercentage(new BigDecimal("16.67"))
                .seller(SellerResponseDTO.builder().name("Beta Store").build())
                .build();

        ProductPriceResponseDTO o3 = ProductPriceResponseDTO.builder()
                .id(UUID.randomUUID())
                .currentPrice(new BigDecimal("500.00"))
                .originalPrice(new BigDecimal("600.00")) // ~16.67% discount
                .discountPercentage(new BigDecimal("16.67"))
                .seller(SellerResponseDTO.builder().name("Alpha Store").build())
                .build();

        List<NormalizedOfferDTO> offers = normalizer.normalizeOffers(List.of(o1, o2, o3), "Tied Item");

        assertEquals(3, offers.size());

        // o3 and o2 both have highest discount (16.67%), but o3 has seller "Alpha Store" < "Beta Store"
        assertEquals("Alpha Store", offers.get(0).getSource().getSellerName());
        assertTrue(offers.get(0).getIsBestDeal());

        assertEquals("Beta Store", offers.get(1).getSource().getSellerName());
        assertFalse(offers.get(1).getIsBestDeal());

        assertEquals("Zeta Store", offers.get(2).getSource().getSellerName());
        assertFalse(offers.get(2).getIsBestDeal());

        // All three tied on base price, so difference from lowest is zero for all
        assertEquals(0, offers.get(0).getPriceDifferenceFromLowest().compareTo(BigDecimal.ZERO));
        assertEquals(0, offers.get(1).getPriceDifferenceFromLowest().compareTo(BigDecimal.ZERO));
        assertEquals(0, offers.get(2).getPriceDifferenceFromLowest().compareTo(BigDecimal.ZERO));
    }

    @Test
    @DisplayName("Strictly rejects invalid, null, and negative base prices by returning null")
    void testNormalize_rejectsInvalidAndNegativePrices() {
        ProductPriceResponseDTO dtoNegative = ProductPriceResponseDTO.builder()
                .id(UUID.randomUUID())
                .currentPrice(new BigDecimal("-100.00"))
                .seller(SellerResponseDTO.builder().name("Negative Merchant").build())
                .build();
        assertNull(normalizer.normalize(dtoNegative));

        ProductPriceResponseDTO dtoNull = ProductPriceResponseDTO.builder()
                .id(UUID.randomUUID())
                .currentPrice(null)
                .seller(SellerResponseDTO.builder().name("Null Merchant").build())
                .build();
        assertNull(normalizer.normalize(dtoNull));
    }

    @Test
    @DisplayName("Accepts legitimate zero base prices (promotional free tier) only when explicitly provided")
    void testNormalize_acceptsLegitimateZeroPrice() {
        ProductPriceResponseDTO dtoZero = ProductPriceResponseDTO.builder()
                .id(UUID.randomUUID())
                .currentPrice(BigDecimal.ZERO)
                .originalPrice(new BigDecimal("100.00"))
                .seller(SellerResponseDTO.builder().name("Free Merchant").build())
                .build();

        NormalizedOfferDTO offer = normalizer.normalize(dtoZero);
        assertNotNull(offer);
        assertEquals(0, offer.getPricing().getBasePrice().compareTo(BigDecimal.ZERO));
        assertEquals(new BigDecimal("100.00"), offer.getPricing().getDiscountPercentage());
    }

    @Test
    @DisplayName("Excludes invalid offers and preserves valid offers without fabricating fallback prices")
    void testNormalizeOffers_excludesInvalidOffersAndPreservesValidOffers() {
        ProductPriceResponseDTO pNeg = ProductPriceResponseDTO.builder()
                .id(UUID.randomUUID())
                .currentPrice(new BigDecimal("-50.00"))
                .seller(SellerResponseDTO.builder().name("Negative Merchant").build())
                .build();

        ProductPriceResponseDTO pNull = ProductPriceResponseDTO.builder()
                .id(UUID.randomUUID())
                .currentPrice(null)
                .seller(SellerResponseDTO.builder().name("Null Merchant").build())
                .build();

        ProductPriceResponseDTO pValid = ProductPriceResponseDTO.builder()
                .id(UUID.randomUUID())
                .currentPrice(new BigDecimal("299.99"))
                .seller(SellerResponseDTO.builder().name("Valid Merchant").build())
                .build();

        List<NormalizedOfferDTO> offers = normalizer.normalizeOffers(List.of(pNeg, pNull, pValid), "Safety Test Product");

        // Only the valid offer must survive
        assertEquals(1, offers.size());
        assertEquals("Valid Merchant", offers.get(0).getSource().getSellerName());
        assertEquals(new BigDecimal("299.99"), offers.get(0).getPricing().getBasePrice());
        assertTrue(offers.get(0).getIsBestDeal());
        assertEquals(0, offers.get(0).getPriceDifferenceFromLowest().compareTo(BigDecimal.ZERO));
    }

    @Test
    @DisplayName("Returns empty list when all offers have invalid prices, ensuring no offer receives isBestDeal")
    void testNormalizeOffers_allInvalidReturnsEmptyList() {
        ProductPriceResponseDTO pNeg = ProductPriceResponseDTO.builder()
                .id(UUID.randomUUID())
                .currentPrice(new BigDecimal("-50.00"))
                .build();

        ProductPriceResponseDTO pNull = ProductPriceResponseDTO.builder()
                .id(UUID.randomUUID())
                .currentPrice(null)
                .build();

        List<NormalizedOfferDTO> offers = normalizer.normalizeOffers(List.of(pNeg, pNull), "All Invalid");
        assertTrue(offers.isEmpty());
    }

    @Test
    @DisplayName("Legitimate zero price ranks first alongside positive price without confusing with invalid input")
    void testNormalizeOffers_legitimateZeroPriceRanksFirst() {
        ProductPriceResponseDTO pPos = ProductPriceResponseDTO.builder()
                .id(UUID.randomUUID())
                .currentPrice(new BigDecimal("100.00"))
                .originalPrice(new BigDecimal("150.00"))
                .seller(SellerResponseDTO.builder().name("Positive Seller").build())
                .build();

        ProductPriceResponseDTO pFree = ProductPriceResponseDTO.builder()
                .id(UUID.randomUUID())
                .currentPrice(BigDecimal.ZERO)
                .originalPrice(new BigDecimal("100.00"))
                .seller(SellerResponseDTO.builder().name("Free Promo Seller").build())
                .build();

        List<NormalizedOfferDTO> offers = normalizer.normalizeOffers(List.of(pPos, pFree), "Promo Product");
        assertEquals(2, offers.size());

        // Zero price must be ranked #1 as best deal
        assertEquals("Free Promo Seller", offers.get(0).getSource().getSellerName());
        assertEquals(0, offers.get(0).getPricing().getBasePrice().compareTo(BigDecimal.ZERO));
        assertTrue(offers.get(0).getIsBestDeal());

        // Positive price must be ranked #2
        assertEquals("Positive Seller", offers.get(1).getSource().getSellerName());
        assertEquals(new BigDecimal("100.00"), offers.get(1).getPricing().getBasePrice());
        assertFalse(offers.get(1).getIsBestDeal());
        assertEquals(new BigDecimal("100.00"), offers.get(1).getPriceDifferenceFromLowest());
    }

    @Test
    @DisplayName("Handles missing or zero original prices without fabricating discounts")
    void testNormalize_handlesMissingOrZeroOriginalPrices() {
        for (BigDecimal invalidOrig : List.of(BigDecimal.ZERO, new BigDecimal("-50.00"))) {
            ProductPriceResponseDTO dto = ProductPriceResponseDTO.builder()
                    .id(UUID.randomUUID())
                    .currentPrice(new BigDecimal("100.00"))
                    .originalPrice(invalidOrig)
                    .discountPercentage(null)
                    .seller(SellerResponseDTO.builder().name("Merchant").build())
                    .build();

            NormalizedOfferDTO offer = normalizer.normalize(dto);
            assertNull(offer.getPricing().getOriginalPrice());
            assertEquals(0, offer.getPricing().getDiscountPercentage().compareTo(BigDecimal.ZERO));
        }

        ProductPriceResponseDTO dtoNullOrig = ProductPriceResponseDTO.builder()
                .id(UUID.randomUUID())
                .currentPrice(new BigDecimal("100.00"))
                .originalPrice(null)
                .discountPercentage(null)
                .seller(SellerResponseDTO.builder().name("Merchant").build())
                .build();

        NormalizedOfferDTO offer = normalizer.normalize(dtoNullOrig);
        assertNull(offer.getPricing().getOriginalPrice());
        assertEquals(0, offer.getPricing().getDiscountPercentage().compareTo(BigDecimal.ZERO));
    }

    @Test
    @DisplayName("Calculates discount percentage when missing on entity with original price > base price")
    void testNormalize_entityParityWithDto() {
        com.pricepilot.productprice.ProductPriceEntity entity = com.pricepilot.productprice.ProductPriceEntity.builder()
                .currentPrice(new BigDecimal("604.98"))
                .originalPrice(new BigDecimal("699.00"))
                .discountPercentage(null) // missing discount percentage
                .lastUpdated(LocalDateTime.now())
                .seller(com.pricepilot.seller.SellerEntity.builder().name("Entity Merchant").build())
                .build();
        entity.setId(UUID.randomUUID());

        NormalizedOfferDTO offer = normalizer.normalize(entity);
        assertNotNull(offer);
        assertEquals(entity.getId(), offer.getId());
        assertEquals("Entity Merchant", offer.getSource().getSellerName());
        assertEquals(new BigDecimal("604.98"), offer.getPricing().getBasePrice());
        assertEquals(new BigDecimal("699.00"), offer.getPricing().getOriginalPrice());
        // Auto-calculated: (699 - 604.98) / 699 * 100 = 13.45%
        assertEquals(new BigDecimal("13.45"), offer.getPricing().getDiscountPercentage());
        assertEquals("USD", offer.getPricing().getCurrency());
        assertEquals(AvailabilityStatus.UNKNOWN, offer.getAvailability().getStatus());
        assertNull(offer.getPricing().getShippingPrice());

        // Entity invalid price rejection
        com.pricepilot.productprice.ProductPriceEntity invalidEntity = com.pricepilot.productprice.ProductPriceEntity.builder()
                .currentPrice(new BigDecimal("-10.00"))
                .seller(com.pricepilot.seller.SellerEntity.builder().name("Invalid Merchant").build())
                .build();
        assertNull(normalizer.normalize(invalidEntity));
    }
}
