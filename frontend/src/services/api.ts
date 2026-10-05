import axios from 'axios';
import type {
  Product,
  ProductWithPrices,
  Seller,
  ProductPrice,
  User,
  SavedProduct,
  Watchlist,
  PriceHistory,
  ProductAnalytics,
  ComparisonRequest,
  ComparisonResponse,
  RecommendationResponse,
  RecommendationCompareRequest,
  PriceAlert,
  WatchlistAlertPreference,
  UpdateWatchlistAlertPreferenceRequest,
  DashboardV2Response,
  DiscoverySearchResponse,
  SearchSuggestion,
  UserShoppingPreference,
  UpdateShoppingPreferenceRequest,
  AssistantConversationDTO,
  AssistantResponseDTO,
  AlternativeResponse,
  AlternativeParams,
  AlternativeQueryParams
} from '../types';
import { convertToUsd, getDisplayPrice, getSavedCurrency, formatPrice } from '../currency';

export const getApiBaseUrl = (customEnv?: { VITE_API_URL?: string; VITE_API_BASE_URL?: string }): string => {
  const envUrl = customEnv?.VITE_API_URL ?? 
                 customEnv?.VITE_API_BASE_URL ?? 
                 import.meta.env.VITE_API_URL ?? 
                 import.meta.env.VITE_API_BASE_URL;
  if (!envUrl) {
    return 'http://localhost:8080/api/v1';
  }
  const trimmed = envUrl.replace(/\/+$/, '');
  return trimmed.endsWith('/api/v1') ? trimmed : `${trimmed}/api/v1`;
};

const API_BASE_URL = getApiBaseUrl();

export const apiClient = axios.create({
  baseURL: API_BASE_URL,
  headers: {
    'Content-Type': 'application/json',
  },
});

// Automatically inject JWT token into header and track request start timestamp
apiClient.interceptors.request.use(
  (config) => {
    (config as unknown as Record<string, unknown>).metadata = { startTime: performance.now() };
    const token = localStorage.getItem('token');
    if (token) {
      config.headers.Authorization = `Bearer ${token}`;
    }
    return config;
  },
  (error) => Promise.reject(error)
);

// Response interceptor for API latency logging in development
apiClient.interceptors.response.use(
  (response) => {
    const configWithMeta = response.config as unknown as { metadata?: { startTime: number } };
    if (import.meta.env.DEV && response.config && configWithMeta.metadata) {
      const duration = performance.now() - configWithMeta.metadata.startTime;
      console.debug(
        `[API Latency] ${response.config.method?.toUpperCase()} ${response.config.url} - ${duration.toFixed(2)}ms (${response.status})`
      );
    }
    return response;
  },
  (error) => {
    const configWithMeta = error.config as unknown as { metadata?: { startTime: number } };
    if (import.meta.env.DEV && error.config && configWithMeta.metadata) {
      const duration = performance.now() - configWithMeta.metadata.startTime;
      console.debug(
        `[API Latency Error] ${error.config.method?.toUpperCase()} ${error.config.url} - ${duration.toFixed(2)}ms (${error.response?.status || 'NETWORK_ERROR'})`
      );
    }
    return Promise.reject(error);
  }
);



