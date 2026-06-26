import { describe, it, expect, vi, beforeEach } from 'vitest';
import { apiService, apiClient } from './api';

describe('apiService Watchlist Error Parsing', () => {
  beforeEach(() => {
    vi.restoreAllMocks();
    
    // Mock localStorage since we are running in a Node environment under Vitest
    const store: Record<string, string> = {};
    vi.stubGlobal('localStorage', {
      getItem: (key: string) => store[key] || null,
      setItem: (key: string, value: string) => { store[key] = value; },
      removeItem: (key: string) => { delete store[key]; },
      clear: () => { for (const key in store) delete store[key]; },
      length: 0,
      key: (index: number) => Object.keys(store)[index] || null
    });
  });

  it('should format localized createWatchlist error message using details payload instead of regex', async () => {
    const errorResponse = {
      response: {
        status: 400,
        data: {
          message: 'Target price must be less than the current best price.',
          code: 'INVALID_TARGET_PRICE',
          details: {
            currentBestPrice: 1099.00,
            currency: 'USD'
          }
        }
      }
    };

    // Spy on the post method of the API client and reject with structured error
    const postSpy = vi.spyOn(apiClient, 'post').mockRejectedValue(errorResponse);

    // Call createWatchlist. We expect it to fail, and throw the formatted error.
    await expect(apiService.createWatchlist('p1', 1200))
      .rejects.toThrow('Target price must be less than the current best price. (₹87,920)');

    expect(postSpy).toHaveBeenCalled();
  });

  it('should format localized updateWatchlist error message using details payload instead of regex', async () => {
    const errorResponse = {
      response: {
        status: 400,
        data: {
          message: 'Target price must be less than the current best price.',
          code: 'INVALID_TARGET_PRICE',
          details: {
            currentBestPrice: 1099.00,
            currency: 'USD'
          }
        }
      }
    };

    // Spy on the put method of the API client and reject with structured error
    const putSpy = vi.spyOn(apiClient, 'put').mockRejectedValue(errorResponse);

    // Call updateWatchlist. We expect it to fail, and throw the formatted error.
    await expect(apiService.updateWatchlist('w1', 1200))
      .rejects.toThrow('Target price must be less than the current best price. (₹87,920)');

    expect(putSpy).toHaveBeenCalled();
  });
});
