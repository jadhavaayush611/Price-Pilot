export type CurrencyCode = 'USD' | 'INR' | 'EUR' | 'GBP' | 'JPY';

export interface CurrencyConfig {
  symbol: string;
  rate: number;
  locale: string;
}

export interface CurrencyMetadata {
  code: CurrencyCode;
  name: string;
  symbol: string;
}
