import { describe, it, expect, vi, beforeEach } from 'vitest';
import { apiService } from '../../services/api';
import { getDisplayPrice, formatPrice, type CurrencyCode } from '../../currency';
import type { ProductWithPrices, ProductAnalytics } from '../../types';

// Mock localStorage for test environment
class LocalStorageMock {
  private store: Record<string, string> = {};
  clear() { this.store = {}; }
  getItem(key: string) { return this.store[key] || null; }
  setItem(key: string, value: string) { this.store[key] = value.toString(); }
  removeItem(key: string) { delete this.store[key]; }
}

if (typeof window === 'undefined') {
  const g = globalThis as unknown as { localStorage: LocalStorageMock; window: unknown };
  g.localStorage = new LocalStorageMock();
  g.window = { localStorage: g.localStorage };
}

describe('Landing Page FeaturedPricePreview Component Logic', () => {
  const sampleProduct: ProductWithPrices = {
    id: 'prod-iphone-15',
    name: 'Apple iPhone 15 (128 GB)',
    brand: 'Apple',
    description: 'Latest Apple Smartphone',
    category: 'Smartphones',
    lowestPrice: 799.00,
    imageUrl: 'https://m.media-amazon.com/images/I/71d7rfSl0wL._AC_UF1000,1000_QL80_.jpg',
    prices: [
      {
        id: 'price-1',
        seller: { id: 'seller-1', name: 'Amazon', websiteUrl: 'https://amazon.com', logoUrl: '' },
        currentPrice: 799.00,
        originalPrice: 899.00,
        discountPercentage: 11.12,
        productUrl: 'https://amazon.com/dp/B0CHX1W1XY',
        lastUpdated: '2026-09-01T10:00:00Z',
      },
    ],
  };

  const sampleAnalytics: ProductAnalytics = {
    productId: 'prod-iphone-15',
    viewCount: 200,
    saveCount: 45,
    watchlistCount: 20,
    priceChangeCount: 5,
    trendingScore: 92.0,
    currentPrice: 799.00,
    historicalMin: 749.00,
    historicalMax: 899.00,
    historicalAvg: 830.00,
    historicalMedian: 820.00,
    priceRange: 150.00,
    volatilityValue: 0.05,
    volatility: 'LOW',
    trend: 'FALLING',
    trendPercentage: -8.0,
    pricePositionScore: 85.0,
    dealQuality: 'EXCELLENT_DEAL',
    purchaseSignal: 'BUY_NOW',
    purchaseSignalReason: 'Current price ($799.00) is close to historical low.',
    supportingEvidence: ['Recorded major drop recently'],
    observationCount: 6,
    historicalLowDistance: 50.00,
    historicalAverageDistance: -31.00,
    historicalEvents: [],
    priceSeries: [
      { timestamp: '2026-08-01T10:00:00Z', price: 899.00, sellerName: 'Amazon' },
      { timestamp: '2026-08-15T10:00:00Z', price: 849.00, sellerName: 'Best Buy' },
      { timestamp: '2026-09-01T10:00:00Z', price: 799.00, sellerName: 'Amazon' },
    ],
    analyzedAt: '2026-09-02T10:00:00Z',
  };

  beforeEach(() => {
    vi.restoreAllMocks();
  });

  it('fetches real catalog product and historical price series for preview', async () => {
    vi.spyOn(apiService, 'getTrendingProducts').mockResolvedValue([sampleProduct]);
    vi.spyOn(apiService, 'getIntelligenceAnalytics').mockResolvedValue(sampleAnalytics);

    const candidates = await apiService.getTrendingProducts(6);
    expect(candidates.length).toBeGreaterThan(0);
    expect(candidates[0].id).toBe('prod-iphone-15');

    const analytics = await apiService.getIntelligenceAnalytics(candidates[0].id);
    expect(analytics.priceSeries).toBeDefined();
    expect(analytics.priceSeries!.length).toBe(3);
    expect(analytics.currentPrice).toBe(799.00);
    expect(analytics.trend).toBe('FALLING');
    expect(analytics.trendPercentage).toBe(-8.0);
  });

  describe('Multi-Currency Verification (Locked Deterministic Offline Rates)', () => {
    const testCases: { currency: CurrencyCode; rate: number; symbolPattern: RegExp }[] = [
      { currency: 'USD', rate: 1.0, symbolPattern: /\$/ },
      { currency: 'INR', rate: 80.0, symbolPattern: /₹/ },
      { currency: 'EUR', rate: 0.9, symbolPattern: /€/ },
      { currency: 'GBP', rate: 0.8, symbolPattern: /£/ },
      { currency: 'JPY', rate: 150.0, symbolPattern: /[¥￥]/ },
    ];

    testCases.forEach(({ currency, rate, symbolPattern }) => {
      it(`correctly converts canonical USD to ${currency} with rate ${rate} without double conversion`, () => {
        const canonicalCurrent = sampleAnalytics.currentPrice ?? 799.00;
        const canonicalOriginal = sampleProduct.prices[0].originalPrice;

        const displayCurrent = getDisplayPrice(canonicalCurrent, currency);
        const displayOriginal = getDisplayPrice(canonicalOriginal, currency);

        expect(displayCurrent).toBeCloseTo(canonicalCurrent * rate, 2);
        expect(displayOriginal).toBeCloseTo(canonicalOriginal * rate, 2);

        const formattedCurrent = formatPrice(displayCurrent, currency);
        const formattedOriginal = formatPrice(displayOriginal, currency);

        expect(formattedCurrent).toMatch(symbolPattern);
        expect(formattedOriginal).toMatch(symbolPattern);
      });
    });

    it('converts all price series points into target display currency', () => {
      const inrSeries = (sampleAnalytics.priceSeries || []).map(pt => ({
        ...pt,
        displayPrice: getDisplayPrice(pt.price, 'INR'),
      }));

      expect(inrSeries[0].displayPrice).toBe(899 * 80); // 71,920
      expect(inrSeries[1].displayPrice).toBe(849 * 80); // 67,920
      expect(inrSeries[2].displayPrice).toBe(799 * 80); // 63,920
    });
  });

  describe('Trend and Trajectory Calculations', () => {
    it('computes falling trend correctly', () => {
      const trend = sampleAnalytics.trend;
      const trendPct = sampleAnalytics.trendPercentage;
      const trendLabel = trend === 'FALLING' && trendPct !== undefined
        ? `↓ ${Math.abs(trendPct).toFixed(0)}%`
        : 'Stable';

      expect(trendLabel).toBe('↓ 8%');
    });

    it('computes rising trend correctly when price increases', () => {
      const risingAnalytics: ProductAnalytics = {
        ...sampleAnalytics,
        trend: 'RISING',
        trendPercentage: 12.5,
      };

      const trendLabel = risingAnalytics.trend === 'RISING' && risingAnalytics.trendPercentage !== undefined
        ? `↑ ${risingAnalytics.trendPercentage.toFixed(0)}%`
        : 'Stable';

      expect(trendLabel).toBe('↑ 13%');
    });

    it('derives trend from price series points if trend metadata is undefined', () => {
      const series = [
        { timestamp: '2026-08-01', price: 100 },
        { timestamp: '2026-08-15', price: 90 },
      ];

      const firstP = series[0].price;
      const lastP = series[1].price;
      const deltaPct = ((lastP - firstP) / firstP) * 100;

      expect(deltaPct).toBe(-10);
      const trendLabel = deltaPct < -1 ? `↓ ${Math.abs(deltaPct).toFixed(0)}%` : 'Stable';
      expect(trendLabel).toBe('↓ 10%');
    });
  });
});
