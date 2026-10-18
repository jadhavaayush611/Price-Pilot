package com.pricepilot.intelligence.marketplace.adapter;

import com.pricepilot.intelligence.marketplace.dto.NormalizedOfferDTO;
import com.pricepilot.intelligence.marketplace.model.AvailabilityStatus;
import com.pricepilot.intelligence.marketplace.model.ProvenanceType;
import com.pricepilot.intelligence.marketplace.model.VerificationStatus;
import com.pricepilot.intelligence.marketplace.normalizer.OfferNormalizer;
import com.pricepilot.product.ProductEntity;
import com.pricepilot.productprice.ProductPriceEntity;
import com.pricepilot.productprice.ProductPriceRepository;
import com.pricepilot.seller.SellerEntity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("Marketplace Adapter Framework & Offline Orchestration Tests")
class MarketplaceAdapterFrameworkTest {

    @Mock
    private ProductPriceRepository productPriceRepository;

    private OfferNormalizer normalizer;
    private CatalogOfferAdapter catalogAdapter;
    private MockMarketplaceOfferAdapter mockAdapter;
    private MarketplaceOfferAggregatorService aggregatorService;

    @BeforeEach
    void setUp() {
        normalizer = new OfferNormalizer();
        catalogAdapter = new CatalogOfferAdapter(productPriceRepository);
        mockAdapter = new MockMarketplaceOfferAdapter();
        aggregatorService = new MarketplaceOfferAggregatorService(
                List.of(catalogAdapter, mockAdapter),
                normalizer
        );
    }

    @Test
    @DisplayName("Catalog adapter maps persisted prices to raw offers preserving demo/unverified provenance")
    void testCatalogAdapter_fetchesAndPreservesProvenance() {
        UUID prodId = UUID.randomUUID();
        UUID priceId = UUID.randomUUID();
        ProductEntity product = ProductEntity.builder().name("Test Phone").build();
        product.setId(prodId);
        SellerEntity seller = SellerEntity.builder().name("Best Retailer").websiteUrl("https://bestretailer.com").build();
        ProductPriceEntity price = ProductPriceEntity.builder()
                .product(product)
                .seller(seller)
                .currentPrice(new BigDecimal("799.00"))
                .originalPrice(new BigDecimal("899.00"))
                .productUrl("https://bestretailer.com/item/1")
                .lastUpdated(LocalDateTime.now().minusHours(2))
                .build();
        price.setId(priceId);

        when(productPriceRepository.findPricesWithSellersByProductIds(anyList()))
                .thenReturn(List.of(price));

        MarketplaceQueryContext context = MarketplaceQueryContext.of(prodId, "Test Phone");
        List<RawMarketplaceOffer> raw = catalogAdapter.fetchRawOffers(context);

        assertEquals(1, raw.size());
        assertEquals("Best Retailer", raw.get(0).sellerName());
        assertEquals(new BigDecimal("799.00"), raw.get(0).price());

        // When aggregated, catalog offer provenance MUST remain DEMO and UNVERIFIED
        List<NormalizedOfferDTO> offers = aggregatorService.aggregateOffers(context);
        assertFalse(offers.isEmpty());

        NormalizedOfferDTO catalogOffer = offers.stream()
                .filter(o -> "Best Retailer".equals(o.getSource().getSellerName()))
                .findFirst()
                .orElse(null);

        assertNotNull(catalogOffer);
        assertEquals(ProvenanceType.DEMO, catalogOffer.getProvenance().getSourceType());
        assertEquals(VerificationStatus.UNVERIFIED, catalogOffer.getProvenance().getVerificationStatus());
        assertFalse(catalogOffer.getProvenance().isLiveVerified());
        assertEquals("Catalog Offer", catalogOffer.getProvenance().getTrustLabel());
    }

    @Test
    @DisplayName("Mock adapter produces simulated offers that are explicitly labeled as simulated and never live verified")
    void testMockAdapter_simulatedProvenanceNeverLive() {
        UUID prodId = UUID.randomUUID();
        MarketplaceQueryContext context = MarketplaceQueryContext.of(prodId, "iPhone 15 Pro");

        when(productPriceRepository.findPricesWithSellersByProductIds(anyList()))
                .thenReturn(Collections.emptyList());

        List<NormalizedOfferDTO> offers = aggregatorService.aggregateOffers(context);
        assertFalse(offers.isEmpty());

        for (NormalizedOfferDTO offer : offers) {
            assertEquals(ProvenanceType.DEMO, offer.getProvenance().getSourceType());
            assertEquals(VerificationStatus.UNVERIFIED, offer.getProvenance().getVerificationStatus());
            assertFalse(offer.getProvenance().isLiveVerified());
            assertEquals("Simulated Offer", offer.getProvenance().getTrustLabel());
            assertTrue(offer.getProvenance().getTrustDescription().contains("Simulated offline marketplace offer"));
        }
    }

