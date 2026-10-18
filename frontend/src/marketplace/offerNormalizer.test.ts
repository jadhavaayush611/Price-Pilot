import { describe, it, expect } from 'vitest';
import type { Product, ProductPrice, ProductWithPrices } from '../types';
import type { NormalizedOffer, NormalizerOptions } from './types';
import {
  normalizeOffer,
  normalizeProductOffers,
  getOfferDisplayPrices,
  formatOfferObservationTime,
  getProvenanceBadge,
  offerToLegacyPrice,
  DEFAULT_STALE_HOURS,
} from './offerNormalizer';

function mustNormalize(
  price: ProductPrice,
  product?: Product | ProductWithPrices,
  options?: NormalizerOptions
): NormalizedOffer {
  const o = normalizeOffer(price, product, options);
  if (!o) {
    throw new Error('Expected offer to normalize successfully');
  }
  return o;
}

describe('Marketplace Offer Normalizer & Provenance Tests', () => {
  const samplePrice: ProductPrice = {
    id: 'price-123',
    productId: 'prod-456',
    sellerId: 'seller-789',
    currentPrice: 604.975,
    originalPrice: 699.0,
    discountPercentage: 13.45,
    productUrl: 'https://example.com/store/item',
    lastUpdated: '2026-10-15T12:00:00Z',
    seller: {
      id: 'seller-789',
      name: 'RetailGiant',
      websiteUrl: 'https://example.com',
      logoUrl: 'https://example.com/logo.png',
    },
  };

  describe('A. Base Price Normalization & Fee Separation', () => {
    it('normalizes canonical base price in USD without fabricating checkout fees', () => {
      const offer = mustNormalize(samplePrice);

      expect(offer.id).toBe('price-123');
      expect(offer.productId).toBe('prod-456');
      expect(offer.source.sellerName).toBe('RetailGiant');
      expect(offer.pricing.basePrice).toBe(604.975);
      expect(offer.pricing.originalPrice).toBe(699.0);
      expect(offer.pricing.currency).toBe('USD');

      // Crucial Marketplace Contract: Missing fees MUST NOT be inferred as 0
      expect(offer.pricing.shippingPrice).toBeUndefined();
      expect(offer.pricing.taxesAndFees).toBeUndefined();
      expect(offer.pricing.totalPrice).toBeUndefined();
      expect(offer.pricing.isBasePriceOnly).toBe(true);
      expect(offer.pricing.pricingDisclaimer.toLowerCase()).toContain('fees may apply at checkout');
    });

    it('calculates discount percentage when not provided in raw record', () => {
      const priceNoDiscount: ProductPrice = {
        ...samplePrice,
        discountPercentage: 0,
      };
      const offer = mustNormalize(priceNoDiscount);
      // ((699 - 604.975) / 699) * 100 = ~13.451%
      expect(offer.pricing.discountPercentage).toBeCloseTo(13.451, 2);
    });

    it('gracefully handles missing or zero original price without fabricating discount', () => {
      for (const zeroVal of [0, -10, NaN, undefined]) {
        const priceNoOriginal: ProductPrice = {
          ...samplePrice,
          originalPrice: zeroVal as number,
          discountPercentage: undefined as unknown as number,
        };
        const offer = mustNormalize(priceNoOriginal);
        expect(offer.pricing.originalPrice).toBeUndefined();
        expect(offer.pricing.discountPercentage).toBe(0);
      }
    });

    it('strictly rejects invalid, non-finite, missing, or negative base prices by returning null', () => {
      for (const invalidPrice of [-100, -0.01, NaN, Infinity, -Infinity, undefined, null, 'invalid' as unknown as number]) {
        const offer = normalizeOffer({
          ...samplePrice,
          currentPrice: invalidPrice as unknown as number,
        });
        expect(offer).toBeNull();
      }
    });

    it('accepts legitimate zero base prices (free promotional tier) only when explicitly a finite number', () => {
      const offer = mustNormalize({
        ...samplePrice,
        currentPrice: 0,
        originalPrice: 100,
        discountPercentage: undefined as unknown as number,
      });
      expect(offer.pricing.basePrice).toBe(0);
      expect(offer.pricing.originalPrice).toBe(100);
      expect(offer.pricing.discountPercentage).toBe(100);
    });

    it('handles missing optional availability gracefully', () => {
      const offer = mustNormalize({
        ...samplePrice,
        // no inStock field
      });
      expect(offer.availability.status).toBe('unknown');
      expect(offer.availability.isAvailable).toBeUndefined();
    });
  });

  describe('B. Provenance & Trust Classification', () => {
    it('treats catalog seed data as unverified demo data and never claims live verification', () => {
      const offer = mustNormalize(samplePrice);

      expect(offer.provenance.sourceType).toBe('demo');
      expect(offer.provenance.verificationStatus).toBe('unverified');
      expect(offer.provenance.isLiveVerified).toBe(false);
      expect(offer.provenance.trustLabel).toBe('Catalog Offer');
      expect(offer.provenance.trustDescription).toContain('Not a live verified retailer feed');
    });

    it('correctly attributes live verified provenance when explicitly configured', () => {
      const offer = mustNormalize(samplePrice, undefined, {
        defaultSourceType: 'live',
        defaultVerificationStatus: 'verified',
      });

      expect(offer.provenance.sourceType).toBe('live');
      expect(offer.provenance.verificationStatus).toBe('verified');
      expect(offer.provenance.isLiveVerified).toBe(true);
      expect(offer.provenance.trustLabel).toBe('Live Verified');
    });

    it('correctly attributes user-submitted provenance without false verification claims', () => {
      const offer = mustNormalize(samplePrice, undefined, {
        defaultSourceType: 'user_submitted',
        defaultVerificationStatus: 'unverified',
      });

      expect(offer.provenance.sourceType).toBe('user_submitted');
      expect(offer.provenance.verificationStatus).toBe('unverified');
      expect(offer.provenance.isLiveVerified).toBe(false);
      expect(offer.provenance.trustLabel).toBe('User Submitted');
    });

    it('generates appropriate badge UI configurations for each provenance type', () => {
      const liveBadge = getProvenanceBadge({
        sourceType: 'live',
        verificationStatus: 'verified',
        isLiveVerified: true,
        isStale: false,
        trustLabel: 'Live Verified',
        trustDescription: 'Real-time verified offer',
      });
      expect(liveBadge.tone).toBe('emerald');
      expect(liveBadge.label).toBe('Live Verified');

      const demoBadge = getProvenanceBadge({
        sourceType: 'demo',
        verificationStatus: 'unverified',
        isLiveVerified: false,
        isStale: false,
        trustLabel: 'Catalog Offer',
        trustDescription: 'Demo seed data',
      });
      expect(demoBadge.tone).toBe('zinc');
      expect(demoBadge.label).toBe('Catalog Offer');

      const staleDemoBadge = getProvenanceBadge({
        sourceType: 'demo',
        verificationStatus: 'unverified',
        isLiveVerified: false,
        isStale: true,
        trustLabel: 'Catalog Offer (Stale)',
        trustDescription: 'Old seed data',
      });
      expect(staleDemoBadge.tone).toBe('amber');
      expect(staleDemoBadge.label).toBe('Catalog Offer (Stale)');
    });
  });

  describe('C. Observation Time, Timezones & Staleness Handling', () => {
    it('parses valid ISO timestamps in UTC without fabricating dates', () => {
      const offer = mustNormalize({
        ...samplePrice,
        lastUpdated: '2026-10-15T12:00:00Z',
      });

      expect(offer.provenance.observedAt).toBe('2026-10-15T12:00:00.000Z');
      expect(formatOfferObservationTime(offer.provenance.observedAt)).toMatch(/Oct 15, 2026/);
    });

    it('parses valid ISO timestamps with timezone offsets correctly into UTC ISO', () => {
      const offer = mustNormalize({
        ...samplePrice,
        lastUpdated: '2026-10-15T17:30:00+05:30',
      });

      // 17:30:00 +05:30 = 12:00:00Z
      expect(offer.provenance.observedAt).toBe('2026-10-15T12:00:00.000Z');
      expect(formatOfferObservationTime(offer.provenance.observedAt)).toMatch(/Oct 15, 2026/);
    });

    it('preserves supported human-relative timestamps in rawLastUpdated without inventing fake dates', () => {
      for (const rel of ['10 mins ago', '2 hours ago', 'yesterday', 'just now', '3 days ago']) {
        const offer = mustNormalize({
          ...samplePrice,
          lastUpdated: rel,
        });

        expect(offer.provenance.observedAt).toBeUndefined();
        expect(offer.provenance.rawLastUpdated).toBe(rel);
        expect(formatOfferObservationTime(offer.provenance.observedAt, offer.provenance.rawLastUpdated)).toBe(rel);
      }
    });

    it('rejects unsupported arbitrary text strings and does not store them as relative timestamps', () => {
      for (const unsupp of ['some time ago', 'not-a-date', 'random gibberish', 'foo bar']) {
        const offer = mustNormalize({
          ...samplePrice,
          lastUpdated: unsupp,
        });

        expect(offer.provenance.observedAt).toBeUndefined();
        expect(offer.provenance.rawLastUpdated).toBeUndefined();
        expect(formatOfferObservationTime(offer.provenance.observedAt, offer.provenance.rawLastUpdated)).toBe('Update time unavailable');
      }
    });

    it('handles empty and whitespace timestamps conservatively', () => {
      for (const emptyVal of ['', '   ', '\t\n']) {
        const offer = mustNormalize({
          ...samplePrice,
          lastUpdated: emptyVal,
        });

        expect(offer.provenance.observedAt).toBeUndefined();
        expect(offer.provenance.rawLastUpdated).toBeUndefined();
        expect(formatOfferObservationTime(offer.provenance.observedAt, offer.provenance.rawLastUpdated)).toBe('Update time unavailable');
      }
    });

    it('discards invalid / junk timestamp strings and displays "Update time unavailable"', () => {
      for (const junk of ['null', 'undefined', 'invalid', 'invalid date', 'N/A', 'none', 'unknown']) {
        const offer = mustNormalize({
          ...samplePrice,
          lastUpdated: junk,
        });
        expect(offer.provenance.observedAt).toBeUndefined();
        expect(offer.provenance.rawLastUpdated).toBeUndefined();
        expect(formatOfferObservationTime(offer.provenance.observedAt, offer.provenance.rawLastUpdated)).toBe('Update time unavailable');
      }
    });

    it('handles missing timestamp (undefined) conservatively without marking demo data as verified', () => {
      const offer = mustNormalize({
        ...samplePrice,
        lastUpdated: undefined as unknown as string,
      });

      expect(offer.provenance.observedAt).toBeUndefined();
      expect(offer.provenance.rawLastUpdated).toBeUndefined();
      expect(offer.provenance.isLiveVerified).toBe(false);
      expect(offer.provenance.verificationStatus).toBe('unverified');
      expect(formatOfferObservationTime(offer.provenance.observedAt, offer.provenance.rawLastUpdated)).toBe('Update time unavailable');
    });

    it('handles future timestamps without flagging them as stale', () => {
      const futureDate = new Date(Date.now() + 2 * 24 * 3600 * 1000).toISOString();
      const offer = mustNormalize({
        ...samplePrice,
        lastUpdated: futureDate,
      });

      expect(offer.provenance.observedAt).toBe(futureDate);
      expect(offer.provenance.isStale).toBe(false);
      expect(offer.provenance.trustLabel).toBe('Catalog Offer');
    });

    it('flags observations older than staleThresholdHours as stale without falsely implying verified source', () => {
      // 30 days ago
      const thirtyDaysAgo = new Date(Date.now() - 30 * 24 * 3600 * 1000).toISOString();
      const offer = mustNormalize({
        ...samplePrice,
        lastUpdated: thirtyDaysAgo,
      }, undefined, { staleThresholdHours: DEFAULT_STALE_HOURS });

      expect(offer.provenance.isStale).toBe(true);
      expect(offer.provenance.sourceType).toBe('demo');
      // Crucial: Stale status does NOT imply a verified source.
      expect(offer.provenance.verificationStatus).toBe('unverified');
      expect(offer.provenance.isLiveVerified).toBe(false);
      expect(offer.provenance.trustLabel).toBe('Catalog Offer (Stale)');
    });

    it('degrades previously verified live feeds to stale verification status when observation expires', () => {
      const thirtyDaysAgo = new Date(Date.now() - 30 * 24 * 3600 * 1000).toISOString();
      const offer = mustNormalize({
        ...samplePrice,
        lastUpdated: thirtyDaysAgo,
      }, undefined, {
        defaultSourceType: 'live',
        defaultVerificationStatus: 'verified',
        staleThresholdHours: DEFAULT_STALE_HOURS,
      });

      expect(offer.provenance.isStale).toBe(true);
      expect(offer.provenance.sourceType).toBe('stale');
      expect(offer.provenance.verificationStatus).toBe('stale');
      expect(offer.provenance.isLiveVerified).toBe(false);
      expect(offer.provenance.trustLabel).toBe('Stale Observation');
    });
  });

  describe('D. Availability & Stock State', () => {
    it('marks availability as unknown when source data contains no inventory claim', () => {
      const offer = mustNormalize(samplePrice);
      expect(offer.availability.status).toBe('unknown');
      expect(offer.availability.isAvailable).toBeUndefined();
    });

    it('correctly maps explicit inStock: true to in_stock', () => {
      const priceWithStock = {
        ...samplePrice,
        inStock: true,
      };
      const offer = mustNormalize(priceWithStock as ProductPrice);
      expect(offer.availability.status).toBe('in_stock');
      expect(offer.availability.isAvailable).toBe(true);
    });

    it('correctly maps explicit inStock: false to out_of_stock', () => {
      const priceOutStock = {
        ...samplePrice,
        inStock: false,
      };
      const offer = mustNormalize(priceOutStock as ProductPrice);
      expect(offer.availability.status).toBe('out_of_stock');
      expect(offer.availability.isAvailable).toBe(false);
    });
  });

  describe('E. Competitive Product Offers Ranking & Parity', () => {
    const mockProduct: ProductWithPrices = {
      id: 'prod-001',
      name: 'Nothing Phone 2',
      brand: 'Nothing',
      description: 'Transparent phone',
      category: 'Smartphone',
      imageUrl: 'https://example.com/nothing2.jpg',
      prices: [
        {
          id: 'price-higher',
          currentPrice: 620.0,
          originalPrice: 699.0,
          discountPercentage: 11.3,
          productUrl: 'https://amazon.com/nothing2',
          lastUpdated: '1 hour ago',
          seller: { id: 's2', name: 'Amazon', websiteUrl: '', logoUrl: '' },
        },
        {
          id: 'price-lowest',
          currentPrice: 604.975,
          originalPrice: 699.0,
          discountPercentage: 13.45,
          productUrl: 'https://flipkart.com/nothing2',
          lastUpdated: '10 mins ago',
          seller: { id: 's1', name: 'Flipkart', websiteUrl: '', logoUrl: '' },
        },
      ],
    };

    it('sorts offers canonically by base price and identifies the best deal', () => {
      const offers = normalizeProductOffers(mockProduct);

      expect(offers).toHaveLength(2);
      expect(offers[0].id).toBe('price-lowest');
      expect(offers[0].isBestDeal).toBe(true);
      expect(offers[0].priceDifferenceFromLowest).toBe(0);

      expect(offers[1].id).toBe('price-higher');
      expect(offers[1].isBestDeal).toBe(false);
      expect(offers[1].priceDifferenceFromLowest).toBeCloseTo(15.025, 3);
    });

    it('resolves ties deterministically: by discount percentage then seller name (exact backend parity)', () => {
      const tiedProduct: ProductWithPrices = {
        ...mockProduct,
        prices: [
          {
            id: 'p-zeta',
            currentPrice: 500.0,
            originalPrice: 550.0,
            discountPercentage: 9.09,
            productUrl: '#',
            lastUpdated: '1 hour ago',
            seller: { id: 'sz', name: 'Zeta Store', websiteUrl: '', logoUrl: '' },
          },
          {
            id: 'p-beta',
            currentPrice: 500.0,
            originalPrice: 600.0,
            discountPercentage: 16.67,
            productUrl: '#',
            lastUpdated: '1 hour ago',
            seller: { id: 'sb', name: 'Beta Store', websiteUrl: '', logoUrl: '' },
          },
          {
            id: 'p-alpha',
            currentPrice: 500.0,
            originalPrice: 600.0,
            discountPercentage: 16.67,
            productUrl: '#',
            lastUpdated: '1 hour ago',
            seller: { id: 'sa', name: 'Alpha Store', websiteUrl: '', logoUrl: '' },
          },
        ],
      };

      const offers = normalizeProductOffers(tiedProduct);

      expect(offers).toHaveLength(3);
      // Both p-alpha and p-beta have highest discount (16.67%), but "Alpha Store" < "Beta Store"
      expect(offers[0].source.sellerName).toBe('Alpha Store');
      expect(offers[0].isBestDeal).toBe(true);

      expect(offers[1].source.sellerName).toBe('Beta Store');
      expect(offers[1].isBestDeal).toBe(false);

      expect(offers[2].source.sellerName).toBe('Zeta Store');
      expect(offers[2].isBestDeal).toBe(false);

      // All tied on base price, so difference from lowest is 0 for all
      expect(offers[0].priceDifferenceFromLowest).toBe(0);
      expect(offers[1].priceDifferenceFromLowest).toBe(0);
      expect(offers[2].priceDifferenceFromLowest).toBe(0);
    });

    it('calculates canonical USD savings correctly for each offer', () => {
      const offers = normalizeProductOffers(mockProduct);
      // 699.0 - 604.975 = 94.025
      expect(offers[0].savings).toBeCloseTo(94.025, 3);
    });

    it('preserves backward compatibility when converting to legacy price', () => {
      const offers = normalizeProductOffers(mockProduct);
      const legacy = offerToLegacyPrice(offers[0]);

      expect(legacy.id).toBe('price-lowest');
      expect(legacy.currentPrice).toBe(604.975);
      expect(legacy.seller?.name).toBe('Flipkart');
    });

    it('excludes invalid offers and preserves valid offers without fabricating fallback prices', () => {
      const mixedProduct: ProductWithPrices = {
        ...mockProduct,
        prices: [
          {
            ...samplePrice,
            id: 'p-negative',
            currentPrice: -50.0,
            seller: { id: 's-neg', name: 'Negative Seller', websiteUrl: '', logoUrl: '' },
          },
          {
            ...samplePrice,
            id: 'p-nan',
            currentPrice: NaN,
            seller: { id: 's-nan', name: 'NaN Seller', websiteUrl: '', logoUrl: '' },
          },
          {
            ...samplePrice,
            id: 'p-infinity',
            currentPrice: Infinity,
            seller: { id: 's-inf', name: 'Infinity Seller', websiteUrl: '', logoUrl: '' },
          },
          {
            ...samplePrice,
            id: 'p-valid',
            currentPrice: 299.99,
            seller: { id: 's-val', name: 'Valid Seller', websiteUrl: '', logoUrl: '' },
          },
        ],
      };

      const offers = normalizeProductOffers(mixedProduct);

      // Only the single valid offer must survive
      expect(offers).toHaveLength(1);
      expect(offers[0].id).toBe('p-valid');
      expect(offers[0].pricing.basePrice).toBe(299.99);
      expect(offers[0].isBestDeal).toBe(true);
      expect(offers[0].priceDifferenceFromLowest).toBe(0);
    });

    it('returns empty array when all offers have invalid prices, ensuring no offer receives isBestDeal', () => {
      const allInvalidProduct: ProductWithPrices = {
        ...mockProduct,
        prices: [
          { ...samplePrice, id: 'inv-1', currentPrice: -100 },
          { ...samplePrice, id: 'inv-2', currentPrice: NaN },
          { ...samplePrice, id: 'inv-3', currentPrice: undefined as unknown as number },
        ],
      };

      const offers = normalizeProductOffers(allInvalidProduct);
      expect(offers).toHaveLength(0);
    });

    it('handles legitimate zero base prices alongside positive prices without confusing them with invalid prices', () => {
      const promoProduct: ProductWithPrices = {
        ...mockProduct,
        prices: [
          {
            ...samplePrice,
            id: 'p-positive',
            currentPrice: 100.0,
            originalPrice: 150.0,
            seller: { id: 's-pos', name: 'Positive Seller', websiteUrl: '', logoUrl: '' },
          },
          {
            ...samplePrice,
            id: 'p-free',
            currentPrice: 0.0,
            originalPrice: 100.0,
            seller: { id: 's-free', name: 'Free Promo Seller', websiteUrl: '', logoUrl: '' },
          },
        ],
      };

      const offers = normalizeProductOffers(promoProduct);
      expect(offers).toHaveLength(2);

      // Zero-price promotional offer must legitimately be the cheapest
      expect(offers[0].id).toBe('p-free');
      expect(offers[0].pricing.basePrice).toBe(0);
      expect(offers[0].isBestDeal).toBe(true);

      // Positive offer is second
      expect(offers[1].id).toBe('p-positive');
      expect(offers[1].pricing.basePrice).toBe(100.0);
      expect(offers[1].isBestDeal).toBe(false);
      expect(offers[1].priceDifferenceFromLowest).toBe(100.0);
    });
  });

  describe('F. Display Currency Conversion Integration & Invariants', () => {
    it('converts canonical pricing to target display currencies using deterministic rates', () => {
      const offer = normalizeOffer(samplePrice)!;

      // INR (rate: 80)
      const inrPrices = getOfferDisplayPrices(offer, 'INR');
      expect(inrPrices.displayBasePrice).toBeCloseTo(48398.0, 1);
      expect(Math.round(inrPrices.displayBasePrice)).toBe(48398);
      expect(inrPrices.formattedBasePrice).toContain('₹');
      expect(inrPrices.formattedBasePrice).toMatch(/₹\s*48,398/);

      // EUR (rate: 0.90)
      const eurPrices = getOfferDisplayPrices(offer, 'EUR');
      expect(eurPrices.displayBasePrice).toBeCloseTo(544.4775, 2);
      expect(eurPrices.formattedBasePrice).toContain('€');

      // USD (rate: 1.0)
      const usdPrices = getOfferDisplayPrices(offer, 'USD');
      expect(usdPrices.displayBasePrice).toBe(604.975);
      expect(usdPrices.formattedBasePrice).toContain('$');
    });

    it('currency conversion is presentation-only and cannot alter canonical sorting or best deal selection', () => {
      const p1: ProductPrice = { ...samplePrice, id: 'p1', currentPrice: 100 };
      const p2: ProductPrice = { ...samplePrice, id: 'p2', currentPrice: 200 };
      const product: ProductWithPrices = {
        id: 'p-multi',
        name: 'Currency Invariant Item',
        brand: 'Test',
        category: 'Test',
        description: '',
        imageUrl: '',
        prices: [p2, p1],
      };

      const offers = normalizeProductOffers(product);
      expect(offers[0].id).toBe('p1');
      expect(offers[0].isBestDeal).toBe(true);

      // Converted presentation prices across all currencies
      for (const curr of ['USD', 'INR', 'EUR', 'GBP', 'JPY'] as const) {
        const p1Conv = getOfferDisplayPrices(offers[0], curr);
        const p2Conv = getOfferDisplayPrices(offers[1], curr);
        expect(p1Conv.displayBasePrice).toBeLessThan(p2Conv.displayBasePrice);
      }
    });

    it('repeated normalization or currency changes cannot double-convert prices', () => {
      const initialOffer = normalizeOffer(samplePrice)!;
      expect(initialOffer.pricing.basePrice).toBe(604.975);

      // Convert to INR
      const inr1 = getOfferDisplayPrices(initialOffer, 'INR');
      expect(Math.round(inr1.displayBasePrice)).toBe(48398);

      // Convert to EUR
      const eur1 = getOfferDisplayPrices(initialOffer, 'EUR');
      expect(Math.round(eur1.displayBasePrice)).toBe(544);

      // Convert back to INR
      const inr2 = getOfferDisplayPrices(initialOffer, 'INR');
      expect(Math.round(inr2.displayBasePrice)).toBe(48398);

      // Re-normalizing from legacy price retains canonical 604.975 without multiplying
      const legacy = offerToLegacyPrice(initialOffer);
      const reNormalized = normalizeOffer(legacy)!;
      expect(reNormalized.pricing.basePrice).toBe(604.975);
      const reInr = getOfferDisplayPrices(reNormalized, 'INR');
      expect(Math.round(reInr.displayBasePrice)).toBe(48398);
    });
  });
});
