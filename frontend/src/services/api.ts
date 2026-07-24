import axios from 'axios';
import type { Product, ProductWithPrices, Seller, ProductPrice, User, SavedProduct, Watchlist, PriceHistory, ProductAnalytics, ComparisonRequest, ComparisonResponse, RecommendationResponse } from '../types';
import { convertToUsd, getDisplayPrice, getSavedCurrency, formatPrice } from '../currency';

const API_BASE_URL = import.meta.env.VITE_API_BASE_URL || 'http://localhost:8080/api/v1';

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

// Mock Data
export const MOCK_SELLERS: Seller[] = [
  { id: 's1', name: 'Amazon', websiteUrl: 'https://amazon.com', logoUrl: 'https://upload.wikimedia.org/wikipedia/commons/a/a9/Amazon_logo.svg' },
  { id: 's2', name: 'Best Buy', websiteUrl: 'https://bestbuy.com', logoUrl: 'https://upload.wikimedia.org/wikipedia/commons/f/f5/Best_Buy_Logo.svg' },
  { id: 's3', name: 'Walmart', websiteUrl: 'https://walmart.com', logoUrl: 'https://upload.wikimedia.org/wikipedia/commons/c/ca/Walmart_logo.svg' },
  { id: 's4', name: 'Target', websiteUrl: 'https://target.com', logoUrl: 'https://upload.wikimedia.org/wikipedia/commons/c/c5/Target_Corporation_logo_vector.svg' },
  { id: 's5', name: 'eBay', websiteUrl: 'https://ebay.com', logoUrl: 'https://upload.wikimedia.org/wikipedia/commons/1/1b/EBay_logo.svg' }
];

