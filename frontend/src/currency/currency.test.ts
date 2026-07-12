import { describe, it, expect, beforeEach } from 'vitest';
import {
  convertToUsd,
  convertFromUsd,
  getDisplayPrice,
  formatPrice,
  formatCompactPrice,
  getSavedCurrency,
  saveCurrency,
} from './index';

// Simple self-contained mock for localStorage in Node test environment
class LocalStorageMock {
  private store: Record<string, string> = {};

  clear() {
    this.store = {};
  }

  getItem(key: string) {
    return this.store[key] || null;
  }

  setItem(key: string, value: string) {
    this.store[key] = value.toString();
  }

  removeItem(key: string) {
    delete this.store[key];
  }
}

if (typeof window === 'undefined') {
  const g = globalThis as any;
  g.localStorage = new LocalStorageMock();
  g.window = { localStorage: g.localStorage };
}

describe('Currency Subsystem Tests', () => {
  describe('Conversion Logic', () => {
    it('should convert to USD correctly', () => {
      expect(convertToUsd(100, 'USD')).toBe(100);
      expect(convertToUsd(8000, 'INR')).toBe(100);
      expect(convertToUsd(90, 'EUR')).toBe(100);
      expect(convertToUsd(80, 'GBP')).toBe(100);
      expect(convertToUsd(15000, 'JPY')).toBe(100);
    });

    it('should convert from USD correctly', () => {
      expect(convertFromUsd(100, 'USD')).toBe(100);
      expect(convertFromUsd(100, 'INR')).toBe(8000);
      expect(convertFromUsd(100, 'EUR')).toBe(90);
      expect(convertFromUsd(100, 'GBP')).toBe(80);
      expect(convertFromUsd(100, 'JPY')).toBe(15000);
    });

    it('should calculate display price based on legacy heuristic', () => {
      // For value < 5000: assumed to be USD originally
      // 100 USD converted to EUR (rate 0.9) -> 90 EUR
      expect(getDisplayPrice(100, 'EUR')).toBe(90);

      // For value >= 5000: assumed to be INR originally (conversion rate 80)
      // 8000 INR / 80 -> 100 USD -> converted to EUR (rate 0.9) -> 90 EUR
      expect(getDisplayPrice(8000, 'EUR')).toBe(90);
    });
  });

  describe('Regional Formatting', () => {
    it('should format standard prices correctly according to region rules', () => {
      const formattedUSD = formatPrice(100, 'USD');
      expect(formattedUSD).toContain('$');
      expect(formattedUSD).toContain('100');

      const formattedINR = formatPrice(100, 'INR');
      expect(formattedINR).toContain('₹');
      expect(formattedINR).toContain('100');

      const formattedEUR = formatPrice(100, 'EUR');
      expect(formattedEUR).toContain('€');
      expect(formattedEUR).toContain('100');

      const formattedGBP = formatPrice(100, 'GBP');
      expect(formattedGBP).toContain('£');
      expect(formattedGBP).toContain('100');

      const formattedJPY = formatPrice(100, 'JPY');
      expect(formattedJPY).toMatch(/¥|￥/);
      expect(formattedJPY).toContain('100');
    });


    it('should format compact prices correctly', () => {
      const compactUSD = formatCompactPrice(1500, 'USD');
      expect(compactUSD).toContain('$');
      expect(compactUSD).toContain('1.5');
      // Should contain 'K' for thousand in US English compact format
      expect(compactUSD).toMatch(/K|k/);

      const compactMillionUSD = formatCompactPrice(1500000, 'USD');
      expect(compactMillionUSD).toContain('$');
      expect(compactMillionUSD).toContain('1.5');
      expect(compactMillionUSD).toMatch(/M|m/);
    });
  });

  describe('Persistence (localStorage)', () => {
    beforeEach(() => {
      localStorage.clear();
    });

    it('should default to INR when nothing is stored', () => {
      expect(getSavedCurrency()).toBe('INR');
    });

    it('should store and retrieve valid currency codes', () => {
      saveCurrency('USD');
      expect(getSavedCurrency()).toBe('USD');

      saveCurrency('EUR');
      expect(getSavedCurrency()).toBe('EUR');

      saveCurrency('GBP');
      expect(getSavedCurrency()).toBe('GBP');

      saveCurrency('JPY');
      expect(getSavedCurrency()).toBe('JPY');
    });

    it('should fall back to default INR on invalid stored values', () => {
      localStorage.setItem('pricepilot_currency', 'AUD');
      expect(getSavedCurrency()).toBe('INR');
    });
  });
});