    @Test
    @DisplayName("Canonical normalizer safety gate strictly excludes invalid, negative, or null prices from providers")
    void testAggregator_excludesInvalidPricesFromProviders() {
        UUID prodId = UUID.randomUUID();
        MarketplaceQueryContext context = MarketplaceQueryContext.of(prodId, "Generic Item");

        // Custom failing/malformed adapter
        MarketplaceOfferAdapter corruptAdapter = new MarketplaceOfferAdapter() {
            @Override
            public String getProviderId() {
                return "corrupt";
            }

            @Override
            public String getDisplayName() {
                return "Corrupt Provider";
            }

            @Override
            public boolean isAvailable() {
                return true;
            }

            @Override
            public List<RawMarketplaceOffer> fetchRawOffers(MarketplaceQueryContext context) {
                return List.of(
                        new RawMarketplaceOffer("c1", "Corrupt Neg", null, null, new BigDecimal("-100.00"), null, "USD", true, 1, LocalDateTime.now()),
                        new RawMarketplaceOffer("c2", "Corrupt Null", null, null, null, null, "USD", true, 1, LocalDateTime.now()),
                        new RawMarketplaceOffer("c3", "Corrupt Valid", null, null, new BigDecimal("120.00"), null, "USD", true, 1, LocalDateTime.now())
                );
            }
        };

        MarketplaceOfferAggregatorService customAggregator = new MarketplaceOfferAggregatorService(
                List.of(corruptAdapter),
                normalizer
        );

        List<NormalizedOfferDTO> offers = customAggregator.aggregateOffers(context);
        assertEquals(1, offers.size(), "Only the valid offer must pass through");
        assertEquals("Corrupt Valid", offers.get(0).getSource().getSellerName());
        assertEquals(new BigDecimal("120.00"), offers.get(0).getPricing().getBasePrice());
    }

    @Test
    @DisplayName("Currency correctness: rejects non-USD and unsupported currencies from canonical ranking without fabricating conversion")
    void testAggregator_rejectsNonUsdCurrency() {
        UUID prodId = UUID.randomUUID();
        MarketplaceQueryContext context = MarketplaceQueryContext.of(prodId, "Global Item");

        MarketplaceOfferAdapter currencyAdapter = new MarketplaceOfferAdapter() {
            @Override
            public String getProviderId() {
                return "multi-currency";
            }

            @Override
            public String getDisplayName() {
                return "Multi-Currency Provider";
            }

            @Override
            public boolean isAvailable() {
                return true;
            }

            @Override
            public List<RawMarketplaceOffer> fetchRawOffers(MarketplaceQueryContext context) {
                return List.of(
                        // 100 EUR should not be ranked as 100 USD
                        new RawMarketplaceOffer("eur-1", "Euro Seller", null, null, new BigDecimal("100.00"), null, "EUR", true, 1, LocalDateTime.now()),
                        // 5000 JPY should not be ranked as 5000 USD
                        new RawMarketplaceOffer("jpy-1", "Tokyo Seller", null, null, new BigDecimal("5000.00"), null, "JPY", true, 1, LocalDateTime.now()),
                        // Missing/null currency is strictly rejected from canonical USD ranking
                        new RawMarketplaceOffer("usd-null", "Default USD Seller", null, null, new BigDecimal("75.00"), null, null, true, 1, LocalDateTime.now()),
                        // Explicit USD is accepted
                        new RawMarketplaceOffer("usd-1", "Standard USD Seller", null, null, new BigDecimal("80.00"), null, "USD", true, 1, LocalDateTime.now())
                );
            }
        };

        MarketplaceOfferAggregatorService customAggregator = new MarketplaceOfferAggregatorService(
                List.of(currencyAdapter),
                normalizer
        );

        List<NormalizedOfferDTO> offers = customAggregator.aggregateOffers(context);
        // Only explicit USD offers must survive; non-USD and missing/null currencies are safely rejected
        assertEquals(1, offers.size());
        assertEquals("Standard USD Seller", offers.get(0).getSource().getSellerName());
        assertEquals(new BigDecimal("80.00"), offers.get(0).getPricing().getBasePrice());
        assertEquals("USD", offers.get(0).getPricing().getCurrency());
    }

