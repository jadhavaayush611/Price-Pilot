import { describe, it, expect, vi, beforeEach } from 'vitest';
import { apiService, apiClient, getApiBaseUrl } from './api';

describe('getApiBaseUrl configuration and normalization', () => {
  it('falls back to default localhost URL when no environment variables are set', () => {
    expect(getApiBaseUrl({})).toBe('http://localhost:8080/api/v1');
  });

  it('normalizes VITE_API_URL by appending /api/v1 when given root backend origin', () => {
    expect(getApiBaseUrl({ VITE_API_URL: 'https://pricepilot-backend-4sxk.onrender.com' }))
      .toBe('https://pricepilot-backend-4sxk.onrender.com/api/v1');
  });

  it('handles trailing slashes on VITE_API_URL cleanly', () => {
    expect(getApiBaseUrl({ VITE_API_URL: 'https://pricepilot-backend-4sxk.onrender.com/' }))
      .toBe('https://pricepilot-backend-4sxk.onrender.com/api/v1');
  });

  it('preserves VITE_API_URL when /api/v1 is already present', () => {
    expect(getApiBaseUrl({ VITE_API_URL: 'https://pricepilot-backend-4sxk.onrender.com/api/v1' }))
      .toBe('https://pricepilot-backend-4sxk.onrender.com/api/v1');
  });

  it('supports legacy VITE_API_BASE_URL fallback', () => {
    expect(getApiBaseUrl({ VITE_API_BASE_URL: 'https://custom-api.com/api/v1/' }))
      .toBe('https://custom-api.com/api/v1');
  });
});


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

describe('apiService.checkHealth real backend propagation', () => {
  beforeEach(() => {
    vi.restoreAllMocks();
  });

  it('returns authentic payload when backend health check succeeds', async () => {
    const healthPayload = {
      status: 'UP',
      database: 'UP',
      cache: 'UP_IN_MEMORY',
      ai_service: 'STANDBY_DETERMINISTIC'
    };

    const getSpy = vi.spyOn(apiClient, 'get').mockResolvedValue({ data: healthPayload });

    const result = await apiService.checkHealth();
    expect(result).toEqual(healthPayload);
    expect(result.status).toBe('UP');
    expect(getSpy).toHaveBeenCalledWith('/health');
  });

  it('rejects and does NOT return synthetic UP status when backend health check fails', async () => {
    const networkError = new Error('Network Error / Backend Outage');
    vi.spyOn(apiClient, 'get').mockRejectedValue(networkError);

    await expect(apiService.checkHealth()).rejects.toThrow('Network Error / Backend Outage');
  });

  it('rejects when backend returns 503 SERVICE UNAVAILABLE', async () => {
    const error503 = {
      response: {
        status: 503,
        data: { status: 'DOWN', database: 'DOWN' }
      },
      message: 'Request failed with status code 503'
    };
    vi.spyOn(apiClient, 'get').mockRejectedValue(error503);

    await expect(apiService.checkHealth()).rejects.toMatchObject({
      response: { status: 503, data: { status: 'DOWN', database: 'DOWN' } }
    });
  });
});

