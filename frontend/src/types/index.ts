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
  createdAt?: string;
  updatedAt?: string;
}

export interface ProductPrice {
  id: string;
  productId?: string;
  sellerId?: string;
  currentPrice: number;
  originalPrice: number;
  discountPercentage: number;
  productUrl: string;
  lastUpdated: string;
  product?: Product;
  seller?: Seller;
  createdAt?: string;
  updatedAt?: string;
}

export interface ProductWithPrices extends Product {
  prices: ProductPrice[];
  lowestPrice?: number;
  highestPrice?: number;
}

export interface User {
  id: string;
  email: string;
  firstName: string;
  lastName: string;
  role: 'USER' | 'ADMIN';
  enabled: boolean;
}

export interface SavedProduct {
  productId: string;
  name: string;
  brand: string;
  category: string;
  imageUrl: string;
  bestPrice: number | null;
  savedAt: string;
}

export interface Watchlist {
  id: string;
  productId: string;
  productName: string;
  brand: string;
  imageUrl: string;
  targetPrice: number;
  currentBestPrice: number;
  priceDifference: number;
  active: boolean;
  createdAt: string;
  updatedAt: string;
}

export interface PriceHistory {
  id: string;
  productId: string;
  productName: string;
  sellerId: string;
  sellerName: string;
  oldPrice: number;
  newPrice: number;
  priceDifference: number;
  changePercentage: number;
  changedAt: string;
}

export interface ProductAnalytics {
  productId: string;
  viewCount: number;
  saveCount: number;
  watchlistCount: number;
  priceChangeCount: number;
  trendingScore: number;
}

export interface UserInteractionEvent {
  id: string;
  userId?: string;
  userEmail?: string;
  productId?: string;
  productName?: string;
  sellerId?: string;
  sellerName?: string;
  interactionType:
    | 'PRODUCT_VIEW'
    | 'PRODUCT_SAVE'
    | 'PRODUCT_UNSAVE'
    | 'WATCHLIST_CREATE'
    | 'WATCHLIST_DELETE'
    | 'PRICE_HISTORY_VIEW'
    | 'SELLER_CLICK'
    | 'SEARCH'
    | 'TRENDING_VIEW';
  metadata: Record<string, unknown>;
  createdAt: string;
}



