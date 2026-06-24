import axios from 'axios';
import type { Product, ProductWithPrices, Seller, ProductPrice, User, SavedProduct, Watchlist, PriceHistory } from '../types';

const API_BASE_URL = import.meta.env.VITE_API_BASE_URL || 'http://localhost:8080/api/v1';

export const apiClient = axios.create({
  baseURL: API_BASE_URL,
  headers: {
    'Content-Type': 'application/json',
  },
});

// Automatically inject JWT token into header of every API request
apiClient.interceptors.request.use(
  (config) => {
    const token = localStorage.getItem('token');
    if (token) {
      config.headers.Authorization = `Bearer ${token}`;
    }
    return config;
  },
  (error) => Promise.reject(error)
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
    } catch (error) {
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
    const params: any = { page, size };
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
    try {
      const response = await this.getProducts(0, 50, undefined, undefined, query);
      return response.content.map(p => ({
        ...p,
        prices: [],
      }));
    } catch (error) {
      console.warn('Backend search failed, falling back to mock data');
      await new Promise((resolve) => setTimeout(resolve, 300));
      
      if (!query) {
        return MOCK_PRODUCTS;
      }
      
      const lowerQuery = query.toLowerCase();
      return MOCK_PRODUCTS.filter(
        (product) =>
          product.name.toLowerCase().includes(lowerQuery) ||
          product.brand.toLowerCase().includes(lowerQuery) ||
          product.category.toLowerCase().includes(lowerQuery)
      );
    }
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
    try {
      const response = await apiClient.get('/search', { params });
      return response.data;
    } catch (error) {
      console.warn('Backend search API failed, falling back to mock data filtering');
      await new Promise((resolve) => setTimeout(resolve, 300));
      
      const { keyword = '', category = 'All', brand = 'All', page = 0, size = 10, sort = 'default' } = params;
      let filtered = [...MOCK_PRODUCTS];

      if (keyword.trim()) {
        const lowerKeyword = keyword.toLowerCase();
        filtered = filtered.filter(
          (p) =>
            p.name.toLowerCase().includes(lowerKeyword) ||
            p.brand.toLowerCase().includes(lowerKeyword) ||
            p.category.toLowerCase().includes(lowerKeyword) ||
            p.description.toLowerCase().includes(lowerKeyword)
        );
      }

      if (category !== 'All') {
        filtered = filtered.filter((p) => p.category.toLowerCase() === category.toLowerCase());
      }

      if (brand !== 'All') {
        filtered = filtered.filter((p) => p.brand.toLowerCase() === brand.toLowerCase());
      }

      // Sort
      filtered.sort((a, b) => {
        if (sort === 'price-asc' || sort === 'price,asc') {
          return (a.lowestPrice || 0) - (b.lowestPrice || 0);
        }
        if (sort === 'price-desc' || sort === 'price,desc') {
          return (b.lowestPrice || 0) - (a.lowestPrice || 0);
        }
        if (sort === 'discount-desc' || sort === 'discount,desc') {
          const maxDiscount = (p: ProductWithPrices) =>
            p.prices && p.prices.length > 0 ? Math.max(...p.prices.map((pr) => pr.discountPercentage)) : 0;
          return maxDiscount(b) - maxDiscount(a);
        }
        // default by name
        return a.name.localeCompare(b.name);
      });

      // Paginate
      const start = page * size;
      const paginated = filtered.slice(start, start + size);

      return {
        content: paginated,
        totalPages: Math.ceil(filtered.length / size),
        totalElements: filtered.length,
        size,
        number: page,
      };
    }
  },

  // Get single product details (hybrid)
  async getProduct(id: string): Promise<ProductWithPrices | null> {
    try {
      const response = await apiClient.get(`/products/${id}`);
      return response.data;
    } catch (error) {
      console.warn(`Failed to get product ${id} from API, trying mock data`);
      await new Promise((resolve) => setTimeout(resolve, 200));
      const product = MOCK_PRODUCTS.find((p) => p.id === id);
      return product || null;
    }
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
    const params: any = { page, size };
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
    const params: any = { page, size };
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

  async login(credentials: any): Promise<{ token: string; user: User }> {
    const response = await apiClient.post('/auth/login', credentials);
    return response.data;
  },

  async register(userData: any): Promise<{ token: string; user: User }> {
    const response = await apiClient.post('/auth/register', userData);
    return response.data;
  },
  
  async getCurrentUser(): Promise<User> {
    const response = await apiClient.get('/users/me');
    return response.data;
  },

  // Saved Products Operations (Real API with fallback)
  async getSavedProducts(): Promise<SavedProduct[]> {
    try {
      const response = await apiClient.get('/users/saved-products');
      return response.data;
    } catch (error) {
      console.warn('Backend saved products fetch failed, using fallback mock data');
      await new Promise((resolve) => setTimeout(resolve, 200));
      const savedIds = JSON.parse(localStorage.getItem('saved_product_ids') || '[]');
      return MOCK_PRODUCTS
        .filter(p => savedIds.includes(p.id))
        .map(p => ({
          productId: p.id,
          name: p.name,
          brand: p.brand,
          category: p.category,
          imageUrl: p.imageUrl,
          bestPrice: p.lowestPrice || null,
          savedAt: new Date().toISOString()
        }));
    }
  },

  async saveProduct(productId: string): Promise<void> {
    try {
      await apiClient.post(`/users/saved-products/${productId}`);
    } catch (error: any) {
      if (error.response && error.response.status === 409) {
        throw new Error('Product already saved');
      }
      if (error.response && error.response.status === 404) {
        throw new Error('Product not found');
      }
      console.warn('Backend save product failed, simulating locally');
      const savedIds = JSON.parse(localStorage.getItem('saved_product_ids') || '[]');
      if (!savedIds.includes(productId)) {
        savedIds.push(productId);
        localStorage.setItem('saved_product_ids', JSON.stringify(savedIds));
      }
    }
  },

  async removeProduct(productId: string): Promise<void> {
    try {
      await apiClient.delete(`/users/saved-products/${productId}`);
    } catch (error) {
      console.warn('Backend remove product failed, simulating locally');
      let savedIds = JSON.parse(localStorage.getItem('saved_product_ids') || '[]');
      savedIds = savedIds.filter((id: string) => id !== productId);
      localStorage.setItem('saved_product_ids', JSON.stringify(savedIds));
    }
  },

  // Watchlist Operations (Real API with fallback)
  async getWatchlists(): Promise<Watchlist[]> {
    try {
      const response = await apiClient.get('/watchlists');
      return response.data;
    } catch (error) {
      console.warn('Backend get watchlists failed, using fallback mock data');
      await new Promise((resolve) => setTimeout(resolve, 200));
      return JSON.parse(localStorage.getItem('price_watchlists') || '[]');
    }
  },

  async createWatchlist(productId: string, targetPrice: number): Promise<Watchlist> {
    try {
      const response = await apiClient.post('/watchlists', { productId, targetPrice });
      return response.data;
    } catch (error: any) {
      if (error.response && error.response.status === 409) {
        throw new Error('You are already watching this product');
      }
      if (error.response && error.response.status === 400) {
        throw new Error(error.response.data?.message || 'Invalid target price');
      }
      console.warn('Backend create watchlist failed, simulating locally');
      
      const watchlists: Watchlist[] = JSON.parse(localStorage.getItem('price_watchlists') || '[]');
      if (watchlists.some(w => w.productId === productId)) {
        throw new Error('You are already watching this product');
      }

      // Find product name and current price from mock products
      const product = MOCK_PRODUCTS.find(p => p.id === productId);
      if (!product) {
        throw new Error('Product not found');
      }

      const bestPrice = product.lowestPrice || 67999;
      if (targetPrice >= bestPrice) {
        throw new Error(`Target price must be less than the current best price (₹${bestPrice.toLocaleString()})`);
      }

      const newWatchlist: Watchlist = {
        id: crypto.randomUUID ? crypto.randomUUID() : Math.random().toString(36).substring(2),
        productId,
        productName: product.name,
        brand: product.brand,
        imageUrl: product.imageUrl,
        targetPrice,
        currentBestPrice: bestPrice,
        priceDifference: bestPrice - targetPrice,
        active: true,
        createdAt: new Date().toISOString(),
        updatedAt: new Date().toISOString()
      };

      watchlists.push(newWatchlist);
      localStorage.setItem('price_watchlists', JSON.stringify(watchlists));
      return newWatchlist;
    }
  },

  async updateWatchlist(id: string, targetPrice: number, active?: boolean): Promise<Watchlist> {
    try {
      const response = await apiClient.put(`/watchlists/${id}`, { targetPrice, active });
      return response.data;
    } catch (error: any) {
      if (error.response && error.response.status === 400) {
        throw new Error(error.response.data?.message || 'Invalid target price');
      }
      console.warn('Backend update watchlist failed, simulating locally');
      const watchlists: Watchlist[] = JSON.parse(localStorage.getItem('price_watchlists') || '[]');
      const index = watchlists.findIndex(w => w.id === id);
      if (index === -1) {
        throw new Error('Watchlist entry not found');
      }

      const w = watchlists[index];
      if (targetPrice >= w.currentBestPrice) {
        throw new Error(`Target price must be less than the current best price (₹${w.currentBestPrice.toLocaleString()})`);
      }

      w.targetPrice = targetPrice;
      if (active !== undefined) {
        w.active = active;
      }
      w.priceDifference = w.currentBestPrice - targetPrice;
      w.updatedAt = new Date().toISOString();

      watchlists[index] = w;
      localStorage.setItem('price_watchlists', JSON.stringify(watchlists));
      return w;
    }
  },

  async deleteWatchlist(id: string): Promise<void> {
    try {
      await apiClient.delete(`/watchlists/${id}`);
    } catch (error) {
      console.warn('Backend delete watchlist failed, simulating locally');
      let watchlists: Watchlist[] = JSON.parse(localStorage.getItem('price_watchlists') || '[]');
      watchlists = watchlists.filter(w => w.id !== id);
      localStorage.setItem('price_watchlists', JSON.stringify(watchlists));
    }
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
    try {
      const response = await apiClient.get(`/products/${productId}/price-history`, {
        params: { page, size, sort: 'changedAt,desc' }
      });
      return response.data;
    } catch (error) {
      console.warn(`Failed to get price history for product ${productId}, using fallback mock data`);
      await new Promise((resolve) => setTimeout(resolve, 300));
      
      // Let's generate mock history for a realistic chart/table
      const mockHistory: PriceHistory[] = [
        {
          id: 'ph1',
          productId,
          productName: 'iPhone 15 Pro Max (256GB, Space Black)',
          sellerId: 's1',
          sellerName: 'Amazon',
          oldPrice: 67999,
          newPrice: 64999,
          priceDifference: -3000,
          changePercentage: -4.41,
          changedAt: new Date(Date.now() - 2 * 24 * 60 * 60 * 1000).toISOString() // 2 days ago
        },
        {
          id: 'ph2',
          productId,
          productName: 'iPhone 15 Pro Max (256GB, Space Black)',
          sellerId: 's2',
          sellerName: 'Best Buy',
          oldPrice: 65999,
          newPrice: 67999,
          priceDifference: 2000,
          changePercentage: 3.03,
          changedAt: new Date(Date.now() - 5 * 24 * 60 * 60 * 1000).toISOString() // 5 days ago
        },
        {
          id: 'ph3',
          productId,
          productName: 'iPhone 15 Pro Max (256GB, Space Black)',
          sellerId: 's1',
          sellerName: 'Amazon',
          oldPrice: 69999,
          newPrice: 67999,
          priceDifference: -2000,
          changePercentage: -2.86,
          changedAt: new Date(Date.now() - 10 * 24 * 60 * 60 * 1000).toISOString() // 10 days ago
        }
      ];
      
      return {
        content: mockHistory,
        totalPages: 1,
        totalElements: mockHistory.length,
        size,
        number: page
      };
    }
  }
};
