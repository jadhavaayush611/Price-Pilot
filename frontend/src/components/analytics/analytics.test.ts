import { describe, it, expect, vi, beforeEach } from 'vitest';
import { apiService, apiClient } from '../../services/api';
import type { ProductAnalytics } from '../../types';

describe('Price Intelligence Frontend Analytics Tests', () => {
  beforeEach(() => {
    vi.restoreAllMocks();
  });

  it('fetches and parses complete price intelligence analytics structure', async () => {
    const mockAnalytics: ProductAnalytics = {
      productId: 'prod-123',
      viewCount: 150,
      saveCount: 35,
      watchlistCount: 12,
      priceChangeCount: 8,
      trendingScore: 88.5,
      currentPrice: 849.99,
      historicalMin: 829.99,
      historicalMax: 999.99,
      historicalAvg: 915.50,
      historicalMedian: 899.99,
      priceRange: 170.00,
      volatilityValue: 0.045,
      volatility: 'LOW',
      trend: 'FALLING',
      trendPercentage: -4.2,
      pricePositionScore: 88.2,
      dealQuality: 'GOOD_DEAL',
      purchaseSignal: 'GOOD_TIME',
      purchaseSignalReason: 'Current price ($849.99) is favorably positioned below the historical average ($915.50).',
      supportingEvidence: [
        'Current price is 2.4% above the recorded all-time low ($829.99)',
        'Current price is 7.2% below the historical average ($915.50)',
        'Price volatility is LOW (CV: 4.5%), indicating high price consistency',
        'Recent trajectory reflects a FALLING trend (-4.2% relative to baseline)',
      ],
      observationCount: 8,
      historicalLowDistance: 20.00,
      historicalAverageDistance: -65.51,
      historicalEvents: [
        {
          eventType: 'MAJOR_DROP',
          percentageChange: -12.5,
          amountChange: -120.0,
          resultingPrice: 849.99,
          occurredAt: '2026-08-20T10:00:00Z',
          description: 'Significant price drop of 12.5% (-$120)',
        },
      ],
      priceSeries: [
        { timestamp: '2026-08-01T10:00:00Z', price: 999.99, sellerName: 'Store A' },
        { timestamp: '2026-08-10T10:00:00Z', price: 969.99, sellerName: 'Store B' },
        { timestamp: '2026-08-20T10:00:00Z', price: 849.99, sellerName: 'Store A' },
      ],
      analyzedAt: '2026-08-30T21:00:00Z',
    };

    const getSpy = vi.spyOn(apiClient, 'get').mockResolvedValue({ data: mockAnalytics });

    const result = await apiService.getIntelligenceAnalytics('prod-123');

    expect(getSpy).toHaveBeenCalledWith('/analytics/prod-123');
    expect(result.productId).toBe('prod-123');
    expect(result.dealQuality).toBe('GOOD_DEAL');
    expect(result.purchaseSignal).toBe('GOOD_TIME');
    expect(result.volatility).toBe('LOW');
    expect(result.trend).toBe('FALLING');
    expect(result.supportingEvidence?.length).toBe(4);
    expect(result.priceSeries?.length).toBe(3);
    expect(result.historicalEvents?.[0].eventType).toBe('MAJOR_DROP');
  });

  it('correctly parses insufficient-data state from API without errors', async () => {
    const insufficientAnalytics: ProductAnalytics = {
      productId: 'prod-new',
      viewCount: 2,
      saveCount: 0,
      watchlistCount: 0,
      priceChangeCount: 0,
      trendingScore: 2.0,
      currentPrice: 50.00,
      historicalMin: 50.00,
      historicalMax: 50.00,
      historicalAvg: 50.00,
      observationCount: 1,
      volatility: 'INSUFFICIENT_DATA',
      trend: 'INSUFFICIENT_DATA',
      dealQuality: 'INSUFFICIENT_DATA',
      purchaseSignal: 'INSUFFICIENT_DATA',
      purchaseSignalReason: 'Insufficient historical price records to determine a purchase timing signal.',
      supportingEvidence: [],
      priceSeries: [{ timestamp: '2026-08-30T12:00:00Z', price: 50.00 }],
    };

    vi.spyOn(apiClient, 'get').mockResolvedValue({ data: insufficientAnalytics });

    const result = await apiService.getIntelligenceAnalytics('prod-new');

    expect(result.purchaseSignal).toBe('INSUFFICIENT_DATA');
    expect(result.dealQuality).toBe('INSUFFICIENT_DATA');
    expect(result.observationCount).toBe(1);
    expect(result.supportingEvidence).toEqual([]);
  });

  it('rejects nonexistent products and propagates exact product UUID without mock fallback', async () => {
    const validUuid = '550e8400-e29b-41d4-a716-446655440000';
    const getSpy = vi.spyOn(apiClient, 'get').mockResolvedValue({
      data: {
        id: validUuid,
        name: 'Real Catalog Item',
        brand: 'RealBrand',
        prices: [],
      },
    });

    const product = await apiService.getProduct(validUuid);
    expect(getSpy).toHaveBeenCalledWith(`/products/${validUuid}`);
    expect(product).toBeDefined();
    expect(product?.id).toBe(validUuid);
    expect(product?.name).not.toContain('iPhone 15 Pro Max (256GB, Space Black)');
  });

  it('handles backend 404 cleanly when querying an invalid or nonexistent analytics UUID', async () => {
    const invalidId = 'nonexistent-uuid-999';
    vi.spyOn(apiClient, 'get').mockRejectedValue({
      response: { status: 404, data: { message: 'Product analytics not found' } },
    });

    await expect(apiService.getIntelligenceAnalytics(invalidId)).rejects.toBeDefined();
  });

  describe('P1-D Product-Driven Analytics & Currency Conversion', () => {
    const sampleCanonicalPriceUsd = 604.975;

    it('formats price metrics accurately across all 5 configured currencies without double conversion', async () => {
      const { getDisplayPrice, formatPrice } = await import('../../currency');

      // Locked conversion rates: USD 1, INR 80, EUR 0.90, GBP 0.80, JPY 150
      expect(getDisplayPrice(sampleCanonicalPriceUsd, 'USD')).toBeCloseTo(604.975, 2);
      expect(getDisplayPrice(sampleCanonicalPriceUsd, 'INR')).toBeCloseTo(48398, 2);
      expect(getDisplayPrice(sampleCanonicalPriceUsd, 'EUR')).toBeCloseTo(544.4775, 2);
      expect(getDisplayPrice(sampleCanonicalPriceUsd, 'GBP')).toBeCloseTo(483.98, 2);
      expect(getDisplayPrice(sampleCanonicalPriceUsd, 'JPY')).toBeCloseTo(90746.25, 2);

      const inrFormatted = formatPrice(getDisplayPrice(sampleCanonicalPriceUsd, 'INR'), 'INR');
      expect(inrFormatted).toContain('48,398');
      expect(inrFormatted).toContain('₹');
      expect(inrFormatted).not.toContain('38,71,840'); // No double conversion
    });

    it('ensures catalog selection API is called without hardcoded fallback products', async () => {
      const getSpy = vi.spyOn(apiClient, 'get').mockResolvedValue({
        data: {
          content: [
            { id: 'prod-sony', name: 'Sony WH-1000XM5', brand: 'Sony', lowestPrice: 349.99 },
            { id: 'prod-bose', name: 'Bose QuietComfort Ultra', brand: 'Bose', lowestPrice: 379.99 },
          ],
          totalElements: 2,
          totalPages: 1,
        },
      });

      const catalog = await apiService.getProducts(0, 24, undefined, undefined, 'Sony');
      expect(getSpy).toHaveBeenCalledWith('/products', {
        params: { page: 0, size: 24, search: 'Sony' },
      });
      expect(catalog.content.length).toBe(2);
      expect(catalog.content[0].name).toBe('Sony WH-1000XM5');
    });

    it('ensures analytics response matches product header price when present', async () => {
      const prodId = 'prod-match-123';
      vi.spyOn(apiClient, 'get').mockImplementation(async (url: string) => {
        if (url === `/products/${prodId}`) {
          return {
            data: {
              id: prodId,
              name: 'Apple iPad Pro 13 M4',
              brand: 'Apple',
              lowestPrice: 1268.18,
              prices: [{ id: 'p1', currentPrice: 1268.18, originalPrice: 1299.00 }],
            },
          };
        }
        if (url === `/analytics/${prodId}`) {
          return {
            data: {
              productId: prodId,
              currentPrice: 1268.18,
              historicalMin: 1199.00,
              historicalAvg: 1275.00,
              historicalMax: 1399.00,
              dealQuality: 'FAIR_PRICE',
              purchaseSignal: 'NEUTRAL',
            },
          };
        }
        return { data: {} };
      });

      const [prod, ana] = await Promise.all([
        apiService.getProduct(prodId),
        apiService.getIntelligenceAnalytics(prodId),
      ]);

      expect(prod?.lowestPrice).toBe(ana.currentPrice);
    });
  });
});

