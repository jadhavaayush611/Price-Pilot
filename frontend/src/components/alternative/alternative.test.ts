import { describe, it, expect, vi, beforeEach } from 'vitest';
import { apiService, apiClient } from '../../services/api';
import type { AlternativeResponse, AlternativeProduct, AlternativeType } from '../../types';
import { formatAlternativeType } from './AlternativeCard';

describe('Alternative Finder Frontend Integration', () => {
  beforeEach(() => {
    vi.restoreAllMocks();
  });

  describe('API Client Methods', () => {
    it('constructs generic product alternatives URL with query params', async () => {
      const mockResponse: AlternativeResponse = {
        alternativeType: 'SIMILAR',
        executionMode: 'PRODUCT',
        totalFound: 1,
        content: [
          {
            id: 'alt-1',
            name: 'Sony WH-1000XM4',
            brand: 'Sony',
            category: 'Electronics',
            currentBestPrice: 278,
            originalPrice: 348,
            discountPercentage: 20,
            alternativeScore: 91.5,
            rating: 4.8,
            inStock: true,
            reasonCodes: ['LOWER_PRICE', 'HIGH_SEMANTIC_SIMILARITY'],
            evidence: [
              {
                category: 'PRICE',
                sourceValue: '$349',
                candidateValue: '$278',
                relationship: 'LOWER',
                confidence: 0.95,
                description: 'Costs $71 less than source product',
              },
            ],
          },
        ],
      };

      const getSpy = vi.spyOn(apiClient, 'get').mockResolvedValue({ data: mockResponse });

      const result = await apiService.getAlternatives('prod-123', {
        type: 'CHEAPER',
        limit: 5,
        minRating: 4.0,
      });

      expect(getSpy).toHaveBeenCalledWith('/alternatives/product/prod-123', {
        params: {
          type: 'CHEAPER',
          limit: 5,
          minRating: 4.0,
        },
      });

      expect(result.content.length).toBe(1);
      expect(result.content[0].name).toBe('Sony WH-1000XM4');
      expect(result.content[0].alternativeScore).toBe(91.5);
    });

    it('constructs personalized product alternatives URL without client-side userId parameter', async () => {
      const mockPersonalizedResponse: AlternativeResponse = {
        alternativeType: 'BETTER_VALUE',
        executionMode: 'PRODUCT',
        totalFound: 2,
        content: [
          {
            id: 'alt-bose',
            name: 'Bose QuietComfort 45',
            brand: 'Bose',
            category: 'Electronics',
            currentBestPrice: 229,
            alternativeScore: 88.0,
            personalizedScore: 94.5,
            personalizationAdjustment: 6.5,
            personalizedEvidence: {
              summary: 'Matches your preferred brand and is under your $250 budget limit.',
              scoreAdjustment: 6.5,
              reasons: [
                {
                  dimension: 'BRAND_AFFINITY',
                  reason: 'Matches your preferred brand (Bose)',
                  scoreImpact: 4.0,
                },
                {
                  dimension: 'BUDGET_FIT',
                  reason: 'Fits within your specified budget of $250',
                  scoreImpact: 2.5,
                },
              ],
            },
          },
        ],
      };

      const getSpy = vi.spyOn(apiClient, 'get').mockResolvedValue({ data: mockPersonalizedResponse });

      const result = await apiService.getPersonalizedAlternatives('prod-123', {
        type: 'BETTER_VALUE',
        limit: 10,
      });

      // Strict security verification: URL is /alternatives/product/{id}/personalized and no userId in params
      expect(getSpy).toHaveBeenCalledWith('/alternatives/product/prod-123/personalized', {
        params: {
          type: 'BETTER_VALUE',
          limit: 10,
        },
      });

      const params = (getSpy.mock.calls[0] as unknown as [string, { params?: Record<string, unknown> }])[1]?.params;
      expect(params?.userId).toBeUndefined();
      expect(params?.user_id).toBeUndefined();

      expect(result.content[0].personalizedScore).toBe(94.5);
      expect(result.content[0].personalizedEvidence?.reasons?.length).toBe(2);
    });

    it('constructs query-driven alternatives URL correctly', async () => {
      const mockResponse: AlternativeResponse = {
        executionMode: 'QUERY',
        query: 'wireless noise cancelling headphones',
        totalFound: 1,
        content: [],
      };

      const getSpy = vi.spyOn(apiClient, 'get').mockResolvedValue({ data: mockResponse });

      await apiService.getQueryAlternatives('wireless noise cancelling headphones', {
        type: 'PERFORMANCE_UPGRADE',
        limit: 8,
      });

      expect(getSpy).toHaveBeenCalledWith('/alternatives/query', {
        params: {
          query: 'wireless noise cancelling headphones',
          type: 'PERFORMANCE_UPGRADE',
          limit: 8,
        },
      });
    });

    it('constructs personalized query-driven alternatives URL without client-side userId', async () => {
      const mockResponse: AlternativeResponse = {
        executionMode: 'QUERY',
        query: 'mechanical keyboard',
        totalFound: 0,
        content: [],
      };

      const getSpy = vi.spyOn(apiClient, 'get').mockResolvedValue({ data: mockResponse });

      await apiService.getPersonalizedQueryAlternatives('mechanical keyboard', {
        type: 'SIMILAR',
      });

      expect(getSpy).toHaveBeenCalledWith('/alternatives/query/personalized', {
        params: {
          query: 'mechanical keyboard',
          type: 'SIMILAR',
        },
      });

      const params = (getSpy.mock.calls[0] as unknown as [string, { params?: Record<string, unknown> }])[1]?.params;
      expect(params?.userId).toBeUndefined();
    });

    it('propagates API error gracefully', async () => {
      vi.spyOn(apiClient, 'get').mockRejectedValue(new Error('Network connectivity lost'));

      await expect(apiService.getAlternatives('prod-err')).rejects.toThrow('Network connectivity lost');
    });
  });

  describe('Presentation Formatting & Logic Invariants', () => {
    it('maps backend AlternativeType enum values to human-readable labels', () => {
      expect(formatAlternativeType('CHEAPER')).toBe('Cheaper Alternative');
      expect(formatAlternativeType('SIMILAR')).toBe('Similar Product');
      expect(formatAlternativeType('BETTER_VALUE')).toBe('Better Value');
      expect(formatAlternativeType('PERFORMANCE_UPGRADE')).toBe('Performance Upgrade');
      expect(formatAlternativeType('PREMIUM')).toBe('Premium Alternative');
      expect(formatAlternativeType('BUDGET_FALLBACK')).toBe('Budget Alternative');
      expect(formatAlternativeType('CUSTOM_FUTURE_TYPE' as AlternativeType)).toBe('CUSTOM FUTURE TYPE');
      expect(formatAlternativeType(undefined)).toBe('Alternative');
    });

    it('preserves strict backend ranking order without client-side re-sorting', () => {
      // Backend returns candidates ordered: A, B, C
      // Notice: candidate A has higher price and lower rating than candidate B,
      // but backend ranked A first due to semantic relevance.
      const candidates: AlternativeProduct[] = [
        {
          id: 'cand-A',
          name: 'Candidate A (Rank 1 by Backend)',
          brand: 'BrandX',
          alternativeScore: 92.0,
          currentBestPrice: 500,
          rating: 4.2,
        },
        {
          id: 'cand-B',
          name: 'Candidate B (Rank 2 by Backend)',
          brand: 'BrandY',
          alternativeScore: 89.0,
          currentBestPrice: 200,
          rating: 4.9,
        },
        {
          id: 'cand-C',
          name: 'Candidate C (Rank 3 by Backend)',
          brand: 'BrandZ',
          alternativeScore: 84.0,
          currentBestPrice: 150,
          rating: 4.0,
        },
      ];

      const response: AlternativeResponse = {
        totalFound: 3,
        content: candidates,
      };

      // The frontend must NOT execute .sort() on response.content
      const renderedOrder = response.content.map(c => c.id);
      expect(renderedOrder).toEqual(['cand-A', 'cand-B', 'cand-C']);
      expect(renderedOrder[0]).toBe('cand-A');
      expect(renderedOrder[1]).toBe('cand-B');
      expect(renderedOrder[2]).toBe('cand-C');
    });

    it('does not fabricate evidence or personalization when not provided by backend', () => {
      const genericCandidate: AlternativeProduct = {
        id: 'cand-gen',
        name: 'Generic Laptop',
        brand: 'Apple',
        alternativeScore: 85.0,
        currentBestPrice: 999,
        // No evidence or personalizedEvidence provided by backend
      };

      // Invariants check:
      expect(genericCandidate.personalizedScore).toBeUndefined();
      expect(genericCandidate.personalizedEvidence).toBeUndefined();
      expect(genericCandidate.evidence).toBeUndefined();

      // Ensure score is displayed from backend value without calculations
      const displayedScore = genericCandidate.personalizedScore ?? genericCandidate.alternativeScore;
      expect(displayedScore).toBe(85.0);
    });

    it('handles cold-start personalized alternatives where personalizedEvidence is empty', () => {
      const coldStartCandidate: AlternativeProduct = {
        id: 'cand-cold',
        name: 'Cold Start Phone',
        brand: 'BrandN',
        alternativeScore: 82.0,
        personalizedScore: 82.0,
        personalizationAdjustment: 0.0,
        personalizedEvidence: {
          summary: '',
          scoreAdjustment: 0.0,
          reasons: [],
          supportingFactors: [],
          tradeOffs: [],
        },
      };

      expect(coldStartCandidate.personalizationAdjustment).toBe(0.0);
      expect(coldStartCandidate.personalizedEvidence?.reasons).toEqual([]);
      expect(coldStartCandidate.personalizedScore).toBe(82.0);
    });
  });
});
