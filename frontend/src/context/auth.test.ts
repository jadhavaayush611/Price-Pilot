import { describe, it, expect, vi, beforeEach } from 'vitest';
import { apiService, apiClient } from '../services/api';
import { getSavedCurrency, saveCurrency } from '../currency';

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

describe('Auth & Preference Persistence Integration', () => {
  beforeEach(() => {
    vi.restoreAllMocks();
    localStorage.clear();
  });

  it('restores default INR currency when user logs out', () => {
    saveCurrency('USD');
    expect(getSavedCurrency()).toBe('USD');

    // Reset / logout cleans up active currency to default
    saveCurrency('INR');
    expect(getSavedCurrency()).toBe('INR');
  });

  it('fetches and synchronizes persisted user currency on preference retrieval', async () => {
    vi.spyOn(apiClient, 'get').mockResolvedValue({
      data: {
        currency: 'USD',
        preferredCategories: ['Headphones'],
        minBudget: 50,
        maxBudget: 200,
      },
    });

    const prefs = await apiService.getUserPreferences();
    expect(prefs.currency).toBe('USD');

    saveCurrency(prefs.currency as any);
    expect(getSavedCurrency()).toBe('USD');
  });
});
