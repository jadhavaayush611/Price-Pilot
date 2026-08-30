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

export type PriceTrend = 'RISING' | 'FALLING' | 'STABLE' | 'INSUFFICIENT_DATA';
export type PriceVolatility = 'LOW' | 'MEDIUM' | 'HIGH' | 'INSUFFICIENT_DATA';
export type DealQuality = 'EXCELLENT_DEAL' | 'GOOD_DEAL' | 'FAIR_PRICE' | 'ABOVE_AVERAGE' | 'HIGH_PRICE' | 'INSUFFICIENT_DATA';
export type PurchaseSignal = 'BUY_NOW' | 'GOOD_TIME' | 'WAIT' | 'NEUTRAL' | 'INSUFFICIENT_DATA';

export interface HistoricalPricePoint {
  timestamp: string;
  price: number;
  sellerId?: string;
  sellerName?: string;
}

export interface PriceDropRecoveryEvent {
  eventType: 'MAJOR_DROP' | 'PRICE_INCREASE' | 'RECOVERY_AFTER_DROP' | 'NEW_HISTORICAL_LOW' | 'NEW_HISTORICAL_HIGH';
  percentageChange: number;
  amountChange: number;
  resultingPrice: number;
  occurredAt: string;
  description: string;
}

export interface ProductAnalytics {
  productId: string;
  viewCount: number;
  saveCount: number;
  watchlistCount: number;
  priceChangeCount: number;
  trendingScore: number;

  // Phase 4 fields
  currentPrice?: number;
  historicalMin?: number;
  historicalMax?: number;
  historicalAvg?: number;
  historicalMedian?: number;
  priceRange?: number;
  volatilityValue?: number;
  volatility?: PriceVolatility;
  trend?: PriceTrend;
  trendPercentage?: number;
  pricePositionScore?: number;
  dealQuality?: DealQuality;
  purchaseSignal?: PurchaseSignal;
  purchaseSignalReason?: string;
  supportingEvidence?: string[];
  observationCount?: number;
  historicalLowDistance?: number;
  historicalAverageDistance?: number;
  historicalEvents?: PriceDropRecoveryEvent[];
  priceSeries?: HistoricalPricePoint[];
  analyzedAt?: string;
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
  createdAt?: string;
  // eslint-disable-next-line @typescript-eslint/no-explicit-any
  metadata?: Record<string, any>;
}

export interface ComparisonRequest {
  productIds: string[];
  category?: string;
  criteria?: string[];
  userId?: string;
  sessionToken?: string;
  name?: string;
  notes?: string;
}

export interface ComparisonRow {
  featureName: string;
  category: string;
  valuesByProductId: Record<string, string>;
  isHighlight: boolean;
  highlightedProductIds?: string[];
  rowType?: string;
}

export interface ProductScore {
  productId: string;
  productName: string;
  overallScore: number;
  priceValueScore: number;
  featureScore: number;
  popularityScore: number;
  breakdown: Record<string, number>;
  recommendationBadge: string;
}

export interface ComparisonResponse {
  comparisonId: string;
  products: ProductWithPrices[];
  rows: ComparisonRow[];
  scores: Record<string, ProductScore>;
  summary: string;
  createdAt: string;
}

export interface SavedComparison {
  id: string;
  userId: string;
  sessionId?: string;
  name: string;
  productIds: string[];
  notes?: string;
  createdAt: string;
  products?: ProductWithPrices[];
}

export interface EvidenceItem {
  productId: string;
  productName: string;
  type: string;
  description: string;
  metricName?: string;
  metricValue?: number | string;
  comparisonValue?: number | string;
  positive: boolean;
  importance: number;
}

export interface RecommendationCompareRequest {
  productIds: string[];
  recommendationType?: string;
}

export interface RecommendationResponse {
  targetProductId?: string;
  userId?: string;
  recommendedProducts: ProductWithPrices[];
  recommendedProduct?: ProductWithPrices;
  recommendationType?: string;
  score?: number;
  confidence?: number;
  explanation: string;
  supportingFactors?: string[];
  tradeOffs?: string[];
  evidence?: EvidenceItem[];
  scores: ProductScore[];
  scoringStrategy?: string;
  explanationStrategy?: string;
  strategyUsed: string;
  generatedAt: string;
}

export type AlertType =
  | 'PRICE_DROP'
  | 'PRICE_TARGET_REACHED'
  | 'HISTORICAL_LOW_REACHED'
  | 'GOOD_DEAL_DETECTED'
  | 'PRICE_INCREASE'
  | 'PRICE_RECOVERY'
  | 'BACK_IN_STOCK';

export interface PriceAlert {
  id: string;
  userId: string;
  productId: string;
  productName: string;
  productImageUrl?: string;
  watchlistId?: string;
  alertType: AlertType;
  title: string;
  message: string;
  triggerValue?: number;
  observedValue?: number;
  read: boolean;
  readAt?: string;
  createdAt: string;
}

export interface WatchlistAlertPreference {
  id: string;
  watchlistId: string;
  enabled: boolean;
  priceDropEnabled: boolean;
  priceDropPercentage: number;
  targetPriceEnabled: boolean;
  historicalLowEnabled: boolean;
  goodDealEnabled: boolean;
  backInStockEnabled: boolean;
  priceIncreaseEnabled: boolean;
}

export interface UpdateWatchlistAlertPreferenceRequest {
  enabled?: boolean;
  priceDropEnabled?: boolean;
  priceDropPercentage?: number;
  targetPriceEnabled?: boolean;
  historicalLowEnabled?: boolean;
  goodDealEnabled?: boolean;
  backInStockEnabled?: boolean;
  priceIncreaseEnabled?: boolean;
}