    @Test
    @DisplayName("Cross-provider deduplication preserves both offers when cross-provider identity cannot be verified safely")
    void testAggregator_crossProviderIdentityPreserved() {
        UUID prodId = UUID.randomUUID();
        MarketplaceQueryContext context = MarketplaceQueryContext.of(prodId, "Phone Item");

        // Provider A has an offer
        MarketplaceOfferAdapter providerA = new MarketplaceOfferAdapter() {
            @Override
            public String getProviderId() {
                return "provider-alpha";
            }

            @Override
            public String getDisplayName() {
                return "Alpha Provider";
            }

            @Override
            public boolean isAvailable() {
                return true;
            }

            @Override
            public List<RawMarketplaceOffer> fetchRawOffers(MarketplaceQueryContext context) {
                return List.of(
                        new RawMarketplaceOffer("a-1", "BestBuy Store", "https://bestbuy.com", "https://bestbuy.com/p/1", new BigDecimal("799.00"), null, "USD", true, 1, LocalDateTime.now())
                );
            }
        };

        // Provider B also claims to see an offer from BestBuy with the same price and URL
        MarketplaceOfferAdapter providerB = new MarketplaceOfferAdapter() {
            @Override
            public String getProviderId() {
                return "provider-beta";
            }

            @Override
            public String getDisplayName() {
                return "Beta Provider";
            }

            @Override
            public boolean isAvailable() {
                return true;
            }

            @Override
            public List<RawMarketplaceOffer> fetchRawOffers(MarketplaceQueryContext context) {
                return List.of(
                        new RawMarketplaceOffer("b-1", "BestBuy Store", "https://bestbuy.com", "https://bestbuy.com/p/1", new BigDecimal("799.00"), null, "USD", true, 1, LocalDateTime.now())
                );
            }
        };

        MarketplaceOfferAggregatorService customAggregator = new MarketplaceOfferAggregatorService(
                List.of(providerA, providerB),
                normalizer
        );

        List<NormalizedOfferDTO> offers = customAggregator.aggregateOffers(context);
        // Cross-provider identity preserves both observations to avoid risking false merges across unvetted providers
        assertEquals(2, offers.size());
        assertEquals("provider-alpha", offers.get(0).getSource().getProviderName());
        assertEquals("provider-beta", offers.get(1).getSource().getProviderName());
    }

    @Test
    @DisplayName("Deduplication merges identical provider offers by identity, not solely by matching prices")
    void testAggregator_deterministicDeduplication() {
        UUID prodId = UUID.randomUUID();
        MarketplaceQueryContext context = MarketplaceQueryContext.of(prodId, "Test Laptop");

        MarketplaceOfferAdapter duplicateAdapter = new MarketplaceOfferAdapter() {
            @Override
            public String getProviderId() {
                return "store-feed";
            }

            @Override
            public String getDisplayName() {
                return "Store Feed";
            }

            @Override
            public boolean isAvailable() {
                return true;
            }

            @Override
            public List<RawMarketplaceOffer> fetchRawOffers(MarketplaceQueryContext context) {
                return List.of(
                        // Two identical offers from same provider, seller, and url
                        new RawMarketplaceOffer("feed-1", "MegaStore", "https://megastore.com", "https://megastore.com/item/1", new BigDecimal("500.00"), null, "USD", true, 1, LocalDateTime.now()),
                        new RawMarketplaceOffer("feed-2", "MegaStore", "https://megastore.com", "https://megastore.com/item/1", new BigDecimal("500.00"), null, "USD", true, 1, LocalDateTime.now()),
                        // Different seller with same price must NOT be deduplicated
                        new RawMarketplaceOffer("feed-3", "GigaStore", "https://gigastore.com", "https://gigastore.com/item/1", new BigDecimal("500.00"), null, "USD", true, 1, LocalDateTime.now())
                );
            }
        };

        MarketplaceOfferAggregatorService customAggregator = new MarketplaceOfferAggregatorService(
                List.of(duplicateAdapter),
                normalizer
        );

        List<NormalizedOfferDTO> offers = customAggregator.aggregateOffers(context);
        assertEquals(2, offers.size(), "Duplicate MegaStore offer dropped, but GigaStore preserved despite same price");
    }

