import { describe, it, expect, vi, beforeEach } from 'vitest';
import { apiService, apiClient } from '../services/api';
import type { DashboardV2Response } from '../types';

describe('Dashboard V2 Frontend Service Tests', () => {
  beforeEach(() => {
    vi.restoreAllMocks();
  });

  it('fetches Dashboard V2 payload correctly via apiService.getDashboardV2', async () => {
    const mockDashboard: DashboardV2Response = {
      overview: {
        activeWatchlistsCount: 5,
        unreadAlertsCount: 2,
        historicalLowCount: 1,
        goodOrExcellentDealCount: 3,
        recentPriceDropCount: 2,
        savedComparisonsCount: 4,
        savedProductsCount: 8,
      },
      attentionItems: [
        {
          productId: 'prod-101',
          productName: 'Sony WH-1000XM5',
          brand: 'Sony',
          urgencyScore: 180,
          urgencyLevel: 'CRITICAL',
          primaryReason: 'Target price reached! Current: $299.00 (Target: $320.00)',
          supportingEvidence: ['Current price meets or beats your target'],
          currentPrice: 299.0,
          targetPrice: 320.0,
          dealQuality: 'EXCELLENT_DEAL',
          purchaseSignal: 'BUY_NOW',
          navigationUrl: '/product/prod-101',
        },
      ],
      priceOpportunities: [
        {
          productId: 'prod-101',
          productName: 'Sony WH-1000XM5',
          brand: 'Sony',
          currentPrice: 299.0,
          historicalMin: 299.0,
          historicalAvg: 380.0,
          dealQuality: 'EXCELLENT_DEAL',
          purchaseSignal: 'BUY_NOW',
          keyEvidence: '21.3% below historical average',
          navigationUrl: '/product/prod-101',
        },
      ],
      watchedProducts: [
        {
          productId: 'prod-101',
          watchlistId: 'watch-1',
          productName: 'Sony WH-1000XM5',
          brand: 'Sony',
          category: 'Headphones',
          currentPrice: 299.0,
          targetPrice: 320.0,
          historicalMin: 299.0,
          historicalAvg: 380.0,
          volatility: 'LOW',
          trend: 'FALLING',
          trendPercentage: -8.5,
          dealQuality: 'EXCELLENT_DEAL',
          purchaseSignal: 'BUY_NOW',
          targetMet: true,
          active: true,
        },
      ],
      recentAlerts: [
        {
          id: 'alert-1',
          userId: 'user-1',
          productId: 'prod-101',
          productName: 'Sony WH-1000XM5',
          alertType: 'PRICE_TARGET_REACHED',
          title: 'Target Price Hit',
          message: 'Target reached at $299.00',
          triggerValue: 320.0,
          observedValue: 299.0,
          read: false,
          createdAt: '2026-08-30T10:00:00Z',
        },
      ],
      recommendations: {
        items: [
          {
            productId: 'prod-202',
            productName: 'Bose QuietComfort Ultra',
            brand: 'Bose',
            currentPrice: 379.0,
            recommendationType: 'BEST_OVERALL',
            score: 95.0,
            confidence: 0.9,
            keyReason: 'TOP MATCH',
            explanation: 'Superior noise cancellation and long-term durability',
          },
        ],
        strategyUsed: 'HYBRID_V2',
        generatedAt: '2026-08-30T10:00:00Z',
        available: true,
      },
      recentActivity: [
        {
          id: 'act-1',
          productId: 'prod-101',
          productName: 'Sony WH-1000XM5',
          eventType: 'MAJOR_DROP',
          title: 'Major Price Drop (-12%)',
          description: 'Observed at $299.00',
          observedPrice: 299.0,
          amountChange: -40.0,
          percentageChange: -11.8,
          timestamp: '2026-08-30T09:30:00Z',
        },
      ],
      generatedAt: '2026-08-30T10:00:00Z',
    };

    const getSpy = vi.spyOn(apiClient, 'get').mockResolvedValue({ data: mockDashboard });

    const result = await apiService.getDashboardV2();

    expect(getSpy).toHaveBeenCalledWith('/dashboard/v2');
    expect(result).toBeDefined();
    expect(result.overview.activeWatchlistsCount).toBe(5);
    expect(result.overview.unreadAlertsCount).toBe(2);
    expect(result.attentionItems.length).toBe(1);
    expect(result.attentionItems[0].urgencyLevel).toBe('CRITICAL');
    expect(result.priceOpportunities.length).toBe(1);
    expect(result.watchedProducts.length).toBe(1);
    expect(result.watchedProducts[0].targetMet).toBe(true);
    expect(result.recommendations.available).toBe(true);
    expect(result.recentAlerts.length).toBe(1);
    expect(result.recentActivity.length).toBe(1);
  });

  it('handles empty dashboard overview cleanly', async () => {
    const emptyDashboard: DashboardV2Response = {
      overview: {
        activeWatchlistsCount: 0,
        unreadAlertsCount: 0,
        historicalLowCount: 0,
        goodOrExcellentDealCount: 0,
        recentPriceDropCount: 0,
        savedComparisonsCount: 0,
        savedProductsCount: 0,
      },
      attentionItems: [],
      priceOpportunities: [],
      watchedProducts: [],
      recentAlerts: [],
      recommendations: {
        items: [],
        available: false,
      },
      recentActivity: [],
      generatedAt: '2026-08-30T10:00:00Z',
    };

    vi.spyOn(apiClient, 'get').mockResolvedValue({ data: emptyDashboard });

    const result = await apiService.getDashboardV2();

    expect(result.overview.activeWatchlistsCount).toBe(0);
    expect(result.attentionItems).toEqual([]);
    expect(result.priceOpportunities).toEqual([]);
    expect(result.watchedProducts).toEqual([]);
  });
});
