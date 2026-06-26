import { CONVERSION_RATES } from './constants';
import { CurrencyCode } from './types';

export function convertFromUsd(priceInUsd: number, toCurrency: CurrencyCode): number {
  const rate = CONVERSION_RATES[toCurrency] || 1;
  return priceInUsd * rate;
}

export function convertToUsd(priceInCurrency: number, fromCurrency: CurrencyCode): number {
  const rate = CONVERSION_RATES[fromCurrency] || 1;
  return priceInCurrency / rate;
}

export function getDisplayPrice(val: number, targetCurrency: CurrencyCode): number {
  // Heuristic to handle legacy mixed database values: if a price is >= 5000,
  // it is assumed to have been stored in INR (conversion rate 80), otherwise USD.
  const isOriginallyUSD = val < 5000;
  const priceInUsd = isOriginallyUSD ? val : val / 80;
  return convertFromUsd(priceInUsd, targetCurrency);
}
