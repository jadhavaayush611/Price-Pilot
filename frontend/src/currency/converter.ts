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

export function resolveAssistantDisplayPrice(
  price: number | undefined | null,
  sourceCurrency: string | undefined,
  targetCurrency: CurrencyCode
): number {
  if (price === undefined || price === null || isNaN(price)) {
    return 0;
  }
  if (sourceCurrency) {
    const srcUpper = sourceCurrency.toUpperCase() as CurrencyCode;
    if (srcUpper === targetCurrency) {
      return price;
    }
    const usdPrice = convertToUsd(price, srcUpper);
    return getDisplayPrice(usdPrice, targetCurrency);
  }
  return getDisplayPrice(price, targetCurrency);
}

