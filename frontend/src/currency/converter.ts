import { CONVERSION_RATES } from './constants';
import type { CurrencyCode } from './types';

export function convertFromUsd(priceInUsd: number, toCurrency: CurrencyCode): number {
  const rate = CONVERSION_RATES[toCurrency] || 1;
  return priceInUsd * rate;
}

export function convertToUsd(priceInCurrency: number, fromCurrency: CurrencyCode): number {
  const rate = CONVERSION_RATES[fromCurrency] || 1;
  return priceInCurrency / rate;
}

export function getDisplayPrice(val: number, targetCurrency: CurrencyCode): number {
  if (val === undefined || val === null || isNaN(val)) {
    return 0;
  }
  return convertFromUsd(val, targetCurrency);
}
