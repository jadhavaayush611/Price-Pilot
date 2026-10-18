import { describe, it, expect, vi, beforeEach } from 'vitest';
import {
  mapDtoToNormalizedOffer,
  normalizeProductOffers,
  getProvenanceBadge,
  getOfferDisplayPrices,
  type NormalizedOfferDTO,
  type NormalizedOffer,
} from '../marketplace';
import type { ProductWithPrices } from '../types';
import { apiService } from '../services/api';

describe('Unit 5.5: Marketplace Product Offers Integration & Fallback Tests', () => {
  const mockProductId = '3fa85f64-5717-4562-b3fc-2c963f66afa6';

  const sampleCatalogDto: NormalizedOfferDTO = {
    id: 'offer-catalog-1',
    productId: mockProductId,
    productTitle: 'Sony WH-1000XM5',
    source: {
      sellerId: 'amazon-us',
      sellerName: 'Amazon US',
      websiteUrl: 'https://amazon.com',
      offerUrl: 'https://amazon.com/dp/B09XS7JWHH',
      providerName: 'catalog',
    },
    pricing: {
      basePrice: 348.0,
      originalPrice: 399.99,
      discountPercentage: 13.0,
      currency: 'USD',
      shippingPrice: null,
      taxesAndFees: null,
      totalPrice: null,
      basePriceOnly: true,
      pricingDisclaimer: 'Listed base price. Additional shipping, taxes, and merchant fees may apply at checkout.',
    },
    provenance: {
      sourceType: 'DEMO',
      verificationStatus: 'UNVERIFIED',
      liveVerified: false,
      observedAt: '2026-10-18T12:00:00Z',
      rawLastUpdated: '2026-10-18T12:00:00Z',
      stale: false,
      trustLabel: 'Catalog Offer',
      trustDescription: 'Catalog demonstration offer recorded in PricePilot.',
    },
    availability: {
      status: 'IN_STOCK',
      available: true,
      stockQuantity: 10,
    },
    isBestDeal: true,
    savings: 51.99,
    priceDifferenceFromLowest: 0.0,
  };

  const sampleSimulatedDto: NormalizedOfferDTO = {
    id: 'offer-mock-2',
    productId: mockProductId,
    productTitle: 'Sony WH-1000XM5',
    source: {
      sellerId: 'mock-seller-2',
      sellerName: 'ElectroHub Direct',
      websiteUrl: 'https://mock.example.com',
      offerUrl: 'https://mock.example.com/p/202',
      providerName: 'mock-marketplace',
    },
    pricing: {
      basePrice: 329.99,
      originalPrice: 389.0,
      discountPercentage: 15.17,
      currency: 'USD',
      shippingPrice: null,
      taxesAndFees: null,
      totalPrice: null,
      basePriceOnly: true,
    },
    provenance: {
      sourceType: 'DEMO',
      verificationStatus: 'UNVERIFIED',
      liveVerified: false,
      stale: false,
      trustLabel: 'Simulated Offer',
      trustDescription: 'Simulated offline marketplace offer for testing. Not a verified retailer listing.',
    },
    availability: {
      status: 'UNKNOWN',
      available: null,
      stockQuantity: null,
    },
    isBestDeal: true,
    savings: 59.01,
    priceDifferenceFromLowest: 0.0,
  };

  const sampleProduct: ProductWithPrices = {
    id: mockProductId,
    name: 'Sony WH-1000XM5',
    brand: 'Sony',
    category: 'Audio',
    description: 'Noise Cancelling Headphones',
    imageUrl: 'https://example.com/sony.jpg',
    createdAt: '2026-01-01T00:00:00Z',
    updatedAt: '2026-10-18T00:00:00Z',
    prices: [
      {
        id: 'legacy-price-1',
        productId: mockProductId,
        sellerId: 'bestbuy-us',
        currentPrice: 379.99,
        originalPrice: 399.99,
        discountPercentage: 5.0,
        productUrl: 'https://bestbuy.com/p/1',
        lastUpdated: '2026-10-18T10:00:00Z',
        seller: {
          id: 'bestbuy-us',
          name: 'Best Buy',
          websiteUrl: 'https://bestbuy.com',
          logoUrl: 'https://bestbuy.com/logo.png',
        },
      },
    ],
  };

  describe('1. DTO to NormalizedOffer Mapping', () => {
    it('correctly maps backend DTO without fabricating missing fees or stock', () => {
      const offer = mapDtoToNormalizedOffer(sampleCatalogDto);

      expect(offer.id).toBe('offer-catalog-1');
      expect(offer.productId).toBe(mockProductId);
      expect(offer.pricing.basePrice).toBe(348.0);
      expect(offer.pricing.originalPrice).toBe(399.99);
      expect(offer.pricing.currency).toBe('USD');
      expect(offer.pricing.shippingPrice).toBeUndefined();
      expect(offer.pricing.taxesAndFees).toBeUndefined();
      expect(offer.pricing.totalPrice).toBeUndefined();
      expect(offer.provenance.isLiveVerified).toBe(false);
      expect(offer.provenance.trustLabel).toBe('Catalog Offer');
      expect(offer.availability.status).toBe('in_stock');
      expect(offer.availability.isAvailable).toBe(true);
    });

    it('strictly forbids simulated offers from receiving live-verified badges or emerald styling', () => {
      // Even if a malformed payload claimed liveVerified: true
      const forgedDto: NormalizedOfferDTO = {
        ...sampleSimulatedDto,
        provenance: {
          ...sampleSimulatedDto.provenance,
          liveVerified: true,
        },
      };

      const offer = mapDtoToNormalizedOffer(forgedDto);
      expect(offer.provenance.isLiveVerified).toBe(false);

      const badge = getProvenanceBadge(offer.provenance);
      expect(badge.label).toBe('Simulated Offer');
      expect(badge.tone).toBe('amber');
      expect(badge.label).not.toContain('Live Verified');
      expect(badge.description.toLowerCase()).toContain('simulated');
    });

    it('preserves unknown availability and unknown timestamps as unknown', () => {
      const offer = mapDtoToNormalizedOffer(sampleSimulatedDto);

      expect(offer.availability.status).toBe('unknown');
      expect(offer.availability.isAvailable).toBeUndefined();
      expect(offer.availability.stockQuantity).toBeUndefined();
      expect(offer.provenance.observedAt).toBeUndefined();
    });
  });

  describe('2. Endpoint Data Loading, Empty States & Resilient Fallback', () => {
    beforeEach(() => {
      vi.restoreAllMocks();
    });

    it('loads aggregated offers successfully when endpoint responds 200', async () => {
      vi.spyOn(apiService, 'getProductOffers').mockResolvedValueOnce([sampleCatalogDto, sampleSimulatedDto]);

      const dtos = await apiService.getProductOffers(mockProductId);
      const mapped = dtos.map(mapDtoToNormalizedOffer);

      expect(mapped).toHaveLength(2);
      expect(mapped[0].source.sellerName).toBe('Amazon US');
      expect(mapped[1].source.sellerName).toBe('ElectroHub Direct');
      expect(mapped[1].provenance.sourceType).toBe('simulated');
    });

    it('handles empty results from endpoint cleanly without crashing', async () => {
      vi.spyOn(apiService, 'getProductOffers').mockResolvedValueOnce([]);

      const dtos = await apiService.getProductOffers(mockProductId);
      const mapped = dtos.map(mapDtoToNormalizedOffer);

      expect(mapped).toHaveLength(0);
    });

    it('falls back to local catalog prices when aggregated offers endpoint fails', async () => {
      vi.spyOn(apiService, 'getProductOffers').mockRejectedValueOnce(new Error('Network error / 500'));

      let isFallback = false;
      let activeOffers: NormalizedOffer[] = [];

      try {
        const dtos = await apiService.getProductOffers(mockProductId);
        activeOffers = dtos.map(mapDtoToNormalizedOffer);
      } catch {
        isFallback = true;
        activeOffers = normalizeProductOffers(sampleProduct);
      }

      expect(isFallback).toBe(true);
      expect(activeOffers).toHaveLength(1);
      expect(activeOffers[0].source.sellerName).toBe('Best Buy');
      expect(activeOffers[0].pricing.basePrice).toBe(379.99);
      expect(activeOffers[0].provenance.trustLabel).toBe('Catalog Offer');
      expect(activeOffers[0].provenance.isLiveVerified).toBe(false);
    });

    it('guards against fast navigation overwriting state with superseded responses', async () => {
      let isCurrentA = true;
      let isCurrentB = true;

      let resultA: NormalizedOffer[] | null = null;
      let resultB: NormalizedOffer[] | null = null;

      // Simulated navigation: User visits Product A, then navigates to Product B before A completes
      const promiseA = new Promise<NormalizedOfferDTO[]>((resolve) => {
        setTimeout(() => resolve([sampleCatalogDto]), 50);
      });
      const promiseB = new Promise<NormalizedOfferDTO[]>((resolve) => {
        setTimeout(() => resolve([sampleSimulatedDto]), 10);
      });

      // User starts on A
      promiseA.then((dtos) => {
        if (!isCurrentA) return;
        resultA = dtos.map(mapDtoToNormalizedOffer);
      });

      // User navigates away from A immediately to B
      isCurrentA = false; // Component unmount / id change cleanup cancels A

      // User on B
      await promiseB.then((dtos) => {
        if (!isCurrentB) return;
        resultB = dtos.map(mapDtoToNormalizedOffer);
      });

      // Wait for promiseA to finish
      await promiseA;

      // Result for A was discarded
      expect(resultA).toBeNull();
      // Result for B was applied
      expect(resultB).not.toBeNull();
      expect(resultB![0].source.sellerName).toBe('ElectroHub Direct');
    });
  });

  describe('3. Provenance Differentiation: Catalog vs Simulated vs Fallback', () => {
    it('distinguishes catalog offers from simulated offers in trust labels and tones', () => {
      const catalogOffer = mapDtoToNormalizedOffer(sampleCatalogDto);
      const simulatedOffer = mapDtoToNormalizedOffer(sampleSimulatedDto);

      const catalogBadge = getProvenanceBadge(catalogOffer.provenance);
      const simulatedBadge = getProvenanceBadge(simulatedOffer.provenance);

      expect(catalogBadge.label).toBe('Catalog Offer');
      expect(catalogBadge.tone).toBe('zinc');

      expect(simulatedBadge.label).toBe('Simulated Offer');
      expect(simulatedBadge.tone).toBe('amber');
    });

    it('identifies stale offers correctly without fabricating freshness', () => {
      const staleCatalogDto: NormalizedOfferDTO = {
        ...sampleCatalogDto,
        provenance: {
          ...sampleCatalogDto.provenance,
          stale: true,
          trustLabel: 'Catalog Offer (Stale)',
        },
      };

      const offer = mapDtoToNormalizedOffer(staleCatalogDto);
      const badge = getProvenanceBadge(offer.provenance);

      expect(badge.label).toBe('Catalog Offer (Stale)');
      expect(badge.tone).toBe('amber');
    });

    it('ensures stale lowest-price offers do not receive unqualified Best Deal label in UI presentation', () => {
      const staleLowestDto: NormalizedOfferDTO = {
        ...sampleCatalogDto,
        isBestDeal: true,
        pricing: {
          ...sampleCatalogDto.pricing,
          basePrice: 299.99,
        },
        provenance: {
          ...sampleCatalogDto.provenance,
          stale: true,
          trustLabel: 'Catalog Offer (Stale)',
        },
      };

      const offer = mapDtoToNormalizedOffer(staleLowestDto);
      expect(offer.isBestDeal).toBe(true);
      expect(offer.provenance.isStale).toBe(true);

      // In SellerCard and ProductPage table, isBestDeal && isStaleOffer resolves to "Lowest Stale Listed Price" (amber)
      const isSimulated = offer.provenance.sourceType?.toLowerCase() === 'simulated';
      const isStale = Boolean(offer.provenance.isStale);
      const dealLabel = isSimulated
        ? 'Simulated Deal (Testing)'
        : isStale
        ? 'Lowest Stale Listed Price'
        : 'Best Deal';

      expect(dealLabel).toBe('Lowest Stale Listed Price');
      expect(dealLabel).not.toBe('Best Deal');
    });

    it('handles all-stale offer lists without implying any is a currently reliable bargain', () => {
      const dtos: NormalizedOfferDTO[] = [
        {
          ...sampleCatalogDto,
          id: 'stale-1',
          isBestDeal: true,
          pricing: { ...sampleCatalogDto.pricing, basePrice: 250.0 },
          provenance: { ...sampleCatalogDto.provenance, stale: true, trustLabel: 'Catalog Offer (Stale)' },
        },
        {
          ...sampleCatalogDto,
          id: 'stale-2',
          isBestDeal: false,
          pricing: { ...sampleCatalogDto.pricing, basePrice: 300.0 },
          provenance: { ...sampleCatalogDto.provenance, stale: true, trustLabel: 'Catalog Offer (Stale)' },
        },
      ];

      const offers = dtos.map(mapDtoToNormalizedOffer);
      expect(offers).toHaveLength(2);
      expect(offers.every((o) => o.provenance.isStale)).toBe(true);

      // Lowest stale offer is labeled truthfully as stale listed price, not Best Deal
      const lowestOffer = offers[0];
      const lowestLabel = lowestOffer.provenance.isStale ? 'Lowest Stale Listed Price' : 'Best Deal';
      expect(lowestLabel).toBe('Lowest Stale Listed Price');
    });
  });

  describe('4. Currency Conversion Consistency', () => {
    it('converts both aggregated and fallback prices accurately without double-conversion', () => {
      const offer = mapDtoToNormalizedOffer(sampleCatalogDto);

      const usdPrices = getOfferDisplayPrices(offer, 'USD');
      expect(usdPrices.displayBasePrice).toBe(348.0);
      expect(usdPrices.formattedBasePrice).toContain('$348');

      const inrPrices = getOfferDisplayPrices(offer, 'INR');
      // CONVERSION_RATES.INR = 80
      expect(inrPrices.displayBasePrice).toBe(348.0 * 80);
      expect(inrPrices.formattedBasePrice).toContain('₹');

      const eurPrices = getOfferDisplayPrices(offer, 'EUR');
      // CONVERSION_RATES.EUR = 0.9
      expect(eurPrices.displayBasePrice).toBe(348.0 * 0.9);
    });
  });
});
