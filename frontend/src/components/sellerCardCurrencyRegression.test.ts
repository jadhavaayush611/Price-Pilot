import { describe, it, expect } from 'vitest';
import { getDisplayPrice, formatPrice, type CurrencyCode } from '../currency';
import type { ProductWithPrices } from '../types';

describe('Seller Price & Multi-Currency Pipeline Regression Tests', () => {
  // Test products referenced in user report
  const testCases = [
    {
      name: 'Nothing Phone 2',
      canonicalUsd: 604.975,
      expectedInr: 48398, // 604.975 * 80 = 48398
      originalUsd: 699.00,
      expectedOriginalInr: 55920
    },
    {
      name: 'Google Pixel 8a',
      canonicalUsd: 516.20,
      expectedInr: 41296, // 516.20 * 80 = 41296
      originalUsd: 599.00,
      expectedOriginalInr: 47920
    },
    {
      name: 'iPad Pro 11 M4',
      canonicalUsd: 999.00,
      expectedInr: 79920, // 999 * 80 = 79920
      originalUsd: 1099.00,
      expectedOriginalInr: 87920
    },
    {
      name: 'iPad Pro 13 M4',
      canonicalUsd: 1268.175,
      expectedInr: 101454, // 1268.175 * 80 = 101454
      originalUsd: 1399.00,
      expectedOriginalInr: 111920
    }
  ];

  it('converts canonical USD seller offers to INR exactly once and does not double-convert', () => {
    for (const tc of testCases) {
      // 1. Single conversion from canonical USD
      const inrDisplay = getDisplayPrice(tc.canonicalUsd, 'INR');
      expect(Math.round(inrDisplay)).toBe(tc.expectedInr);

      // Formatted INR string check
      const formatted = formatPrice(inrDisplay, 'INR');
      expect(formatted).toContain('₹');
      // Format should NOT contain double-converted millions (e.g., 38,71,872 or 81,16,352)
      expect(formatted).not.toContain('38,71,8');
      expect(formatted).not.toContain('33,03,6');
      expect(formatted).not.toContain('81,16,3');

      // 2. Prevent double conversion: if a value is already INR, running getDisplayPrice again must NOT happen in pipeline
      const doubleConverted = getDisplayPrice(inrDisplay, 'INR');
      expect(doubleConverted).toBeGreaterThan(inrDisplay * 70); // This demonstrates the bug we fixed!
    }
  });

  it('correctly converts canonical $604.975 across all 5 supported currencies using deterministic locked rates', () => {
    const canonicalUsd = 604.975;
    const originalUsd = 699.00;

    // Deterministic configured offline rates:
    // USD -> USD = 1.0
    // USD -> INR = 80.0
    // USD -> EUR = 0.90
    // USD -> GBP = 0.80
    // USD -> JPY = 150.0

    const expectedRates: Record<CurrencyCode, { rate: number; expectedCurrentRaw: number; expectedCurrentRounded: number }> = {
      USD: { rate: 1.0, expectedCurrentRaw: 604.975, expectedCurrentRounded: 605 },
      INR: { rate: 80.0, expectedCurrentRaw: 48398.0, expectedCurrentRounded: 48398 },
      EUR: { rate: 0.90, expectedCurrentRaw: 544.4775, expectedCurrentRounded: 544 },
      GBP: { rate: 0.80, expectedCurrentRaw: 483.98, expectedCurrentRounded: 484 },
      JPY: { rate: 150.0, expectedCurrentRaw: 90746.25, expectedCurrentRounded: 90746 }
    };

    const currencies: CurrencyCode[] = ['USD', 'INR', 'EUR', 'GBP', 'JPY'];

    for (const curr of currencies) {
      const exp = expectedRates[curr];
      const currentLocal = getDisplayPrice(canonicalUsd, curr);
      const originalLocal = getDisplayPrice(originalUsd, curr);
      const savings = originalLocal - currentLocal;

      // Exact numerical check against deterministic formula
      expect(currentLocal).toBeCloseTo(exp.expectedCurrentRaw, 4);
      expect(Math.round(currentLocal)).toBe(exp.expectedCurrentRounded);
      expect(originalLocal).toBeCloseTo(originalUsd * exp.rate, 4);
      expect(savings).toBeCloseTo((originalUsd - canonicalUsd) * exp.rate, 4);

      // Formatted check
      const formattedCurrent = formatPrice(currentLocal, curr);
      const formattedOriginal = formatPrice(originalLocal, curr);
      const formattedSavings = formatPrice(savings, curr);

      expect(formattedCurrent.length).toBeGreaterThan(0);
      expect(formattedOriginal.length).toBeGreaterThan(0);
      expect(formattedSavings.length).toBeGreaterThan(0);
    }
  });

  it('seller offer prices and product-level best price agree exactly when seller is the best deal', () => {
    const product: ProductWithPrices = {
      id: 'prod-123',
      name: 'Nothing Phone 2',
      brand: 'Nothing',
      description: 'Transparent phone',
      category: 'Smartphone',
      imageUrl: 'https://example.com/nothing2.jpg',
      prices: [
        {
          id: 'price-1',
          currentPrice: 604.975,
          originalPrice: 699.00,
          discountPercentage: 13.45,
          productUrl: 'https://flipkart.com/nothing2',
          lastUpdated: '10 mins ago',
          seller: { id: 's1', name: 'Flipkart', websiteUrl: '', logoUrl: '' }
        },
        {
          id: 'price-2',
          currentPrice: 620.00,
          originalPrice: 699.00,
          discountPercentage: 11.3,
          productUrl: 'https://amazon.com/nothing2',
          lastUpdated: '1 hour ago',
          seller: { id: 's2', name: 'Amazon', websiteUrl: '', logoUrl: '' }
        }
      ]
    };

    const currency: CurrencyCode = 'INR';

    // Canonical sorting as done in ProductPage
    const sortedPrices = [...product.prices].sort((a, b) => a.currentPrice - b.currentPrice);
    const lowestPriceUsd = sortedPrices[0].currentPrice;
    const lowestPriceLocal = getDisplayPrice(lowestPriceUsd, currency);

    // Product-level best price
    const productLevelBestPrice = formatPrice(lowestPriceLocal, currency);

    // SellerCard computation for best deal seller (Flipkart)
    const bestSellerPrice = sortedPrices[0];
    const sellerCardCurrentLocal = getDisplayPrice(bestSellerPrice.currentPrice, currency);
    const sellerCardFormatted = formatPrice(sellerCardCurrentLocal, currency);

    // Both MUST be identical: ₹48,398
    expect(sellerCardFormatted).toBe(productLevelBestPrice);
    expect(sellerCardFormatted).toMatch(/₹\s*48,398/);

    // Original price check
    const sellerOriginalLocal = getDisplayPrice(bestSellerPrice.originalPrice, currency);
    const sellerOriginalFormatted = formatPrice(sellerOriginalLocal, currency);
    expect(sellerOriginalFormatted).toMatch(/₹\s*55,920/);

    // Savings check
    const savingsLocal = sellerOriginalLocal - sellerCardCurrentLocal;
    const savingsFormatted = formatPrice(savingsLocal, currency);
    expect(savingsFormatted).toMatch(/₹\s*7,522/);

    // Competitor seller delta check (Amazon)
    const competitorPrice = sortedPrices[1];
    const competitorLocal = getDisplayPrice(competitorPrice.currentPrice, currency);
    const diffFromLowest = competitorLocal - lowestPriceLocal;
    expect(Math.round(diffFromLowest)).toBe(Math.round((620.00 - 604.975) * 80));
    const diffFormatted = formatPrice(diffFromLowest, currency);
    expect(diffFormatted).toMatch(/₹\s*1,202/);
  });
});
