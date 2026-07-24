import React, { useEffect, useState } from 'react';
import { useNavigate, Link } from 'react-router-dom';
import { apiService } from '../services/api';
import { useAuth } from '../context/AuthContext';
import type { UserInteractionEvent, ProductWithPrices, Watchlist, SavedProduct } from '../types';
import { formatPrice, getDisplayPrice, getSavedCurrency } from '../currency';
import {
  Eye,
  Heart,
  Bell,
  Search,
  ExternalLink,
  Sparkles,
  Activity,
  History,
  TrendingUp,
  Inbox,
  ArrowRight,
  User as UserIcon,
  Shield,
  Layers,
  ArrowUpRight,
  TrendingDown,
  Tag,
  Store,
  Grid,
  CheckCircle2,
  AlertCircle
} from 'lucide-react';
import { motion, AnimatePresence } from 'framer-motion';

interface DashboardData {
  firstName: string;
  lastName: string;
  email: string;
  role: string;
  savedCount: number;
  watchlistCount: number;
  totalActivitiesCount: number;
  activePriceAlertsCount: number;
  recommendations: ProductWithPrices[];
  recentlyViewed: ProductWithPrices[];
  priceDropAlerts: Watchlist[];
  trendingProducts: ProductWithPrices[];
  watchlists: Watchlist[];
  savedProducts: SavedProduct[];
  recentActivity: UserInteractionEvent[];
  recentSearches: string[];
  mostClickedSellers: { name: string; count: number }[];
}

