import type { CurrencyCode, CurrencyConfig, CurrencyMetadata } from './types';

export const DEFAULT_CURRENCY: CurrencyCode = 'INR';

export const CURRENCY_CONFIGS: Record<CurrencyCode, CurrencyConfig> = {
  USD: { symbol: '$', rate: 1, locale: 'en-US' },
  INR: { symbol: '₹', rate: 80, locale: 'en-IN' },
  EUR: { symbol: '€', rate: 0.9, locale: 'de-DE' },
  GBP: { symbol: '£', rate: 0.8, locale: 'en-GB' },
  JPY: { symbol: '¥', rate: 150, locale: 'ja-JP' },
};

export const CURRENCY_SYMBOLS: Record<CurrencyCode, string> = {
  USD: '$',
  INR: '₹',
  EUR: '€',
  GBP: '£',
  JPY: '¥',
};

export const CONVERSION_RATES: Record<CurrencyCode, number> = {
  USD: 1,
  INR: 80,
  EUR: 0.9,
  GBP: 0.8,
  JPY: 150,
};

export const CURRENCY_METADATA: Record<CurrencyCode, CurrencyMetadata> = {
  USD: { code: 'USD', name: 'US Dollar', symbol: '$' },
  INR: { code: 'INR', name: 'Indian Rupee', symbol: '₹' },
  EUR: { code: 'EUR', name: 'Euro', symbol: '€' },
  GBP: { code: 'GBP', name: 'British Pound', symbol: '£' },
  JPY: { code: 'JPY', name: 'Japanese Yen', symbol: '¥' },
};
