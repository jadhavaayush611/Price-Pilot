import { clsx, type ClassValue } from "clsx"
import { twMerge } from "tailwind-merge"

export function cn(...inputs: ClassValue[]) {
  return twMerge(clsx(inputs))
}

export type CurrencyCode = 'USD' | 'INR' | 'EUR' | 'GBP' | 'JPY';

export const CONVERSION_RATES: Record<CurrencyCode, number> = {
  USD: 1,
  INR: 80,
  EUR: 0.9,
  GBP: 0.8,
  JPY: 150,
};

export const CURRENCY_SYMBOLS: Record<CurrencyCode, string> = {
  USD: '$',
  INR: '₹',
  EUR: '€',
  GBP: '£',
  JPY: '¥'
};

export function getSavedCurrency(): CurrencyCode {
  const saved = localStorage.getItem('pricepilot_currency');
  if (saved && ['USD', 'INR', 'EUR', 'GBP', 'JPY'].includes(saved)) {
    return saved as CurrencyCode;
  }
  return 'INR';
}

export function saveCurrency(currency: CurrencyCode): void {
  localStorage.setItem('pricepilot_currency', currency);
}

export function convertFromUsd(priceInUsd: number, toCurrency: CurrencyCode): number {
  const rate = CONVERSION_RATES[toCurrency] || 1;
  return priceInUsd * rate;
}

export function convertToUsd(priceInCurrency: number, fromCurrency: CurrencyCode): number {
  const rate = CONVERSION_RATES[fromCurrency] || 1;
  return priceInCurrency / rate;
}

export function getDisplayPrice(val: number, targetCurrency: CurrencyCode): number {
  const isOriginallyUSD = val < 5000;
  const priceInUsd = isOriginallyUSD ? val : val / 80;
  return convertFromUsd(priceInUsd, targetCurrency);
}

export function formatPrice(price: number, currency: CurrencyCode = 'INR') {
  let locale = 'en-US';
  switch (currency) {
    case 'INR':
      locale = 'en-IN';
      break;
    case 'EUR':
      locale = 'de-DE';
      break;
    case 'GBP':
      locale = 'en-GB';
      break;
    case 'JPY':
      locale = 'ja-JP';
      break;
    case 'USD':
    default:
      locale = 'en-US';
      break;
  }
  return new Intl.NumberFormat(locale, {
    style: 'currency',
    currency: currency,
    maximumFractionDigits: 0
  }).format(price);
}

