export interface Product {
  id: string;
  name: string;
  brand: string;
  description: string;
  category: string;
  imageUrl: string;
  createdAt?: string;
  updatedAt?: string;
}

export interface Seller {
  id: string;
  name: string;
  websiteUrl: string;
  logoUrl: string;
}

export interface ProductPrice {
  id: string;
  productId: string;
  sellerId: string;
  currentPrice: number;
  originalPrice: number;
  discountPercentage: number;
  productUrl: string;
  lastUpdated: string;
  seller?: Seller;
}

export interface ProductWithPrices extends Product {
  prices: ProductPrice[];
  lowestPrice?: number;
  highestPrice?: number;
}
