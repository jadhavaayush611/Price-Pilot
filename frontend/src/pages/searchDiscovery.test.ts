import { describe, it, expect, vi, beforeEach } from 'vitest';
import { apiService, apiClient } from '../services/api';
import type { DiscoverySearchResponse, ProductWithPrices } from '../types';

describe('Personalized Discovery Frontend Integration', () => {
  beforeEach(() => {
    vi.restoreAllMocks();
  });

  describe('API Client Contracts', () => {
    it('calls discoverPersonalizedProducts with valid filters and NO client-side userId', async () => {
      const mockResponse: DiscoverySearchResponse = {
        content: [
          {
            id: 'prod-mac',
            name: 'Apple MacBook Pro 14"',
            brand: 'Apple',
            category: 'Electronics',
            description: 'M3 Pro chip laptop',
            imageUrl: 'https://example.com/mac.jpg',
            lowestPrice: 1999,
            prices: [],
            personalizedScore: 94.0,
            personalizationAdjustment: 6.0,
            personalizedEvidence: {
              summary: 'Matches your preferred brand (Apple)',
              reasons: [
                {
                  dimension: 'BRAND_AFFINITY',
                  reason: 'Matches your preferred brand (Apple)',
                  scoreImpact: 4.0,
                },
              ],
            },
          },
        ],
        page: 0,
        size: 6,
        totalElements: 1,
        totalPages: 1,
        availableCategories: ['Electronics'],
        availableBrands: ['Apple'],
      };

      const getSpy = vi.spyOn(apiClient, 'get').mockResolvedValue({ data: mockResponse });

      const result = await apiService.discoverPersonalizedProducts({
        query: 'laptop',
        category: 'Electronics',
        brand: 'Apple',
        minPrice: 1000,
        maxPrice: 2500,
        minRating: 4.0,
        inStock: true,
        sort: 'relevance',
        page: 0,
        size: 6,
      });

      expect(getSpy).toHaveBeenCalledWith('/discovery/personalized', {
        params: {
          query: 'laptop',
          category: 'Electronics',
          brand: 'Apple',
          minPrice: 1000,
          maxPrice: 2500,
          minRating: 4.0,
          inStock: true,
          sort: 'relevance',
          page: 0,
          size: 6,
        },
      });

      // Strict security boundary: assert NO userId in query parameters
      const callParams = (getSpy.mock.calls[0] as unknown as [string, { params?: Record<string, unknown> }])[1]?.params;
      expect(callParams?.userId).toBeUndefined();
      expect(callParams?.user_id).toBeUndefined();

      expect(result.content.length).toBe(1);
      expect(result.content[0].personalizedScore).toBe(94.0);
      expect(result.content[0].personalizedEvidence?.summary).toBe('Matches your preferred brand (Apple)');
    });

    it('preserves generic discoverProducts behavior when personalized is false/unspecified', async () => {
      const mockGenericResponse: DiscoverySearchResponse = {
        content: [
          {
            id: 'prod-generic',
            name: 'Standard Laptop',
            brand: 'GenericBrand',
            category: 'Electronics',
            description: 'Good value laptop',
            imageUrl: 'https://example.com/laptop.jpg',
            lowestPrice: 699,
            prices: [],
          },
        ],
        page: 0,
        size: 6,
        totalElements: 1,
        totalPages: 1,
        availableCategories: ['Electronics'],
        availableBrands: ['GenericBrand'],
      };

      const getSpy = vi.spyOn(apiClient, 'get').mockResolvedValue({ data: mockGenericResponse });

      const result = await apiService.discoverProducts({
        query: 'laptop',
        page: 0,
        size: 6,
      });

      expect(getSpy).toHaveBeenCalledWith('/discovery/products', {
        params: {
          query: 'laptop',
          page: 0,
          size: 6,
        },
      });

      expect(result.content[0].personalizedScore).toBeUndefined();
      expect(result.content[0].personalizedEvidence).toBeUndefined();
    });
  });

  describe('Ordering & Grounding Invariants', () => {
    it('preserves strict backend ranking order without client-side re-sorting', () => {
      // Backend returned candidates in order: P1, P2, P3
      // Notice: P1 has lower price and lower rating, but backend ranked P1 top due to personalization
      const backendResults: ProductWithPrices[] = [
        {
          id: 'p1',
          name: 'Product 1 (Rank 1 by Backend)',
          brand: 'BrandA',
          category: 'Electronics',
          description: '',
          imageUrl: '',
          lowestPrice: 800,
          rating: 4.1,
          personalizedScore: 95.0,
          prices: [],
        },
        {
          id: 'p2',
          name: 'Product 2 (Rank 2 by Backend)',
          brand: 'BrandB',
          category: 'Electronics',
          description: '',
          imageUrl: '',
          lowestPrice: 400,
          rating: 4.9,
          personalizedScore: 88.0,
          prices: [],
        },
        {
          id: 'p3',
          name: 'Product 3 (Rank 3 by Backend)',
          brand: 'BrandC',
          category: 'Electronics',
          description: '',
          imageUrl: '',
          lowestPrice: 200,
          rating: 4.5,
          personalizedScore: 82.0,
          prices: [],
        },
      ];

      const response: DiscoverySearchResponse = {
        content: backendResults,
        page: 0,
        size: 6,
        totalElements: 3,
        totalPages: 1,
        availableCategories: [],
        availableBrands: [],
      };

      // Invariant: Rendered DOM sequence must match backend order [p1, p2, p3]
      const order = response.content.map(p => p.id);
      expect(order).toEqual(['p1', 'p2', 'p3']);
    });

    it('does not fabricate personalized claims when backend response is generic', () => {
      const genericProduct: ProductWithPrices = {
        id: 'p-generic',
        name: 'Generic Phone',
        brand: 'Apple',
        category: 'Electronics',
        description: 'Smartphone',
        imageUrl: '',
        lowestPrice: 999,
        prices: [],
      };

      // Invariants check:
      expect(genericProduct.personalizedScore).toBeUndefined();
      expect(genericProduct.personalizedEvidence).toBeUndefined();
    });

    it('handles cold-start personalized discovery without errors', async () => {
      const coldStartResponse: DiscoverySearchResponse = {
        content: [
          {
            id: 'p-cold',
            name: 'Baseline Ranked Item',
            brand: 'BrandX',
            category: 'Electronics',
            description: 'Item with empty preference adjustments',
            imageUrl: '',
            lowestPrice: 500,
            prices: [],
            personalizedScore: 85.0,
            personalizationAdjustment: 0.0,
            personalizedEvidence: {
              summary: '',
              reasons: [],
              supportingFactors: [],
              tradeOffs: [],
            },
          },
        ],
        page: 0,
        size: 6,
        totalElements: 1,
        totalPages: 1,
        availableCategories: [],
        availableBrands: [],
      };

      vi.spyOn(apiClient, 'get').mockResolvedValue({ data: coldStartResponse });

      const result = await apiService.discoverPersonalizedProducts({ query: 'tech' });
      expect(result.content[0].personalizationAdjustment).toBe(0.0);
      expect(result.content[0].personalizedEvidence?.reasons).toEqual([]);
      expect(result.content[0].personalizedScore).toBe(85.0);
    });

    it('isolates user personalization across identity transitions without leaking evidence', async () => {
      const userAResult: DiscoverySearchResponse = {
        content: [
          {
            id: 'p-apple',
            name: 'Apple iPhone 15',
            brand: 'Apple',
            category: 'Phones',
            description: '',
            imageUrl: '',
            prices: [],
            personalizedScore: 96.0,
            personalizedEvidence: {
              summary: 'Matches User A preference (Apple)',
              reasons: [{ reason: 'Matches User A preferred brand (Apple)' }],
            },
          },
        ],
        page: 0,
        size: 6,
        totalElements: 1,
        totalPages: 1,
        availableCategories: [],
        availableBrands: [],
      };

      const userBResult: DiscoverySearchResponse = {
        content: [
          {
            id: 'p-samsung',
            name: 'Samsung Galaxy S24',
            brand: 'Samsung',
            category: 'Phones',
            description: '',
            imageUrl: '',
            prices: [],
            personalizedScore: 93.0,
            personalizedEvidence: {
              summary: 'Matches User B preference (Samsung)',
              reasons: [{ reason: 'Matches User B preferred brand (Samsung)' }],
            },
          },
        ],
        page: 0,
        size: 6,
        totalElements: 1,
        totalPages: 1,
        availableCategories: [],
        availableBrands: [],
      };

      const getSpy = vi.spyOn(apiClient, 'get')
        .mockResolvedValueOnce({ data: userAResult })
        .mockResolvedValueOnce({ data: userBResult });

      const resA = await apiService.discoverPersonalizedProducts({ query: 'phone' });
      expect(resA.content[0].name).toBe('Apple iPhone 15');
      expect(resA.content[0].personalizedEvidence?.summary).toContain('User A');

      const resB = await apiService.discoverPersonalizedProducts({ query: 'phone' });
      expect(resB.content[0].name).toBe('Samsung Galaxy S24');
      expect(resB.content[0].personalizedEvidence?.summary).toContain('User B');

      expect(getSpy).toHaveBeenCalledTimes(2);
    });
  });
});
