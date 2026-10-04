import { describe, it, expect } from 'vitest';
import { 
  resolveAssistantDisplayPrice, 
  formatPrice, 
  type CurrencyCode 
} from '../currency';

describe('Assistant Decision Signal Presentation & Currency Subsystem Regression', () => {
  describe('Issue 1: Assistant Deal Rating Presentation & Enum Formatting', () => {
    const formatDealQuality = (quality?: string): string => {
      if (!quality) return 'Verified Price';
      const q = quality.toUpperCase();
      switch (q) {
        case 'EXCELLENT_DEAL':
        case 'EXCELLENT':
          return 'Excellent Deal';
        case 'GOOD_DEAL':
        case 'GOOD':
          return 'Good Deal';
        case 'FAIR_PRICE':
        case 'FAIR':
          return 'Fair Price';
        case 'POOR_DEAL':
        case 'POOR':
          return 'Poor Deal';
        case 'OVERPRICED':
          return 'Overpriced';
        case 'AVAILABLE':
          return 'Verified Price';
        case 'UNKNOWN':
          return 'Catalog Price';
        default:
          return quality.replace(/_/g, ' ').replace(/\b\w/g, c => c.toUpperCase());
      }
    };

    const formatPurchaseSignal = (signal?: string): string => {
      if (!signal) return '';
      const s = signal.toUpperCase();
      switch (s) {
        case 'BUY_NOW':
          return 'Buy Now';
        case 'GOOD_TIME':
          return 'Good Time to Buy';
        case 'WAIT':
          return 'Wait for Drop';
        case 'NEUTRAL':
          return 'Neutral Timing';
        case 'UNKNOWN':
          return '';
        default:
          return signal.replace(/_/g, ' ').replace(/\b\w/g, c => c.toUpperCase());
      }
    };

    const formatPriceTrend = (trend?: string): string => {
      if (!trend) return '';
      const t = trend.toUpperCase();
      switch (t) {
        case 'STABLE':
          return 'Stable Price';
        case 'FALLING':
          return 'Falling Price';
        case 'RISING':
          return 'Rising Price';
        case 'VOLATILE':
          return 'Volatile Price';
        default:
          return trend.replace(/_/g, ' ').replace(/\b\w/g, c => c.toUpperCase());
      }
    };

    const cleanRawEnums = (text: string): string => {
      return text
        .replace(/\bEXCELLENT_DEAL\b/g, 'Excellent Deal')
        .replace(/\bGOOD_DEAL\b/g, 'Good Deal')
        .replace(/\bFAIR_PRICE\b/g, 'Fair Price')
        .replace(/\bPOOR_DEAL\b/g, 'Poor Deal')
        .replace(/\bOVERPRICED\b/g, 'Overpriced')
        .replace(/\bBUY_NOW\b/g, 'Buy Now')
        .replace(/\bGOOD_TIME\b/g, 'Good Time to Buy')
        .replace(/\bWAIT_FOR_DROP\b/g, 'Wait for Drop');
    };

    it('should format all deal quality variations without underscores or raw enum names', () => {
      expect(formatDealQuality('EXCELLENT_DEAL')).toBe('Excellent Deal');
      expect(formatDealQuality('GOOD_DEAL')).toBe('Good Deal');
      expect(formatDealQuality('FAIR_PRICE')).toBe('Fair Price');
      expect(formatDealQuality('POOR_DEAL')).toBe('Poor Deal');
      expect(formatDealQuality('OVERPRICED')).toBe('Overpriced');
      expect(formatDealQuality('AVAILABLE')).toBe('Verified Price');
      expect(formatDealQuality(undefined)).toBe('Verified Price');
    });

    it('should format all purchase signal variations without raw uppercase or underscores', () => {
      expect(formatPurchaseSignal('BUY_NOW')).toBe('Buy Now');
      expect(formatPurchaseSignal('GOOD_TIME')).toBe('Good Time to Buy');
      expect(formatPurchaseSignal('WAIT')).toBe('Wait for Drop');
      expect(formatPurchaseSignal('NEUTRAL')).toBe('Neutral Timing');
    });

    it('should format all price trend variations cleanly', () => {
      expect(formatPriceTrend('STABLE')).toBe('Stable Price');
      expect(formatPriceTrend('FALLING')).toBe('Falling Price');
      expect(formatPriceTrend('RISING')).toBe('Rising Price');
      expect(formatPriceTrend('VOLATILE')).toBe('Volatile Price');
    });

    it('should parse Deal Rating line into distinct semantic components', () => {
      const line = '- Deal Rating: EXCELLENT_DEAL (Signal: BUY_NOW, Trend: STABLE)';
      const regex = /^(?:[-•*]\s*)?Deal Rating:\s*([A-Za-z0-9_]+)(?:\s*\((?:Signal:\s*([A-Za-z0-9_]+))?(?:,?\s*Trend:\s*([A-Za-z0-9_]+))?\))?/i;
      const match = line.match(regex);
      expect(match).not.toBeNull();
      if (match) {
        expect(formatDealQuality(match[1])).toBe('Excellent Deal');
        expect(formatPurchaseSignal(match[2])).toBe('Buy Now');
        expect(formatPriceTrend(match[3])).toBe('Stable Price');
      }
    });

    it('should sanitize raw enums embedded within plain text lines', () => {
      const rawText = 'This product is currently rated EXCELLENT_DEAL and BUY_NOW.';
      expect(cleanRawEnums(rawText)).toBe('This product is currently rated Excellent Deal and Buy Now.');
    });
  });

  describe('Issue 2: Currency Double Conversion Prevention in Evidence Cards', () => {
    const currencies: CurrencyCode[] = ['USD', 'INR', 'EUR', 'GBP', 'JPY'];

    it('should not double-convert an already converted INR price in evidence card', () => {
      const backendCard = {
        productId: 'iphone-15',
        productName: 'iPhone 15',
        currentPrice: 63738.40,
        originalPrice: 79673.00,
        currency: 'INR'
      };

      const resolved = resolveAssistantDisplayPrice(backendCard.currentPrice, backendCard.currency, 'INR');
      expect(resolved).toBe(63738.40);
      
      const formatted = formatPrice(resolved, 'INR');
      expect(formatted).toContain('63,738');
      expect(formatted).not.toContain('50,99,072');
    });

    it('should handle all 5 configured offline currencies accurately', () => {
      const canonicalUsdPrice = 604.975;

      const expectedByCurrency: Record<CurrencyCode, number> = {
        USD: 604.975,
        INR: 48398,
        EUR: 544.4775,
        GBP: 483.98,
        JPY: 90746.25,
      };

      for (const cur of currencies) {
        const resolved = resolveAssistantDisplayPrice(canonicalUsdPrice, 'USD', cur);
        expect(resolved).toBeCloseTo(expectedByCurrency[cur], 2);
      }
    });

    it('should correctly convert if backend currency differs from active user currency', () => {
      // Backend returned in USD $100, user is viewing in JPY
      expect(resolveAssistantDisplayPrice(100, 'USD', 'JPY')).toBe(15000);

      // Backend returned in INR 8000, user is viewing in USD
      expect(resolveAssistantDisplayPrice(8000, 'INR', 'USD')).toBe(100);

      // Backend returned in EUR 90, user is viewing in GBP (90 EUR -> 100 USD -> 80 GBP)
      expect(resolveAssistantDisplayPrice(90, 'EUR', 'GBP')).toBeCloseTo(80, 2);
    });

    it('should safely fallback when price is missing or null', () => {
      expect(resolveAssistantDisplayPrice(null, 'INR', 'INR')).toBe(0);
      expect(resolveAssistantDisplayPrice(undefined, 'INR', 'INR')).toBe(0);
      expect(resolveAssistantDisplayPrice(NaN, 'INR', 'INR')).toBe(0);
    });
  });
});