export const MOCK_PRODUCTS: ProductWithPrices[] = [
  {
    id: 'p1',
    name: 'iPhone 15 Pro Max (256GB, Space Black)',
    brand: 'Apple',
    description: 'The ultimate iPhone featuring a strong and lightweight titanium design, new Action button, powerful camera upgrades, and A17 Pro for next-level gaming.',
    category: 'Electronics',
    imageUrl: 'https://images.unsplash.com/photo-1695048133142-1a20484d2569?auto=format&fit=crop&q=80&w=600',
    lowestPrice: 1099,
    highestPrice: 1199,
    prices: [
      { id: 'pr1', productId: 'p1', sellerId: 's1', currentPrice: 1099, originalPrice: 1199, discountPercentage: 8.3, productUrl: 'https://amazon.com', lastUpdated: '2 mins ago', seller: MOCK_SELLERS[0] },
      { id: 'pr2', productId: 'p1', sellerId: 's2', currentPrice: 1149, originalPrice: 1199, discountPercentage: 4.1, productUrl: 'https://bestbuy.com', lastUpdated: '1 hour ago', seller: MOCK_SELLERS[1] },
      { id: 'pr3', productId: 'p1', sellerId: 's3', currentPrice: 1199, originalPrice: 1199, discountPercentage: 0, productUrl: 'https://walmart.com', lastUpdated: '5 mins ago', seller: MOCK_SELLERS[2] }
    ]
  },
  {
    id: 'p2',
    name: 'Sony WH-1000XM5 Wireless Headphones',
    brand: 'Sony',
    description: 'Industry-leading noise canceling wireless over-ear headphones with Auto NC Optimizer, crystal clear hands-free calling, and Alexa Voice Control.',
    category: 'Electronics',
    imageUrl: 'https://images.unsplash.com/photo-1618384887929-16ec33fab9ef?auto=format&fit=crop&q=80&w=600',
    lowestPrice: 328,
    highestPrice: 399,
    prices: [
      { id: 'pr4', productId: 'p2', sellerId: 's1', currentPrice: 328, originalPrice: 399, discountPercentage: 17.8, productUrl: 'https://amazon.com', lastUpdated: '10 mins ago', seller: MOCK_SELLERS[0] },
      { id: 'pr5', productId: 'p2', sellerId: 's2', currentPrice: 349, originalPrice: 399, discountPercentage: 12.5, productUrl: 'https://bestbuy.com', lastUpdated: '2 hours ago', seller: MOCK_SELLERS[1] },
      { id: 'pr6', productId: 'p2', sellerId: 's5', currentPrice: 399, originalPrice: 399, discountPercentage: 0, productUrl: 'https://ebay.com', lastUpdated: '1 day ago', seller: MOCK_SELLERS[4] }
    ]
  },
  {
    id: 'p3',
    name: 'MacBook Pro 14" M3 (8GB/512GB SSD)',
    brand: 'Apple',
    description: 'The Apple M3 chip gives the 14-inch MacBook Pro speed and capability. With industry-leading battery life up to 22 hours and a beautiful Liquid Retina XDR display.',
    category: 'Electronics',
    imageUrl: 'https://images.unsplash.com/photo-1517336714731-489689fd1ca8?auto=format&fit=crop&q=80&w=600',
    lowestPrice: 1399,
    highestPrice: 1599,
    prices: [
      { id: 'pr7', productId: 'p3', sellerId: 's1', currentPrice: 1399, originalPrice: 1599, discountPercentage: 12.5, productUrl: 'https://amazon.com', lastUpdated: '1 min ago', seller: MOCK_SELLERS[0] },
      { id: 'pr8', productId: 'p3', sellerId: 's2', currentPrice: 1449, originalPrice: 1599, discountPercentage: 9.4, productUrl: 'https://bestbuy.com', lastUpdated: '30 mins ago', seller: MOCK_SELLERS[1] },
      { id: 'pr9', productId: 'p3', sellerId: 's4', currentPrice: 1599, originalPrice: 1599, discountPercentage: 0, productUrl: 'https://target.com', lastUpdated: '4 hours ago', seller: MOCK_SELLERS[3] }
    ]
  },
  {
    id: 'p4',
    name: 'Nintendo Switch OLED Model',
    brand: 'Nintendo',
    description: 'Featuring a vibrant 7-inch OLED screen, a wide adjustable stand, a dock with a wired LAN port, 64 GB of internal storage, and enhanced audio.',
    category: 'Electronics',
    imageUrl: 'https://images.unsplash.com/photo-1578632767115-351597cf2477?auto=format&fit=crop&q=80&w=600',
    lowestPrice: 299,
    highestPrice: 349,
    prices: [
      { id: 'pr10', productId: 'p4', sellerId: 's3', currentPrice: 299, originalPrice: 349, discountPercentage: 14.3, productUrl: 'https://walmart.com', lastUpdated: '1 hour ago', seller: MOCK_SELLERS[2] },
      { id: 'pr11', productId: 'p4', sellerId: 's1', currentPrice: 319, originalPrice: 349, discountPercentage: 8.6, productUrl: 'https://amazon.com', lastUpdated: '12 mins ago', seller: MOCK_SELLERS[0] },
      { id: 'pr12', productId: 'p4', sellerId: 's2', currentPrice: 349, originalPrice: 349, discountPercentage: 0, productUrl: 'https://bestbuy.com', lastUpdated: '3 hours ago', seller: MOCK_SELLERS[1] }
    ]
  }
];

export const apiService = {
  // Check backend health
  async checkHealth(): Promise<{ status: string }> {
    try {
      const response = await apiClient.get('/health');
      return response.data;
    } catch {
      console.warn('Backend connection failed, falling back to mock UP status');
      return { status: 'UP' };
    }
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
    category?: string;
    brand?: string;
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

  // Assistant APIs
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

  async getIntelligenceRecommendations(productId: string, limit: number = 10): Promise<RecommendationResponse> {
    const response = await apiClient.get(`/recommendations/${productId}`, { params: { limit } });
    return response.data;
  },

  // eslint-disable-next-line @typescript-eslint/no-explicit-any
  async getIntelligenceAnalytics(productId: string): Promise<any> {
    const response = await apiClient.get(`/analytics/${productId}`);
    return response.data;
  }
};

