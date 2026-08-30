import { describe, it, expect, vi, beforeEach } from 'vitest';
import { apiService, apiClient } from '../../services/api';
import type { PriceAlert, WatchlistAlertPreference } from '../../types';

describe('Price Alerts & Notification Center Frontend Tests', () => {
  beforeEach(() => {
    vi.restoreAllMocks();
  });

  it('fetches unread alerts correctly via apiService.getUnreadAlerts', async () => {
    const mockAlerts: PriceAlert[] = [
      {
        id: 'alert-1',
        userId: 'user-1',
        productId: 'prod-1',
        productName: 'Sony WH-1000XM5',
        alertType: 'PRICE_DROP',
        title: 'Price Drop Alert (-12.5%)',
        message: 'Price dropped by 12.5% from $400.00 to $350.00',
        triggerValue: 10.0,
        observedValue: 12.5,
        read: false,
        createdAt: '2026-08-30T10:00:00Z',
      },
      {
        id: 'alert-2',
        userId: 'user-1',
        productId: 'prod-2',
        productName: 'MacBook Pro M3',
        alertType: 'HISTORICAL_LOW_REACHED',
        title: 'New All-Time Historical Low!',
        message: 'Current price ($1699.00) established a new recorded all-time low',
        triggerValue: 1700.0,
        observedValue: 1699.0,
        read: false,
        createdAt: '2026-08-30T11:00:00Z',
      },
    ];

    const getSpy = vi.spyOn(apiClient, 'get').mockResolvedValue({ data: mockAlerts });

    const results = await apiService.getUnreadAlerts();

    expect(getSpy).toHaveBeenCalledWith('/alerts/unread');
    expect(results.length).toBe(2);
    expect(results[0].alertType).toBe('PRICE_DROP');
    expect(results[1].alertType).toBe('HISTORICAL_LOW_REACHED');
  });

  it('fetches unread alert count via apiService.getUnreadAlertCount', async () => {
    const getSpy = vi.spyOn(apiClient, 'get').mockResolvedValue({ data: { unreadCount: 5 } });

    const count = await apiService.getUnreadAlertCount();

    expect(getSpy).toHaveBeenCalledWith('/alerts/unread/count');
    expect(count).toBe(5);
  });

  it('marks single alert as read via apiService.markAlertRead', async () => {
    const patchSpy = vi.spyOn(apiClient, 'patch').mockResolvedValue({
      data: {
        id: 'alert-1',
        read: true,
        readAt: '2026-08-30T12:00:00Z',
      },
    });

    const result = await apiService.markAlertRead('alert-1');

    expect(patchSpy).toHaveBeenCalledWith('/alerts/alert-1/read');
    expect(result.read).toBe(true);
  });

  it('marks all alerts as read via apiService.markAllAlertsRead', async () => {
    const patchSpy = vi.spyOn(apiClient, 'patch').mockResolvedValue({ data: { markedCount: 3 } });

    const count = await apiService.markAllAlertsRead();

    expect(patchSpy).toHaveBeenCalledWith('/alerts/read-all');
    expect(count).toBe(3);
  });

  it('fetches watchlist alert preferences via apiService.getWatchlistAlertPreferences', async () => {
    const mockPrefs: WatchlistAlertPreference = {
      id: 'pref-1',
      watchlistId: 'watch-1',
      enabled: true,
      priceDropEnabled: true,
      priceDropPercentage: 10.0,
      targetPriceEnabled: true,
      historicalLowEnabled: true,
      goodDealEnabled: true,
      backInStockEnabled: true,
      priceIncreaseEnabled: false,
    };

    const getSpy = vi.spyOn(apiClient, 'get').mockResolvedValue({ data: mockPrefs });

    const prefs = await apiService.getWatchlistAlertPreferences('watch-1');

    expect(getSpy).toHaveBeenCalledWith('/watchlists/watch-1/alerts');
    expect(prefs.enabled).toBe(true);
    expect(prefs.priceDropPercentage).toBe(10.0);
    expect(prefs.goodDealEnabled).toBe(true);
  });

  it('updates watchlist alert preferences via apiService.updateWatchlistAlertPreferences', async () => {
    const updatedPrefs: WatchlistAlertPreference = {
      id: 'pref-1',
      watchlistId: 'watch-1',
      enabled: true,
      priceDropEnabled: true,
      priceDropPercentage: 15.0,
      targetPriceEnabled: true,
      historicalLowEnabled: true,
      goodDealEnabled: true,
      backInStockEnabled: true,
      priceIncreaseEnabled: true,
    };

    const putSpy = vi.spyOn(apiClient, 'put').mockResolvedValue({ data: updatedPrefs });

    const result = await apiService.updateWatchlistAlertPreferences('watch-1', {
      priceDropPercentage: 15.0,
      priceIncreaseEnabled: true,
    });

    expect(putSpy).toHaveBeenCalledWith('/watchlists/watch-1/alerts', {
      priceDropPercentage: 15.0,
      priceIncreaseEnabled: true,
    });
    expect(result.priceDropPercentage).toBe(15.0);
    expect(result.priceIncreaseEnabled).toBe(true);
  });
});
