import React, { useEffect, useState } from 'react';
import { useParams, useNavigate, Link } from 'react-router-dom';
import { apiService } from '../services/api';
import type { ProductWithPrices } from '../types';
import { ArrowLeft, Clock, ExternalLink, Sparkles, Tag, AlertCircle, ShoppingBag, LayoutGrid, List, Heart, Bell, Trash2, X, Eye, Activity } from 'lucide-react';
import { motion, AnimatePresence } from 'framer-motion';
import { formatPrice, getSavedCurrency, saveCurrency, getDisplayPrice, type CurrencyCode, CURRENCY_SYMBOLS } from '../currency';
import { SellerCard } from '../components/SellerCard';
import { useAuth } from '../context/AuthContext';
import { PriceHistorySection } from '../components/PriceHistorySection';

export const ProductPage: React.FC = () => {
  const { id } = useParams<{ id: string }>();
  const navigate = useNavigate();
  const { isAuthenticated } = useAuth();
  const [product, setProduct] = useState<ProductWithPrices | null>(null);
  const [loading, setLoading] = useState(true);
  const [viewMode, setViewMode] = useState<'card' | 'table'>('card');
  const [currency, setCurrency] = useState<CurrencyCode>(getSavedCurrency());

  useEffect(() => {
    saveCurrency(currency);
  }, [currency]);
  const [isSaved, setIsSaved] = useState(false);
  const [saving, setSaving] = useState(false);

  // Watchlist specific states
  const [isTracking, setIsTracking] = useState(false);
  const [watchlistEntry, setWatchlistEntry] = useState<any>(null);
  const [isTrackingModalOpen, setIsTrackingModalOpen] = useState(false);
  const [targetPriceInput, setTargetPriceInput] = useState('');
  const [trackingError, setTrackingError] = useState<string | null>(null);
  const [isSubmittingTracking, setIsSubmittingTracking] = useState(false);

  // Analytics states
  const [analytics, setAnalytics] = useState<any | null>(null);
  const [analyticsLoading, setAnalyticsLoading] = useState(true);
  const [similarProducts, setSimilarProducts] = useState<ProductWithPrices[]>([]);

  useEffect(() => {
    if (id) {
      setLoading(true);
      apiService.getProduct(id)
        .then((data) => {
          setProduct(data);
        })
        .finally(() => {
          // Add a slight delay for smoother skeleton transition
          setTimeout(() => {
            setLoading(false);
          }, 300);
        });

      // Fetch similar products
      apiService.getSimilarProducts(id, 4)
        .then((data) => {
          setSimilarProducts(data);
        })
        .catch((err) => {
          console.error("Error loading similar products:", err);
        });

      // Fetch analytics
      setAnalyticsLoading(true);
      apiService.getProductAnalytics(id)
        .then((data) => {
          setAnalytics(data);
        })
        .catch((err) => {
          console.error("Error loading analytics:", err);
        })
        .finally(() => {
          setAnalyticsLoading(false);
        });
    }
  }, [id]);

  useEffect(() => {
    if (id && isAuthenticated) {
      apiService.getSavedProducts()
        .then((saved) => {
          setIsSaved(saved.some(sp => sp.productId === id));
        })
        .catch(err => console.error("Error checking saved state:", err));
    }
  }, [id, isAuthenticated]);

  const fetchWatchlistState = async () => {
    if (id && isAuthenticated) {
      try {
        const watchlists = await apiService.getWatchlists();
        const matched = watchlists.find(w => w.productId === id);
        if (matched) {
          setIsTracking(true);
          setWatchlistEntry(matched);
          setTargetPriceInput(matched.targetPrice.toString());
        } else {
          setIsTracking(false);
          setWatchlistEntry(null);
        }
      } catch (err) {
        console.error("Error checking watchlist state:", err);
      }
    }
  };

  useEffect(() => {
    fetchWatchlistState();
  }, [id, isAuthenticated, product]);

  const handleToggleSave = async () => {
    if (!isAuthenticated) {
      navigate('/login', { state: { from: { pathname: `/product/${id}` } } });
      return;
    }
    
    setSaving(true);
    try {
      if (isSaved) {
        await apiService.removeProduct(id!);
        setIsSaved(false);
      } else {
        await apiService.saveProduct(id!);
        setIsSaved(true);
      }
    } catch (err) {
      console.error("Failed to toggle save state:", err);
    } finally {
      setSaving(false);
    }
  };

  const handleOpenTrackingModal = () => {
    if (!isAuthenticated) {
      navigate('/login', { state: { from: { pathname: `/product/${id}` } } });
      return;
    }
    
    // Set default target price if not set
    if (watchlistEntry) {
      setTargetPriceInput(watchlistEntry.targetPrice.toString());
    } else {
      const best = lowestPrice || (product && product.prices && product.prices[0] ? getDisplayPrice(product.prices[0].currentPrice, currency) : 0);
      setTargetPriceInput(Math.floor(best * 0.9).toString());
    }
    setTrackingError(null);
    setIsTrackingModalOpen(true);
  };

  const handleSaveTracking = async (e: React.FormEvent) => {
    e.preventDefault();
    const target = parseFloat(targetPriceInput);
    if (isNaN(target) || target <= 0) {
      setTrackingError("Target price must be greater than zero");
      return;
    }

    const bestLocal = lowestPrice || (product && product.prices && product.prices[0] ? getDisplayPrice(product.prices[0].currentPrice, currency) : 0);
    if (target >= bestLocal) {
      setTrackingError(`Target price must be strictly less than the current best price (${formatPrice(bestLocal, currency)})`);
      return;
    }

    setIsSubmittingTracking(true);
    setTrackingError(null);

    try {
      if (isTracking && watchlistEntry) {
        // Update existing watchlist
        const updated = await apiService.updateWatchlist(watchlistEntry.id, target);
        setWatchlistEntry(updated);
      } else {
        // Create new watchlist
        const created = await apiService.createWatchlist(id!, target);
        setIsTracking(true);
        setWatchlistEntry(created);
      }
      setIsTrackingModalOpen(false);
    } catch (err: any) {
      setTrackingError(err.message || "Failed to update tracking preference");
    } finally {
      setIsSubmittingTracking(false);
    }
  };

  const handleRemoveTracking = async () => {
    if (!watchlistEntry) return;
    setIsSubmittingTracking(true);
    setTrackingError(null);
    try {
      await apiService.deleteWatchlist(watchlistEntry.id);
      setIsTracking(false);
      setWatchlistEntry(null);
      setIsTrackingModalOpen(false);
    } catch (err: any) {
      setTrackingError(err.message || "Failed to remove watchlist entry");
    } finally {
      setIsSubmittingTracking(false);
    }
  };

  // Motion animation config
  const pageVariants = {
    hidden: { opacity: 0, y: 15 },
    visible: { 
      opacity: 1, 
      y: 0,
      transition: {
        type: 'spring' as const,
        stiffness: 80,
        damping: 15,
        staggerChildren: 0.1
      }
    }
  };

  const childVariants = {
    hidden: { opacity: 0, y: 10 },
    visible: { 
      opacity: 1, 
      y: 0,
      transition: { type: 'spring' as const, stiffness: 100, damping: 15 }
    }
  };

  // Helper to check original currency and display correctly using the utils helper
  const getDisplayPriceVal = (val: number) => {
    return getDisplayPrice(val, currency);
  };

  if (loading) {
    return (
      <div className="flex flex-col gap-8 py-6 max-w-5xl mx-auto w-full">
        {/* Back Link Skeleton */}
        <div className="h-4 w-28 bg-zinc-900 rounded-lg animate-pulse" />
        
        {/* Product Info Skeleton */}
        <div className="grid grid-cols-1 md:grid-cols-2 gap-12 border-b border-zinc-900 pb-12">
          {/* Image skeleton */}
          <div className="aspect-[4/3] rounded-2xl bg-zinc-950 border border-zinc-900 relative overflow-hidden">
            <div className="absolute inset-0 bg-gradient-to-r from-transparent via-zinc-900/20 to-transparent -translate-x-full animate-shimmer" />
          </div>
          
          {/* Details skeleton */}
          <div className="flex flex-col gap-6 justify-center">
            <div className="flex flex-col gap-3">
              <div className="h-5 w-24 bg-zinc-900 rounded-lg relative overflow-hidden">
                <div className="absolute inset-0 bg-gradient-to-r from-transparent via-zinc-900/20 to-transparent -translate-x-full animate-shimmer" />
              </div>
              <div className="h-9 w-5/6 bg-zinc-900 rounded-xl relative overflow-hidden">
                <div className="absolute inset-0 bg-gradient-to-r from-transparent via-zinc-900/20 to-transparent -translate-x-full animate-shimmer" />
              </div>
              <div className="h-4 w-1/3 bg-zinc-900 rounded-lg relative overflow-hidden">
                <div className="absolute inset-0 bg-gradient-to-r from-transparent via-zinc-900/20 to-transparent -translate-x-full animate-shimmer" />
              </div>
            </div>

            <div className="flex flex-col gap-2">
              <div className="h-3.5 w-full bg-zinc-900 rounded relative overflow-hidden">
                <div className="absolute inset-0 bg-gradient-to-r from-transparent via-zinc-900/20 to-transparent -translate-x-full animate-shimmer" />
              </div>
              <div className="h-3.5 w-5/6 bg-zinc-900 rounded relative overflow-hidden">
                <div className="absolute inset-0 bg-gradient-to-r from-transparent via-zinc-900/20 to-transparent -translate-x-full animate-shimmer" />
              </div>
            </div>

            <div className="grid grid-cols-2 gap-4 mt-2">
              <div className="h-16 bg-zinc-950 border border-zinc-900 rounded-xl relative overflow-hidden">
                <div className="absolute inset-0 bg-gradient-to-r from-transparent via-zinc-900/20 to-transparent -translate-x-full animate-shimmer" />
              </div>
              <div className="h-16 bg-zinc-950 border border-zinc-900 rounded-xl relative overflow-hidden">
                <div className="absolute inset-0 bg-gradient-to-r from-transparent via-zinc-900/20 to-transparent -translate-x-full animate-shimmer" />
              </div>
            </div>
          </div>
        </div>

        {/* Comparative Table/Cards Skeleton */}
        <div className="flex flex-col gap-4">
          <div className="h-5 w-40 bg-zinc-900 rounded-lg relative overflow-hidden">
            <div className="absolute inset-0 bg-gradient-to-r from-transparent via-zinc-900/20 to-transparent -translate-x-full animate-shimmer" />
          </div>
          <div className="h-[250px] bg-zinc-950 border border-zinc-900 rounded-2xl relative overflow-hidden">
            <div className="absolute inset-0 bg-gradient-to-r from-transparent via-zinc-900/20 to-transparent -translate-x-full animate-shimmer" />
          </div>
        </div>
      </div>
    );
  }

  if (!product) {
    return (
      <div className="flex flex-col items-center justify-center py-24 gap-5 text-center max-w-md mx-auto">
        <div className="h-12 w-12 rounded-2xl bg-zinc-950 border border-zinc-900 flex items-center justify-center text-rose-500 shadow-lg">
          <AlertCircle className="h-6 w-6" />
        </div>
        <h2 className="text-xl font-bold text-white tracking-tight">Product Unreachable</h2>
        <p className="text-zinc-500 text-xs leading-relaxed">
          The product listing data is currently offline or the unique ID does not exist in our catalog indices.
        </p>
        <button
          onClick={() => navigate('/search')}
          className="flex items-center gap-2 px-5 py-2.5 bg-zinc-900 hover:bg-zinc-100 hover:text-black border border-zinc-800 hover:border-white text-xs font-semibold rounded-xl text-zinc-300 transition-all cursor-pointer shadow-md active:scale-95"
        >
          <ArrowLeft className="h-4 w-4" /> Back to Search Engine
        </button>
      </div>
    );
  }

  // Process prices dynamically for display scaling
  const processedPrices = product.prices
    ? product.prices.map((p) => ({
        ...p,
        currentPrice: getDisplayPriceVal(p.currentPrice),
        originalPrice: getDisplayPriceVal(p.originalPrice),
      })).sort((a, b) => a.currentPrice - b.currentPrice)
    : [];

  const lowestPrice = processedPrices[0]?.currentPrice || 0;
  const highestPrice = processedPrices[processedPrices.length - 1]?.currentPrice || 0;
  const lowestPriceId = processedPrices[0]?.id;

  return (
    <motion.div 
      variants={pageVariants}
      initial="hidden"
      animate="visible"
      className="flex flex-col gap-10 py-6 max-w-5xl mx-auto w-full"
    >
      {/* Back Button */}
      <div>
        <button
          onClick={() => navigate(-1)}
          className="inline-flex items-center gap-2 text-xs font-bold text-zinc-500 hover:text-zinc-300 transition-colors cursor-pointer group"
        >
          <ArrowLeft className="h-4 w-4 transition-transform group-hover:-translate-x-0.5" /> 
          Back to listings
        </button>
      </div>

      {/* Product Information */}
      <div className="grid grid-cols-1 md:grid-cols-2 gap-12 items-center border-b border-zinc-900/60 pb-12">
        {/* Left: Product Image */}
        <motion.div 
          variants={childVariants}
          className="aspect-[4/3] rounded-2xl overflow-hidden bg-zinc-950 border border-zinc-900/80 shadow-2xl relative group"
        >
          <div className="absolute inset-0 bg-gradient-to-t from-black/25 to-transparent pointer-events-none" />
          <img
            src={product.imageUrl}
            alt={product.name}
            className="w-full h-full object-cover group-hover:scale-102 transition-transform duration-700"
          />
        </motion.div>

        {/* Right: Details */}
        <motion.div 
          variants={childVariants}
          className="flex flex-col gap-6"
        >
          <div>
            <span className="px-2.5 py-1 rounded bg-zinc-900 border border-zinc-800 text-[10px] text-zinc-400 font-bold tracking-widest uppercase shadow-inner">
              {product.category}
            </span>
            <div className="flex items-start justify-between gap-4 mt-4 mb-2">
              <h1 className="text-3xl font-extrabold tracking-tight text-white leading-tight">
                {product.name}
              </h1>
              <div className="flex items-center gap-2 shrink-0">
                <button
                  onClick={handleOpenTrackingModal}
                  className={`flex items-center gap-1.5 px-4.5 py-3 rounded-xl border text-xs font-bold transition-all cursor-pointer active:scale-95 ${
                    isTracking
                      ? 'bg-emerald-500/10 border-emerald-500/30 text-emerald-400 hover:bg-emerald-500/20'
                      : 'bg-white hover:bg-zinc-200 text-black border-transparent font-extrabold shadow-md'
                  }`}
                  title="Track Price & Get Notified"
                >
                  <Bell className={`h-4 w-4 ${isTracking ? 'fill-current' : ''}`} />
                  <span>{isTracking ? `Tracking at ${formatPrice(watchlistEntry?.targetPrice, currency)}` : 'Track Price'}</span>
                </button>

                <button
                  onClick={handleToggleSave}
                  disabled={saving}
                  className={`flex items-center justify-center p-3 rounded-xl border transition-all cursor-pointer active:scale-95 shrink-0 ${
                    isSaved 
                      ? 'bg-rose-500/10 border-rose-500/30 text-rose-400 hover:bg-rose-500/20' 
                      : 'bg-zinc-900 border-zinc-800 text-zinc-400 hover:text-white hover:border-zinc-700'
                  }`}
                  title={isSaved ? "Remove from Saved" : "Save Product"}
                >
                  <Heart className={`h-5 w-5 ${isSaved ? 'fill-current text-rose-500' : ''} ${saving ? 'animate-pulse' : ''}`} />
                </button>
              </div>
            </div>
            <div className="flex items-center gap-2 text-xs font-bold tracking-wider text-zinc-500 uppercase">
              <span>Brand: {product.brand}</span>
            </div>
          </div>

          <div className="flex flex-col gap-2">
            <h3 className="text-xs font-bold text-zinc-500 uppercase tracking-widest">Overview</h3>
            <p className="text-sm text-zinc-400 leading-relaxed font-normal">
              {product.description}
            </p>
          </div>

          {/* Quick Stats */}
          {processedPrices.length > 0 && (
            <div className="grid grid-cols-2 gap-4 p-4.5 rounded-2xl bg-zinc-950/40 border border-zinc-900/80 backdrop-blur-sm">
              <div className="flex flex-col">
                <span className="text-[10px] text-zinc-500 uppercase font-bold tracking-wider mb-0.5">Best Price</span>
                <span className="text-xl font-extrabold text-emerald-400">
                  {formatPrice(lowestPrice, currency)}
                </span>
              </div>
              <div className="flex flex-col">
                <span className="text-[10px] text-zinc-500 uppercase font-bold tracking-wider mb-0.5">Market Range</span>
                <span className="text-xl font-extrabold text-zinc-400">
                  {formatPrice(lowestPrice, currency)} - {formatPrice(highestPrice, currency)}
                </span>
              </div>
            </div>
          )}
        </motion.div>
      </div>

      {/* Comparison Section */}
      <motion.div 
        variants={childVariants}
        className="flex flex-col gap-6"
      >
        <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-4 border-b border-zinc-900/60 pb-5">
          <div>
            <h2 className="text-xl font-bold text-white tracking-tight flex items-center gap-2">
              <ShoppingBag className="h-5 w-5 text-zinc-400" />
              Compare Seller Prices
            </h2>
            <p className="text-xs text-zinc-500 mt-0.5">Aggregated retail offers verified in real-time</p>
          </div>

          <div className="flex items-center gap-3.5 self-end sm:self-auto">
            {/* Currency Selector */}
            <div className="flex items-center rounded-xl bg-zinc-950/80 border border-zinc-900 p-1 gap-1">
              {(['USD', 'INR', 'EUR', 'GBP', 'JPY'] as CurrencyCode[]).map((cur) => (
                <button
                  key={cur}
                  onClick={() => setCurrency(cur)}
                  className={`px-2 py-1.5 text-[10px] font-bold rounded-lg transition-all cursor-pointer ${
                    currency === cur
                      ? 'bg-zinc-850 text-white border border-zinc-800 shadow-inner'
                      : 'text-zinc-500 hover:text-zinc-300'
                  }`}
                >
                  {CURRENCY_SYMBOLS[cur]} {cur}
                </button>
              ))}
            </div>

            {/* Layout View Mode Switcher */}
            <div className="flex items-center rounded-xl bg-zinc-950/80 border border-zinc-900 p-1">
              <button
                onClick={() => setViewMode('card')}
                className={`p-1.5 rounded-lg transition-all ${
                  viewMode === 'card'
                    ? 'bg-zinc-855 text-white border border-zinc-800 shadow-inner'
                    : 'text-zinc-500 hover:text-zinc-300'
                }`}
                title="Card Grid View"
              >
                <LayoutGrid className="h-4.5 w-4.5" />
              </button>
              <button
                onClick={() => setViewMode('table')}
                className={`p-1.5 rounded-lg transition-all ${
                  viewMode === 'table'
                    ? 'bg-zinc-855 text-white border border-zinc-800 shadow-inner'
                    : 'text-zinc-500 hover:text-zinc-300'
                }`}
                title="Detailed Table View"
              >
                <List className="h-4.5 w-4.5" />
              </button>
            </div>
          </div>
        </div>

        {processedPrices.length > 0 ? (
          <AnimatePresence mode="wait">
            {viewMode === 'card' ? (
              <motion.div
                key="card-grid"
                initial={{ opacity: 0, y: 10 }}
                animate={{ opacity: 1, y: 0 }}
                exit={{ opacity: 0, y: -10 }}
                transition={{ duration: 0.2 }}
                className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-6 animate-fade-in"
              >
                {processedPrices.map((price) => (
                  <SellerCard
                    key={price.id}
                    price={price}
                    isBestDeal={price.id === lowestPriceId}
                    lowestPrice={lowestPrice}
                    currency={currency}
                  />
                ))}
              </motion.div>
            ) : (
              <motion.div
                key="detailed-table"
                initial={{ opacity: 0, y: 10 }}
                animate={{ opacity: 1, y: 0 }}
                exit={{ opacity: 0, y: -10 }}
                transition={{ duration: 0.2 }}
                className="overflow-hidden rounded-2xl border border-zinc-900 bg-zinc-950/30 shadow-2xl backdrop-blur-xl animate-fade-in"
              >
                <div className="overflow-x-auto">
                  <table className="w-full text-left border-collapse">
                    <thead>
                      <tr className="border-b border-zinc-900 bg-zinc-950/60 text-[10px] font-bold uppercase tracking-widest text-zinc-500">
                        <th className="px-6 py-4.5">Seller</th>
                        <th className="px-6 py-4.5">Listed Price</th>
                        <th className="px-6 py-4.5">Savings & Difference</th>
                        <th className="px-6 py-4.5">Sync Frequency</th>
                        <th className="px-6 py-4.5 text-right">Direct Storefront</th>
                      </tr>
                    </thead>
                    <tbody className="divide-y divide-zinc-900/60 text-sm">
                      {processedPrices.map((price) => {
                        const isLowest = price.id === lowestPriceId;
                        const savings = price.originalPrice > price.currentPrice 
                          ? price.originalPrice - price.currentPrice 
                          : 0;
                        const diffFromLowest = price.currentPrice - lowestPrice;

                        return (
                          <tr
                            key={price.id}
                            className={`transition-colors duration-200 hover:bg-zinc-900/10 ${
                              isLowest ? 'bg-emerald-500/[0.015]' : ''
                            }`}
                          >
                            {/* Seller Logo & Name */}
                            <td className="px-6 py-4.5 flex items-center gap-3.5 font-medium text-white">
                              {price.seller?.logoUrl ? (
                                <div className="h-7 w-14 flex items-center justify-center bg-zinc-950 border border-zinc-900 rounded-lg p-1.5 shadow-sm">
                                  <img
                                    src={price.seller.logoUrl}
                                    alt={price.seller.name}
                                    className="max-h-full max-w-full object-contain filter brightness-95"
                                  />
                                </div>
                              ) : (
                                <span className="h-7 w-14 flex items-center justify-center bg-zinc-900 border border-zinc-800 rounded-lg text-[9px] font-bold text-zinc-500 uppercase tracking-widest">
                                  {price.seller?.name.substring(0, 3)}
                                </span>
                              )}
                              <div className="flex flex-col">
                                <span className="font-semibold text-zinc-200">{price.seller?.name}</span>
                                {isLowest && (
                                  <span className="inline-flex items-center gap-1 text-[9px] font-extrabold text-emerald-400 mt-0.5 tracking-wide uppercase">
                                    <Sparkles className="h-2.5 w-2.5" /> Best Deal
                                  </span>
                                )}
                              </div>
                            </td>

                            {/* Price & Original Price */}
                            <td className="px-6 py-4.5">
                              <div className="flex items-baseline gap-2">
                                <span className="font-extrabold text-white text-base">
                                  {formatPrice(price.currentPrice, currency)}
                                </span>
                                {price.originalPrice > price.currentPrice && (
                                  <span className="text-xs text-zinc-500 line-through font-normal">
                                    {formatPrice(price.originalPrice, currency)}
                                  </span>
                                )}
                              </div>
                            </td>

                            {/* Savings & Difference */}
                            <td className="px-6 py-4.5">
                              <div className="flex flex-col gap-1">
                                {savings > 0 ? (
                                  <span className="inline-flex items-center gap-1.5 text-emerald-400 text-xs font-bold">
                                    <Tag className="h-3 w-3" /> Save {formatPrice(savings, currency)} ({price.discountPercentage.toFixed(0)}% off)
                                  </span>
                                ) : (
                                  <span className="text-zinc-500 text-xs">Standard Price</span>
                                )}
                                {!isLowest && diffFromLowest > 0 && (
                                  <span className="text-[10px] text-rose-400/90 font-semibold">
                                    {formatPrice(diffFromLowest, currency)} more than best deal
                                  </span>
                                )}
                                {isLowest && (
                                  <span className="text-[10px] text-emerald-400 font-semibold flex items-center gap-1">
                                    <Sparkles className="h-2.5 w-2.5 animate-pulse" /> Lowest price guaranteed
                                  </span>
                                )}
                              </div>
                            </td>

                            {/* Last Updated */}
                            <td className="px-6 py-4.5 text-zinc-500 text-xs">
                              <span className="inline-flex items-center gap-1.5 font-medium">
                                <Clock className="h-3.5 w-3.5 text-zinc-600" /> {price.lastUpdated}
                              </span>
                            </td>

                            {/* Redirect button */}
                            <td className="px-6 py-4.5 text-right">
                              <a
                                href={price.productUrl}
                                target="_blank"
                                rel="noopener noreferrer"
                                onClick={() => apiService.trackSellerClick(price.id)}
                                className={`inline-flex items-center gap-1.5 px-4 py-2 text-xs font-bold rounded-xl border transition-all active:scale-95 ${
                                  isLowest
                                    ? 'bg-white text-black hover:bg-zinc-200 border-white shadow-md'
                                    : 'bg-zinc-900 text-zinc-300 hover:bg-zinc-800 border-zinc-800 hover:text-white'
                                }`}
                              >
                                Visit Seller
                                <ExternalLink className="h-3 w-3" />
                              </a>
                            </td>
                          </tr>
                        );
                      })}
                    </tbody>
                  </table>
                </div>
              </motion.div>
            )}
          </AnimatePresence>
        ) : (
          <div className="flex flex-col items-center justify-center py-12 px-6 border border-zinc-900 border-dashed rounded-2xl bg-zinc-950/10 text-center">
            <ShoppingBag className="h-8 w-8 text-zinc-600 mb-3" />
            <h4 className="text-sm font-semibold text-zinc-300 mb-1">No Active Sellers</h4>
            <p className="text-xs text-zinc-500 max-w-xs leading-relaxed">
              We couldn't locate any live purchase links for this product listing. Please check back later.
            </p>
          </div>
        )}
      </motion.div>

      {product && (
        <motion.div 
          variants={childVariants}
          className="flex flex-col gap-6"
        >
          <div className="border-b border-zinc-900 pb-5">
            <h2 className="text-xl font-bold text-white tracking-tight flex items-center gap-2">
              <Activity className="h-5 w-5 text-zinc-400" />
              Product Interaction & Metrics
            </h2>
            <p className="text-xs text-zinc-500 mt-0.5">Real-time demand analytics and price volatility indicators</p>
          </div>

          {analyticsLoading ? (
            <div className="grid grid-cols-1 md:grid-cols-3 gap-6">
              <div className="md:col-span-1 h-36 rounded-2xl bg-zinc-950/40 border border-zinc-900 animate-pulse" />
              <div className="md:col-span-2 grid grid-cols-2 gap-4">
                {[1, 2, 3, 4].map(n => (
                  <div key={n} className="h-16 rounded-xl bg-zinc-950/40 border border-zinc-900 animate-pulse" />
                ))}
              </div>
            </div>
          ) : !analytics ? (
            <div className="flex flex-col items-center justify-center py-6 px-4 border border-zinc-900 border-dashed rounded-2xl bg-zinc-950/10 text-center">
              <p className="text-xs text-zinc-500">Analytics data is currently unavailable for this product.</p>
            </div>
          ) : (
            <div className="grid grid-cols-1 md:grid-cols-3 gap-6">
              {/* Trending Score Card */}
              <div className="md:col-span-1 p-6 rounded-2xl bg-gradient-to-b from-zinc-900/40 to-zinc-950/80 border border-zinc-900 flex flex-col justify-between gap-4 relative overflow-hidden group shadow-lg">
                <div className="absolute top-[-20%] right-[-20%] w-32 h-32 bg-zinc-800/10 rounded-full blur-xl pointer-events-none" />
                <div className="flex flex-col gap-1.5">
                  <span className="text-[10px] font-bold text-zinc-500 uppercase tracking-widest flex items-center gap-1.5">
                    <Sparkles className="h-3 w-3 text-amber-500" />
                    Trending Score
                  </span>
                  <p className="text-xs text-zinc-500 leading-relaxed mt-1">
                    A higher Trending Score indicates stronger customer interest and recent marketplace activity.
                  </p>
                </div>
                <div className="flex items-baseline gap-2.5 mt-2">
                  <span className="text-4xl font-black text-white tracking-tight font-mono">
                    {analytics.trendingScore}
                  </span>
                  <span className="text-[10px] font-bold text-zinc-400 bg-zinc-900/80 border border-zinc-800 px-2 py-0.5 rounded-full uppercase tracking-wider font-mono">
                    Score
                  </span>
                </div>
              </div>

              {/* Metrics Grid */}
              <div className="md:col-span-2 grid grid-cols-1 sm:grid-cols-2 gap-4">
                {/* Views Card */}
                <div className="p-5 rounded-xl bg-zinc-950/40 border border-zinc-900/80 hover:border-zinc-850 flex items-center justify-between gap-4 transition-all duration-300">
                  <div className="flex flex-col gap-1">
                    <span className="text-[10px] text-zinc-500 uppercase font-bold tracking-wider">Product Views</span>
                    <span className="text-2xl font-black text-zinc-100 font-mono">{analytics.viewCount}</span>
                  </div>
                  <div className="h-10 w-10 flex items-center justify-center rounded-lg bg-zinc-900/60 border border-zinc-800/60 text-zinc-400">
                    <Eye className="h-5 w-5" />
                  </div>
                </div>

                {/* Saves Card */}
                <div className="p-5 rounded-xl bg-zinc-950/40 border border-zinc-900/80 hover:border-zinc-850 flex items-center justify-between gap-4 transition-all duration-300">
                  <div className="flex flex-col gap-1">
                    <span className="text-[10px] text-zinc-500 uppercase font-bold tracking-wider">Saved Count</span>
                    <span className="text-2xl font-black text-rose-400 font-mono">{analytics.saveCount}</span>
                  </div>
                  <div className="h-10 w-10 flex items-center justify-center rounded-lg bg-zinc-900/60 border border-zinc-800/60 text-rose-500/80">
                    <Heart className="h-5 w-5" />
                  </div>
                </div>

                {/* Watchlists Card */}
                <div className="p-5 rounded-xl bg-zinc-950/40 border border-zinc-900/80 hover:border-zinc-850 flex items-center justify-between gap-4 transition-all duration-300">
                  <div className="flex flex-col gap-1">
                    <span className="text-[10px] text-zinc-500 uppercase font-bold tracking-wider">Active Watchlists</span>
                    <span className="text-2xl font-black text-emerald-400 font-mono">{analytics.watchlistCount}</span>
                  </div>
                  <div className="h-10 w-10 flex items-center justify-center rounded-lg bg-zinc-900/60 border border-zinc-800/60 text-emerald-500/80">
                    <Bell className="h-5 w-5" />
                  </div>
                </div>

                {/* Price Changes Card */}
                <div className="p-5 rounded-xl bg-zinc-950/40 border border-zinc-900/80 hover:border-zinc-850 flex items-center justify-between gap-4 transition-all duration-300">
                  <div className="flex flex-col gap-1">
                    <span className="text-[10px] text-zinc-500 uppercase font-bold tracking-wider">Price Updates</span>
                    <span className="text-2xl font-black text-amber-500 font-mono">{analytics.priceChangeCount}</span>
                  </div>
                  <div className="h-10 w-10 flex items-center justify-center rounded-lg bg-zinc-900/60 border border-zinc-800/60 text-amber-500/80">
                    <Clock className="h-5 w-5" />
                  </div>
                </div>
              </div>
            </div>
          )}
        </motion.div>
      )}

      {product && (
        <PriceHistorySection productId={product.id} currency={currency} />
      )}

      {similarProducts.length > 0 && (
        <div className="flex flex-col gap-6 mt-12 border-t border-zinc-900 pt-12 text-left">
          <h2 className="text-xl font-bold text-white flex items-center gap-2">
            <Sparkles className="h-5 w-5 text-indigo-400" />
            Similar Products You Might Like
          </h2>
          <div className="grid grid-cols-2 md:grid-cols-4 gap-6">
            {similarProducts.map((p) => {
              const lowest = p.lowestPrice || (p.prices && p.prices.length > 0 ? Math.min(...p.prices.map(pr => pr.currentPrice)) : 0);
              return (
                <div key={p.id} className="bg-zinc-955 border border-zinc-900 hover:border-zinc-800 p-3 rounded-2xl flex flex-col gap-3 group relative overflow-hidden transition-all hover:bg-zinc-900/10">
                  <div className="aspect-square w-full rounded-xl overflow-hidden bg-zinc-900 border border-zinc-900">
                    <img src={p.imageUrl} alt={p.name} className="h-full w-full object-cover group-hover:scale-103 transition-transform duration-300" />
                  </div>
                  <div className="flex flex-col min-w-0">
                    <Link to={`/product/${p.id}`} className="text-white hover:text-blue-400 text-xs font-bold truncate block">
                      {p.name}
                    </Link>
                    <span className="text-[9px] text-zinc-500 font-bold mt-0.5">{p.brand}</span>
                    <div className="flex justify-between items-baseline mt-2">
                      <span className="text-xs font-mono font-extrabold text-white">
                        {lowest > 0 ? formatPrice(lowest, currency) : 'N/A'}
                      </span>
                      <Link to={`/product/${p.id}`} className="text-[10px] text-blue-450 font-bold hover:underline">
                        View
                      </Link>
                    </div>
                  </div>
                </div>
              );
            })}
          </div>
        </div>
      )}

      {/* Price Watchlist Modal */}
      <AnimatePresence>
        {isTrackingModalOpen && (
          <div className="fixed inset-0 z-50 flex items-center justify-center p-4 bg-black/70 backdrop-blur-md">
            {/* Background click handler to close */}
            <motion.div
              initial={{ opacity: 0 }}
              animate={{ opacity: 1 }}
              exit={{ opacity: 0 }}
              onClick={() => setIsTrackingModalOpen(false)}
              className="absolute inset-0 cursor-default"
            />

            {/* Modal content */}
            <motion.div
              initial={{ opacity: 0, scale: 0.95, y: 10 }}
              animate={{ opacity: 1, scale: 1, y: 0 }}
              exit={{ opacity: 0, scale: 0.95, y: 10 }}
              transition={{ type: 'spring', duration: 0.3 }}
              className="relative z-10 w-full max-w-md overflow-hidden rounded-2xl border border-zinc-900 bg-zinc-950 p-6 shadow-2xl"
            >
              {/* Close button */}
              <button
                onClick={() => setIsTrackingModalOpen(false)}
                className="absolute top-4 right-4 p-1.5 rounded-lg text-zinc-500 hover:text-zinc-300 hover:bg-zinc-900 transition-all cursor-pointer"
              >
                <X className="h-4.5 w-4.5" />
              </button>

              <div className="flex items-center gap-2 mb-4">
                <div className="h-10 w-10 rounded-xl bg-emerald-500/10 border border-emerald-500/20 flex items-center justify-center text-emerald-400">
                  <Bell className="h-5 w-5" />
                </div>
                <div>
                  <h3 className="text-base font-bold text-white leading-none">
                    {isTracking ? 'Edit Price Watchlist' : 'Set Price Watchlist'}
                  </h3>
                  <p className="text-[11px] text-zinc-500 mt-1">
                    Get alerted when this product drops below your target price.
                  </p>
                </div>
              </div>

              <div className="mb-4 p-3.5 rounded-xl bg-zinc-900/40 border border-zinc-900 flex items-center justify-between">
                <div className="flex flex-col">
                  <span className="text-[9px] text-zinc-500 font-bold uppercase tracking-wider">Product</span>
                  <span className="text-xs font-semibold text-zinc-200 line-clamp-1 mt-0.5">{product.name}</span>
                </div>
                <div className="flex flex-col text-right shrink-0">
                  <span className="text-[9px] text-zinc-500 font-bold uppercase tracking-wider">Best Price</span>
                  <span className="text-sm font-extrabold text-emerald-400 mt-0.5">
                    {formatPrice(lowestPrice || product.prices?.[0]?.currentPrice || 0, currency)}
                  </span>
                </div>
              </div>

              <form onSubmit={handleSaveTracking} className="flex flex-col gap-4">
                <div className="flex flex-col gap-2">
                  <label htmlFor="targetPrice" className="text-xs font-bold text-zinc-400">
                    Target Price ({CURRENCY_SYMBOLS[currency]})
                  </label>
                  <div className="relative">
                    <span className="absolute left-3.5 top-1/2 -translate-y-1/2 text-sm text-zinc-500 font-bold">
                      {CURRENCY_SYMBOLS[currency]}
                    </span>
                    <input
                      id="targetPrice"
                      type="number"
                      required
                      placeholder="Enter target price"
                      value={targetPriceInput}
                      onChange={(e) => setTargetPriceInput(e.target.value)}
                      className="w-full pl-8 pr-4 py-2.5 rounded-xl bg-zinc-900 border border-zinc-800 text-sm font-bold text-white placeholder-zinc-600 focus:outline-none focus:border-zinc-700 transition-colors"
                    />
                  </div>
                </div>

                {/* Quick Discounts Suggestions */}
                <div className="flex flex-col gap-2">
                  <span className="text-[10px] font-bold text-zinc-500 uppercase tracking-wider">Quick Select Target</span>
                  <div className="grid grid-cols-3 gap-2">
                    {[0.95, 0.9, 0.85].map((factor) => {
                      const best = lowestPrice || (product.prices?.[0] ? getDisplayPrice(product.prices[0].currentPrice, currency) : 0);
                      const discounted = Math.floor(best * factor);
                      const pct = Math.round((1 - factor) * 100);
                      return (
                        <button
                          key={factor}
                          type="button"
                          onClick={() => setTargetPriceInput(discounted.toString())}
                          className="px-2 py-1.5 rounded-lg border border-zinc-900 hover:border-zinc-800 bg-zinc-955 hover:bg-zinc-900 text-[10px] font-bold text-zinc-400 hover:text-white transition-all cursor-pointer text-center"
                        >
                          {pct}% Off ({formatPrice(discounted, currency)})
                        </button>
                      );
                    })}
                  </div>
                </div>

                {trackingError && (
                  <div className="p-3 rounded-xl bg-rose-500/10 border border-rose-500/20 text-[11px] text-rose-400 flex items-start gap-2">
                    <AlertCircle className="h-4 w-4 shrink-0 mt-0.5" />
                    <span>{trackingError}</span>
                  </div>
                )}

                <div className="flex items-center justify-between gap-3 mt-2 pt-4 border-t border-zinc-900">
                  {isTracking ? (
                    <button
                      type="button"
                      onClick={handleRemoveTracking}
                      disabled={isSubmittingTracking}
                      className="flex items-center gap-1.5 px-4 py-2.5 rounded-xl border border-rose-500/20 bg-rose-500/10 hover:bg-rose-500/20 text-rose-400 text-xs font-bold transition-all cursor-pointer disabled:opacity-50"
                    >
                      <Trash2 className="h-4 w-4" />
                      <span>Remove</span>
                    </button>
                  ) : (
                    <div />
                  )}

                  <div className="flex items-center gap-2">
                    <button
                      type="button"
                      onClick={() => setIsTrackingModalOpen(false)}
                      disabled={isSubmittingTracking}
                      className="px-4 py-2.5 text-xs font-bold text-zinc-500 hover:text-zinc-300 transition-colors cursor-pointer"
                    >
                      Cancel
                    </button>
                    <button
                      type="submit"
                      disabled={isSubmittingTracking}
                      className="px-5 py-2.5 rounded-xl bg-white hover:bg-zinc-200 text-black text-xs font-extrabold transition-all shadow-md cursor-pointer active:scale-95 disabled:opacity-50"
                    >
                      {isSubmittingTracking ? 'Saving...' : isTracking ? 'Update Track' : 'Start Tracking'}
                    </button>
                  </div>
                </div>
              </form>
            </motion.div>
          </div>
        )}
      </AnimatePresence>
    </motion.div>
  );
};

export default ProductPage;