export const DashboardPage: React.FC = () => {
  const navigate = useNavigate();
  const { isAuthenticated } = useAuth();
  const currency = getSavedCurrency();
  const [data, setData] = useState<DashboardData | null>(null);
  const [loading, setLoading] = useState(true);
  
  // Timeline pagination states
  const [timelineEvents, setTimelineEvents] = useState<UserInteractionEvent[]>([]);
  const [loadingMore, setLoadingMore] = useState(false);
  const [timelinePage, setTimelinePage] = useState(0);
  const [timelineTotalPages, setTimelineTotalPages] = useState(1);
  const timelinePageSize = 8;

  // Tabs state: 'overview' | 'recommendations' | 'alerts' | 'trending'
  const [activeTab, setActiveTab] = useState<'overview' | 'recommendations' | 'alerts' | 'trending'>('overview');

  useEffect(() => {
    if (!isAuthenticated) {
      navigate('/login?from=/dashboard');
      return;
    }

    apiService.getDashboard()
      .then((res) => {
        setData(res);
        setTimelineEvents(res.recentActivity || []);
        // Set fallback total pages for timeline
        setTimelineTotalPages(Math.ceil((res.totalActivitiesCount || 20) / timelinePageSize));
      })
      .catch((err) => {
        console.error('Error fetching dashboard data:', err);
      })
      .finally(() => {
        setLoading(false);
      });
  }, [isAuthenticated, navigate]);

  const loadMoreEvents = async () => {
    if (!data || timelinePage + 1 >= timelineTotalPages || loadingMore) return;

    setLoadingMore(true);
    try {
      const nextPage = timelinePage + 1;
      const res = await apiService.getMyEvents(nextPage, timelinePageSize);
      setTimelineEvents((prev) => [...prev, ...res.content]);
      setTimelinePage(nextPage);
      setTimelineTotalPages(res.totalPages);
    } catch (err) {
      console.error('Failed to load more events:', err);
    } finally {
      setLoadingMore(false);
    }
  };

  const getLowestPrice = (product: ProductWithPrices) => {
    if (product.lowestPrice) return product.lowestPrice;
    if (!product.prices || product.prices.length === 0) return null;
    return Math.min(...product.prices.map(p => p.currentPrice));
  };

  const getHighestPrice = (product: ProductWithPrices) => {
    if (product.highestPrice) return product.highestPrice;
    if (!product.prices || product.prices.length === 0) return null;
    return Math.max(...product.prices.map(p => p.currentPrice));
  };

  const getMaxDiscount = (product: ProductWithPrices) => {
    if (!product.prices || product.prices.length === 0) return 0;
    return Math.max(...product.prices.map(p => p.discountPercentage));
  };

  const formatEventTime = (timestamp: string) => {
    const date = new Date(timestamp);
    const now = new Date();
    const diffMs = now.getTime() - date.getTime();
    const diffMins = Math.floor(diffMs / (60 * 1000));
    const diffHours = Math.floor(diffMs / (3600 * 1000));
    const diffDays = Math.floor(diffMs / (24 * 3600 * 1000));

    if (diffMins < 1) return 'Just now';
    if (diffMins < 60) return `${diffMins}m ago`;
    if (diffHours < 24) return `${diffHours}h ago`;
    if (diffDays === 1) return 'Yesterday';
    if (diffDays < 7) return `${diffDays} days ago`;
    
    return date.toLocaleDateString(undefined, {
      month: 'short',
      day: 'numeric'
    });
  };

  const getEventStyle = (type: string) => {
    switch (type) {
      case 'PRODUCT_VIEW':
        return {
          icon: <Eye className="h-4 w-4" />,
          bgColor: 'bg-blue-500/10 text-blue-400 border-blue-500/20',
          label: 'Viewed Product'
        };
      case 'PRODUCT_SAVE':
        return {
          icon: <Heart className="h-4 w-4" />,
          bgColor: 'bg-rose-500/10 text-rose-400 border-rose-500/20',
          label: 'Saved Product'
        };
      case 'PRODUCT_UNSAVE':
        return {
          icon: <Heart className="h-4 w-4" />,
          bgColor: 'bg-zinc-800 text-zinc-500 border-zinc-800',
          label: 'Unsaved Product'
        };
      case 'WATCHLIST_CREATE':
        return {
          icon: <Bell className="h-4 w-4" />,
          bgColor: 'bg-emerald-500/10 text-emerald-400 border-emerald-500/20',
          label: 'Created Price Alert'
        };
      case 'WATCHLIST_DELETE':
        return {
          icon: <Bell className="h-4 w-4" />,
          bgColor: 'bg-zinc-800 text-zinc-500 border-zinc-800',
          label: 'Removed Price Alert'
        };
      case 'PRICE_HISTORY_VIEW':
        return {
          icon: <History className="h-4 w-4" />,
          bgColor: 'bg-amber-500/10 text-amber-400 border-amber-500/20',
          label: 'Viewed Price History'
        };
      case 'SELLER_CLICK':
        return {
          icon: <ExternalLink className="h-4 w-4" />,
          bgColor: 'bg-cyan-500/10 text-cyan-400 border-cyan-500/20',
          label: 'Visited Seller'
        };
      case 'SEARCH':
        return {
          icon: <Search className="h-4 w-4" />,
          bgColor: 'bg-purple-500/10 text-purple-400 border-purple-500/20',
          label: 'Searched Catalog'
        };
      case 'TRENDING_VIEW':
        return {
          icon: <Sparkles className="h-4 w-4" />,
          bgColor: 'bg-indigo-500/10 text-indigo-400 border-indigo-500/20',
          label: 'Viewed Trending'
        };
      default:
        return {
          icon: <Activity className="h-4 w-4" />,
          bgColor: 'bg-zinc-850 text-zinc-350 border-zinc-800',
          label: 'Activity Logged'
        };
    }
  };

  const renderEventDetails = (event: UserInteractionEvent) => {
    const { interactionType, metadata, productId, productName, sellerName } = event;

    switch (interactionType) {
      case 'PRODUCT_VIEW':
        return (
          <span className="text-zinc-300 text-xs">
            You viewed{' '}
            {productId ? (
              <Link to={`/product/${productId}`} className="text-white hover:text-blue-400 font-semibold hover:underline">
                {productName || 'product'}
              </Link>
            ) : (
              <span className="font-semibold text-white">{productName || 'product'}</span>
            )}
          </span>
        );
      case 'PRODUCT_SAVE':
        return (
          <span className="text-zinc-300 text-xs">
            Saved{' '}
            {productId ? (
              <Link to={`/product/${productId}`} className="text-white hover:text-rose-400 font-semibold hover:underline">
                {productName || 'product'}
              </Link>
            ) : (
              <span className="font-semibold text-white">{productName || 'product'}</span>
            )}
          </span>
        );
      case 'PRODUCT_UNSAVE':
        return (
          <span className="text-zinc-400 text-xs">
            Unsaved <span className="font-semibold text-zinc-300">{productName || 'product'}</span>.
          </span>
        );
      case 'WATCHLIST_CREATE':
        return (
          <span className="text-zinc-300 text-xs">
            Watched{' '}
            <Link to={`/product/${productId}`} className="text-white hover:text-emerald-400 font-semibold hover:underline">
              {productName || 'product'}
            </Link>{' '}
            at target <span className="text-emerald-400 font-mono font-bold">{formatPrice(getDisplayPrice((metadata?.targetPrice as number) || 0, currency), currency)}</span>
          </span>
        );
      case 'WATCHLIST_DELETE':
        return (
          <span className="text-zinc-400 text-xs">
            Removed watchlist for <span className="font-semibold text-zinc-300">{productName || 'product'}</span>.
          </span>
        );
      case 'PRICE_HISTORY_VIEW':
        return (
          <span className="text-zinc-300 text-xs">
            Analyzed prices for{' '}
            <Link to={`/product/${productId}`} className="text-white hover:text-amber-400 font-semibold hover:underline">
              {productName || 'product'}
            </Link>
          </span>
        );
      case 'SELLER_CLICK':
        return (
          <span className="text-zinc-300 text-xs">
            Redirected to <span className="text-white font-semibold">{sellerName || (metadata?.seller as string) || 'seller'}</span> for{' '}
            <Link to={`/product/${productId}`} className="text-white hover:text-cyan-400 font-semibold hover:underline">
              {productName || 'product'}
            </Link>
          </span>
        );
      case 'SEARCH':
        return (
          <span className="text-zinc-300 text-xs">
            Searched for <span className="text-zinc-100 font-semibold font-mono">"{(metadata?.keyword as string) || '*'}"</span>
          </span>
        );
      default:
        return <span className="text-zinc-400 text-xs">Logged interaction</span>;
    }
  };

  if (loading) {
    return (
      <div className="max-w-7xl mx-auto w-full py-12 px-4 flex flex-col gap-8">
        <div className="flex items-center gap-4 animate-pulse">
          <div className="h-14 w-14 rounded-2xl bg-zinc-900" />
          <div className="flex flex-col gap-2 flex-grow">
            <div className="h-6 w-48 bg-zinc-900 rounded-lg" />
            <div className="h-3 w-64 bg-zinc-900 rounded" />
          </div>
        </div>
        
        <div className="grid grid-cols-2 md:grid-cols-4 gap-6 animate-pulse">
          {[1, 2, 3, 4].map((i) => (
            <div key={i} className="h-28 rounded-2xl bg-zinc-950 border border-zinc-900" />
          ))}
        </div>

        <div className="grid grid-cols-1 lg:grid-cols-3 gap-8">
          <div className="lg:col-span-2 h-96 bg-zinc-950/20 border border-zinc-900 rounded-2xl animate-pulse" />
          <div className="h-96 bg-zinc-950/20 border border-zinc-900 rounded-2xl animate-pulse" />
        </div>
      </div>
    );
  }

  if (!data) {
    return (
      <div className="max-w-md mx-auto text-center py-20 px-4">
        <AlertCircle className="h-12 w-12 text-rose-500 mx-auto mb-4" />
        <h2 className="text-xl font-bold text-white">Failed to load dashboard</h2>
        <p className="text-zinc-400 mt-2">There was an issue compiling dashboard details.</p>
        <button onClick={() => window.location.reload()} className="mt-4 px-4 py-2 bg-zinc-900 text-white rounded-lg border border-zinc-800">
          Try Again
        </button>
      </div>
    );
  }

  return (
    <div className="max-w-7xl mx-auto w-full py-8 px-4 flex flex-col gap-8 select-none text-left">
      {/* Welcome Panel */}
      <div className="flex flex-col md:flex-row md:items-center justify-between gap-6">
        <div className="flex items-center gap-4">
          <div className="h-14 w-14 rounded-2xl bg-gradient-to-br from-white/10 to-zinc-950 border border-zinc-800 flex items-center justify-center text-white shrink-0 shadow-lg">
            <UserIcon className="h-6 w-6" />
          </div>
          <div>
            <h1 className="text-3xl font-extrabold tracking-tight text-white flex items-center gap-2.5">
              <span>Personalized Dashboard</span>
            </h1>
            <p className="text-xs text-zinc-500 mt-1">
              Welcome back, <span className="text-zinc-200 font-bold">{data.firstName} {data.lastName}</span> ({data.email})
            </p>
          </div>
        </div>
        <div className="flex items-center gap-2">
          {data.role === 'ADMIN' && (
            <span className="inline-flex items-center gap-1.5 px-3 py-1 rounded-full bg-amber-500/10 border border-amber-500/20 text-xs text-amber-400 font-mono font-bold uppercase tracking-wider">
              <Shield className="h-3.5 w-3.5" />
              Admin
            </span>
          )}
          <span className="inline-flex items-center gap-1.5 px-3 py-1 rounded-full bg-blue-500/10 border border-blue-500/20 text-xs text-blue-400 font-mono font-bold uppercase tracking-wider">
            Phase 2 Active
          </span>
        </div>
      </div>

      {/* Stats Cards */}
      <div className="grid grid-cols-2 lg:grid-cols-4 gap-6">
        <motion.div
          whileHover={{ y: -4, borderColor: 'rgba(255,255,255,0.08)' }}
          onClick={() => navigate('/saved-products')}
          className="rounded-2xl bg-zinc-950/20 border border-zinc-900 p-5 cursor-pointer flex justify-between items-center group relative overflow-hidden transition-colors hover:bg-zinc-950/40"
        >
          <div className="flex flex-col gap-1">
            <span className="text-zinc-500 text-xs font-bold uppercase tracking-wider">Saved Products</span>
            <span className="text-3xl font-extrabold text-white">{data.savedCount}</span>
          </div>
          <div className="h-10 w-10 rounded-xl bg-rose-500/5 border border-rose-500/10 text-rose-450 flex items-center justify-center group-hover:bg-rose-500/10 transition-colors shrink-0">
            <Heart className="h-5 w-5 fill-rose-500/20" />
          </div>
        </motion.div>

        <motion.div
          whileHover={{ y: -4, borderColor: 'rgba(255,255,255,0.08)' }}
          onClick={() => navigate('/watchlist')}
          className="rounded-2xl bg-zinc-950/20 border border-zinc-900 p-5 cursor-pointer flex justify-between items-center group relative overflow-hidden transition-colors hover:bg-zinc-950/40"
        >
          <div className="flex flex-col gap-1">
            <span className="text-zinc-500 text-xs font-bold uppercase tracking-wider">Price Alerts</span>
            <span className="text-3xl font-extrabold text-white">{data.watchlistCount}</span>
          </div>
          <div className="h-10 w-10 rounded-xl bg-emerald-500/5 border border-emerald-500/10 text-emerald-450 flex items-center justify-center group-hover:bg-emerald-500/10 transition-colors shrink-0">
            <Bell className="h-5 w-5" />
          </div>
        </motion.div>

        <motion.div
          whileHover={{ y: -4, borderColor: 'rgba(255,255,255,0.08)' }}
          className="rounded-2xl bg-zinc-950/20 border border-zinc-900 p-5 flex justify-between items-center group relative overflow-hidden transition-colors hover:bg-zinc-950/40"
        >
          <div className="flex flex-col gap-1">
            <span className="text-zinc-500 text-xs font-bold uppercase tracking-wider">Active Deals</span>
            <span className="text-3xl font-extrabold text-emerald-450">{data.activePriceAlertsCount}</span>
          </div>
          <div className="h-10 w-10 rounded-xl bg-amber-500/5 border border-amber-500/10 text-amber-450 flex items-center justify-center shrink-0">
            <Tag className="h-5 w-5" />
          </div>
        </motion.div>

        <motion.div
          whileHover={{ y: -4, borderColor: 'rgba(255,255,255,0.08)' }}
          className="rounded-2xl bg-zinc-950/20 border border-zinc-900 p-5 flex justify-between items-center group relative overflow-hidden transition-colors hover:bg-zinc-950/40"
        >
          <div className="flex flex-col gap-1">
            <span className="text-zinc-500 text-xs font-bold uppercase tracking-wider">Logged Activities</span>
            <span className="text-3xl font-extrabold text-white">{data.totalActivitiesCount}</span>
          </div>
          <div className="h-10 w-10 rounded-xl bg-blue-500/5 border border-blue-500/10 text-blue-450 flex items-center justify-center shrink-0">
            <Activity className="h-5 w-5" />
          </div>
        </motion.div>
      </div>

      {/* Tabs Selector */}
      <div className="flex border-b border-zinc-900 gap-6 overflow-x-auto pb-px">
        {[
          { id: 'overview', label: 'Overview', icon: <Grid className="h-4 w-4" /> },
          { id: 'recommendations', label: 'Personalized Choices', icon: <Sparkles className="h-4 w-4" /> },
          { id: 'alerts', label: 'Triggered Deals', icon: <Tag className="h-4 w-4" /> },
          { id: 'trending', label: 'Trending & Analytics', icon: <TrendingUp className="h-4 w-4" /> }
        ].map((tab) => (
          <button
            key={tab.id}
            onClick={() => setActiveTab(tab.id as 'overview' | 'recommendations' | 'alerts' | 'trending')}
            className={`flex items-center gap-2 py-3 px-1 border-b-2 text-sm font-semibold transition-all relative whitespace-nowrap cursor-pointer ${
              activeTab === tab.id
                ? 'border-white text-white font-bold'
                : 'border-transparent text-zinc-400 hover:text-zinc-250'
            }`}
          >
            {tab.icon}
            <span>{tab.label}</span>
            {tab.id === 'alerts' && data.activePriceAlertsCount > 0 && (
              <span className="ml-1 px-1.5 py-0.2 rounded-full bg-emerald-500/20 border border-emerald-500/30 text-[10px] text-emerald-400 font-mono font-bold">
                {data.activePriceAlertsCount}
              </span>
            )}
          </button>
        ))}
      </div>

      {/* Dynamic Tab Views */}
      <div className="min-h-96">
        <AnimatePresence mode="wait">
          {activeTab === 'overview' && (
            <motion.div
              key="overview"
              initial={{ opacity: 0, y: 10 }}
              animate={{ opacity: 1, y: 0 }}
              exit={{ opacity: 0, y: -10 }}
              className="grid grid-cols-1 lg:grid-cols-3 gap-8"
            >
              {/* Left column (2/3 width) */}
              <div className="lg:col-span-2 flex flex-col gap-8">
                {/* Triggered Deals Alert Box */}
                {data.priceDropAlerts.length > 0 && (
                  <div className="border border-emerald-500/20 rounded-2xl bg-emerald-500/5 p-6 flex flex-col gap-4 relative overflow-hidden">
                    <div className="absolute right-0 top-0 h-40 w-40 bg-emerald-500/10 rounded-full blur-3xl -z-10" />
                    <div className="flex items-center justify-between">
                      <div className="flex items-center gap-2">
                        <CheckCircle2 className="h-5 w-5 text-emerald-400" />
                        <h2 className="text-lg font-bold text-white">Deal Alerts Triggered</h2>
                      </div>
                      <span className="text-xs text-emerald-400 font-mono font-bold bg-emerald-500/10 border border-emerald-500/20 px-2 py-0.5 rounded">
                        Target Price Hit!
                      </span>
                    </div>
                    
                    <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
                      {data.priceDropAlerts.slice(0, 4).map((alert) => (
                        <div key={alert.id} className="bg-zinc-950/60 border border-zinc-900 rounded-xl p-3 flex gap-3 items-center">
                          <img src={alert.imageUrl} alt={alert.productName} className="h-12 w-12 rounded bg-zinc-900 object-cover border border-zinc-900" />
                          <div className="flex flex-col min-w-0">
                            <Link to={`/product/${alert.productId}`} className="text-white hover:text-emerald-400 text-xs font-bold truncate block">
                              {alert.productName}
                            </Link>
                            <span className="text-[10px] text-zinc-500 font-medium truncate">{alert.brand}</span>
                            <div className="flex items-center gap-2 mt-1">
                              <span className="text-emerald-400 text-xs font-mono font-bold">{formatPrice(getDisplayPrice(alert.currentBestPrice, currency), currency)}</span>
                              <span className="text-zinc-500 text-[10px] line-through font-mono">{formatPrice(getDisplayPrice(alert.targetPrice, currency), currency)}</span>
                            </div>
                          </div>
                        </div>
                      ))}
                    </div>
                  </div>
                )}

                {/* Recommendations Grid */}
                <div className="flex flex-col gap-4">
                  <div className="flex items-center justify-between">
                    <h2 className="text-lg font-bold text-white flex items-center gap-2">
                      <Sparkles className="h-5 w-5 text-zinc-400" />
                      Recommended for You
                    </h2>
                    <button onClick={() => setActiveTab('recommendations')} className="text-xs text-zinc-400 hover:text-white flex items-center gap-1 font-bold">
                      View All <ArrowRight className="h-3 w-3" />
                    </button>
                  </div>
                  
                  {data.recommendations.length === 0 ? (
                    <div className="py-12 border border-dashed border-zinc-900 rounded-2xl text-center">
                      <Inbox className="h-8 w-8 text-zinc-600 mx-auto mb-2" />
                      <p className="text-xs text-zinc-500">No profile suggestions. Interact with the catalog to build suggestions!</p>
                    </div>
                  ) : (
                    <div className="grid grid-cols-1 sm:grid-cols-2 gap-6">
                      {data.recommendations.slice(0, 4).map((product) => {
                        const discount = getMaxDiscount(product);
                        const lowest = getLowestPrice(product);
                        return (
                          <div key={product.id} className="bg-zinc-950/20 border border-zinc-900 hover:border-zinc-800 p-4 rounded-2xl flex flex-col gap-3 group relative overflow-hidden transition-all">
                            <div className="aspect-video relative rounded-lg bg-zinc-900 overflow-hidden border border-zinc-900">
                              <img src={product.imageUrl} alt={product.name} className="h-full w-full object-cover group-hover:scale-103 transition-transform duration-500" />
                              {discount > 0 && (
                                <span className="absolute left-2 top-2 bg-emerald-500/10 border border-emerald-500/20 text-emerald-400 text-[9px] font-mono font-bold px-1.5 py-0.5 rounded flex items-center gap-0.5">
                                  <TrendingDown className="h-3 w-3" />
                                  {discount}% OFF
                                </span>
                              )}
                            </div>
                            <div className="flex flex-col min-w-0">
                              <Link to={`/product/${product.id}`} className="text-white hover:text-blue-400 text-sm font-bold truncate block transition-colors">
                                {product.name}
                              </Link>
                              <span className="text-[10px] text-zinc-500 mt-0.5 font-bold uppercase tracking-wider">{product.brand} • {product.category}</span>
                              <div className="flex items-baseline justify-between mt-2">
                                <span className="text-sm font-mono font-extrabold text-white">
                                  {lowest ? formatPrice(getDisplayPrice(lowest, currency), currency) : 'N/A'}
                                </span>
                                <span className="text-[10px] text-zinc-500 font-medium">Best Price</span>
                              </div>
                            </div>
                          </div>
                        );
                      })}
                    </div>
                  )}
                </div>

                {/* Recently Viewed Products */}
                <div className="flex flex-col gap-4">
                  <h2 className="text-lg font-bold text-white flex items-center gap-2">
                    <History className="h-5 w-5 text-zinc-400" />
                    Recently Viewed
                  </h2>
                  {data.recentlyViewed.length === 0 ? (
                    <div className="py-8 border border-dashed border-zinc-900 rounded-2xl text-center">
                      <p className="text-xs text-zinc-500">No viewed products found in history feed.</p>
                    </div>
                  ) : (
                    <div className="grid grid-cols-2 sm:grid-cols-4 gap-4">
                      {data.recentlyViewed.slice(0, 4).map((p) => {
                        const price = getLowestPrice(p);
                        return (
                          <div key={p.id} className="bg-zinc-950/15 border border-zinc-900 p-2.5 rounded-xl flex flex-col gap-2 hover:border-zinc-800 transition-colors">
                            <img src={p.imageUrl} alt={p.name} className="aspect-square w-full rounded object-cover bg-zinc-900 border border-zinc-900" />
                            <div className="flex flex-col min-w-0">
                              <Link to={`/product/${p.id}`} className="text-white hover:text-blue-450 text-xs font-bold truncate block">
                                {p.name}
                              </Link>
                              <span className="text-[9px] font-mono text-zinc-555 font-bold mt-0.5">{price ? formatPrice(getDisplayPrice(price, currency), currency) : 'N/A'}</span>
                            </div>
                          </div>
                        );
                      })}
                    </div>
                  )}
                </div>
              </div>

              {/* Right column (1/3 width) */}
              <div className="flex flex-col gap-8">
                {/* Most Clicked Sellers Widget */}
                {data.mostClickedSellers.length > 0 && (
                  <div className="border border-zinc-900 rounded-2xl bg-zinc-950/20 p-5 flex flex-col gap-4">
                    <div>
                      <h2 className="text-sm font-bold text-zinc-200 flex items-center gap-2">
                        <Store className="h-4 w-4 text-zinc-400" />
                        Most Visited Stores
                      </h2>
                      <p className="text-[10px] text-zinc-500 mt-0.5">Sellers you clicked redirect links for</p>
                    </div>
                    <div className="flex flex-col gap-3.5">
                      {data.mostClickedSellers.map((seller, i) => {
                        const maxCount = Math.max(...data.mostClickedSellers.map(s => s.count));
                        const percent = maxCount > 0 ? (seller.count / maxCount) * 100 : 0;
                        return (
                          <div key={i} className="flex flex-col gap-1 text-xs">
                            <div className="flex justify-between items-center">
                              <span className="text-zinc-300 font-semibold">{seller.name}</span>
                              <span className="text-[10px] text-zinc-500 font-mono font-bold">{seller.count} clicks</span>
                            </div>
                            <div className="h-1.5 w-full bg-zinc-900 rounded-full overflow-hidden">
                              <div className="h-full bg-gradient-to-r from-blue-500 to-indigo-500 rounded-full" style={{ width: `${percent}%` }} />
                            </div>
                          </div>
                        );
                      })}
                    </div>
                  </div>
                )}

                {/* Recent Searches Tag Cloud */}
                {data.recentSearches.length > 0 && (
                  <div className="border border-zinc-900 rounded-2xl bg-zinc-950/20 p-5 flex flex-col gap-3">
                    <h2 className="text-sm font-bold text-zinc-200 flex items-center gap-2">
                      <Search className="h-4 w-4 text-zinc-400" />
                      Recent Searches
                    </h2>
                    <div className="flex flex-wrap gap-2">
                      {data.recentSearches.map((keyword, index) => (
                        <span
                          key={index}
                          onClick={() => navigate(`/search?q=${encodeURIComponent(keyword)}`)}
                          className="px-2.5 py-1 rounded bg-zinc-900 hover:bg-zinc-850 hover:text-white border border-zinc-800 text-[10px] font-mono text-zinc-450 font-bold uppercase transition-all cursor-pointer"
                        >
                          {keyword}
                        </span>
                      ))}
                    </div>
                  </div>
                )}

                {/* Activity timeline feed */}
                <div className="border border-zinc-900 rounded-2xl bg-zinc-950/20 p-5 flex flex-col gap-4 max-h-[500px] overflow-hidden relative">
                  <h2 className="text-sm font-bold text-zinc-250 flex items-center gap-2">
                    <Layers className="h-4 w-4 text-zinc-400" />
                    Recent Actions
                  </h2>
                  <div className="overflow-y-auto pr-1 flex flex-col gap-5 text-left border-l border-zinc-900/60 pl-3.5 relative ml-1.5">
                    {timelineEvents.slice(0, 6).map((event) => {
                      const style = getEventStyle(event.interactionType);
                      return (
                        <div key={event.id} className="relative flex flex-col gap-1 shrink-0 select-none">
                          <div className={`absolute -left-[23px] top-0.5 h-5.5 w-5.5 rounded-full border flex items-center justify-center ${style.bgColor} scale-85 shrink-0 z-10`}>
                            {style.icon}
                          </div>
                          <span className="text-[9px] text-zinc-550 font-bold uppercase font-mono tracking-wider">
                            {style.label} • {formatEventTime(event.createdAt || '')}
                          </span>
                          {renderEventDetails(event)}
                        </div>
                      );
                    })}
                    {timelinePage + 1 < timelineTotalPages && (
                      <button
                        onClick={loadMoreEvents}
                        disabled={loadingMore}
                        className="text-[10px] text-zinc-500 font-bold hover:text-white mt-1 cursor-pointer block text-left transition-colors"
                      >
                        {loadingMore ? 'Loading...' : 'Load More Actions →'}
                      </button>
                    )}
                  </div>
                </div>
              </div>
            </motion.div>
          )}

          {activeTab === 'recommendations' && (
            <motion.div
              key="recommendations"
              initial={{ opacity: 0, y: 10 }}
              animate={{ opacity: 1, y: 0 }}
              exit={{ opacity: 0, y: -10 }}
              className="flex flex-col gap-6"
            >
              <div className="flex flex-col gap-1">
                <h2 className="text-xl font-bold text-white flex items-center gap-2">
                  <Sparkles className="h-5.5 w-5.5 text-blue-400" />
                  Your Personalized Matches
                </h2>
                <p className="text-xs text-zinc-500">Based on saved items, watchlist tracking, click history, and category affinity.</p>
              </div>

              {data.recommendations.length === 0 ? (
                <div className="py-24 border border-dashed border-zinc-900 rounded-3xl text-center">
                  <Inbox className="h-12 w-12 text-zinc-700 mx-auto mb-4" />
                  <h3 className="text-zinc-200 font-bold text-base">No Recommendations Available</h3>
                  <p className="text-xs text-zinc-500 max-w-sm mx-auto mt-2">
                    Interact with our catalog, compare prices, or save items to populate your recommendation profile.
                  </p>
                </div>
              ) : (
                <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-6">
                  {data.recommendations.map((product) => {
                    const discount = getMaxDiscount(product);
                    const lowest = getLowestPrice(product);
                    const highest = getHighestPrice(product);
                    return (
                      <div key={product.id} className="bg-zinc-950/20 border border-zinc-900 hover:border-zinc-800 p-5 rounded-2xl flex flex-col gap-4 group relative overflow-hidden transition-all hover:bg-zinc-950/30">
                        <div className="aspect-video relative rounded-xl bg-zinc-900 overflow-hidden border border-zinc-900">
                          <img src={product.imageUrl} alt={product.name} className="h-full w-full object-cover group-hover:scale-102 transition-transform duration-500" />
                          {discount > 0 && (
                            <span className="absolute left-3 top-3 bg-emerald-500/15 border border-emerald-500/25 text-emerald-400 text-xs font-mono font-bold px-2 py-0.5 rounded-full flex items-center gap-0.5">
                              <TrendingDown className="h-3.5 w-3.5" />
                              {discount}% OFF
                            </span>
                          )}
                        </div>
                        <div className="flex flex-col min-w-0 flex-grow">
                          <Link to={`/product/${product.id}`} className="text-white hover:text-blue-400 text-base font-bold truncate block transition-colors">
                            {product.name}
                          </Link>
                          <span className="text-[10px] text-zinc-500 font-bold uppercase tracking-wider mt-1">{product.brand} • {product.category}</span>
                          <p className="text-xs text-zinc-400 mt-2 line-clamp-2 h-8 leading-relaxed">{product.description}</p>
                          <div className="flex items-baseline justify-between mt-4 pt-3 border-t border-zinc-900/60">
                            <div className="flex flex-col">
                              <span className="text-[10px] text-zinc-500 font-medium">Price Range</span>
                              <span className="text-sm font-mono font-extrabold text-white">
                                {lowest ? formatPrice(getDisplayPrice(lowest, currency), currency) : 'N/A'} - {highest ? formatPrice(getDisplayPrice(highest, currency), currency) : ''}
                              </span>
                            </div>
                            <Link to={`/product/${product.id}`} className="inline-flex items-center gap-1 text-xs text-blue-400 font-bold hover:text-blue-300">
                              View Details <ArrowUpRight className="h-3.5 w-3.5" />
                            </Link>
                          </div>
                        </div>
                      </div>
                    );
                  })}
                </div>
              )}
            </motion.div>
          )}

          {activeTab === 'alerts' && (
            <motion.div
              key="alerts"
              initial={{ opacity: 0, y: 10 }}
              animate={{ opacity: 1, y: 0 }}
              exit={{ opacity: 0, y: -10 }}
              className="flex flex-col gap-6"
            >
              <div className="flex flex-col gap-1">
                <h2 className="text-xl font-bold text-white flex items-center gap-2">
                  <Tag className="h-5.5 w-5.5 text-emerald-400" />
                  Your Active Deal Alerts
                </h2>
                <p className="text-xs text-zinc-500">Products where the current lowest store price is below or equal to your target alert price.</p>
              </div>

              {data.priceDropAlerts.length === 0 ? (
                <div className="py-24 border border-dashed border-zinc-900 rounded-3xl text-center flex flex-col items-center justify-center">
                  <AlertCircle className="h-12 w-12 text-zinc-700 mb-4" />
                  <h3 className="text-zinc-200 font-bold text-base">No Active Deals Triggered</h3>
                  <p className="text-xs text-zinc-500 max-w-sm mt-2">
                    None of your watchlisted items have hit their target price targets yet. We'll update the list once they do!
                  </p>
                </div>
              ) : (
                <div className="grid grid-cols-1 md:grid-cols-2 gap-6">
                  {data.priceDropAlerts.map((alert) => {
                    const diffRaw = alert.priceDifference || 0;
                    const diff = getDisplayPrice(diffRaw, currency);
                    return (
                      <div key={alert.id} className="bg-zinc-955/20 border border-emerald-500/20 p-5 rounded-2xl flex gap-4 items-start relative overflow-hidden transition-all hover:bg-zinc-950/30">
                        <div className="absolute right-0 top-0 h-32 w-32 bg-emerald-500/5 rounded-full blur-2xl -z-10" />
                        <img src={alert.imageUrl} alt={alert.productName} className="h-20 w-20 rounded-xl bg-zinc-900 object-cover border border-zinc-900 shrink-0" />
                        <div className="flex flex-col min-w-0 flex-grow text-left">
                          <Link to={`/product/${alert.productId}`} className="text-white hover:text-emerald-400 text-sm font-bold truncate block transition-colors">
                            {alert.productName}
                          </Link>
                          <span className="text-[10px] text-zinc-550 font-bold uppercase tracking-wider mt-0.5">{alert.brand}</span>
                          
                          <div className="grid grid-cols-3 gap-2 mt-3 pt-3 border-t border-zinc-900">
                            <div className="flex flex-col">
                              <span className="text-[9px] text-zinc-500 font-bold uppercase">Best Price</span>
                              <span className="text-xs font-mono font-bold text-emerald-400">{formatPrice(getDisplayPrice(alert.currentBestPrice, currency), currency)}</span>
                            </div>
                            <div className="flex flex-col">
                              <span className="text-[9px] text-zinc-500 font-bold uppercase">Target Price</span>
                              <span className="text-xs font-mono font-bold text-white">{formatPrice(getDisplayPrice(alert.targetPrice, currency), currency)}</span>
                            </div>
                            <div className="flex flex-col">
                              <span className="text-[9px] text-zinc-555 font-bold uppercase">Savings</span>
                              <span className="text-xs font-mono font-bold text-emerald-450 flex items-center gap-0.5">
                                <TrendingDown className="h-3.5 w-3.5" />
                                {formatPrice(diff, currency)}
                              </span>
                            </div>
                          </div>
                        </div>
                      </div>
                    );
                  })}
                </div>
              )}
            </motion.div>
          )}

          {activeTab === 'trending' && (
            <motion.div
              key="trending"
              initial={{ opacity: 0, y: 10 }}
              animate={{ opacity: 1, y: 0 }}
              exit={{ opacity: 0, y: -10 }}
              className="flex flex-col gap-6"
            >
              <div className="flex flex-col gap-1">
                <h2 className="text-xl font-bold text-white flex items-center gap-2">
                  <TrendingUp className="h-5.5 w-5.5 text-indigo-400" />
                  Trending on PricePilot
                </h2>
                <p className="text-xs text-zinc-500">Explore the most active and popular products on the platform based on recent shopper activity.</p>
              </div>

              <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-6">
                {data.trendingProducts.map((product) => {
                  const discount = getMaxDiscount(product);
                  const lowest = getLowestPrice(product);
                  return (
                    <div key={product.id} className="bg-zinc-950/20 border border-zinc-900 hover:border-zinc-800 p-4 rounded-2xl flex flex-col gap-3 group relative overflow-hidden transition-all hover:bg-zinc-950/30">
                      <div className="aspect-video relative rounded-lg bg-zinc-900 overflow-hidden border border-zinc-900">
                        <img src={product.imageUrl} alt={product.name} className="h-full w-full object-cover group-hover:scale-102 transition-transform duration-500" />
                        {discount > 0 && (
                          <span className="absolute left-2 top-2 bg-emerald-500/10 border border-emerald-500/20 text-emerald-400 text-[10px] font-mono font-bold px-2 py-0.5 rounded flex items-center gap-0.5">
                            <TrendingDown className="h-3.5 w-3.5" />
                            {discount}% OFF
                          </span>
                        )}
                      </div>
                      <div className="flex flex-col min-w-0">
                        <Link to={`/product/${product.id}`} className="text-white hover:text-blue-400 text-sm font-bold truncate block transition-colors">
                          {product.name}
                        </Link>
                        <span className="text-[10px] text-zinc-500 font-bold uppercase mt-0.5">{product.brand} • {product.category}</span>
                        <div className="flex items-baseline justify-between mt-3 pt-2.5 border-t border-zinc-900">
                          <span className="text-xs font-mono font-bold text-white">
                            {lowest ? formatPrice(getDisplayPrice(lowest, currency), currency) : 'N/A'}
                          </span>
                          <Link to={`/product/${product.id}`} className="text-[11px] text-blue-450 font-bold hover:text-blue-350 flex items-center gap-0.5">
                            Go To Product <ArrowRight className="h-3 w-3" />
                          </Link>
                        </div>
                      </div>
                    </div>
                  );
                })}
              </div>
            </motion.div>
          )}
        </AnimatePresence>
      </div>
    </div>
  );
};

export default DashboardPage;
