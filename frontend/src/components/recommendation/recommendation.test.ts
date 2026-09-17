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

  it('fetches personalized recommendations via getPersonalizedRecommendations without sending client-selected userId', async () => {
    const mockPersonalizedResponse: RecommendationResponse = {
      targetProductId: undefined,
      recommendedProduct: {
        id: 'prod-mac',
        name: 'Apple MacBook Pro M3',
        brand: 'Apple',
        category: 'Electronics',
        description: 'High performance laptop for power users',
        imageUrl: 'https://example.com/macbook.jpg',
        prices: [
          {
            id: 'price-mac-1',
            currentPrice: 1999.00,
            originalPrice: 2199.00,
            discountPercentage: 9.1,
            productUrl: 'https://seller.com/macbook',
            lastUpdated: '2026-09-17T12:00:00Z',
          }
        ],
        lowestPrice: 1999.00,
      },
      recommendedProducts: [
        {
          id: 'prod-mac',
          name: 'Apple MacBook Pro M3',
          brand: 'Apple',
          category: 'Electronics',
          description: 'High performance laptop for power users',
          imageUrl: 'https://example.com/macbook.jpg',
          prices: [],
          lowestPrice: 1999.00,
        },
        {
          id: 'prod-galaxy',
          name: 'Samsung Galaxy Book',
          brand: 'Samsung',
          category: 'Electronics',
          description: 'AMOLED screen laptop',
          imageUrl: 'https://example.com/galaxy.jpg',
          prices: [],
          lowestPrice: 1499.00,
        }
      ],
      recommendationType: 'BEST_OVERALL',
      score: 96.0,
      baseScore: 88.0,
      personalizationContribution: 8.0,
      confidence: 0.92,
      explanation: 'Apple MacBook Pro M3 is recommended because it is within your preferred budget and matches your preferred brand (Apple).',
      supportingFactors: [
        'High customer satisfaction and verified deal quality',
        'Strong seller reliability across 3 merchants'
      ],
      tradeOffs: [
        'Samsung Galaxy Book offers lower initial price ($1,499 vs $1,999)'
      ],
      evidence: [
        {
          productId: 'prod-mac',
          productName: 'Apple MacBook Pro M3',
          type: 'HIGH_RATING',
          description: 'Customer rating 4.9/5.0 exceeds category average',
          positive: true,
          importance: 0.85,
        }
      ],
      personalizationEvidence: [
        {
          productId: 'prod-mac',
          productName: 'Apple MacBook Pro M3',
          type: 'PREFERRED_BRAND',
          description: 'Matches your preferred brand (Apple)',
          positive: true,
          importance: 1.0,
        },
        {
          productId: 'prod-mac',
          productName: 'Apple MacBook Pro M3',
          type: 'WITHIN_BUDGET',
          description: 'Fits within your $2,500 target budget',
          positive: true,
          importance: 0.9,
        }
      ],
      scores: [
        {
          productId: 'prod-mac',
          productName: 'Apple MacBook Pro M3',
          overallScore: 96.0,
          baseScore: 88.0,
          personalizationContribution: 8.0,
          priceValueScore: 85.0,
          featureScore: 92.0,
          popularityScore: 90.0,
          breakdown: { FeatureScore: 92.0, ValueScore: 85.0 },
          personalizationBreakdown: { BrandAffinity: 5.0, BudgetFit: 3.0 },
          recommendationBadge: 'BEST OVERALL',
        },
        {
          productId: 'prod-galaxy',
          productName: 'Samsung Galaxy Book',
          overallScore: 85.0,
          baseScore: 85.0,
          personalizationContribution: 0.0,
          priceValueScore: 88.0,
          featureScore: 84.0,
          popularityScore: 82.0,
          breakdown: { FeatureScore: 84.0, ValueScore: 88.0 },
          recommendationBadge: 'GREAT VALUE',
        }
      ],
      scoringStrategy: 'PERSONALIZED_HYBRID_SCORER',
      explanationStrategy: 'DETERMINISTIC_RULE_BASED',
      strategyUsed: 'PERSONALIZED_HYBRID_SCORER',
      generatedAt: '2026-09-17T12:00:00Z',
    };

    const getSpy = vi.spyOn(apiClient, 'get').mockResolvedValue({ data: mockPersonalizedResponse });

    const result = await apiService.getPersonalizedRecommendations(12);

    // Verify security requirement: no client-supplied userId param
    expect(getSpy).toHaveBeenCalledWith('/recommendations/personalized', {
      params: { limit: 12 }
    });

    // Verify personalized fields are correctly parsed
    expect(result.recommendedProducts.length).toBe(2);
    expect(result.recommendedProducts[0].name).toBe('Apple MacBook Pro M3');
    expect(result.recommendedProducts[1].name).toBe('Samsung Galaxy Book');
    expect(result.score).toBe(96.0);
    expect(result.baseScore).toBe(88.0);
    expect(result.personalizationContribution).toBe(8.0);
    expect(result.personalizationEvidence?.length).toBe(2);
    expect(result.personalizationEvidence?.[0].description).toBe('Matches your preferred brand (Apple)');
    expect(result.evidence?.length).toBe(1);
    expect(result.evidence?.[0].description).toContain('Customer rating');
    expect(result.scores[0].personalizationContribution).toBe(8.0);
  });

  it('handles cold-start personalized recommendations with empty personalization signals', async () => {
    const mockColdStartResponse: RecommendationResponse = {
      recommendedProducts: [
        {
          id: 'prod-generic',
          name: 'Neutral Smart Watch',
          brand: 'GenericBrand',
          category: 'Wearables',
          description: 'All-day battery smart watch',
          imageUrl: 'https://example.com/watch.jpg',
          prices: [],
          lowestPrice: 199.00,
        }
      ],
      recommendationType: 'BEST_OVERALL',
      score: 84.0,
      baseScore: 84.0,
      personalizationContribution: 0.0,
      confidence: 0.80,
      explanation: 'Neutral Smart Watch is recommended based on overall market value and customer rating.',
      supportingFactors: ['Best price in category'],
      tradeOffs: [],
      evidence: [],
      personalizationEvidence: [],
      scores: [
        {
          productId: 'prod-generic',
          productName: 'Neutral Smart Watch',
          overallScore: 84.0,
          baseScore: 84.0,
          personalizationContribution: 0.0,
          priceValueScore: 84.0,
          featureScore: 84.0,
          popularityScore: 80.0,
          breakdown: {},
          recommendationBadge: 'BEST OVERALL',
        }
      ],
      scoringStrategy: 'PERSONALIZED_HYBRID_SCORER',
      explanationStrategy: 'DETERMINISTIC_RULE_BASED',
      strategyUsed: 'PERSONALIZED_HYBRID_SCORER',
      generatedAt: '2026-09-17T12:00:00Z',
    };

    vi.spyOn(apiClient, 'get').mockResolvedValue({ data: mockColdStartResponse });

    const result = await apiService.getPersonalizedRecommendations(10);

    expect(result.personalizationContribution).toBe(0.0);
    expect(result.personalizationEvidence).toEqual([]);
    expect(result.recommendedProducts[0].name).toBe('Neutral Smart Watch');
  });

  it('preserves strict backend ranking order without client-side re-sorting', async () => {
    const mockRankedResponse: RecommendationResponse = {
      recommendedProducts: [
        {
          id: 'prod-1',
          name: 'High Score Product ($1000)',
          brand: 'BrandA',
          category: 'Tech',
          description: 'First by backend ranking',
          imageUrl: '',
          prices: [],
          lowestPrice: 1000,
        },
        {
          id: 'prod-2',
          name: 'Low Price Product ($200)',
          brand: 'BrandB',
          category: 'Tech',
          description: 'Second by backend ranking',
          imageUrl: '',
          prices: [],
          lowestPrice: 200,
        }
      ],
      scores: [],
      explanation: '',
      strategyUsed: 'DEFAULT_COMPARISON_SCORER',
      generatedAt: '2026-09-17T12:00:00Z',
    };

    vi.spyOn(apiClient, 'get').mockResolvedValue({ data: mockRankedResponse });

    const result = await apiService.getPersonalizedRecommendations();

    // The order must match backend ranking regardless of price differences
    expect(result.recommendedProducts[0].id).toBe('prod-1');
    expect(result.recommendedProducts[1].id).toBe('prod-2');
  });
});
