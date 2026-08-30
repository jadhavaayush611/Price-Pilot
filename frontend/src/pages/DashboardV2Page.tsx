import React, { useEffect, useState, useCallback } from 'react';
import { Link } from 'react-router-dom';
import {
  TrendingDown,
  TrendingUp,
  Minus,
  AlertTriangle,
  Flame,
  Sparkles,
  CheckCircle2,
  Clock,
  ArrowUpRight,
  RefreshCw,
  Bell,
  SlidersHorizontal,
  Bookmark,
  Layers,
  ShoppingBag
} from 'lucide-react';
import { apiService } from '../services/api';
import { useAuth } from '../context/AuthContext';
import { formatPrice } from '../currency';
import type { DashboardV2Response } from '../types';

export const DashboardV2Page: React.FC = () => {
  const { user } = useAuth();
  const [dashboard, setDashboard] = useState<DashboardV2Response | null>(null);
  const [loading, setLoading] = useState<boolean>(true);
  const [refreshing, setRefreshing] = useState<boolean>(false);
  const [error, setError] = useState<string | null>(null);

  const fetchDashboard = useCallback(async (isRefresh = false) => {
    if (isRefresh) setRefreshing(true);
    else setLoading(true);
    setError(null);

    try {
      const data = await apiService.getDashboardV2();
      setDashboard(data);
    } catch (err: unknown) {
      console.error('Failed to load Dashboard V2:', err);
      setError('Unable to load shopping intelligence dashboard. Please check connection and try again.');
    } finally {
      setLoading(false);
      setRefreshing(false);
    }
  }, []);

  useEffect(() => {
    fetchDashboard();
  }, [fetchDashboard]);

  const handleMarkAlertRead = async (alertId: string) => {
    try {
      await apiService.markAlertRead(alertId);
      setDashboard((prev) => {
        if (!prev) return null;
        return {
          ...prev,
          overview: {
            ...prev.overview,
            unreadAlertsCount: Math.max(0, prev.overview.unreadAlertsCount - 1),
          },
          recentAlerts: prev.recentAlerts.map((a) =>
            a.id === alertId ? { ...a, read: true, readAt: new Date().toISOString() } : a
          ),
        };
      });
    } catch (err) {
      console.error('Failed to mark alert as read:', err);
    }
  };

  if (loading) {
    return (
      <main className="max-w-7xl mx-auto px-4 py-8 space-y-8 animate-pulse" aria-busy="true" aria-label="Loading Shopping Intelligence Dashboard">
        <div className="h-10 bg-zinc-900 rounded-lg w-1/3" />
        <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-4">
          {[1, 2, 3, 4].map((i) => (
            <div key={i} className="h-28 bg-zinc-900 border border-zinc-800 rounded-xl" />
          ))}
        </div>
        <div className="h-64 bg-zinc-900 border border-zinc-800 rounded-xl" />
      </main>
    );
  }

  if (error || !dashboard) {
    return (
      <main className="max-w-7xl mx-auto px-4 py-16 text-center">
        <div className="inline-flex items-center justify-center p-4 bg-red-500/10 rounded-full mb-4">
          <AlertTriangle className="h-8 w-8 text-red-400" aria-hidden="true" />
        </div>
        <h1 className="text-2xl font-bold text-white mb-2">Shopping Intelligence Unavailable</h1>
        <p className="text-zinc-400 max-w-md mx-auto mb-6">{error || 'Could not assemble dashboard telemetry.'}</p>
        <button
          onClick={() => fetchDashboard()}
          className="inline-flex items-center gap-2 px-4 py-2 rounded-lg bg-indigo-600 hover:bg-indigo-500 text-white text-sm font-medium transition-colors"
        >
          <RefreshCw className="h-4 w-4" />
          <span>Retry Loading</span>
        </button>
      </main>
    );
  }

  const { overview, attentionItems, priceOpportunities, watchedProducts, recentAlerts, recommendations, recentActivity } = dashboard;
  const isZeroState = overview.activeWatchlistsCount === 0;

  return (
    <main className="max-w-7xl mx-auto px-4 py-8 space-y-10" aria-label="Shopping Intelligence Dashboard">
      {/* Header & Greetings */}
      <header className="flex flex-col sm:flex-row sm:items-center justify-between gap-4 border-b border-zinc-800/80 pb-6">
        <div>
          <div className="inline-flex items-center gap-2 px-3 py-1 rounded-full bg-zinc-900 border border-zinc-800 text-xs text-zinc-400 font-mono mb-2">
            <span className="h-2 w-2 rounded-full bg-emerald-400 animate-pulse" aria-hidden="true" />
            <span>PricePilot v1.1 Live Intelligence</span>
          </div>
          <h1 className="text-3xl font-bold tracking-tight text-white">
            {user?.firstName ? `Welcome back, ${user.firstName}` : 'Shopping Intelligence Dashboard'}
          </h1>
          <p className="text-zinc-400 text-sm mt-1">
            Personalized purchasing control center aggregating deterministic price analytics and alerts.
          </p>
        </div>

        <div className="flex items-center gap-3">
          <button
            onClick={() => fetchDashboard(true)}
            disabled={refreshing}
            className="inline-flex items-center gap-2 px-3.5 py-2 rounded-xl bg-zinc-900 border border-zinc-800 hover:bg-zinc-800 text-zinc-300 text-xs font-semibold transition-all disabled:opacity-50"
            aria-label="Refresh Dashboard Telemetry"
          >
            <RefreshCw className={`h-3.5 w-3.5 ${refreshing ? 'animate-spin' : ''}`} aria-hidden="true" />
            <span>{refreshing ? 'Refreshing...' : 'Refresh'}</span>
          </button>
          <Link
            to="/watchlist"
            className="inline-flex items-center gap-2 px-3.5 py-2 rounded-xl bg-indigo-600 hover:bg-indigo-500 text-white text-xs font-semibold transition-all shadow-sm"
          >
            <SlidersHorizontal className="h-3.5 w-3.5" aria-hidden="true" />
            <span>Manage Watchlist</span>
          </Link>
        </div>
      </header>

      {/* Summary Metrics Cards */}
      <section aria-labelledby="metrics-heading" className="grid grid-cols-2 sm:grid-cols-3 lg:grid-cols-6 gap-3 sm:gap-4">
        <h2 id="metrics-heading" className="sr-only">Dashboard Overview Metrics</h2>

        <div className="bg-zinc-950 border border-zinc-900 rounded-xl p-4 flex flex-col justify-between">
          <span className="text-xs font-medium text-zinc-400">Active Watchlists</span>
          <div className="flex items-baseline justify-between mt-2">
            <span className="text-2xl font-bold text-white">{overview.activeWatchlistsCount}</span>
            <Bookmark className="h-4 w-4 text-indigo-400" aria-hidden="true" />
          </div>
        </div>

        <div className="bg-zinc-950 border border-zinc-900 rounded-xl p-4 flex flex-col justify-between">
          <span className="text-xs font-medium text-zinc-400">Unread Alerts</span>
          <div className="flex items-baseline justify-between mt-2">
            <span className="text-2xl font-bold text-amber-400">{overview.unreadAlertsCount}</span>
            <Bell className="h-4 w-4 text-amber-400" aria-hidden="true" />
          </div>
        </div>

        <div className="bg-zinc-950 border border-zinc-900 rounded-xl p-4 flex flex-col justify-between">
          <span className="text-xs font-medium text-zinc-400">Price Deals</span>
          <div className="flex items-baseline justify-between mt-2">
            <span className="text-2xl font-bold text-emerald-400">{overview.goodOrExcellentDealCount}</span>
            <Flame className="h-4 w-4 text-emerald-400" aria-hidden="true" />
          </div>
        </div>

        <div className="bg-zinc-950 border border-zinc-900 rounded-xl p-4 flex flex-col justify-between">
          <span className="text-xs font-medium text-zinc-400">At All-Time Low</span>
          <div className="flex items-baseline justify-between mt-2">
            <span className="text-2xl font-bold text-teal-400">{overview.historicalLowCount}</span>
            <TrendingDown className="h-4 w-4 text-teal-400" aria-hidden="true" />
          </div>
        </div>

        <div className="bg-zinc-950 border border-zinc-900 rounded-xl p-4 flex flex-col justify-between">
          <span className="text-xs font-medium text-zinc-400">Recent Drops</span>
          <div className="flex items-baseline justify-between mt-2">
            <span className="text-2xl font-bold text-blue-400">{overview.recentPriceDropCount}</span>
            <Sparkles className="h-4 w-4 text-blue-400" aria-hidden="true" />
          </div>
        </div>

        <div className="bg-zinc-950 border border-zinc-900 rounded-xl p-4 flex flex-col justify-between">
          <span className="text-xs font-medium text-zinc-400">Saved Items</span>
          <div className="flex items-baseline justify-between mt-2">
            <span className="text-2xl font-bold text-zinc-300">
              {overview.savedProductsCount + overview.savedComparisonsCount}
            </span>
            <Layers className="h-4 w-4 text-zinc-400" aria-hidden="true" />
          </div>
        </div>
      </section>

      {/* Empty State when User has no watchlists */}
      {isZeroState ? (
        <section aria-labelledby="empty-state-heading" className="bg-zinc-950 border border-zinc-800/80 rounded-2xl p-8 sm:p-12 text-center space-y-6">
          <div className="mx-auto w-14 h-14 rounded-2xl bg-indigo-500/10 border border-indigo-500/20 flex items-center justify-center">
            <ShoppingBag className="h-7 w-7 text-indigo-400" aria-hidden="true" />
          </div>
          <div className="max-w-md mx-auto space-y-2">
            <h2 id="empty-state-heading" className="text-xl font-bold text-white">Start Building Shopping Intelligence</h2>
            <p className="text-sm text-zinc-400">
              Track products to enable automatic price volatility monitoring, deal classification, and smart target alerts.
            </p>
          </div>
          <div className="flex flex-wrap justify-center gap-3">
            <Link
              to="/products"
              className="inline-flex items-center gap-2 px-5 py-2.5 rounded-xl bg-indigo-600 hover:bg-indigo-500 text-white text-sm font-semibold transition-all"
            >
              <span>Explore Products</span>
              <ArrowUpRight className="h-4 w-4" aria-hidden="true" />
            </Link>
            <Link
              to="/compare"
              className="inline-flex items-center gap-2 px-5 py-2.5 rounded-xl bg-zinc-900 border border-zinc-800 hover:bg-zinc-800 text-zinc-200 text-sm font-semibold transition-all"
            >
              <span>Compare Products</span>
            </Link>
          </div>
        </section>
      ) : (
        <>
          {/* Section 1: Smart Attention Required Feed */}
          {attentionItems && attentionItems.length > 0 && (
            <section aria-labelledby="attention-heading" className="space-y-4">
              <div className="flex items-center justify-between">
                <div className="flex items-center gap-2">
                  <span className="h-2.5 w-2.5 rounded-full bg-red-500 animate-ping" aria-hidden="true" />
                  <h2 id="attention-heading" className="text-lg font-bold text-white tracking-tight">
                    Attention Required
                  </h2>
                </div>
                <span className="text-xs font-mono text-zinc-500">
                  {attentionItems.length} item{attentionItems.length > 1 ? 's' : ''} prioritized
                </span>
              </div>

              <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-4">
                {attentionItems.map((item) => {
                  const isCritical = item.urgencyLevel === 'CRITICAL';
                  const isHigh = item.urgencyLevel === 'HIGH';

                  return (
                    <article
                      key={item.productId}
                      className={`relative rounded-xl border p-5 transition-all flex flex-col justify-between ${
                        isCritical
                          ? 'bg-red-950/20 border-red-900/60 shadow-lg shadow-red-950/20'
                          : isHigh
                          ? 'bg-amber-950/20 border-amber-900/50'
                          : 'bg-zinc-950 border-zinc-800'
                      }`}
                    >
                      <div className="space-y-3">
                        <div className="flex items-start justify-between gap-3">
                          <div>
                            <span
                              className={`inline-block px-2 py-0.5 rounded text-[10px] font-bold tracking-wider uppercase mb-1 ${
                                isCritical
                                  ? 'bg-red-500/20 text-red-300 border border-red-500/30'
                                  : isHigh
                                  ? 'bg-amber-500/20 text-amber-300 border border-amber-500/30'
                                  : 'bg-blue-500/20 text-blue-300 border border-blue-500/30'
                              }`}
                            >
                              {item.urgencyLevel} ACTION
                            </span>
                            <h3 className="text-sm font-semibold text-white line-clamp-1">{item.productName}</h3>
                            {item.brand && <p className="text-xs text-zinc-400">{item.brand}</p>}
                          </div>
                          {item.productImageUrl && (
                            <img
                              src={item.productImageUrl}
                              alt=""
                              className="w-12 h-12 rounded-lg object-cover bg-zinc-900 border border-zinc-800 shrink-0"
                            />
                          )}
                        </div>

                        <div className="p-3 bg-zinc-900/80 rounded-lg border border-zinc-800/80 space-y-1">
                          <p className="text-xs font-medium text-zinc-200 flex items-center gap-1.5">
                            <AlertTriangle className="h-3.5 w-3.5 text-amber-400 shrink-0" aria-hidden="true" />
                            <span>{item.primaryReason}</span>
                          </p>
                          {item.supportingEvidence && item.supportingEvidence.length > 0 && (
                            <p className="text-[11px] text-zinc-400 pl-5">
                              {item.supportingEvidence[0]}
                            </p>
                          )}
                        </div>

                        <div className="flex items-baseline justify-between text-xs pt-1">
                          <div>
                            <span className="text-zinc-400 text-[11px]">Current: </span>
                            <span className="text-emerald-400 font-bold text-sm">
                              {formatPrice(item.currentPrice)}
                            </span>
                          </div>
                          {item.targetPrice && (
                            <div>
                              <span className="text-zinc-400 text-[11px]">Target: </span>
                              <span className="text-zinc-200 font-medium">{formatPrice(item.targetPrice)}</span>
                            </div>
                          )}
                        </div>
                      </div>

                      <div className="mt-4 pt-3 border-t border-zinc-800/80 flex items-center justify-between">
                        <Link
                          to={`/analytics/${item.productId}`}
                          className="text-xs text-indigo-400 hover:text-indigo-300 font-medium inline-flex items-center gap-1"
                        >
                          <span>Analytics</span>
                          <ArrowUpRight className="h-3 w-3" aria-hidden="true" />
                        </Link>
                        <Link
                          to={`/product/${item.productId}`}
                          className="px-3 py-1 bg-zinc-800 hover:bg-zinc-700 text-white rounded-md text-xs font-semibold transition-colors"
                        >
                          View Product
                        </Link>
                      </div>
                    </article>
                  );
                })}
              </div>
            </section>
          )}

          {/* Section 2: Price Opportunities */}
          {priceOpportunities && priceOpportunities.length > 0 && (
            <section aria-labelledby="opportunities-heading" className="space-y-4">
              <div className="flex items-center justify-between">
                <div className="flex items-center gap-2">
                  <Flame className="h-5 w-5 text-emerald-400" aria-hidden="true" />
                  <h2 id="opportunities-heading" className="text-lg font-bold text-white tracking-tight">
                    Price Opportunities
                  </h2>
                </div>
                <span className="text-xs text-zinc-400">Watched items at compelling purchase points</span>
              </div>

              <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-4">
                {priceOpportunities.map((opp) => (
                  <article key={opp.productId} className="bg-zinc-950 border border-zinc-900 hover:border-zinc-800 rounded-xl p-5 transition-all flex flex-col justify-between">
                    <div>
                      <div className="flex items-start justify-between gap-3 mb-3">
                        <div>
                          <span className="inline-block px-2 py-0.5 rounded text-[10px] font-bold bg-emerald-500/10 text-emerald-300 border border-emerald-500/20 mb-1">
                            {opp.dealQuality ? opp.dealQuality.replace('_', ' ') : 'ATTRACTIVE PRICE'}
                          </span>
                          <h3 className="text-sm font-semibold text-white line-clamp-1">{opp.productName}</h3>
                        </div>
                        {opp.productImageUrl && (
                          <img src={opp.productImageUrl} alt="" className="w-10 h-10 rounded-lg object-cover bg-zinc-900 border border-zinc-800 shrink-0" />
                        )}
                      </div>

                      <div className="space-y-1 mb-4">
                        <div className="flex items-baseline gap-2">
                          <span className="text-xl font-bold text-white">{formatPrice(opp.currentPrice)}</span>
                          {opp.historicalAvg && (
                            <span className="text-xs text-zinc-500 line-through">
                              Avg {formatPrice(opp.historicalAvg)}
                            </span>
                          )}
                        </div>
                        {opp.keyEvidence && (
                          <p className="text-xs text-zinc-400 line-clamp-2">
                            {opp.keyEvidence}
                          </p>
                        )}
                      </div>
                    </div>

                    <div className="pt-3 border-t border-zinc-900 flex items-center justify-between">
                      <Link
                        to={`/analytics/${opp.productId}`}
                        className="text-xs text-zinc-400 hover:text-zinc-200 transition-colors inline-flex items-center gap-1"
                      >
                        <span>View Analytics</span>
                        <ArrowUpRight className="h-3 w-3" aria-hidden="true" />
                      </Link>
                      <Link
                        to={`/product/${opp.productId}`}
                        className="text-xs font-semibold text-emerald-400 hover:underline"
                      >
                        Inspect &rarr;
                      </Link>
                    </div>
                  </article>
                ))}
              </div>
            </section>
          )}

          {/* Section 3: Watched Product Cards */}
          <section aria-labelledby="watched-heading" className="space-y-4">
            <div className="flex items-center justify-between">
              <div className="flex items-center gap-2">
                <Bookmark className="h-5 w-5 text-indigo-400" aria-hidden="true" />
                <h2 id="watched-heading" className="text-lg font-bold text-white tracking-tight">
                  Watched Products ({watchedProducts.length})
                </h2>
              </div>
              <Link to="/watchlist" className="text-xs text-indigo-400 hover:underline">
                View full watchlist &rarr;
              </Link>
            </div>

            <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-4">
              {watchedProducts.map((p) => {
                const isFalling = p.trend === 'FALLING';
                const isRising = p.trend === 'RISING';

                return (
                  <article key={p.productId} className="bg-zinc-950 border border-zinc-900 hover:border-zinc-800 rounded-xl p-5 transition-all flex flex-col justify-between">
                    <div className="space-y-3">
                      <div className="flex items-start justify-between gap-3">
                        <div className="space-y-0.5">
                          {p.brand && <span className="text-[11px] font-mono text-zinc-500 uppercase">{p.brand}</span>}
                          <h3 className="text-sm font-semibold text-white line-clamp-1">{p.productName}</h3>
                        </div>
                        {p.imageUrl && (
                          <img src={p.imageUrl} alt="" className="w-10 h-10 rounded-lg object-cover bg-zinc-900 border border-zinc-800 shrink-0" />
                        )}
                      </div>

                      {/* Pricing & Targets */}
                      <div className="flex items-baseline justify-between pt-1">
                        <div>
                          <span className="text-xs text-zinc-500 block">Best Price</span>
                          <span className="text-lg font-bold text-white">{formatPrice(p.currentPrice)}</span>
                        </div>
                        {p.targetPrice && (
                          <div className="text-right">
                            <span className="text-xs text-zinc-500 block">Target</span>
                            <span className={`text-sm font-semibold ${p.targetMet ? 'text-emerald-400' : 'text-zinc-300'}`}>
                              {formatPrice(p.targetPrice)} {p.targetMet && '✓'}
                            </span>
                          </div>
                        )}
                      </div>

                      {/* Signals & Badges */}
                      <div className="flex flex-wrap items-center gap-1.5 pt-1">
                        {p.dealQuality && (
                          <span className="px-2 py-0.5 rounded text-[10px] font-medium bg-zinc-900 border border-zinc-800 text-zinc-300">
                            {p.dealQuality.replace('_', ' ')}
                          </span>
                        )}
                        {p.trend && (
                          <span className={`inline-flex items-center gap-1 px-2 py-0.5 rounded text-[10px] font-medium ${
                            isFalling ? 'bg-emerald-500/10 text-emerald-400' : isRising ? 'bg-red-500/10 text-red-400' : 'bg-zinc-900 text-zinc-400'
                          }`}>
                            {isFalling ? <TrendingDown className="h-3 w-3" /> : isRising ? <TrendingUp className="h-3 w-3" /> : <Minus className="h-3 w-3" />}
                            <span>{p.trend} {p.trendPercentage ? `${Math.abs(p.trendPercentage).toFixed(1)}%` : ''}</span>
                          </span>
                        )}
                        {p.purchaseSignal && (
                          <span className={`px-2 py-0.5 rounded text-[10px] font-semibold ${
                            p.purchaseSignal === 'BUY_NOW' ? 'bg-emerald-500/20 text-emerald-300 border border-emerald-500/30' :
                            p.purchaseSignal === 'GOOD_TIME' ? 'bg-blue-500/20 text-blue-300' : 'bg-zinc-900 text-zinc-400'
                          }`}>
                            {p.purchaseSignal.replace('_', ' ')}
                          </span>
                        )}
                      </div>
                    </div>

                    <div className="mt-4 pt-3 border-t border-zinc-900 flex items-center justify-between text-xs">
                      <Link to={`/analytics/${p.productId}`} className="text-zinc-400 hover:text-white transition-colors">
                        Analytics &rarr;
                      </Link>
                      <Link to={`/product/${p.productId}`} className="text-indigo-400 hover:text-indigo-300 font-medium">
                        Product Details
                      </Link>
                    </div>
                  </article>
                );
              })}
            </div>
          </section>

          {/* Section 4: Personalized Recommendations */}
          {recommendations && recommendations.items && recommendations.items.length > 0 && (
            <section aria-labelledby="recommendations-heading" className="space-y-4">
              <div className="flex items-center justify-between">
                <div className="flex items-center gap-2">
                  <Sparkles className="h-5 w-5 text-indigo-400" aria-hidden="true" />
                  <h2 id="recommendations-heading" className="text-lg font-bold text-white tracking-tight">
                    Explainable AI Recommendations
                  </h2>
                </div>
                <span className="text-xs text-zinc-500 font-mono">
                  Engine v2 Matrix
                </span>
              </div>

              <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-4">
                {recommendations.items.map((item) => (
                  <article key={item.productId} className="bg-zinc-950 border border-zinc-900 rounded-xl p-4 flex flex-col justify-between space-y-3">
                    <div className="space-y-2">
                      <span className="inline-block px-2 py-0.5 rounded text-[10px] font-bold bg-indigo-500/10 text-indigo-300 border border-indigo-500/20">
                        {item.keyReason || 'TOP MATCH'}
                      </span>
                      <h3 className="text-sm font-semibold text-white line-clamp-1">{item.productName}</h3>
                      {item.currentPrice && (
                        <p className="text-base font-bold text-emerald-400">{formatPrice(item.currentPrice)}</p>
                      )}
                      {item.explanation && (
                        <p className="text-xs text-zinc-400 line-clamp-2">{item.explanation}</p>
                      )}
                    </div>

                    <div className="pt-2 border-t border-zinc-900 flex items-center justify-between">
                      {item.confidence && (
                        <span className="text-[11px] text-zinc-500 font-mono">
                          {(item.confidence * 100).toFixed(0)}% conf
                        </span>
                      )}
                      <Link to={`/product/${item.productId}`} className="text-xs text-indigo-400 hover:underline">
                        Explore &rarr;
                      </Link>
                    </div>
                  </article>
                ))}
              </div>
            </section>
          )}

          {/* Section 5 & 6: Alerts & Recent Activity Grid */}
          <div className="grid grid-cols-1 lg:grid-cols-2 gap-6">
            {/* Recent Alerts */}
            <section aria-labelledby="alerts-heading" className="bg-zinc-950 border border-zinc-900 rounded-2xl p-5 space-y-4">
              <div className="flex items-center justify-between">
                <div className="flex items-center gap-2">
                  <Bell className="h-4 w-4 text-amber-400" aria-hidden="true" />
                  <h2 id="alerts-heading" className="text-sm font-bold text-white tracking-tight">
                    Recent Price Alerts
                  </h2>
                </div>
                <span className="text-xs text-zinc-500">{recentAlerts.length} total</span>
              </div>

              {recentAlerts.length === 0 ? (
                <p className="text-xs text-zinc-500 py-6 text-center">No alerts triggered yet.</p>
              ) : (
                <div className="space-y-2">
                  {recentAlerts.slice(0, 5).map((a) => (
                    <div
                      key={a.id}
                      className={`p-3 rounded-xl border flex items-start justify-between gap-3 text-xs transition-colors ${
                        a.read ? 'bg-zinc-900/40 border-zinc-900 text-zinc-400' : 'bg-zinc-900 border-zinc-800 text-zinc-200'
                      }`}
                    >
                      <div className="space-y-1">
                        <div className="flex items-center gap-2">
                          <span className="font-semibold text-white">{a.productName}</span>
                          <span className="text-[10px] font-mono text-zinc-500">{a.alertType.replace('_', ' ')}</span>
                        </div>
                        <p className="text-zinc-400">{a.message}</p>
                      </div>

                      {!a.read && (
                        <button
                          onClick={() => handleMarkAlertRead(a.id)}
                          className="shrink-0 p-1 rounded hover:bg-zinc-800 text-zinc-400 hover:text-white transition-colors"
                          title="Mark read"
                          aria-label="Mark alert as read"
                        >
                          <CheckCircle2 className="h-4 w-4" />
                        </button>
                      )}
                    </div>
                  ))}
                </div>
              )}
            </section>

            {/* Recent Activity Timeline */}
            <section aria-labelledby="activity-heading" className="bg-zinc-950 border border-zinc-900 rounded-2xl p-5 space-y-4">
              <div className="flex items-center justify-between">
                <div className="flex items-center gap-2">
                  <Clock className="h-4 w-4 text-zinc-400" aria-hidden="true" />
                  <h2 id="activity-heading" className="text-sm font-bold text-white tracking-tight">
                    Recent Price Activity
                  </h2>
                </div>
                <span className="text-xs text-zinc-500">Historical milestones</span>
              </div>

              {recentActivity.length === 0 ? (
                <p className="text-xs text-zinc-500 py-6 text-center">No recent price movements recorded.</p>
              ) : (
                <div className="space-y-3">
                  {recentActivity.slice(0, 5).map((act) => (
                    <div key={act.id} className="flex items-start gap-3 text-xs">
                      <div className="mt-1 h-2 w-2 rounded-full bg-indigo-500 shrink-0" aria-hidden="true" />
                      <div className="flex-1">
                        <span className="font-semibold text-white block">{act.productName}</span>
                        <p className="text-zinc-400">{act.title}</p>
                      </div>
                      {act.observedPrice && (
                        <span className="font-bold text-emerald-400 shrink-0">
                          {formatPrice(act.observedPrice)}
                        </span>
                      )}
                    </div>
                  ))}
                </div>
              )}
            </section>
          </div>
        </>
      )}
    </main>
  );
};
export default DashboardV2Page;