    @Test
    @DisplayName("Adapter failure or exception does not crash aggregator or cause loss of catalog results")
    void testAggregator_adapterFailureResilience() {
        UUID prodId = UUID.randomUUID();
        MarketplaceQueryContext context = MarketplaceQueryContext.of(prodId, "Resilience Item");

        ProductEntity product = ProductEntity.builder().name("Resilience Item").build();
        product.setId(prodId);
        SellerEntity seller = SellerEntity.builder().name("Safe Catalog").websiteUrl("https://safe.com").build();
        ProductPriceEntity price = ProductPriceEntity.builder()
                .product(product)
                .seller(seller)
                .currentPrice(new BigDecimal("350.00"))
                .build();
        price.setId(UUID.randomUUID());

        when(productPriceRepository.findPricesWithSellersByProductIds(anyList()))
                .thenReturn(List.of(price));

        MarketplaceOfferAdapter failingAdapter = new MarketplaceOfferAdapter() {
            @Override
            public String getProviderId() {
                return "failing-provider";
            }

            @Override
            public String getDisplayName() {
                return "Failing Provider";
            }

            @Override
            public boolean isAvailable() {
                return true;
            }

            @Override
            public List<RawMarketplaceOffer> fetchRawOffers(MarketplaceQueryContext context) {
                throw new RuntimeException("Simulated provider connection timeout");
            }
        };

        MarketplaceOfferAggregatorService resilientAggregator = new MarketplaceOfferAggregatorService(
                List.of(failingAdapter, catalogAdapter),
                normalizer
        );

        List<NormalizedOfferDTO> offers = resilientAggregator.aggregateOffers(context);
        assertEquals(1, offers.size());
        assertEquals("Safe Catalog", offers.get(0).getSource().getSellerName());
        assertEquals(new BigDecimal("350.00"), offers.get(0).getPricing().getBasePrice());
    }

    @Test
    @DisplayName("Empty results or all-invalid offers return empty list safely without assigning best deal")
    void testAggregator_emptyOrAllInvalid() {
        UUID prodId = UUID.randomUUID();
        MarketplaceQueryContext context = MarketplaceQueryContext.of(prodId, "Empty Item");

        when(productPriceRepository.findPricesWithSellersByProductIds(anyList()))
                .thenReturn(Collections.emptyList());

        mockAdapter.setEnabled(false); // disable mock

        List<NormalizedOfferDTO> offers = aggregatorService.aggregateOffers(context);
        assertTrue(offers.isEmpty());
    }

    @Test
    @DisplayName("Deterministic ranking correctly selects lowest price as best deal across multiple adapters")
    void testAggregator_deterministicRankingAcrossAdapters() {
        UUID prodId = UUID.randomUUID();
        MarketplaceQueryContext context = MarketplaceQueryContext.of(prodId, "iPhone 15 Pro");

        ProductEntity product = ProductEntity.builder().name("iPhone 15 Pro").build();
        product.setId(prodId);
        SellerEntity seller = SellerEntity.builder().name("Catalog Retailer").build();
        ProductPriceEntity price = ProductPriceEntity.builder()
                .product(product)
                .seller(seller)
                .currentPrice(new BigDecimal("950.00")) // Lowest!
                .originalPrice(new BigDecimal("1099.00"))
                .build();
        price.setId(UUID.randomUUID());

        when(productPriceRepository.findPricesWithSellersByProductIds(anyList()))
                .thenReturn(List.of(price));

        List<NormalizedOfferDTO> offers = aggregatorService.aggregateOffers(context);

        // Catalog Retailer ($950.00) vs ElectroHub Direct ($979.99) vs Apple Authorized ($999.00)
        assertEquals(3, offers.size());
        assertEquals("Catalog Retailer", offers.get(0).getSource().getSellerName());
        assertEquals(new BigDecimal("950.00"), offers.get(0).getPricing().getBasePrice());
        assertTrue(offers.get(0).getIsBestDeal());
        assertEquals(0, offers.get(0).getPriceDifferenceFromLowest().compareTo(BigDecimal.ZERO));

        assertEquals("ElectroHub Direct", offers.get(1).getSource().getSellerName());
        assertEquals(new BigDecimal("979.99"), offers.get(1).getPricing().getBasePrice());
        assertFalse(offers.get(1).getIsBestDeal());
        assertEquals(new BigDecimal("29.99"), offers.get(1).getPriceDifferenceFromLowest());
    }
}
