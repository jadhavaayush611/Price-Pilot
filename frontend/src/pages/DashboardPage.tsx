import React, { useEffect, useState } from 'react';
import { useNavigate, Link } from 'react-router-dom';
import { apiService } from '../services/api';
import { useAuth } from '../context/AuthContext';
import type { UserInteractionEvent } from '../types';
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
  ArrowUpRight
} from 'lucide-react';
import { motion, AnimatePresence } from 'framer-motion';

export const DashboardPage: React.FC = () => {
  const navigate = useNavigate();
  const { user, isAuthenticated } = useAuth();
  const [events, setEvents] = useState<UserInteractionEvent[]>([]);
  const [loading, setLoading] = useState(true);
  const [loadingMore, setLoadingMore] = useState(false);
  const [page, setPage] = useState(0);
  const [totalPages, setTotalPages] = useState(1);
  const [totalElements, setTotalElements] = useState(0);
  const [savedCount, setSavedCount] = useState(0);
  const [watchlistCount, setWatchlistCount] = useState(0);

  const pageSize = 10;

  useEffect(() => {
    if (!isAuthenticated) {
      navigate('/login?from=/dashboard');
      return;
    }

    // Load initial data
    setLoading(true);
    Promise.all([
      apiService.getMyEvents(0, pageSize),
      apiService.getSavedProducts().catch(() => []),
      apiService.getWatchlists().catch(() => [])
    ])
      .then(([eventsData, savedData, watchlistData]) => {
        setEvents(eventsData.content);
        setTotalPages(eventsData.totalPages);
        setTotalElements(eventsData.totalElements);
        setSavedCount(savedData.length);
        setWatchlistCount(watchlistData.length);
      })
      .catch((err) => {
        console.error('Error fetching dashboard data:', err);
      })
      .finally(() => {
        setLoading(false);
      });
  }, [isAuthenticated, navigate]);

  const loadMoreEvents = async () => {
    if (page + 1 >= totalPages || loadingMore) return;

    setLoadingMore(true);
    try {
      const nextPage = page + 1;
      const res = await apiService.getMyEvents(nextPage, pageSize);
      setEvents((prev) => [...prev, ...res.content]);
      setPage(nextPage);
      setTotalPages(res.totalPages);
      setTotalElements(res.totalElements);
    } catch (err) {
      console.error('Failed to load more events:', err);
    } finally {
      setLoadingMore(false);
    }
  };

  // Helper to format timestamps nicely
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
      day: 'numeric',
      hour: '2-digit',
      minute: '2-digit'
    });
  };

  // Render the corresponding icon & color scheme for each interaction type
  const getEventStyle = (type: string) => {
    switch (type) {
      case 'PRODUCT_VIEW':
        return {
          icon: <Eye className="h-4.5 w-4.5" />,
          bgColor: 'bg-blue-500/10 text-blue-400 border-blue-500/20',
          label: 'Viewed Product'
        };
      case 'PRODUCT_SAVE':
        return {
          icon: <Heart className="h-4.5 w-4.5" />,
          bgColor: 'bg-rose-500/10 text-rose-400 border-rose-500/20',
          label: 'Saved Product'
        };
      case 'PRODUCT_UNSAVE':
        return {
          icon: <Heart className="h-4.5 w-4.5" />,
          bgColor: 'bg-zinc-800 text-zinc-500 border-zinc-800',
          label: 'Unsaved Product'
        };
      case 'WATCHLIST_CREATE':
        return {
          icon: <Bell className="h-4.5 w-4.5" />,
          bgColor: 'bg-emerald-500/10 text-emerald-400 border-emerald-500/20',
          label: 'Created Price Alert'
        };
      case 'WATCHLIST_DELETE':
        return {
          icon: <Bell className="h-4.5 w-4.5" />,
          bgColor: 'bg-zinc-800 text-zinc-500 border-zinc-800',
          label: 'Removed Price Alert'
        };
      case 'PRICE_HISTORY_VIEW':
        return {
          icon: <History className="h-4.5 w-4.5" />,
          bgColor: 'bg-amber-500/10 text-amber-400 border-amber-500/20',
          label: 'Viewed Price History'
        };
      case 'SELLER_CLICK':
        return {
          icon: <ExternalLink className="h-4.5 w-4.5" />,
          bgColor: 'bg-cyan-500/10 text-cyan-400 border-cyan-500/20',
          label: 'Visited Seller'
        };
      case 'SEARCH':
        return {
          icon: <Search className="h-4.5 w-4.5" />,
          bgColor: 'bg-purple-500/10 text-purple-400 border-purple-500/20',
          label: 'Searched Catalog'
        };
      case 'TRENDING_VIEW':
        return {
          icon: <Sparkles className="h-4.5 w-4.5" />,
          bgColor: 'bg-indigo-500/10 text-indigo-400 border-indigo-500/20',
          label: 'Viewed Trending'
        };
      default:
        return {
          icon: <Activity className="h-4.5 w-4.5" />,
          bgColor: 'bg-zinc-850 text-zinc-350 border-zinc-800',
          label: 'Activity logged'
        };
    }
  };

  // Build appropriate textual summary and metadata node for the timeline item
  const renderEventDetails = (event: UserInteractionEvent) => {
    const { interactionType, metadata, productId, productName, sellerName } = event;

    switch (interactionType) {
      case 'PRODUCT_VIEW':
        return (
          <div className="flex flex-col gap-1.5">
            <span className="text-zinc-200 text-sm">
              You viewed{' '}
              {productId ? (
                <Link to={`/product/${productId}`} className="text-white hover:text-blue-400 hover:underline font-bold transition-colors">
                  {productName || 'product'}
                </Link>
              ) : (
                <span className="font-bold text-white">{productName || 'product'}</span>
              )}
            </span>
            {metadata.category && (
              <div className="flex flex-wrap gap-2 mt-1">
                <span className="px-2 py-0.5 rounded bg-zinc-900 border border-zinc-850 text-[10px] text-zinc-400 uppercase font-mono">
                  {metadata.brand || 'Brand'}
                </span>
                <span className="px-2 py-0.5 rounded bg-zinc-900 border border-zinc-850 text-[10px] text-zinc-400">
                  {metadata.category}
                </span>
              </div>
            )}
          </div>
        );

      case 'PRODUCT_SAVE':
        return (
          <div className="flex flex-col gap-1">
            <span className="text-zinc-200 text-sm">
              Saved{' '}
              {productId ? (
                <Link to={`/product/${productId}`} className="text-white hover:text-rose-400 hover:underline font-bold transition-colors">
                  {productName || 'product'}
                </Link>
              ) : (
                <span className="font-bold text-white">{productName || 'product'}</span>
              )}{' '}
              to your collection.
            </span>
          </div>
        );

      case 'PRODUCT_UNSAVE':
        return (
          <span className="text-zinc-400 text-sm">
            Removed <span className="font-semibold text-zinc-300">{productName || 'product'}</span> from saved collection.
          </span>
        );

      case 'WATCHLIST_CREATE':
        return (
          <div className="flex flex-col gap-1.5">
            <span className="text-zinc-200 text-sm">
              Created price alert for{' '}
              <Link to={`/product/${productId}`} className="text-white hover:text-emerald-400 hover:underline font-bold transition-colors">
                {productName || 'product'}
              </Link>
            </span>
            <div className="flex flex-wrap items-center gap-3 text-xs mt-1">
              <div className="flex items-center gap-1">
                <span className="text-zinc-500">Target price:</span>
                <span className="font-extrabold text-emerald-400">₹{metadata.targetPrice}</span>
              </div>
              <div className="flex items-center gap-1">
                <span className="text-zinc-500">Initial price:</span>
                <span className="font-semibold text-zinc-400">₹{metadata.currentBestPrice}</span>
              </div>
            </div>
          </div>
        );

      case 'WATCHLIST_DELETE':
        return (
          <div className="flex flex-col gap-1">
            <span className="text-zinc-400 text-sm">
              Deleted watchlist tracking for <span className="font-semibold text-zinc-300">{productName || 'product'}</span>.
            </span>
            {metadata.targetPrice && (
              <span className="text-[10px] text-zinc-500 font-mono mt-0.5">Alert target was ₹{metadata.targetPrice}</span>
            )}
          </div>
        );

      case 'PRICE_HISTORY_VIEW':
        return (
          <span className="text-zinc-200 text-sm">
            Analyzed comparison pricing history for{' '}
            <Link to={`/product/${productId}`} className="text-white hover:text-amber-400 hover:underline font-bold transition-colors">
              {productName || 'product'}
            </Link>
            .
          </span>
        );

      case 'SELLER_CLICK':
        return (
          <div className="flex flex-col gap-2">
            <span className="text-zinc-200 text-sm">
              Clicked redirect link to <span className="text-white font-bold">{sellerName || metadata.seller || 'seller'}</span> for{' '}
              {productId ? (
                <Link to={`/product/${productId}`} className="text-white hover:text-cyan-400 hover:underline font-bold transition-colors">
                  {productName || 'product'}
                </Link>
              ) : (
                <span className="font-bold text-white">{productName || 'product'}</span>
              )}
            </span>
            {metadata.destinationUrl && (
              <a
                href={metadata.destinationUrl}
                target="_blank"
                rel="noopener noreferrer"
                className="inline-flex items-center gap-1 w-fit text-[11px] font-bold text-cyan-400 bg-cyan-555/5 border border-cyan-500/10 hover:border-cyan-500/35 hover:bg-cyan-500/10 px-2.5 py-1 rounded-lg transition-all"
              >
                Go to Site <ArrowUpRight className="h-3 w-3" />
              </a>
            )}
          </div>
        );

      case 'SEARCH':
        return (
          <div className="flex flex-col gap-2">
            <span className="text-zinc-200 text-sm">
              Searched database for keyword <span className="text-zinc-100 font-extrabold italic bg-zinc-900 border border-zinc-800 px-1.5 py-0.5 rounded font-mono">"{metadata.keyword || '*'}"</span>
            </span>
            <div className="flex flex-wrap items-center gap-2 mt-1">
              <span className="px-2 py-0.5 rounded bg-zinc-950 border border-zinc-900 text-[10px] font-mono text-zinc-500 font-bold">
                {metadata.resultCount !== undefined ? `${metadata.resultCount} results` : 'Search completed'}
              </span>
              {metadata.filters && Object.keys(metadata.filters).length > 0 && (
                <span className="text-[10px] text-zinc-500">
                  Filters: {JSON.stringify(metadata.filters)}
                </span>
              )}
            </div>
          </div>
        );

      case 'TRENDING_VIEW':
        return (
          <span className="text-zinc-200 text-sm">
            Explored the trending products list (max limit of {metadata.limit || 10} items).
          </span>
        );

      default:
        return (
          <div className="flex flex-col gap-1">
            <span className="text-zinc-350 text-sm">Interaction logged.</span>
            {Object.keys(metadata).length > 0 && (
              <pre className="text-[10px] bg-zinc-950 border border-zinc-900 text-zinc-500 p-2 rounded overflow-auto mt-1 max-w-full font-mono">
                {JSON.stringify(metadata, null, 2)}
              </pre>
            )}
          </div>
        );
    }
  };

  if (loading) {
    return (
      <div className="max-w-6xl mx-auto w-full py-8 flex flex-col gap-8">
        <div className="flex items-center gap-4 animate-pulse">
          <div className="h-12 w-12 rounded-2xl bg-zinc-900" />
          <div className="flex flex-col gap-2 flex-grow">
            <div className="h-6 w-48 bg-zinc-900 rounded-lg" />
            <div className="h-3 w-64 bg-zinc-900 rounded" />
          </div>
        </div>
        
        <div className="grid grid-cols-1 md:grid-cols-3 gap-6 animate-pulse">
          {[1, 2, 3].map((i) => (
            <div key={i} className="h-28 rounded-2xl bg-zinc-950 border border-zinc-900/50" />
          ))}
        </div>

        <div className="border border-zinc-900/60 rounded-2xl p-6 bg-zinc-950/20 flex flex-col gap-6 animate-pulse">
          <div className="h-5 w-32 bg-zinc-900 rounded-md" />
          {[1, 2, 3].map((n) => (
            <div key={n} className="flex gap-4 items-start pl-2">
              <div className="h-8 w-8 rounded-full bg-zinc-900 shrink-0" />
              <div className="flex flex-col gap-2 flex-grow">
                <div className="h-4 w-1/3 bg-zinc-900 rounded" />
                <div className="h-3 w-1/4 bg-zinc-900 rounded" />
              </div>
            </div>
          ))}
        </div>
      </div>
    );
  }

  return (
    <div className="max-w-6xl mx-auto w-full py-8 flex flex-col gap-8">
      {/* Welcome Panel */}
      <div className="flex flex-col md:flex-row md:items-center justify-between gap-6">
        <div className="flex items-center gap-4">
          <div className="h-14 w-14 rounded-2xl bg-gradient-to-br from-white/10 to-zinc-800 border border-zinc-800 flex items-center justify-center text-white shrink-0">
            <UserIcon className="h-6 w-6" />
          </div>
          <div>
            <h1 className="text-3xl font-extrabold tracking-tight text-white flex items-center gap-2.5">
              <span>User Dashboard</span>
            </h1>
            <p className="text-xs text-zinc-500 mt-1">
              Welcome back, <span className="text-zinc-300 font-bold">{user?.firstName} {user?.lastName}</span> ({user?.email})
            </p>
          </div>
        </div>
        {user?.role === 'ADMIN' && (
          <span className="inline-flex items-center gap-1.5 self-start md:self-center px-3 py-1 rounded-full bg-amber-500/10 border border-amber-500/20 text-xs text-amber-400 font-mono font-bold uppercase tracking-wider">
            <Shield className="h-3.5 w-3.5" />
            System Administrator
          </span>
        )}
      </div>

      {/* Stats Cards */}
      <div className="grid grid-cols-1 sm:grid-cols-3 gap-6">
        <motion.div
          whileHover={{ y: -4, borderColor: 'rgba(255,255,255,0.1)' }}
          transition={{ duration: 0.2 }}
          onClick={() => navigate('/saved-products')}
          className="rounded-2xl bg-zinc-950/20 border border-zinc-900/80 p-5 cursor-pointer flex justify-between items-center group relative overflow-hidden"
        >
          <div className="flex flex-col gap-1">
            <span className="text-zinc-500 text-xs font-bold uppercase tracking-wider">Saved Products</span>
            <span className="text-3xl font-extrabold text-white">{savedCount}</span>
          </div>
          <div className="h-10 w-10 rounded-xl bg-rose-555/5 border border-rose-500/10 text-rose-500 flex items-center justify-center group-hover:bg-rose-500/10 transition-colors shrink-0">
            <Heart className="h-5 w-5 fill-rose-500/20" />
          </div>
        </motion.div>

        <motion.div
          whileHover={{ y: -4, borderColor: 'rgba(255,255,255,0.1)' }}
          transition={{ duration: 0.2 }}
          onClick={() => navigate('/watchlist')}
          className="rounded-2xl bg-zinc-950/20 border border-zinc-900/80 p-5 cursor-pointer flex justify-between items-center group relative overflow-hidden"
        >
          <div className="flex flex-col gap-1">
            <span className="text-zinc-500 text-xs font-bold uppercase tracking-wider">Price Alerts</span>
            <span className="text-3xl font-extrabold text-white">{watchlistCount}</span>
          </div>
          <div className="h-10 w-10 rounded-xl bg-emerald-555/5 border border-emerald-500/10 text-emerald-500 flex items-center justify-center group-hover:bg-emerald-500/10 transition-colors shrink-0">
            <Bell className="h-5 w-5" />
          </div>
        </motion.div>

        <motion.div
          whileHover={{ y: -4 }}
          transition={{ duration: 0.2 }}
          className="rounded-2xl bg-zinc-950/20 border border-zinc-900/80 p-5 flex justify-between items-center group relative overflow-hidden"
        >
          <div className="flex flex-col gap-1">
            <span className="text-zinc-500 text-xs font-bold uppercase tracking-wider">Logged Activities</span>
            <span className="text-3xl font-extrabold text-white">{totalElements}</span>
          </div>
          <div className="h-10 w-10 rounded-xl bg-blue-555/5 border border-blue-500/10 text-blue-500 flex items-center justify-center shrink-0">
            <Activity className="h-5 w-5" />
          </div>
        </motion.div>
      </div>

      {/* Main Section: Activity Timeline */}
      <div className="border border-zinc-900/60 rounded-2xl bg-zinc-950/15 backdrop-blur-md p-6 flex flex-col gap-6 relative overflow-hidden">
        <div>
          <h2 className="text-lg font-bold text-zinc-250 flex items-center gap-2">
            <Layers className="h-5 w-5 text-zinc-400" />
            Activity Timeline
          </h2>
          <p className="text-[11px] text-zinc-500 mt-0.5">
            Real-time feed of your behavior on the PricePilot platform
          </p>
        </div>

        {events.length === 0 ? (
          <div className="flex flex-col items-center justify-center py-20 px-4 border border-dashed border-zinc-900 rounded-xl text-center">
            <div className="h-12 w-12 rounded-xl bg-zinc-950 border border-zinc-900 text-zinc-500 flex items-center justify-center mb-4">
              <Inbox className="h-6 w-6" />
            </div>
            <h3 className="text-zinc-200 font-bold text-base">No activity logged yet</h3>
            <p className="text-xs text-zinc-500 max-w-xs mt-1.5">
              Interact with the products catalog, compare prices, save items, or create alerts to populate your timeline.
            </p>
          </div>
        ) : (
          <div className="relative pl-4 sm:pl-6 border-l border-zinc-900/80 flex flex-col gap-8 mt-2 pb-2">
            <AnimatePresence initial={false}>
              {events.map((event, index) => {
                const style = getEventStyle(event.interactionType);
                return (
                  <motion.div
                    key={event.id || index}
                    initial={{ opacity: 0, x: -10 }}
                    animate={{ opacity: 1, x: 0 }}
                    transition={{ type: 'spring', stiffness: 150, damping: 20 }}
                    className="relative flex flex-col sm:flex-row gap-3 sm:gap-6 items-start text-left group"
                  >
                    {/* Circle timeline bullet */}
                    <div className={`absolute -left-[27px] sm:-left-[35px] top-1 h-8 w-8 rounded-full border flex items-center justify-center ${style.bgColor} shadow-lg shrink-0 z-10 scale-95 group-hover:scale-105 transition-transform duration-300`}>
                      {style.icon}
                    </div>

                    {/* Timeline Item Content Box */}
                    <div className="flex-grow flex flex-col gap-1 select-none">
                      <div className="flex flex-wrap items-baseline gap-2">
                        <span className="text-[10px] font-bold uppercase tracking-wider font-mono text-zinc-400">
                          {style.label}
                        </span>
                        <span className="text-[10px] text-zinc-600 font-medium">
                          • {formatEventTime(event.createdAt)}
                        </span>
                      </div>
                      
                      {/* Body Detail text */}
                      <div className="mt-0.5">
                        {renderEventDetails(event)}
                      </div>
                    </div>
                  </motion.div>
                );
              })}
            </AnimatePresence>

            {/* Load More Trigger */}
            {page + 1 < totalPages && (
              <div className="pt-4 flex justify-center">
                <button
                  onClick={loadMoreEvents}
                  disabled={loadingMore}
                  className="inline-flex items-center gap-2.5 py-2.5 px-6 border border-zinc-900 hover:border-zinc-800 bg-zinc-950 text-xs text-zinc-400 font-bold rounded-xl active:scale-97 disabled:opacity-50 transition-all cursor-pointer shadow-sm hover:text-white"
                >
                  {loadingMore ? (
                    <>
                      <div className="h-3.5 w-3.5 border-2 border-zinc-400 border-t-transparent rounded-full animate-spin" />
                      <span>Loading activities...</span>
                    </>
                  ) : (
                    <>
                      <span>Load More Activity</span>
                      <ArrowRight className="h-3.5 w-3.5 text-zinc-500 group-hover:translate-x-0.5 transition-transform" />
                    </>
                  )}
                </button>
              </div>
            )}
          </div>
        )}
      </div>
    </div>
  );
};

export default DashboardPage;
