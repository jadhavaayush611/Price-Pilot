import { describe, it, expect, vi, beforeEach } from 'vitest';
import { apiService, apiClient } from '../services/api';
import type { UserShoppingPreference, UpdateShoppingPreferenceRequest } from '../types';
import { getSavedCurrency, saveCurrency, type CurrencyCode } from '../currency';

class LocalStorageMock {
  private store: Record<string, string> = {};

  clear() {
    this.store = {};
  }

  getItem(key: string) {
    return this.store[key] || null;
  }

  setItem(key: string, value: string) {
    this.store[key] = value.toString();
  }

  removeItem(key: string) {
    delete this.store[key];
  }
}

if (typeof window === 'undefined') {
  const g = globalThis as unknown as { localStorage: LocalStorageMock; window: unknown };
  g.localStorage = new LocalStorageMock();
  g.window = { localStorage: g.localStorage };
}

describe('Shopping Preferences Frontend Lifecycle & Persistence Tests', () => {
  beforeEach(() => {
    vi.restoreAllMocks();
    localStorage.clear();
  });

  it('hydrates initial state from GET /api/v1/users/preferences correctly', async () => {
    const serverState: UserShoppingPreference = {
      preferredCategories: ['Headphones', 'Audio'],
      preferredBrands: ['Sony', 'Bose'],
      minBudget: 1000,
      maxBudget: 5000,
      minRating: 4.0,
      dealSensitivity: 'HIGH',
      priceSensitivity: 'LOW',
      availabilityPreference: 'IN_STOCK_ONLY',
      currency: 'INR',
    };

    const getSpy = vi.spyOn(apiClient, 'get').mockResolvedValue({ data: serverState });

    const prefs = await apiService.getUserPreferences();

    expect(getSpy).toHaveBeenCalledWith('/users/preferences');
    expect(prefs.preferredCategories).toEqual(['Headphones', 'Audio']);
    expect(prefs.preferredBrands).toEqual(['Sony', 'Bose']);
    expect(prefs.minBudget).toBe(1000);
    expect(prefs.maxBudget).toBe(5000);
    expect(prefs.minRating).toBe(4.0);
    expect(prefs.dealSensitivity).toBe('HIGH');
    expect(prefs.priceSensitivity).toBe('LOW');
    expect(prefs.availabilityPreference).toBe('IN_STOCK_ONLY');
    expect(prefs.currency).toBe('INR');
  });

  it('saves preferences via PUT and returned server state becomes authoritative', async () => {
    const updateReq: UpdateShoppingPreferenceRequest = {
      preferredCategories: ['Smartphones'],
      preferredBrands: ['Apple'],
      minBudget: 500,
      maxBudget: 1200,
      minRating: 4.5,
      dealSensitivity: 'MEDIUM',
      priceSensitivity: 'HIGH',
      availabilityPreference: 'ALL',
      currency: 'USD',
    };

    const serverResponse: UserShoppingPreference = {
      preferredCategories: ['Smartphones'],
      preferredBrands: ['Apple'],
      minBudget: 500,
      maxBudget: 1200,
      minRating: 4.5,
      dealSensitivity: 'MEDIUM',
      priceSensitivity: 'HIGH',
      availabilityPreference: 'ALL',
      currency: 'USD',
    };

    const putSpy = vi.spyOn(apiClient, 'put').mockResolvedValue({ data: serverResponse });

    const saved = await apiService.updateUserPreferences(updateReq);

    expect(putSpy).toHaveBeenCalledWith('/users/preferences', updateReq);
    expect(saved).toEqual(serverResponse);
  });

  it('failed save propagates error and does not falsely report success', async () => {
    vi.spyOn(apiClient, 'put').mockRejectedValue(new Error('Network error or server validation failure'));

    await expect(
      apiService.updateUserPreferences({
        minBudget: 1000,
        maxBudget: 500, // Invalid range
      })
    ).rejects.toThrow('Network error or server validation failure');
  });

  it('simulates navigation away and return: state is rehydrated accurately', async () => {
    const persistedState: UserShoppingPreference = {
      preferredCategories: ['Laptops'],
      preferredBrands: ['Dell'],
      minBudget: 40000,
      maxBudget: 80000,
      minRating: 4.2,
      dealSensitivity: 'MEDIUM',
      priceSensitivity: 'MEDIUM',
      availabilityPreference: 'ALL',
      currency: 'INR',
    };

    vi.spyOn(apiClient, 'get').mockResolvedValue({ data: persistedState });

    // Initial load
    const initial = await apiService.getUserPreferences();
    expect(initial.preferredBrands).toContain('Dell');

    // Simulate navigating to another route and returning (calling getUserPreferences again)
    const returned = await apiService.getUserPreferences();
    expect(returned).toEqual(persistedState);
    expect(returned.maxBudget).toBe(80000);
  });

  it('simulates refresh: currency and preference state persist across session reload', async () => {
    const userPrefs: UserShoppingPreference = {
      preferredCategories: ['Headphones'],
      preferredBrands: ['Sony'],
      minBudget: 5000,
      maxBudget: 10000,
      dealSensitivity: 'MEDIUM',
      priceSensitivity: 'MEDIUM',
      availabilityPreference: 'ALL',
      currency: 'INR',
    };

    saveCurrency('INR');
    vi.spyOn(apiClient, 'get').mockResolvedValue({ data: userPrefs });

    // On page reload, fetch user preferences
    const reloadedPrefs = await apiService.getUserPreferences();
    if (reloadedPrefs.currency) {
      saveCurrency(reloadedPrefs.currency as CurrencyCode);
    }

    expect(reloadedPrefs.currency).toBe('INR');
    expect(getSavedCurrency()).toBe('INR');
    expect(reloadedPrefs.minBudget).toBe(5000);
  });

  it('simulates user switching: User A logging out resets active currency to default and User B gets their own preferences', async () => {
    // User A preference
    const userAPrefs: UserShoppingPreference = {
      preferredCategories: ['Headphones'],
      preferredBrands: ['Sony'],
      dealSensitivity: 'MEDIUM',
      priceSensitivity: 'MEDIUM',
      availabilityPreference: 'ALL',
      currency: 'USD',
      minBudget: 100,
      maxBudget: 300,
    };

    saveCurrency(userAPrefs.currency as CurrencyCode);
    expect(getSavedCurrency()).toBe('USD');

    // User A logs out -> resets local currency to default
    saveCurrency('INR');
    expect(getSavedCurrency()).toBe('INR');

    // User B logs in with EUR preferences
    const userBPrefs: UserShoppingPreference = {
      preferredCategories: ['Gaming'],
      preferredBrands: ['Nintendo'],
      dealSensitivity: 'MEDIUM',
      priceSensitivity: 'MEDIUM',
      availabilityPreference: 'ALL',
      currency: 'EUR',
      minBudget: 200,
      maxBudget: 600,
    };

    vi.spyOn(apiClient, 'get').mockResolvedValue({ data: userBPrefs });
    const fetchedB = await apiService.getUserPreferences();
    if (fetchedB.currency) {
      saveCurrency(fetchedB.currency as CurrencyCode);
    }

    expect(getSavedCurrency()).toBe('EUR');
    expect(fetchedB.preferredBrands).toContain('Nintendo');
    expect(fetchedB.preferredBrands).not.toContain('Sony');
  });

  it('preserves INR + ₹5,000 budget round trip', async () => {
    const request: UpdateShoppingPreferenceRequest = {
      preferredCategories: ['Headphones'],
      preferredBrands: ['Sony'],
      minBudget: 1000,
      maxBudget: 5000,
      minRating: 4.0,
      currency: 'INR',
    };

    const response: UserShoppingPreference = {
      preferredCategories: ['Headphones'],
      preferredBrands: ['Sony'],
      minBudget: 1000,
      maxBudget: 5000,
      minRating: 4.0,
      currency: 'INR',
      dealSensitivity: 'MEDIUM',
      priceSensitivity: 'MEDIUM',
      availabilityPreference: 'ALL',
    };

    vi.spyOn(apiClient, 'put').mockResolvedValue({ data: response });

    const result = await apiService.updateUserPreferences(request);
    expect(result.currency).toBe('INR');
    expect(result.maxBudget).toBe(5000);
    expect(result.minRating).toBe(4.0);
  });

  it('preserves USD + $100 budget round trip', async () => {
    const request: UpdateShoppingPreferenceRequest = {
      preferredCategories: ['Accessories'],
      preferredBrands: ['Anker'],
      minBudget: 20,
      maxBudget: 100,
      minRating: 4.5,
      currency: 'USD',
    };

    const response: UserShoppingPreference = {
      preferredCategories: ['Accessories'],
      preferredBrands: ['Anker'],
      minBudget: 20,
      maxBudget: 100,
      minRating: 4.5,
      currency: 'USD',
      dealSensitivity: 'HIGH',
      priceSensitivity: 'HIGH',
      availabilityPreference: 'IN_STOCK_ONLY',
    };

    vi.spyOn(apiClient, 'put').mockResolvedValue({ data: response });

    const result = await apiService.updateUserPreferences(request);
    expect(result.currency).toBe('USD');
    expect(result.maxBudget).toBe(100);
    expect(result.minBudget).toBe(20);
  });
});
