import React, { useEffect, useState, useCallback } from 'react';
import { useParams, useSearchParams, useNavigate, Link } from 'react-router-dom';
import { apiService } from '../services/api';
import type { ProductAnalytics, ProductWithPrices, Product } from '../types';
import { formatPrice, getSavedCurrency, getDisplayPrice, type CurrencyCode } from '../currency';
import { HistoricalPriceChart } from '../components/analytics/HistoricalPriceChart';
import { ProductImage } from '../components/common/ProductImage';
import {
  TrendingUp,
  TrendingDown,
  Minus,
  AlertCircle,
  CheckCircle2,
  Clock,
  ShieldAlert,
  Sparkles,
  ArrowDownRight,
  ArrowUpRight,
  Activity,
  History,
  Search,
  ArrowRight,
  ShoppingBag,
} from 'lucide-react';

export const AnalyticsPage: React.FC = () => {
  const { productId: pathProductId } = useParams<{ productId: string }>();
  const [searchParams] = useSearchParams();
  const navigate = useNavigate();

  const queryProductId = searchParams.get('productId');
  const productId = pathProductId || queryProductId || '';

  const [product, setProduct] = useState<ProductWithPrices | Product | null>(null);
  const [analytics, setAnalytics] = useState<ProductAnalytics | null>(null);
  const [loading, setLoading] = useState<boolean>(Boolean(productId));
  const [error, setError] = useState<string | null>(null);
  const [isNotFound, setIsNotFound] = useState<boolean>(false);

  // Catalog picker state for direct /analytics navigation
  const [catalogProducts, setCatalogProducts] = useState<Product[]>([]);
  const [catalogLoading, setCatalogLoading] = useState<boolean>(false);
  const [searchTerm, setSearchTerm] = useState<string>('');

  const currency: CurrencyCode = getSavedCurrency();

  // Load catalog products for selection when no productId is present
  const loadCatalog = useCallback(async (query?: string) => {
    setCatalogLoading(true);
    try {
      const res = await apiService.getProducts(0, 24, undefined, undefined, query?.trim() || undefined);
      setCatalogProducts(res.content || []);
    } catch {
      setCatalogProducts([]);
    } finally {
      setCatalogLoading(false);
    }
  }, []);

  useEffect(() => {
    if (!productId) {
      setProduct(null);
      setAnalytics(null);
      setError(null);
      setIsNotFound(false);
      setLoading(false);
      loadCatalog(searchTerm);
      return;
    }

    setLoading(true);
    setError(null);
    setIsNotFound(false);

    Promise.allSettled([
      apiService.getProduct(productId),
      apiService.getIntelligenceAnalytics(productId),
    ])
      .then(([prodResult, anaResult]) => {
        if (prodResult.status === 'fulfilled' && prodResult.value) {
          setProduct(prodResult.value);
        } else {
          setProduct(null);
          setIsNotFound(true);
          setError('Product not found in verified catalog.');
          return;
        }

        if (anaResult.status === 'fulfilled' && anaResult.value) {
          setAnalytics(anaResult.value);
        } else {
          setError('Unable to load real-time price analytics for this product.');
        }
      })
      .catch((err) => {
        setIsNotFound(true);
        setError(err?.message || 'Error communicating with intelligence server.');
      })
      .finally(() => setLoading(false));
  }, [productId, loadCatalog]);

  const handleSearchSubmit = (e: React.FormEvent) => {
    e.preventDefault();
    loadCatalog(searchTerm);
  };

  const getSignalBadge = (signal?: string) => {
    switch (signal) {
      case 'BUY_NOW':
        return {
          text: 'BUY NOW',
          bg: 'bg-emerald-950 border-emerald-700/60 text-emerald-300',
          icon: <CheckCircle2 className="w-4 h-4 text-emerald-400" />,
        };
      case 'GOOD_TIME':
        return {
          text: 'GOOD TIME TO BUY',
          bg: 'bg-teal-950 border-teal-700/60 text-teal-300',
          icon: <Sparkles className="w-4 h-4 text-teal-400" />,
        };
      case 'WAIT':
        return {
          text: 'WAIT FOR DROP',
          bg: 'bg-amber-950 border-amber-700/60 text-amber-300',
          icon: <Clock className="w-4 h-4 text-amber-400" />,
        };
      case 'NEUTRAL':
        return {
          text: 'NEUTRAL TIMING',
          bg: 'bg-zinc-900 border-zinc-700 text-zinc-300',
          icon: <Minus className="w-4 h-4 text-zinc-400" />,
        };
      default:
        return {
          text: 'INSUFFICIENT DATA',
          bg: 'bg-zinc-900 border-zinc-800 text-zinc-500',
          icon: <AlertCircle className="w-4 h-4 text-zinc-500" />,
        };
    }
  };

  const getDealBadge = (deal?: string) => {
    switch (deal) {
      case 'EXCELLENT_DEAL':
        return { label: 'EXCELLENT DEAL', color: 'text-emerald-400 border-emerald-800/40 bg-emerald-950/20' };
      case 'GOOD_DEAL':
        return { label: 'GOOD DEAL', color: 'text-teal-400 border-teal-800/40 bg-teal-950/20' };
      case 'FAIR_PRICE':
        return { label: 'FAIR PRICE', color: 'text-zinc-300 border-zinc-800 bg-zinc-900/40' };
      case 'ABOVE_AVERAGE':
        return { label: 'ABOVE AVERAGE', color: 'text-amber-400 border-amber-800/40 bg-amber-950/20' };
      case 'HIGH_PRICE':
        return { label: 'HIGH PRICE', color: 'text-rose-400 border-rose-800/40 bg-rose-950/20' };
      default:
        return { label: 'INSUFFICIENT DATA', color: 'text-zinc-500 border-zinc-800 bg-zinc-950' };
    }
  };

  const getTrendIcon = (trend?: string, pct?: number) => {
    if (trend === 'RISING') {
      return (
        <span className="flex items-center gap-1 text-rose-400 text-sm font-semibold">
          <TrendingUp className="w-4 h-4" /> RISING {pct !== undefined ? `(+${pct}%)` : ''}
        </span>
      );
    }
    if (trend === 'FALLING') {
      return (
        <span className="flex items-center gap-1 text-emerald-400 text-sm font-semibold">
          <TrendingDown className="w-4 h-4" /> FALLING {pct !== undefined ? `(${pct}%)` : ''}
        </span>
      );
    }
    if (trend === 'STABLE') {
      return (
        <span className="flex items-center gap-1 text-zinc-300 text-sm font-semibold">
          <Minus className="w-4 h-4" /> STABLE {pct !== undefined ? `(${pct}%)` : ''}
        </span>
      );
    }
    return <span className="text-zinc-500 text-xs font-mono">INSUFFICIENT DATA</span>;
  };

  // Direct /analytics navigation without a productId: Show Product Selection State
  if (!productId) {
    return (
      <main className="space-y-8 max-w-7xl mx-auto px-4 py-6 text-left">
        <div className="flex flex-col md:flex-row md:items-center justify-between gap-4 border-b border-zinc-900 pb-6">
          <div>
            <div className="inline-flex items-center gap-2 px-3 py-1 rounded-full bg-zinc-900 border border-zinc-800 text-xs text-zinc-400 font-mono mb-2">
              <Activity className="w-3.5 h-3.5 text-emerald-400" />
              <span>Price History & Deal Analytics</span>
            </div>
            <h1 className="text-3xl font-bold tracking-tight text-white">
              Price Analytics
            </h1>
            <p className="text-xs text-zinc-400 mt-1">
              Select any verified catalog product to inspect historical price trends, market stability, and purchase timing signals.
            </p>
          </div>
        </div>

        {/* Search and Filter Form */}
        <form onSubmit={handleSearchSubmit} className="flex gap-3 max-w-xl">
          <div className="relative flex-1">
            <Search className="absolute left-3.5 top-1/2 -translate-y-1/2 w-4 h-4 text-zinc-500" />
            <input
              type="text"
              placeholder="Search catalog products to analyze..."
              value={searchTerm}
              onChange={(e) => setSearchTerm(e.target.value)}
              className="w-full pl-10 pr-4 py-2.5 bg-zinc-950 border border-zinc-800 rounded-xl text-xs text-zinc-100 placeholder-zinc-500 focus:outline-none focus:border-emerald-500/50"
            />
          </div>
          <button
            type="submit"
            className="px-4 py-2.5 bg-emerald-600 hover:bg-emerald-500 text-white text-xs font-semibold rounded-xl transition-colors cursor-pointer"
          >
            Search
          </button>
        </form>

        {/* Product Picker Grid */}
        <div className="space-y-4">
          <h2 className="text-sm font-semibold text-zinc-300">Choose a Product to Inspect</h2>
          {catalogLoading ? (
            <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-4 animate-pulse">
              {[1, 2, 3, 4, 5, 6, 7, 8].map((i) => (
                <div key={i} className="h-48 bg-zinc-950 border border-zinc-900 rounded-2xl" />
              ))}
            </div>
          ) : catalogProducts.length === 0 ? (
            <div className="p-12 text-center bg-zinc-950 border border-zinc-900 rounded-2xl space-y-3">
              <ShoppingBag className="w-8 h-8 text-zinc-600 mx-auto" />
              <p className="text-sm text-zinc-400 font-medium">No catalog products found matching your query.</p>
              <button
                onClick={() => {
                  setSearchTerm('');
                  loadCatalog();
                }}
                className="text-xs text-emerald-400 hover:underline cursor-pointer"
              >
                Reset Search
              </button>
            </div>
          ) : (
            <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-4">
              {catalogProducts.map((prod) => (
                <div
                  key={prod.id}
                  onClick={() => navigate(`/analytics/${prod.id}`)}
                  className="p-4 bg-zinc-950/60 border border-zinc-900 hover:border-zinc-800 rounded-2xl space-y-3 cursor-pointer group transition-all duration-300 flex flex-col justify-between hover:shadow-[0_8px_30px_rgb(0,0,0,0.4)]"
                >
                  <div className="space-y-3">
                    <div className="h-36 w-full rounded-xl overflow-hidden bg-zinc-900/40 p-2 flex items-center justify-center">
                      <ProductImage
                        src={prod.imageUrl}
                        alt={prod.name}
                        className="h-full w-full object-contain group-hover:scale-105 transition-transform duration-300"
                        showFallbackText
                        fallbackText={prod.brand}
                      />
                    </div>
                    <div>
                      <span className="text-[10px] font-bold text-zinc-500 uppercase tracking-wider">{prod.brand}</span>
                      <h3 className="text-xs font-bold text-zinc-200 group-hover:text-white line-clamp-2 mt-0.5">
                        {prod.name}
                      </h3>
                    </div>
                  </div>
                  <div className="flex items-center justify-between pt-2 border-t border-zinc-900">
                    <span className="text-xs font-mono font-bold text-emerald-400">
                      {(prod as ProductWithPrices).lowestPrice !== undefined ? formatPrice(getDisplayPrice((prod as ProductWithPrices).lowestPrice!, currency), currency) : 'View Prices'}
                    </span>
                    <span className="text-xs text-zinc-400 group-hover:text-emerald-400 font-semibold flex items-center gap-1 transition-colors">
                      Analyze <ArrowRight className="w-3.5 h-3.5" />
                    </span>
                  </div>
                </div>
              ))}
            </div>
          )}
        </div>
      </main>
    );
  }

  // Unavailable / Not Found state
  if (!loading && (isNotFound || (!product && error))) {
    return (
      <main className="space-y-8 max-w-7xl mx-auto px-4 py-6 text-left">
        <div className="border-b border-zinc-900 pb-6">
          <div className="inline-flex items-center gap-2 px-3 py-1 rounded-full bg-zinc-900 border border-zinc-800 text-xs text-zinc-400 font-mono mb-2">
            <Activity className="w-3.5 h-3.5 text-emerald-400" />
            <span>Price Intelligence</span>
          </div>
          <h1 className="text-3xl font-bold tracking-tight text-white">Product Price Intelligence</h1>
        </div>

        <div role="alert" className="p-8 text-center bg-zinc-950 border border-zinc-900 rounded-2xl space-y-4 max-w-xl mx-auto">
          <ShieldAlert className="w-10 h-10 text-amber-400 mx-auto" />
          <h2 className="text-base font-bold text-zinc-100">Product Unavailable</h2>
          <p className="text-xs text-zinc-400 leading-relaxed">
            The requested product (ID: <code className="text-zinc-300 font-mono">{productId}</code>) could not be located in our verified catalog or is no longer active.
          </p>
          <div className="flex items-center justify-center gap-3 pt-2">
            <button
              onClick={() => navigate('/analytics')}
              className="px-4 py-2 text-xs font-semibold bg-emerald-600 hover:bg-emerald-500 text-white rounded-xl transition-colors"
            >
              Select Another Product
            </button>
            <Link
              to="/search"
              className="px-4 py-2 text-xs font-semibold bg-zinc-900 hover:bg-zinc-800 border border-zinc-800 text-zinc-200 rounded-xl transition-colors"
            >
              Browse Catalog
            </Link>
          </div>
        </div>
      </main>
    );
  }

  return (
    <main className="space-y-8 max-w-7xl mx-auto px-4 py-6 text-left">
      {/* Header */}
      <div className="flex flex-col md:flex-row md:items-center justify-between gap-4 border-b border-zinc-900 pb-6">
        <div>
          <h1 className="text-2xl font-bold tracking-tight text-white">
            {product ? product.name : 'Price Analytics'}
          </h1>
          <p className="text-xs text-zinc-400 mt-1">
            Historical price trends, deal quality ratings, and purchase timing recommendations.
          </p>
        </div>

        <div className="flex items-center gap-3">
          <Link
            to="/analytics"
            className="px-3.5 py-2 text-xs font-semibold bg-zinc-900 border border-zinc-800 text-zinc-300 rounded-lg hover:border-zinc-700 hover:bg-zinc-800 transition-colors"
          >
            Change Product
          </Link>
          {product && (
            <Link
              to={`/product/${product.id}`}
              className="px-4 py-2 text-xs font-semibold bg-emerald-950 border border-emerald-800/60 text-emerald-300 rounded-lg hover:bg-emerald-900 transition-colors"
            >
              &larr; Back to Product
            </Link>
          )}
        </div>
      </div>

      {/* Loading Skeleton */}
      {loading && (
        <div className="space-y-6 animate-pulse" role="status" aria-label="Loading analytics">
          <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-4">
            {[1, 2, 3, 4].map((i) => (
              <div key={i} className="h-28 bg-zinc-950 border border-zinc-900 rounded-xl" />
            ))}
          </div>
          <div className="h-80 bg-zinc-950 border border-zinc-900 rounded-xl" />
        </div>
      )}

      {/* Error State (non-fatal, e.g. analytics missing for valid product) */}
      {!loading && error && !isNotFound && (
        <div role="alert" className="p-6 text-center bg-amber-950/20 border border-amber-900/50 rounded-xl space-y-2">
          <AlertCircle className="w-6 h-6 text-amber-400 mx-auto" />
          <h3 className="text-sm font-bold text-amber-300">Analytics Data Pending</h3>
          <p className="text-xs text-zinc-400 max-w-md mx-auto">{error}</p>
        </div>
      )}

      {/* Main Content */}
      {!loading && analytics && (
        <div className="space-y-8">
          {/* Purchase Signal & Recommendation Banner */}
          {(() => {
            const badge = getSignalBadge(analytics.purchaseSignal);
            return (
              <section
                aria-label="Purchase Signal Summary"
                className="bg-gradient-to-r from-zinc-950 via-zinc-900 to-zinc-950 border border-zinc-800 p-6 rounded-2xl space-y-4 shadow-xl"
              >
                <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-3 flex-wrap">
                  <div className="flex items-center gap-2.5">
                    <span
                      className={`px-3 py-1 rounded-full text-xs font-extrabold uppercase tracking-wider border flex items-center gap-1.5 shadow-sm ${badge.bg}`}
                    >
                      {badge.icon}
                      <span>{badge.text}</span>
                    </span>
                    {analytics.dealQuality && (
                      <span
                        className={`text-xs px-2.5 py-0.5 rounded-full border font-bold ${
                          getDealBadge(analytics.dealQuality).color
                        }`}
                      >
                        {getDealBadge(analytics.dealQuality).label}
                      </span>
                    )}
                  </div>

                  <div className="flex items-center gap-3 font-mono text-xs text-zinc-400">
                    <span>
                      Observations:{' '}
                      <strong className="text-zinc-200">{analytics.observationCount || 0}</strong>
                    </span>
                    {analytics.pricePositionScore !== undefined && (
                      <span>
                        Position Score:{' '}
                        <strong className="text-emerald-400">{analytics.pricePositionScore}</strong>/100
                      </span>
                    )}
                  </div>
                </div>

                {analytics.purchaseSignalReason && (
                  <p className="text-sm text-zinc-200 leading-relaxed font-medium">
                    {analytics.purchaseSignalReason}
                  </p>
                )}

                {/* Supporting Evidence List */}
                {analytics.supportingEvidence && analytics.supportingEvidence.length > 0 && (
                  <div className="pt-2 border-t border-zinc-900 space-y-1.5">
                    <span className="text-[11px] font-bold text-zinc-400 uppercase tracking-wider block">
                      Price Intelligence Evidence:
                    </span>
                    <ul className="space-y-1 text-xs text-zinc-300">
                      {analytics.supportingEvidence.map((ev, idx) => (
                        <li key={idx} className="flex items-start gap-2">
                          <span className="text-emerald-400 font-bold shrink-0">✓</span>
                          <span>{ev}</span>
                        </li>
                      ))}
                    </ul>
                  </div>
                )}
              </section>
            );
          })()}

          {/* Key Metric Cards */}
          <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-4">
            {/* Current Price */}
            <div className="bg-zinc-950 border border-zinc-900 rounded-xl p-5 space-y-1">
              <span className="text-xs text-zinc-500 font-medium">Current Best Offer</span>
              <p className="text-2xl font-bold font-mono text-white">
                {analytics.currentPrice ? formatPrice(getDisplayPrice(analytics.currentPrice, currency), currency) : 'N/A'}
              </p>
              <span className="text-[10px] text-zinc-500">Live lowest active offer</span>
            </div>

            {/* Historical Low */}
            <div className="bg-zinc-950 border border-zinc-900 rounded-xl p-5 space-y-1">
              <span className="text-xs text-zinc-500 font-medium">All-Time Recorded Low</span>
              <p className="text-2xl font-bold font-mono text-emerald-400">
                {analytics.historicalMin ? formatPrice(getDisplayPrice(analytics.historicalMin, currency), currency) : 'N/A'}
              </p>
              {analytics.historicalLowDistance !== undefined && analytics.historicalMin ? (
                <span className="text-[10px] text-zinc-400">
                  {analytics.historicalLowDistance === 0
                    ? 'Currently at all-time low'
                    : `+${formatPrice(getDisplayPrice(analytics.historicalLowDistance, currency), currency)} above low`}
                </span>
              ) : (
                <span className="text-[10px] text-zinc-600">Pending history</span>
              )}
            </div>

            {/* Historical Average */}
            <div className="bg-zinc-950 border border-zinc-900 rounded-xl p-5 space-y-1">
              <span className="text-xs text-zinc-500 font-medium">Historical Average</span>
              <p className="text-2xl font-bold font-mono text-indigo-400">
                {analytics.historicalAvg ? formatPrice(getDisplayPrice(analytics.historicalAvg, currency), currency) : 'N/A'}
              </p>
              {analytics.historicalMedian && (
                <span className="text-[10px] text-zinc-400">
                  Median: {formatPrice(getDisplayPrice(analytics.historicalMedian, currency), currency)}
                </span>
              )}
            </div>

            {/* Historical High */}
            <div className="bg-zinc-950 border border-zinc-900 rounded-xl p-5 space-y-1">
              <span className="text-xs text-zinc-500 font-medium">All-Time Peak Price</span>
              <p className="text-2xl font-bold font-mono text-amber-400">
                {analytics.historicalMax ? formatPrice(getDisplayPrice(analytics.historicalMax, currency), currency) : 'N/A'}
              </p>
              {analytics.priceRange !== undefined && (
                <span className="text-[10px] text-zinc-500">
                  Range spread: {formatPrice(getDisplayPrice(analytics.priceRange, currency), currency)}
                </span>
              )}
            </div>
          </div>

          {/* Dynamics Row: Trend & Volatility */}
          <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
            <div className="bg-zinc-950 border border-zinc-900 rounded-xl p-5 space-y-2">
              <div className="flex items-center justify-between">
                <span className="text-xs text-zinc-400 font-medium uppercase tracking-wider">Price Trend</span>
                {getTrendIcon(analytics.trend, analytics.trendPercentage)}
              </div>
              <p className="text-xs text-zinc-400 leading-relaxed">
                Calculated by comparing recent prices against historical baselines.
              </p>
            </div>

            <div className="bg-zinc-950 border border-zinc-900 rounded-xl p-5 space-y-2">
              <div className="flex items-center justify-between">
                <span className="text-xs text-zinc-400 font-medium uppercase tracking-wider">Market Volatility</span>
                <span className="text-xs font-mono font-bold text-zinc-200">
                  {analytics.volatility || 'INSUFFICIENT_DATA'}
                  {analytics.volatilityValue !== undefined && analytics.volatilityValue !== null
                    ? ` (CV: ${(analytics.volatilityValue * 100).toFixed(1)}%)`
                    : ''}
                </span>
              </div>
              <p className="text-xs text-zinc-400 leading-relaxed">
                Measures price stability across recorded seller listings over time.
              </p>
            </div>
          </div>

          {/* Historical Price Chart */}
          <section aria-label="Interactive Price Chart" className="space-y-3">
            <h2 className="text-base font-bold text-zinc-200 flex items-center gap-2">
              <History className="w-4 h-4 text-emerald-400" />
              Price History
            </h2>
            <HistoricalPriceChart
              priceSeries={analytics.priceSeries}
              currentPrice={analytics.currentPrice}
              historicalAvg={analytics.historicalAvg}
              historicalMin={analytics.historicalMin}
              historicalMax={analytics.historicalMax}
              currency={currency}
            />
          </section>

          {/* Historical Milestone Events */}
          {analytics.historicalEvents && analytics.historicalEvents.length > 0 && (
            <section aria-label="Price Milestone Events" className="space-y-4 pt-4 border-t border-zinc-900">
              <h2 className="text-base font-bold text-zinc-200">Price Drop History & Milestones</h2>
              <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-3">
                {analytics.historicalEvents.map((evt, idx) => (
                  <div key={idx} className="bg-zinc-950 border border-zinc-900 rounded-lg p-3.5 space-y-1">
                    <div className="flex items-center justify-between text-xs font-mono">
                      <span className="text-emerald-400 font-semibold flex items-center gap-1">
                        {evt.percentageChange < 0 ? (
                          <ArrowDownRight className="w-3.5 h-3.5 text-emerald-400" />
                        ) : (
                          <ArrowUpRight className="w-3.5 h-3.5 text-rose-400" />
                        )}
                        {evt.eventType.replace(/_/g, ' ')}
                      </span>
                      <span className="text-zinc-500 text-[10px]">
                        {new Date(evt.occurredAt).toLocaleDateString()}
                      </span>
                    </div>
                    <p className="text-xs text-zinc-300">{evt.description}</p>
                    <div className="text-[11px] font-mono text-zinc-500 pt-1">
                      Resulting: {formatPrice(getDisplayPrice(evt.resultingPrice, currency), currency)}
                    </div>
                  </div>
                ))}
              </div>
            </section>
          )}
        </div>
      )}
    </main>
  );
};

export default AnalyticsPage;
