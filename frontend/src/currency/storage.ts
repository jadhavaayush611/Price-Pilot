import type { CurrencyCode } from './types';
import { DEFAULT_CURRENCY } from './constants';

const STORAGE_KEY = 'pricepilot_currency';

export function getSavedCurrency(): CurrencyCode {
  if (typeof window === 'undefined' || !window.localStorage) {
    return DEFAULT_CURRENCY;
  }
  
  const saved = localStorage.getItem(STORAGE_KEY);
  if (saved && ['USD', 'INR', 'EUR', 'GBP', 'JPY'].includes(saved)) {
    return saved as CurrencyCode;
  }
  return DEFAULT_CURRENCY;
}

export function saveCurrency(currency: CurrencyCode): void {
  if (typeof window !== 'undefined' && window.localStorage) {
    localStorage.setItem(STORAGE_KEY, currency);
  }
}