export const apiService = {
  // Check backend health
  async checkHealth(): Promise<{ status: string; [key: string]: unknown }> {
    const response = await apiClient.get('/health');
    return response.data;
  },

  // Get list of products with pagination, sorting, and optional search (Real API)
  async getProducts(
    page: number,
    size: number,
    sortKey?: string,
    sortDir?: 'asc' | 'desc',
    search?: string
  ): Promise<{
    content: Product[];
    totalPages: number;
    totalElements: number;
    size: number;
    number: number;
  }> {
    const params: Record<string, unknown> = { page, size };
    if (sortKey) {
      params.sort = `${sortKey},${sortDir || 'asc'}`;
    }
    if (search) {
      params.search = search;
    }
    const response = await apiClient.get('/products', { params });
    return response.data;
  },

  // Search products (hybrid)
  async searchProducts(query: string): Promise<ProductWithPrices[]> {
    const response = await this.getProducts(0, 50, undefined, undefined, query);
    return response.content.map(p => ({
      ...p,
      prices: [],
    }));
  },

  // Search products with multi-faceted filtering, sorting, and pagination (Real API)
  async searchProductsWithFilters(params: {
    keyword?: string;
    q?: string;
    category?: string;
    brand?: string;
    minPrice?: number;
    maxPrice?: number;
    minDiscount?: number;
    inStock?: boolean;
    dealQuality?: string;
    page?: number;
    size?: number;
    sort?: string;
  }): Promise<{
    content: ProductWithPrices[];
    totalPages: number;
    totalElements: number;
    size: number;
    number: number;
  }> {
    const response = await apiClient.get('/search', { params });
    return response.data;
  },

  // Intelligent Discovery Search with structured evidence and facets (Real API)
  async discoverProducts(params: {
    query?: string;
    category?: string;
    brand?: string;
    minPrice?: number;
    maxPrice?: number;
    minRating?: number;
    minDiscount?: number;
    inStock?: boolean;
    dealQuality?: string;
    sellerId?: string;
    sort?: string;
    page?: number;
    size?: number;
    personalized?: boolean;
    hybrid?: boolean;
    naturalLanguage?: boolean;
  }): Promise<DiscoverySearchResponse> {
    const response = await apiClient.get<DiscoverySearchResponse>('/discovery/products', { params });
    return response.data;
  },

  // Dedicated Personalized Product Discovery (Phase 6.6)
  async discoverPersonalizedProducts(params: {
    query?: string;
    category?: string;
    brand?: string;
    minPrice?: number;
    maxPrice?: number;
    minRating?: number;
    minDiscount?: number;
    inStock?: boolean;
    dealQuality?: string;
    sellerId?: string;
    sort?: string;
    page?: number;
    size?: number;
  }): Promise<DiscoverySearchResponse> {
    const response = await apiClient.get<DiscoverySearchResponse>('/discovery/personalized', { params });
    return response.data;
  },


  // Search autocomplete suggestions (Real API)
  async getSearchSuggestions(query: string, limit: number = 6): Promise<SearchSuggestion[]> {
    if (!query || !query.trim()) return [];
    const response = await apiClient.get<SearchSuggestion[]>('/discovery/suggestions', {
      params: { query, limit }
    });
    return response.data;
  },

  // Get single product details (Real API)
  async getProduct(id: string): Promise<ProductWithPrices | null> {
    const response = await apiClient.get(`/products/${id}`);
    return response.data;
  },

  // Product CRUD Operations (Real API)
  async createProduct(product: Omit<Product, 'id' | 'createdAt' | 'updatedAt'>): Promise<Product> {
    const response = await apiClient.post('/products', product);
    return response.data;
  },

  async updateProduct(id: string, product: Omit<Product, 'id' | 'createdAt' | 'updatedAt'>): Promise<Product> {
    const response = await apiClient.put(`/products/${id}`, product);
    return response.data;
  },

  async deleteProduct(id: string): Promise<void> {
    await apiClient.delete(`/products/${id}`);
  },

  // Seller CRUD Operations (Real API)
  async getSellers(
    page: number,
    size: number,
    sortKey?: string,
    sortDir?: 'asc' | 'desc',
    search?: string
  ): Promise<{
    content: Seller[];
    totalPages: number;
    totalElements: number;
    size: number;
    number: number;
  }> {
    const params: Record<string, unknown> = { page, size };
    if (sortKey) {
      params.sort = `${sortKey},${sortDir || 'asc'}`;
    }
    if (search) {
      params.search = search;
    }
    const response = await apiClient.get('/sellers', { params });
    return response.data;
  },

  async createSeller(seller: Omit<Seller, 'id' | 'createdAt' | 'updatedAt'>): Promise<Seller> {
    const response = await apiClient.post('/sellers', seller);
    return response.data;
  },

  async updateSeller(id: string, seller: Omit<Seller, 'id' | 'createdAt' | 'updatedAt'>): Promise<Seller> {
    const response = await apiClient.put(`/sellers/${id}`, seller);
    return response.data;
  },

  async deleteSeller(id: string): Promise<void> {
    await apiClient.delete(`/sellers/${id}`);
  },

  // ProductPrice CRUD Operations (Real API)
  async getProductPrices(
    page: number,
    size: number,
    sortKey?: string,
    sortDir?: 'asc' | 'desc',
    search?: string
  ): Promise<{
    content: ProductPrice[];
    totalPages: number;
    totalElements: number;
    size: number;
    number: number;
  }> {
    const params: Record<string, unknown> = { page, size };
    if (sortKey) {
      params.sort = `${sortKey},${sortDir || 'asc'}`;
    }
    if (search) {
      params.search = search;
    }
    const response = await apiClient.get('/prices', { params });
    return response.data;
  },

  async createProductPrice(price: {
    productId: string;
    sellerId: string;
    currentPrice: number;
    originalPrice: number;
    productUrl?: string;
  }): Promise<ProductPrice> {
    const response = await apiClient.post('/prices', price);
    return response.data;
  },

  async updateProductPrice(
    id: string,
    price: {
      productId: string;
      sellerId: string;
      currentPrice: number;
      originalPrice: number;
      productUrl?: string;
    }
  ): Promise<ProductPrice> {
    const response = await apiClient.put(`/prices/${id}`, price);
    return response.data;
  },

  async deleteProductPrice(id: string): Promise<void> {
    await apiClient.delete(`/prices/${id}`);
  },

  async login(credentials: Record<string, unknown>): Promise<{ token: string; user: User }> {
    const response = await apiClient.post('/auth/login', credentials);
    return response.data;
  },

  async register(userData: Record<string, unknown>): Promise<{ token: string; user: User }> {
    const response = await apiClient.post('/auth/register', userData);
    return response.data;
  },
  
  async getCurrentUser(): Promise<User> {
    const response = await apiClient.get('/users/me');
    return response.data;
  },

  // Saved Products Operations (Real API)
  async getSavedProducts(): Promise<SavedProduct[]> {
    const response = await apiClient.get('/users/saved-products');
    return response.data;
  },

  async saveProduct(productId: string): Promise<void> {
    await apiClient.post(`/users/saved-products/${productId}`);
  },

  async removeProduct(productId: string): Promise<void> {
    await apiClient.delete(`/users/saved-products/${productId}`);
  },

  // Watchlist Operations (Real API with fallback)
  async getWatchlists(): Promise<Watchlist[]> {
    const response = await apiClient.get('/watchlists');
    const userCurrency = getSavedCurrency();
    return response.data.map((item: Watchlist) => {
      const targetPrice = getDisplayPrice(item.targetPrice, userCurrency);
      const currentBestPrice = getDisplayPrice(item.currentBestPrice, userCurrency);
      return {
        ...item,
        targetPrice,
        currentBestPrice,
        priceDifference: currentBestPrice - targetPrice
      };
    });
  },

  async createWatchlist(productId: string, targetPrice: number): Promise<Watchlist> {
    const userCurrency = getSavedCurrency();
    const targetPriceInUsd = convertToUsd(targetPrice, userCurrency);
    try {
      const response = await apiClient.post('/watchlists', { productId, targetPrice: targetPriceInUsd });
      const item = response.data;
      const localTargetPrice = getDisplayPrice(item.targetPrice, userCurrency);
      const localBestPrice = getDisplayPrice(item.currentBestPrice, userCurrency);
      return {
        ...item,
        targetPrice: localTargetPrice,
        currentBestPrice: localBestPrice,
        priceDifference: localBestPrice - localTargetPrice
      };
    } catch (error: unknown) {
      const err = error as { response?: { status?: number; data?: { message?: string; details?: { currentBestPrice?: number } } } };
      if (err.response && err.response.status === 409) {
        throw new Error('You are already watching this product', { cause: error });
      }
      if (err.response && err.response.status === 400) {
        const data = err.response.data;
        const details = data?.details;
        if (details && typeof details.currentBestPrice === 'number') {
          const usdPrice = details.currentBestPrice;
          const localPrice = getDisplayPrice(usdPrice, userCurrency);
          const formattedLocalPrice = formatPrice(localPrice, userCurrency);
          throw new Error(`${data.message || 'Target price must be less than the current best price.'} (${formattedLocalPrice})`, { cause: error });
        }
        throw new Error(data?.message || 'Invalid target price', { cause: error });
      }
      throw error;
    }
  },

  async updateWatchlist(id: string, targetPrice: number, active?: boolean): Promise<Watchlist> {
    const userCurrency = getSavedCurrency();
    const targetPriceInUsd = convertToUsd(targetPrice, userCurrency);
    try {
      const response = await apiClient.put(`/watchlists/${id}`, { targetPrice: targetPriceInUsd, active });
      const item = response.data;
      const localTargetPrice = getDisplayPrice(item.targetPrice, userCurrency);
      const localBestPrice = getDisplayPrice(item.currentBestPrice, userCurrency);
      return {
        ...item,
        targetPrice: localTargetPrice,
        currentBestPrice: localBestPrice,
        priceDifference: localBestPrice - localTargetPrice
      };
    } catch (error: unknown) {
      const err = error as { response?: { status?: number; data?: { message?: string; details?: { currentBestPrice?: number } } } };
      if (err.response && err.response.status === 400) {
        const data = err.response.data;
        const details = data?.details;
        if (details && typeof details.currentBestPrice === 'number') {
          const usdPrice = details.currentBestPrice;
          const localPrice = getDisplayPrice(usdPrice, userCurrency);
          const formattedLocalPrice = formatPrice(localPrice, userCurrency);
          throw new Error(`${data.message || 'Target price must be less than the current best price.'} (${formattedLocalPrice})`, { cause: error });
        }
        throw new Error(data?.message || 'Invalid target price', { cause: error });
      }
      throw error;
    }
  },

  async deleteWatchlist(id: string): Promise<void> {
    await apiClient.delete(`/watchlists/${id}`);
  },

  // Get price history for a product
  async getProductPriceHistory(
    productId: string,
    page: number,
    size: number
  ): Promise<{
    content: PriceHistory[];
    totalPages: number;
    totalElements: number;
    size: number;
    number: number;
  }> {
    const response = await apiClient.get(`/products/${productId}/price-history`, {
      params: { page, size, sort: 'changedAt,desc' }
    });
    return response.data;
  },

  // Analytics and Trending Endpoints
  async getTrendingProducts(limit: number = 10): Promise<ProductWithPrices[]> {
    const response = await apiClient.get('/products/trending', { params: { limit } });
    return response.data;
  },

  async getBiggestDrops(limit: number = 10): Promise<ProductWithPrices[]> {
    const response = await apiClient.get('/products/biggest-drops', { params: { limit } });
    return response.data;
  },

  async getMostWatchedProducts(limit: number = 10): Promise<ProductWithPrices[]> {
    const response = await apiClient.get('/products/most-watched', { params: { limit } });
    return response.data;
  },

  async getMostSavedProducts(limit: number = 10): Promise<ProductWithPrices[]> {
    const response = await apiClient.get('/products/most-saved', { params: { limit } });
    return response.data;
  },

  async getProductAnalytics(productId: string): Promise<ProductAnalytics> {
    const response = await apiClient.get(`/analytics/products/${productId}`);
    return response.data;
  },

  // Get my events (User Interaction Events)
  async getMyEvents(
    page: number,
    size: number
  ): Promise<{
    // eslint-disable-next-line @typescript-eslint/no-explicit-any
    content: any[];
    totalPages: number;
    totalElements: number;
    size: number;
    number: number;
    last: boolean;
  }> {
    const response = await apiClient.get('/events/me', {
      params: { page, size, sort: 'createdAt,desc' }
    });
    return response.data;
  },

  // Track seller click event
  async trackSellerClick(priceId: string): Promise<void> {
    await apiClient.post(`/events/seller-click/${priceId}`);
  },

  // Get recommendations (Real API)
  async getRecommendations(params?: {
    category?: string;
    brand?: string;
    minPrice?: number;
    maxPrice?: number;
    sort?: string;
    page?: number;
    size?: number;
  }): Promise<{
    content: ProductWithPrices[];
    totalPages: number;
    totalElements: number;
    size: number;
    number: number;
  }> {
    const response = await apiClient.get('/recommendations', { params });
    return response.data;
  },

  // Get similar products (Real API)
  async getSimilarProducts(productId: string, limit: number = 10): Promise<ProductWithPrices[]> {
    const response = await apiClient.get(`/recommendations/similar/${productId}`, { params: { limit } });
    return response.data;
  },

  // Get dashboard data (Real API)
  // eslint-disable-next-line @typescript-eslint/no-explicit-any
  async getDashboard(): Promise<any> {
    const response = await apiClient.get('/dashboard');
    const userCurrency = getSavedCurrency();
    const convertWatchlist = (item: Watchlist) => {
      const targetPrice = getDisplayPrice(item.targetPrice, userCurrency);
      const currentBestPrice = getDisplayPrice(item.currentBestPrice, userCurrency);
      return {
        ...item,
        targetPrice,
        currentBestPrice,
        priceDifference: currentBestPrice - targetPrice
      };
    };
    
    const data = response.data;
    if (data.priceDropAlerts) {
      data.priceDropAlerts = data.priceDropAlerts.map(convertWatchlist);
    }
    if (data.watchlists) {
      data.watchlists = data.watchlists.map(convertWatchlist);
    }
    return data;
  },

  // Assistant APIs (Phase 9 Conversation & Decision Support)
  async listAssistantConversations(): Promise<AssistantConversationDTO[]> {
    const response = await apiClient.get('/assistant/conversations');
    return response.data;
  },

  async getAssistantConversation(id: string): Promise<AssistantConversationDTO> {
    const response = await apiClient.get(`/assistant/conversations/${id}`);
    return response.data;
  },

  async createAssistantConversation(title?: string): Promise<AssistantConversationDTO> {
    const response = await apiClient.post('/assistant/conversations', { title: title || 'New Shopping Discussion' });
    return response.data;
  },

  async deleteAssistantConversation(id: string): Promise<void> {
    await apiClient.delete(`/assistant/conversations/${id}`);
  },

  async sendAssistantMessage(conversationId: string, content: string, activeProductId?: string): Promise<AssistantResponseDTO> {
    const response = await apiClient.post(`/assistant/conversations/${conversationId}/messages`, {
      content,
      activeProductId,
    });
    return response.data;
  },

  // Legacy Assistant APIs
  // eslint-disable-next-line @typescript-eslint/no-explicit-any
  async assistantChat(message: string, conversationId?: string): Promise<any> {
    const response = await apiClient.post('/assistant/chat', { message, conversationId });
    return response.data;
  },

  // eslint-disable-next-line @typescript-eslint/no-explicit-any
  async assistantCompare(productIds: string[], conversationId?: string): Promise<any> {
    const response = await apiClient.post('/assistant/compare', { productIds, conversationId });
    return response.data;
  },

  // eslint-disable-next-line @typescript-eslint/no-explicit-any
  async assistantAsk(question: string, conversationId?: string): Promise<any> {
    const response = await apiClient.post('/assistant/ask', { question, conversationId });
    return response.data;
  },

  // eslint-disable-next-line @typescript-eslint/no-explicit-any
  async assistantClearMemory(conversationId: string): Promise<any> {
    const response = await apiClient.post('/assistant/clear_memory', { conversationId });
    return response.data;
  },

  // Shopping Intelligence Module APIs (v1.1)
  async getComparison(productIds: string[], sessionId?: string): Promise<ComparisonResponse> {
    const params: Record<string, string> = {};
    if (productIds && productIds.length > 0) {
      params.ids = productIds.join(',');
    }
    if (sessionId) {
      params.sessionId = sessionId;
    }
    const response = await apiClient.get('/compare', { params });
    return response.data;
  },

  async postComparison(request: ComparisonRequest): Promise<ComparisonResponse> {
    const response = await apiClient.post('/compare', request);
    return response.data;
  },

  async saveComparison(request: ComparisonRequest): Promise<import('../types').SavedComparison> {
    const response = await apiClient.post('/compare/save', request);
    return response.data;
  },

  async getSavedComparisons(
    page: number = 0,
    size: number = 10,
    sortKey?: string,
    sortDir?: 'asc' | 'desc',
    search?: string
  ): Promise<{
    content: import('../types').SavedComparison[];
    totalPages: number;
    totalElements: number;
  }> {
    const params: Record<string, unknown> = { page, size };
    if (sortKey) params.sortKey = sortKey;
    if (sortDir) params.sortDir = sortDir;
    if (search) params.search = search;
    const response = await apiClient.get('/compare/saved', { params });
    return response.data;
  },

  async getComparisonSession(sessionId: string): Promise<ComparisonResponse> {
    const response = await apiClient.get(`/compare/${sessionId}`);
    return response.data;
  },

  async deleteSavedComparison(sessionId: string): Promise<void> {
    await apiClient.delete(`/compare/${sessionId}`);
  },

  async getIntelligenceRecommendations(productId: string, limit: number = 10, type?: string): Promise<RecommendationResponse> {
    const params: Record<string, string | number> = { limit };
    if (type) params.type = type;
    const response = await apiClient.get(`/recommendations/${productId}`, { params });
    return response.data;
  },

  async compareAndRecommend(request: RecommendationCompareRequest): Promise<RecommendationResponse> {
    const response = await apiClient.post('/recommendations/compare', request);
    return response.data;
  },

  async getIntelligenceAnalytics(productId: string): Promise<ProductAnalytics> {
    const response = await apiClient.get(`/analytics/${productId}`);
    return response.data;
  },

  // Phase 5: Smart Watchlists & Price Alerts API
  async getAlerts(page: number = 0, size: number = 20): Promise<{
    content: PriceAlert[];
    totalPages: number;
    totalElements: number;
    size: number;
    number: number;
  }> {
    const response = await apiClient.get('/alerts', { params: { page, size } });
    return response.data;
  },

  async getUnreadAlerts(): Promise<PriceAlert[]> {
    const response = await apiClient.get('/alerts/unread');
    return response.data;
  },

  async getUnreadAlertCount(): Promise<number> {
    const response = await apiClient.get('/alerts/unread/count');
    return response.data.unreadCount ?? 0;
  },

  async markAlertRead(alertId: string): Promise<PriceAlert> {
    const response = await apiClient.patch(`/alerts/${alertId}/read`);
    return response.data;
  },

  async markAllAlertsRead(): Promise<number> {
    const response = await apiClient.patch('/alerts/read-all');
    return response.data.markedCount ?? 0;
  },

  async getWatchlistAlertPreferences(watchlistId: string): Promise<WatchlistAlertPreference> {
    const response = await apiClient.get(`/watchlists/${watchlistId}/alerts`);
    return response.data;
  },

  async updateWatchlistAlertPreferences(
    watchlistId: string,
    data: UpdateWatchlistAlertPreferenceRequest
  ): Promise<WatchlistAlertPreference> {
    const response = await apiClient.put(`/watchlists/${watchlistId}/alerts`, data);
    return response.data;
  },

  async getDashboardV2(): Promise<DashboardV2Response> {
    const response = await apiClient.get('/dashboard/v2');
    return response.data;
  },

  // Phase 8: User Shopping Preferences & Personalized Intelligence
  async getUserPreferences(): Promise<UserShoppingPreference> {
    const response = await apiClient.get('/users/preferences');
    return response.data;
  },

  async updateUserPreferences(data: UpdateShoppingPreferenceRequest): Promise<UserShoppingPreference> {
    const response = await apiClient.put('/users/preferences', data);
    return response.data;
  },

  async resetUserPreferences(): Promise<void> {
    await apiClient.delete('/users/preferences');
  },

  async getPersonalizedRecommendations(limit: number = 10): Promise<RecommendationResponse> {
    const response = await apiClient.get('/recommendations/personalized', { params: { limit } });
    return response.data;
  },

  // Alternative Finder APIs (v1.2)
  async getAlternatives(productId: string, params?: AlternativeParams): Promise<AlternativeResponse> {
    const response = await apiClient.get<AlternativeResponse>(`/alternatives/product/${productId}`, { params });
    return response.data;
  },

  async getPersonalizedAlternatives(productId: string, params?: Omit<AlternativeParams, 'personalized'>): Promise<AlternativeResponse> {
    const response = await apiClient.get<AlternativeResponse>(`/alternatives/product/${productId}/personalized`, { params });
    return response.data;
  },

  async getQueryAlternatives(query: string, params?: AlternativeQueryParams): Promise<AlternativeResponse> {
    const response = await apiClient.get<AlternativeResponse>('/alternatives/query', {
      params: { query, ...params }
    });
    return response.data;
  },

  async getPersonalizedQueryAlternatives(query: string, params?: Omit<AlternativeQueryParams, 'personalized'>): Promise<AlternativeResponse> {
    const response = await apiClient.get<AlternativeResponse>('/alternatives/query/personalized', {
      params: { query, ...params }
    });
    return response.data;
  }
};


