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
  ExternalLink,
  Layers,
  Scale,
  X,
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
  const [selectedCategory, setSelectedCategory] = useState<string>('ALL');

  const currency: CurrencyCode = getSavedCurrency();

  // Load catalog products for selection when no productId is present
  const loadCatalog = useCallback(async (query?: string, category?: string) => {
    setCatalogLoading(true);
    try {
      const catParam = category && category !== 'ALL' ? category : undefined;
      const res = await apiService.getProducts(0, 24, catParam, undefined, query?.trim() || undefined);
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
      loadCatalog(searchTerm, selectedCategory);
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
  }, [productId, loadCatalog, selectedCategory]);

  const handleSearchSubmit = (e: React.FormEvent) => {
    e.preventDefault();
    loadCatalog(searchTerm, selectedCategory);
  };

  const getSignalBadge = (signal?: string) => {
    switch (signal) {
      case 'BUY_NOW':
        return {
          text: 'Buy Now',
          bg: 'bg-emerald-950/80 border-emerald-500/40 text-emerald-300',
          icon: <CheckCircle2 className="w-4 h-4 text-emerald-400" />,
          description: 'Current price is favorably near its recorded low with strong purchase timing.',
        };
      case 'GOOD_TIME':
        return {
          text: 'Good Time to Buy',
          bg: 'bg-teal-950/80 border-teal-500/40 text-teal-300',
          icon: <Sparkles className="w-4 h-4 text-teal-400" />,
          description: 'Price is below historical averages with favorable market positioning.',
        };
      case 'WAIT':
        return {
          text: 'Wait for Drop',
          bg: 'bg-amber-950/80 border-amber-500/40 text-amber-300',
          icon: <Clock className="w-4 h-4 text-amber-400" />,
          description: 'Price is above baseline averages; waiting for an upcoming drop is advised.',
        };
      case 'NEUTRAL':
        return {
          text: 'Neutral Timing',
          bg: 'bg-zinc-900 border-zinc-700 text-zinc-300',
          icon: <Minus className="w-4 h-4 text-zinc-400" />,
          description: 'Price is stable near historical averages without strong upward or downward pressure.',
        };
      default:
        return {
          text: 'Pending History',
          bg: 'bg-zinc-900 border-zinc-800 text-zinc-400',
          icon: <AlertCircle className="w-4 h-4 text-zinc-500" />,
          description: 'Additional historical price records are being gathered to establish baseline trends.',
        };
    }
  };

  const getDealBadge = (deal?: string) => {
    switch (deal) {
      case 'EXCELLENT_DEAL':
        return { label: 'Excellent Deal', color: 'text-emerald-400 border-emerald-500/30 bg-emerald-500/10' };
      case 'GOOD_DEAL':
        return { label: 'Good Deal', color: 'text-blue-400 border-blue-500/30 bg-blue-500/10' };
      case 'FAIR_PRICE':
        return { label: 'Fair Price', color: 'text-zinc-300 border-zinc-700 bg-zinc-800/40' };
      case 'ABOVE_AVERAGE':
        return { label: 'Above Average', color: 'text-amber-400 border-amber-500/30 bg-amber-500/10' };
      case 'HIGH_PRICE':
      case 'OVERPRICED':
        return { label: 'Overpriced', color: 'text-rose-400 border-rose-500/30 bg-rose-500/10' };
      default:
        return { label: 'Verified Price', color: 'text-zinc-400 border-zinc-800 bg-zinc-900/60' };
    }
  };

  const getTrendBadge = (trend?: string, pct?: number) => {
    if (trend === 'RISING') {
      return (
        <span className="flex items-center gap-1.5 text-rose-400 text-xs font-semibold px-2 py-0.5 rounded-full bg-rose-950/40 border border-rose-900/50">
          <TrendingUp className="w-3.5 h-3.5" /> Rising {pct !== undefined ? `(+${pct}%)` : ''}
        </span>
      );
    }
    if (trend === 'FALLING') {
      return (
        <span className="flex items-center gap-1.5 text-emerald-400 text-xs font-semibold px-2 py-0.5 rounded-full bg-emerald-950/40 border border-emerald-900/50">
          <TrendingDown className="w-3.5 h-3.5" /> Falling {pct !== undefined ? `(${pct}%)` : ''}
        </span>
      );
    }
    if (trend === 'STABLE') {
      return (
        <span className="flex items-center gap-1.5 text-zinc-300 text-xs font-semibold px-2 py-0.5 rounded-full bg-zinc-900 border border-zinc-700/60">
          <Minus className="w-3.5 h-3.5" /> Stable {pct !== undefined ? `(${pct}%)` : ''}
        </span>
      );
    }
    return <span className="text-zinc-500 text-xs font-mono">Stable</span>;
  };

  const CATEGORY_CHIPS = ['ALL', 'Electronics', 'Audio', 'Smartphones', 'Laptops', 'Wearables'];

  // Direct /analytics navigation without a productId: Product Selection Experience
  if (!productId) {
    return (
      <main className="space-y-8 max-w-7xl mx-auto px-4 py-6 text-left">
        {/* Header */}
        <div className="flex flex-col md:flex-row md:items-center justify-between gap-4 border-b border-zinc-800/80 pb-6">
          <div>
            <div className="inline-flex items-center gap-2 px-3 py-1 rounded-full bg-zinc-900 border border-zinc-800 text-xs text-zinc-400 font-mono mb-2">
              <Activity className="w-3.5 h-3.5 text-emerald-400" />
              <span>Catalog Price Intelligence</span>
            </div>
            <h1 className="text-2xl sm:text-3xl font-bold tracking-tight text-white">
              Price Analytics
            </h1>
            <p className="text-xs sm:text-sm text-zinc-400 mt-1 max-w-2xl">
              Search and select a verified catalog product to analyze its historical price movement, evaluate deal quality, and inspect purchase timing signals.
            </p>
          </div>
        </div>

        {/* Search & Category Filter */}
        <div className="space-y-3">
          <form onSubmit={handleSearchSubmit} className="flex gap-2.5 max-w-2xl">
            <div className="relative flex-1">
              <Search className="absolute left-3.5 top-1/2 -translate-y-1/2 w-4 h-4 text-zinc-500" />
              <input
                type="text"
                placeholder="Search catalog products to analyze (e.g. Sony WH-1000XM5, iPhone 15, iPad)..."
                value={searchTerm}
                onChange={(e) => setSearchTerm(e.target.value)}
                className="w-full pl-10 pr-10 py-2.5 bg-zinc-950 border border-zinc-800 hover:border-zinc-700 focus:border-emerald-500/50 rounded-xl text-xs text-zinc-100 placeholder-zinc-500 focus:outline-none transition-colors"
              />
              {searchTerm && (
                <button
                  type="button"
                  onClick={() => {
                    setSearchTerm('');
                    loadCatalog('', selectedCategory);
                  }}
                  className="absolute right-3 top-1/2 -translate-y-1/2 p-1 text-zinc-500 hover:text-zinc-300"
                >
                  <X className="w-3.5 h-3.5" />
                </button>
              )}
            </div>
            <button
              type="submit"
              className="px-5 py-2.5 bg-white hover:bg-zinc-200 text-black text-xs font-semibold rounded-xl transition-all cursor-pointer shrink-0 active:scale-[0.98]"
            >
              Search
            </button>
          </form>

          {/* Category Chips */}
          <div className="flex items-center gap-1.5 flex-wrap pt-1">
            {CATEGORY_CHIPS.map((cat) => {
              const isSelected = selectedCategory === cat;
              return (
                <button
                  key={cat}
                  type="button"
                  onClick={() => {
                    setSelectedCategory(cat);
                    loadCatalog(searchTerm, cat);
                  }}
                  className={`px-3 py-1 text-xs font-medium rounded-full transition-all cursor-pointer ${
                    isSelected
                      ? 'bg-zinc-200 text-black font-semibold shadow-sm'
                      : 'bg-zinc-900 text-zinc-400 hover:text-white border border-zinc-800 hover:border-zinc-700'
                  }`}
                >
                  {cat === 'ALL' ? 'All Categories' : cat}
                </button>
              );
            })}
          </div>
        </div>

        {/* Product Picker Grid */}
        <div className="space-y-4">
          <div className="flex items-center justify-between">
            <h2 className="text-xs font-bold uppercase tracking-wider text-zinc-400">
              Verified Products ({catalogProducts.length})
            </h2>
            {selectedCategory !== 'ALL' && (
              <span className="text-xs text-zinc-500 font-mono">Filtered by: {selectedCategory}</span>
            )}
          </div>

          {catalogLoading ? (
            <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-4 animate-pulse">
              {[1, 2, 3, 4, 5, 6, 7, 8].map((i) => (
                <div key={i} className="h-56 bg-zinc-950/80 border border-zinc-900 rounded-2xl p-4 space-y-3">
                  <div className="h-32 bg-zinc-900/60 rounded-xl" />
                  <div className="h-4 bg-zinc-900/60 rounded w-3/4" />
                  <div className="h-4 bg-zinc-900/60 rounded w-1/2" />
                </div>
              ))}
            </div>
          ) : catalogProducts.length === 0 ? (
            <div className="p-12 text-center bg-zinc-950/60 border border-zinc-900 rounded-2xl space-y-3">
              <ShoppingBag className="w-8 h-8 text-zinc-600 mx-auto" />
              <p className="text-sm text-zinc-400 font-medium">No verified catalog products found matching your search.</p>
              <button
                type="button"
                onClick={() => {
                  setSearchTerm('');
                  setSelectedCategory('ALL');
                  loadCatalog('', 'ALL');
                }}
                className="text-xs text-emerald-400 hover:underline cursor-pointer font-semibold"
              >
                Reset Search Filters
              </button>
            </div>
          ) : (
            <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-4">
              {catalogProducts.map((prod) => {
                const prodWithPrices = prod as ProductWithPrices;
                const lowest = prodWithPrices.lowestPrice;
                return (
                  <div
                    key={prod.id}
                    onClick={() => navigate(`/analytics/${prod.id}`)}
                    className="p-4 bg-zinc-950/80 border border-zinc-900 hover:border-zinc-700/80 rounded-2xl space-y-3.5 cursor-pointer group transition-all duration-200 flex flex-col justify-between hover:shadow-lg hover:-translate-y-0.5"
                  >
                    <div className="space-y-3">
                      <div className="h-36 w-full rounded-xl overflow-hidden bg-zinc-900/30 p-2 flex items-center justify-center border border-zinc-900">
                        <ProductImage
                          src={prod.imageUrl}
                          alt={prod.name}
                          className="h-full w-full object-contain group-hover:scale-105 transition-transform duration-300"
                          showFallbackText
                          fallbackText={prod.brand}
                        />
                      </div>
                      <div>
                        <div className="flex items-center justify-between text-[10px] text-zinc-500 font-bold uppercase tracking-wider">
                          <span>{prod.brand}</span>
                          {prod.category && <span className="text-zinc-600 font-medium">{prod.category}</span>}
                        </div>
                        <h3 className="text-xs font-bold text-zinc-200 group-hover:text-white line-clamp-2 mt-1 leading-snug">
                          {prod.name}
                        </h3>
                      </div>
                    </div>

                    <div className="flex items-center justify-between pt-2.5 border-t border-zinc-900">
                      <div>
                        <span className="text-[10px] text-zinc-500 block">Best Price</span>
                        <span className="text-xs font-mono font-bold text-white">
                          {lowest !== undefined && lowest !== null ? formatPrice(getDisplayPrice(lowest, currency), currency) : 'View Offers'}
                        </span>
                      </div>
                      <span className="text-xs text-zinc-400 group-hover:text-white font-semibold flex items-center gap-1 transition-colors">
                        Analyze <ArrowRight className="w-3.5 h-3.5 text-zinc-500 group-hover:text-white transition-colors" />
                      </span>
                    </div>
                  </div>
                );
              })}
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
            <span>Price Analytics</span>
          </div>
          <h1 className="text-2xl sm:text-3xl font-bold tracking-tight text-white">Product Price Intelligence</h1>
        </div>

        <div role="alert" className="p-8 text-center bg-zinc-950/90 border border-zinc-900 rounded-2xl space-y-4 max-w-xl mx-auto">
          <ShieldAlert className="w-10 h-10 text-amber-400 mx-auto" />
          <h2 className="text-base font-bold text-zinc-100">Product Unavailable</h2>
          <p className="text-xs text-zinc-400 leading-relaxed">
            The requested product (ID: <code className="text-zinc-300 font-mono bg-zinc-900 px-1.5 py-0.5 rounded">{productId}</code>) could not be located in our verified catalog or is no longer active.
          </p>
          <div className="flex items-center justify-center gap-3 pt-2">
            <button
              onClick={() => navigate('/analytics')}
              className="px-4 py-2 text-xs font-semibold bg-white hover:bg-zinc-200 text-black rounded-xl transition-all cursor-pointer active:scale-95"
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

  const prodWithPrices = product as ProductWithPrices | null;
  const currentBestPriceVal = analytics?.currentPrice ?? prodWithPrices?.lowestPrice;

  return (
    <main className="space-y-8 max-w-7xl mx-auto px-4 py-6 text-left">
      {/* Product Hero Header */}
      {product && (
        <section 
          aria-label="Selected Product Header"
          className="p-5 sm:p-6 bg-zinc-950 border border-zinc-800 rounded-2xl flex flex-col md:flex-row md:items-center justify-between gap-6 shadow-md"
        >
          <div className="flex items-start sm:items-center gap-4 min-w-0">
            <div className="h-20 w-20 sm:h-24 sm:w-24 rounded-xl overflow-hidden bg-zinc-900/50 p-2 border border-zinc-800 shrink-0 flex items-center justify-center">
              <ProductImage
                src={product.imageUrl}
                alt={product.name}
                className="h-full w-full object-contain"
                showFallbackText
                fallbackText={product.brand}
              />
            </div>
            <div className="space-y-1.5 min-w-0">
              <div className="flex items-center gap-2 flex-wrap">
                <span className="text-[10px] font-bold text-zinc-400 uppercase tracking-wider bg-zinc-900 px-2 py-0.5 rounded border border-zinc-800">
                  {product.brand}
                </span>
                {product.category && (
                  <span className="text-[10px] font-medium text-zinc-500 bg-zinc-900/60 px-2 py-0.5 rounded border border-zinc-800/60">
                    {product.category}
                  </span>
                )}
                {analytics?.dealQuality && (
                  <span className={`text-[10px] px-2 py-0.5 rounded border font-semibold ${getDealBadge(analytics.dealQuality).color}`}>
                    {getDealBadge(analytics.dealQuality).label}
                  </span>
                )}
              </div>
              <h1 className="text-lg sm:text-xl font-bold text-white tracking-tight leading-snug line-clamp-2">
                {product.name}
              </h1>
              <div className="flex items-baseline gap-2 pt-0.5">
                <span className="text-xs text-zinc-500">Current Verified Price:</span>
                <span className="text-xl sm:text-2xl font-extrabold font-mono text-white">
                  {currentBestPriceVal !== undefined && currentBestPriceVal !== null
                    ? formatPrice(getDisplayPrice(currentBestPriceVal, currency), currency)
                    : 'N/A'}
                </span>
              </div>
            </div>
          </div>

          {/* Quick Actions */}
          <div className="flex items-center gap-2.5 flex-wrap sm:flex-nowrap shrink-0 pt-2 md:pt-0 border-t md:border-t-0 border-zinc-900">
            <Link
              to={`/product/${product.id}`}
              className="flex-1 sm:flex-none px-4 py-2 text-xs font-semibold bg-white hover:bg-zinc-200 text-black rounded-xl transition-all text-center flex items-center justify-center gap-1.5 cursor-pointer shadow-sm active:scale-95"
            >
              <span>View Product & Offers</span>
              <ExternalLink className="w-3.5 h-3.5" />
            </Link>
            <Link
              to={`/compare?ids=${product.id}`}
              className="px-3.5 py-2 text-xs font-semibold bg-zinc-900 hover:bg-zinc-800 border border-zinc-800 text-zinc-300 rounded-xl transition-colors flex items-center justify-center gap-1.5"
              title="Compare with other products"
            >
              <Scale className="w-3.5 h-3.5 text-zinc-400" />
              <span className="hidden sm:inline">Compare</span>
            </Link>
            <Link
              to="/analytics"
              className="px-3.5 py-2 text-xs font-semibold bg-zinc-900 hover:bg-zinc-800 border border-zinc-800 text-zinc-400 hover:text-zinc-200 rounded-xl transition-colors flex items-center justify-center gap-1"
              title="Select a different product to analyze"
            >
              <span>Change</span>
            </Link>
          </div>
        </section>
      )}

      {/* Loading Skeleton */}
      {loading && (
        <div className="space-y-6 animate-pulse" role="status" aria-label="Loading analytics">
          <div className="h-36 bg-zinc-950 border border-zinc-900 rounded-2xl" />
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
          {/* Level 1: Decision Summary Card */}
          {(() => {
            const badge = getSignalBadge(analytics.purchaseSignal);
            return (
              <section
                aria-label="Purchase Timing & Decision Summary"
                className="bg-gradient-to-r from-zinc-950 via-zinc-900 to-zinc-950 border border-zinc-800/90 p-5 sm:p-6 rounded-2xl space-y-4 shadow-xl"
              >
                <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-3 flex-wrap">
                  <div className="flex items-center gap-2.5 flex-wrap">
                    <span
                      className={`px-3.5 py-1.5 rounded-full text-xs font-extrabold uppercase tracking-wider border flex items-center gap-1.5 shadow-sm ${badge.bg}`}
                    >
                      {badge.icon}
                      <span>{badge.text}</span>
                    </span>
                    {analytics.dealQuality && (
                      <span
                        className={`text-xs px-2.5 py-1 rounded-full border font-bold ${
                          getDealBadge(analytics.dealQuality).color
                        }`}
                      >
                        {getDealBadge(analytics.dealQuality).label}
                      </span>
                    )}
                    {analytics.trend && getTrendBadge(analytics.trend, analytics.trendPercentage)}
                  </div>

                  <div className="flex items-center gap-3 font-mono text-xs text-zinc-400">
                    <span>
                      Observations:{' '}
                      <strong className="text-zinc-200">{analytics.observationCount || 0}</strong>
                    </span>
                    {analytics.pricePositionScore !== undefined && (
                      <span className="flex items-center gap-1">
                        Position Score:{' '}
                        <strong className="text-emerald-400 font-bold">{analytics.pricePositionScore}</strong>/100
                      </span>
                    )}
                  </div>
                </div>

                {/* Authoritative Decision Reason */}
                {analytics.purchaseSignalReason ? (
                  <p className="text-sm text-zinc-200 leading-relaxed font-medium">
                    {analytics.purchaseSignalReason}
                  </p>
                ) : (
                  <p className="text-xs text-zinc-400 leading-relaxed">
                    {badge.description}
                  </p>
                )}

                {/* Supporting Evidence List */}
                {analytics.supportingEvidence && analytics.supportingEvidence.length > 0 && (
                  <div className="pt-3 border-t border-zinc-900 space-y-1.5">
                    <span className="text-[10px] font-bold text-zinc-400 uppercase tracking-wider block">
                      Verified Decision Evidence:
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

          {/* Level 2: Key Price Metrics */}
          <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-4">
            {/* Current Best Offer */}
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
                  Spread: {formatPrice(getDisplayPrice(analytics.priceRange, currency), currency)}
                </span>
              )}
            </div>
          </div>

          {/* Level 3: Core Analytical Visualization — Price History Chart */}
          <section aria-label="Interactive Price Chart" className="space-y-3">
            <div className="flex items-center justify-between">
              <h2 className="text-base font-bold text-zinc-200 flex items-center gap-2">
                <History className="w-4 h-4 text-emerald-400" />
                Price Trajectory & Trend History
              </h2>
            </div>
            <HistoricalPriceChart
              priceSeries={analytics.priceSeries}
              currentPrice={analytics.currentPrice}
              historicalAvg={analytics.historicalAvg}
              historicalMin={analytics.historicalMin}
              historicalMax={analytics.historicalMax}
              currency={currency}
            />
          </section>

          {/* Level 4: Verified Sellers & Current Offers Context */}
          {prodWithPrices && prodWithPrices.prices && prodWithPrices.prices.length > 0 && (
            <section aria-label="Current Seller Offers" className="space-y-3 pt-2">
              <div className="flex items-center justify-between">
                <h2 className="text-base font-bold text-zinc-200 flex items-center gap-2">
                  <Layers className="w-4 h-4 text-zinc-400" />
                  Verified Seller Offers ({prodWithPrices.prices.length})
                </h2>
                <Link
                  to={`/product/${product?.id}`}
                  className="text-xs text-emerald-400 hover:text-emerald-300 font-medium flex items-center gap-1"
                >
                  <span>Full Seller Comparison</span>
                  <ExternalLink className="w-3 h-3" />
                </Link>
              </div>

              <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-3">
                {prodWithPrices.prices.map((sp, sIdx) => {
                  const sPrice = sp.currentPrice;
                  const sOrig = sp.originalPrice;
                  const isLowest = currentBestPriceVal !== undefined && Math.abs(sPrice - currentBestPriceVal) < 0.01;

                  return (
                    <div
                      key={sp.id || sIdx}
                      className={`p-3.5 bg-zinc-950 border rounded-xl flex items-center justify-between gap-3 ${
                        isLowest ? 'border-emerald-500/40 bg-emerald-950/10' : 'border-zinc-900'
                      }`}
                    >
                      <div className="space-y-0.5 min-w-0">
                        <div className="flex items-center gap-1.5">
                          <span className="font-semibold text-xs text-white truncate">
                            {sp.seller?.name || `Seller #${sIdx + 1}`}
                          </span>
                          {isLowest && (
                            <span className="text-[9px] font-bold px-1.5 py-0.2 rounded bg-emerald-500/20 text-emerald-300 border border-emerald-500/30 shrink-0">
                              Best Offer
                            </span>
                          )}
                        </div>
                        {sp.discountPercentage !== undefined && sp.discountPercentage > 0 ? (
                          <span className="text-[10px] text-emerald-400 font-medium block">
                            {sp.discountPercentage.toFixed(0)}% savings recorded
                          </span>
                        ) : (
                          <span className="text-[10px] text-zinc-500 block">
                            Verified Seller Offer
                          </span>
                        )}
                      </div>

                      <div className="text-right shrink-0">
                        <span className="text-sm font-bold font-mono text-white block">
                          {formatPrice(getDisplayPrice(sPrice, currency), currency)}
                        </span>
                        {sOrig && sOrig > sPrice && (
                          <span className="text-[10px] text-zinc-500 line-through block font-mono">
                            {formatPrice(getDisplayPrice(sOrig, currency), currency)}
                          </span>
                        )}
                      </div>
                    </div>
                  );
                })}
              </div>
            </section>
          )}

          {/* Level 5: Historical Milestone Events */}
          {analytics.historicalEvents && analytics.historicalEvents.length > 0 && (
            <section aria-label="Price Milestone Events" className="space-y-3 pt-2">
              <h2 className="text-base font-bold text-zinc-200">Price Drop History & Milestones</h2>
              <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-3">
                {analytics.historicalEvents.map((evt, idx) => (
                  <div key={idx} className="bg-zinc-950 border border-zinc-900 rounded-xl p-3.5 space-y-1.5">
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
                    <div className="text-[11px] font-mono text-zinc-400 pt-1 border-t border-zinc-900">
                      Resulting Price: <strong className="text-white">{formatPrice(getDisplayPrice(evt.resultingPrice, currency), currency)}</strong>
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
