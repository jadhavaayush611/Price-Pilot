import { describe, it, expect, vi, beforeEach } from 'vitest';
import { apiService, apiClient } from '../../services/api';
import type { RecommendationResponse, RecommendationCompareRequest } from '../../types';

describe('Explainable Recommendations Frontend Integration', () => {
  beforeEach(() => {
    vi.restoreAllMocks();
  });

  it('fetches intelligence recommendations with strategy type and parses explainable fields', async () => {
    const mockResponse: RecommendationResponse = {
      targetProductId: 'prod-1',
      recommendedProduct: {
        id: 'prod-1',
        name: 'Sony WH-1000XM5',
        brand: 'Sony',
        category: 'Audio',
        description: 'Industry leading noise cancellation',
        imageUrl: 'https://example.com/sony.jpg',
        prices: [
          {
            id: 'price-1',
            currentPrice: 349.99,
            originalPrice: 399.99,
            discountPercentage: 12.5,
            productUrl: 'https://seller.com/item',
            lastUpdated: '2026-08-30T10:00:00Z',
          }
        ],
        lowestPrice: 349.99,
      },
      recommendedProducts: [],
      recommendationType: 'BEST_OVERALL',
      score: 93.0,
      confidence: 0.89,
      explanation: 'Sony WH-1000XM5 is the strongest overall choice because it has the lowest current price and the highest rating.',
      supportingFactors: [
        'Lowest current price among compared products at $349.99',
        'Highest customer satisfaction rating of 4.9/5.0',
        'Strong seller availability across 4 merchants'
      ],
      tradeOffs: [
        'Bose QC Ultra has a lighter frame, but costs $80 more'
      ],
      evidence: [
        {
          productId: 'prod-1',
          productName: 'Sony WH-1000XM5',
          type: 'LOWEST_PRICE',
          description: 'Lowest current price among compared products at $349.99',
          positive: true,
          importance: 0.95,
        },
        {
          productId: 'prod-2',
          productName: 'Bose QC Ultra',
          type: 'HIGHER_PRICE',
          description: 'Bose QC Ultra costs $80 more',
          positive: false,
          importance: 0.75,
        }
      ],
      scores: [
        {
          productId: 'prod-1',
          productName: 'Sony WH-1000XM5',
          overallScore: 93.0,
          priceValueScore: 92.0,
          featureScore: 95.0,
          popularityScore: 94.0,
          breakdown: { PriceCompetitiveness: 95.0, ProductRating: 98.0 },
          recommendationBadge: 'BEST OVERALL',
        }
      ],
      scoringStrategy: 'DEFAULT_COMPARISON_SCORER',
      explanationStrategy: 'DETERMINISTIC_RULE_BASED',
      strategyUsed: 'DEFAULT_COMPARISON_SCORER',
      generatedAt: '2026-08-30T20:00:00Z',
    };

    const getSpy = vi.spyOn(apiClient, 'get').mockResolvedValue({ data: mockResponse });

    const result = await apiService.getIntelligenceRecommendations('prod-1', 5, 'BEST_OVERALL');

    expect(getSpy).toHaveBeenCalledWith('/recommendations/prod-1', {
      params: { limit: 5, type: 'BEST_OVERALL' }
    });
    expect(result.recommendedProduct?.name).toBe('Sony WH-1000XM5');
    expect(result.recommendationType).toBe('BEST_OVERALL');
    expect(result.score).toBe(93.0);
    expect(result.confidence).toBe(0.89);
    expect(result.supportingFactors?.length).toBe(3);
    expect(result.tradeOffs?.length).toBe(1);
    expect(result.evidence?.length).toBe(2);
    expect(result.explanationStrategy).toBe('DETERMINISTIC_RULE_BASED');
  });

  it('posts comparison recommendations and receives structured explainable response', async () => {
    const request: RecommendationCompareRequest = {
      productIds: ['id-1', 'id-2', 'id-3'],
      recommendationType: 'BEST_VALUE'
    };

    const mockResponse: RecommendationResponse = {
      recommendedProduct: {
        id: 'id-1',
        name: 'Budget Earbuds',
        brand: 'SoundBrand',
        category: 'Audio',
        description: 'High value earbuds',
        imageUrl: 'https://example.com/earbuds.jpg',
        prices: [],
      },
      recommendedProducts: [],
      recommendationType: 'BEST_VALUE',
      score: 91.0,
      confidence: 0.84,
      explanation: 'Budget Earbuds offers the best price-to-value choice because it has the lowest current price.',
      supportingFactors: ['Lowest current price among compared products at $49.99'],
      tradeOffs: ['Premium Earbuds has higher rating (4.9 vs 4.4), but costs $150 more'],
      evidence: [],
      scores: [],
      scoringStrategy: 'DEFAULT_COMPARISON_SCORER',
      explanationStrategy: 'AI_GATEWAY_HYBRID',
      strategyUsed: 'DEFAULT_COMPARISON_SCORER',
      generatedAt: '2026-08-30T20:05:00Z',
    };

    const postSpy = vi.spyOn(apiClient, 'post').mockResolvedValue({ data: mockResponse });

    const result = await apiService.compareAndRecommend(request);

    expect(postSpy).toHaveBeenCalledWith('/recommendations/compare', request);
    expect(result.recommendationType).toBe('BEST_VALUE');
    expect(result.confidence).toBe(0.84);
    expect(result.explanationStrategy).toBe('AI_GATEWAY_HYBRID');
    expect(result.tradeOffs?.[0]).toContain('costs $150 more');
  });
});
