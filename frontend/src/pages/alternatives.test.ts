import { describe, it, expect, vi, beforeEach } from 'vitest';
import { apiService, apiClient } from '../services/api';
import type { AlternativeResponse } from '../types';

describe('AlternativesPage Lifecycle & User Isolation Integration', () => {
  beforeEach(() => {
    vi.restoreAllMocks();
  });

  it('handles user switching cleanly without cross-user personalization bleed', async () => {
    const userAAlternatives: AlternativeResponse = {
      alternativeType: 'SIMILAR',
      executionMode: 'PRODUCT',
      totalFound: 1,
      content: [
        {
          id: 'alt-user-a',
          name: 'Alternative Tailored for User A',
          brand: 'BrandA',
          alternativeScore: 88,
          personalizedScore: 95,
          personalizationAdjustment: 7,
          personalizedEvidence: {
            summary: 'User A specific brand match',
            reasons: [
              {
                dimension: 'BRAND_AFFINITY',
                reason: 'Matched User A preferred brand',
              },
            ],
          },
        },
      ],
    };

    const userBAlternatives: AlternativeResponse = {
      alternativeType: 'SIMILAR',
      executionMode: 'PRODUCT',
      totalFound: 1,
      content: [
        {
          id: 'alt-user-b',
          name: 'Alternative Tailored for User B',
          brand: 'BrandB',
          alternativeScore: 84,
          personalizedScore: 91,
          personalizationAdjustment: 7,
          personalizedEvidence: {
            summary: 'User B specific category match',
            reasons: [
              {
                dimension: 'CATEGORY_AFFINITY',
                reason: 'Matched User B preferred category',
              },
            ],
          },
        },
      ],
    };

    const getSpy = vi.spyOn(apiClient, 'get')
      .mockResolvedValueOnce({ data: userAAlternatives })
      .mockResolvedValueOnce({ data: userBAlternatives });

    // 1. Fetch for User A
    const resA = await apiService.getPersonalizedAlternatives('prod-1', { type: 'SIMILAR' });
    expect(resA.content[0].name).toBe('Alternative Tailored for User A');
    expect(resA.content[0].personalizedEvidence?.summary).toBe('User A specific brand match');

    // 2. Fetch for User B
    const resB = await apiService.getPersonalizedAlternatives('prod-1', { type: 'SIMILAR' });
    expect(resB.content[0].name).toBe('Alternative Tailored for User B');
    expect(resB.content[0].personalizedEvidence?.summary).toBe('User B specific category match');

    // Confirm neither request passed any client-side userId param
    expect(getSpy).toHaveBeenCalledTimes(2);
    const paramsA = (getSpy.mock.calls[0] as unknown as [string, { params?: Record<string, unknown> }])[1]?.params;
    const paramsB = (getSpy.mock.calls[1] as unknown as [string, { params?: Record<string, unknown> }])[1]?.params;
    expect(paramsA?.userId).toBeUndefined();
    expect(paramsB?.userId).toBeUndefined();
  });

  it('handles empty alternative response correctly without UI errors', async () => {
    const emptyResponse: AlternativeResponse = {
      alternativeType: 'PREMIUM',
      executionMode: 'PRODUCT',
      totalFound: 0,
      content: [],
      appliedNotes: ['No products meet the required threshold criteria'],
    };

    vi.spyOn(apiClient, 'get').mockResolvedValue({ data: emptyResponse });

    const result = await apiService.getAlternatives('prod-niche', { type: 'PREMIUM' });
    expect(result.content).toEqual([]);
    expect(result.totalFound).toBe(0);
    expect(result.appliedNotes?.length).toBe(1);
  });
});
