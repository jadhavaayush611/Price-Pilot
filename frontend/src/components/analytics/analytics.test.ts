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
});
