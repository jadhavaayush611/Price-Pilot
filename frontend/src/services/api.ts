import axios from 'axios';
import type { Product, ProductWithPrices, Seller } from '../types';

const API_BASE_URL = 'http://localhost:8080/api/v1';

export const apiClient = axios.create({
  baseURL: API_BASE_URL,
  headers: {
    'Content-Type': 'application/json',
  },
});

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

  // Get single product details (hybrid)
  async getProduct(id: string): Promise<ProductWithPrices | null> {
    try {
      const response = await apiClient.get(`/products/${id}`);
      return {
        ...response.data,
        prices: [],
      };
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
  }
};
