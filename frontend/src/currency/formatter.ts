import { CURRENCY_CONFIGS } from './constants';
import { CurrencyCode } from './types';

export function formatPrice(price: number, currency: CurrencyCode = 'INR'): string {
  const config = CURRENCY_CONFIGS[currency] || CURRENCY_CONFIGS.INR;
  return new Intl.NumberFormat(config.locale, {
    style: 'currency',
    currency: currency,
    maximumFractionDigits: 0
  }).format(price);
}

export function formatCompactPrice(price: number, currency: CurrencyCode = 'INR'): string {
  const config = CURRENCY_CONFIGS[currency] || CURRENCY_CONFIGS.INR;
  return new Intl.NumberFormat(config.locale, {
    style: 'currency',
    currency: currency,
    notation: 'compact',
    maximumFractionDigits: 1
  }).format(price);
}
