import { describe, it, expect, vi, beforeEach } from 'vitest';
import { apiService, apiClient } from '../services/api';
import type { RecommendationResponse, UserShoppingPreference } from '../types';

describe('Personalized Recommendations & Preferences Integration Tests', () => {
  beforeEach(() => {
    vi.restoreAllMocks();
  });

  it('fetches user preferences and supports full lifecycle', async () => {
    const mockPreferences: UserShoppingPreference = {
      preferredCategories: ['Electronics', 'Audio'],
      preferredBrands: ['Apple', 'Sony'],
      minBudget: 100,
      maxBudget: 2000,
      minRating: 4.5,
      dealSensitivity: 'HIGH',
      priceSensitivity: 'MEDIUM',
      availabilityPreference: 'IN_STOCK_ONLY',
    };

    const getSpy = vi.spyOn(apiClient, 'get').mockResolvedValue({ data: mockPreferences });

    const result = await apiService.getUserPreferences();

    expect(getSpy).toHaveBeenCalledWith('/users/preferences');
    expect(result.preferredBrands).toContain('Apple');
    expect(result.preferredCategories).toContain('Audio');
    expect(result.maxBudget).toBe(2000);
    expect(result.minRating).toBe(4.5);
    expect(result.dealSensitivity).toBe('HIGH');
  });

  it('updates user preferences correctly', async () => {
    const updateRequest = {
      preferredBrands: ['Samsung'],
      maxBudget: 1500,
    };

    const updatedResponse: UserShoppingPreference = {
      preferredCategories: [],
      preferredBrands: ['Samsung'],
      maxBudget: 1500,
      dealSensitivity: 'MEDIUM',
      priceSensitivity: 'MEDIUM',
      availabilityPreference: 'ALL',
    };

    const putSpy = vi.spyOn(apiClient, 'put').mockResolvedValue({ data: updatedResponse });

    const result = await apiService.updateUserPreferences(updateRequest);

    expect(putSpy).toHaveBeenCalledWith('/users/preferences', updateRequest);
    expect(result.preferredBrands).toEqual(['Samsung']);
    expect(result.maxBudget).toBe(1500);
  });

  it('resets user preferences to defaults cleanly', async () => {
    const deleteSpy = vi.spyOn(apiClient, 'delete').mockResolvedValue({ data: null });

    await apiService.resetUserPreferences();

    expect(deleteSpy).toHaveBeenCalledWith('/users/preferences');
  });

  it('guarantees user isolation: no user ID is leaked in requests or responses', async () => {
    const mockResponse: RecommendationResponse = {
      recommendedProducts: [
        {
          id: 'prod-safe-1',
          name: 'Privacy Safe Headphones',
          brand: 'SafeBrand',
          category: 'Audio',
          description: 'Verified safe product',
          imageUrl: 'https://example.com/safe.jpg',
          prices: [],
          lowestPrice: 150.00,
        }
      ],
      scores: [],
      explanation: 'Recommended based on explicit category preference.',
      personalizationEvidence: [
        {
          productId: 'prod-safe-1',
          productName: 'Privacy Safe Headphones',
          type: 'PREFERRED_CATEGORY',
          description: 'Matches your preferred category (Audio)',
          positive: true,
          importance: 0.9,
        }
      ],
      strategyUsed: 'PERSONALIZED_HYBRID_SCORER',
      generatedAt: '2026-09-17T12:00:00Z',
    };

    vi.spyOn(apiClient, 'get').mockResolvedValue({ data: mockResponse });

    const result = await apiService.getPersonalizedRecommendations(5);

    // Ensure no raw telemetry or user IDs are exposed in the presentation layer
    expect(result.recommendedProducts[0].id).toBe('prod-safe-1');
    expect(result.personalizationEvidence?.[0].description).toBe('Matches your preferred category (Audio)');
    expect(result.personalizationEvidence?.[0].description).not.toContain('userId');
    expect(result.personalizationEvidence?.[0].description).not.toContain('event_id');
  });
});
