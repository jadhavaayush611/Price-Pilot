import React, { useEffect, useState, useMemo, useRef } from 'react';
import { useParams, useNavigate, Link } from 'react-router-dom';
import { apiService } from '../services/api';
import type { ProductWithPrices, Watchlist, ProductAnalytics, AlternativeResponse, AlternativeType } from '../types';
import { ArrowLeft, Clock, ExternalLink, Sparkles, Tag, AlertCircle, ShoppingBag, LayoutGrid, List, Heart, Bell, Trash2, X, Eye, Activity, Bot, Layers, TrendingUp, TrendingDown, Split, CheckCircle2, Info, ShieldCheck, Database } from 'lucide-react';
import { motion, AnimatePresence } from 'framer-motion';
import { formatPrice, getSavedCurrency, saveCurrency, getDisplayPrice, type CurrencyCode, CURRENCY_SYMBOLS, CURRENCY_METADATA } from '../currency';
import { SellerCard } from '../components/SellerCard';
import { useAuth } from '../context/AuthContext';
import { PriceHistorySection } from '../components/PriceHistorySection';
import { AlternativeList } from '../components/alternative/AlternativeList';
import { ProductImage } from '../components/common/ProductImage';
import { normalizeProductOffers, formatOfferObservationTime, getProvenanceBadge } from '../marketplace';

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

  // Normalized marketplace offers preserving canonical USD pricing and honest provenance
  const normalizedOffers = useMemo(() => {
    if (!product?.prices || product.prices.length === 0) return [];
    return normalizeProductOffers(product);
  }, [product]);

  const lowestPriceUsd = normalizedOffers[0]?.pricing.basePrice || 0;
  const highestPriceUsd = normalizedOffers[normalizedOffers.length - 1]?.pricing.basePrice || 0;

  const lowestPriceLocal = getDisplayPrice(lowestPriceUsd, currency);
  const highestPriceLocal = getDisplayPrice(highestPriceUsd, currency);
  const [isSaved, setIsSaved] = useState(false);
  const [saving, setSaving] = useState(false);

  // Watchlist specific states & dialog accessibility refs
  const [isTracking, setIsTracking] = useState(false);
  const [watchlistEntry, setWatchlistEntry] = useState<Watchlist | null>(null);
  const [isTrackingModalOpen, setIsTrackingModalOpen] = useState(false);
  const [targetPriceInput, setTargetPriceInput] = useState('');
  const [trackingError, setTrackingError] = useState<string | null>(null);
  const [isSubmittingTracking, setIsSubmittingTracking] = useState(false);
  const dialogRef = useRef<HTMLDivElement>(null);
  const triggerElementRef = useRef<HTMLElement | null>(null);
  const targetPriceInputRef = useRef<HTMLInputElement>(null);

  // Analytics states
  const [analytics, setAnalytics] = useState<ProductAnalytics | null>(null);
  const [analyticsLoading, setAnalyticsLoading] = useState(true);

  // Alternative Finder states (v1.2)
  const [altType, setAltType] = useState<AlternativeType>('SIMILAR');
  const [altPersonalized, setAltPersonalized] = useState(false);
  const [altResponse, setAltResponse] = useState<AlternativeResponse | null>(null);
  const [altLoading, setAltLoading] = useState(true);
  const [altError, setAltError] = useState<string | null>(null);

  useEffect(() => {
    if (id) {
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

      // Fetch intelligence analytics (with price intelligence and engagement metrics)
      apiService.getIntelligenceAnalytics(id)
        .then((data) => {
          setAnalytics(data);
        })
        .catch(() => {
          return apiService.getProductAnalytics(id).then(setAnalytics).catch(() => {});
        })
        .finally(() => {
          setAnalyticsLoading(false);
        });
    }
  }, [id]);

  // Fetch product alternatives with active strategy and personalization preference
  useEffect(() => {
    if (!id) return;
    setAltLoading(true);
    setAltError(null);

    const fetchAlts = async () => {
      try {
        let res: AlternativeResponse;
        if (altPersonalized && isAuthenticated) {
          res = await apiService.getPersonalizedAlternatives(id, { type: altType, limit: 6 });
        } else {
          res = await apiService.getAlternatives(id, { type: altType, limit: 6 });
        }
        setAltResponse(res);
      } catch (err: unknown) {
        const errorObj = err as { response?: { status?: number; data?: { message?: string } }; message?: string };
        setAltError(errorObj.response?.data?.message || errorObj.message || 'Unable to retrieve alternative products.');
      } finally {
        setAltLoading(false);
      }
    };

    fetchAlts();
  }, [id, altType, altPersonalized, isAuthenticated]);


  useEffect(() => {
    if (id && isAuthenticated) {
      apiService.getSavedProducts()
        .then((saved) => {
          setIsSaved(saved.some(sp => sp.productId === id));
        })
        .catch(err => console.error("Error checking saved state:", err));
    }
  }, [id, isAuthenticated]);

  useEffect(() => {
    if (id && isAuthenticated && product) {
      apiService.getWatchlists()
        .then((watchlists) => {
          const matched = watchlists.find(w => w.productId === id);
          if (matched) {
            setIsTracking(true);
            setWatchlistEntry(matched);
            setTargetPriceInput(matched.targetPrice.toString());
          } else {
            setIsTracking(false);
            setWatchlistEntry(null);
          }
        })
        .catch((err) => {
          console.error("Error checking watchlist state:", err);
        });
    }
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

    // Capture triggering element for accessible focus restoration
    triggerElementRef.current = document.activeElement as HTMLElement | null;

    // Set default target price if not set
    if (watchlistEntry) {
      setTargetPriceInput(watchlistEntry.targetPrice.toString());
    } else {
      const best = lowestPriceLocal || (product && product.prices && product.prices[0] ? getDisplayPrice(product.prices[0].currentPrice, currency) : 0);
      setTargetPriceInput(Math.floor(best * 0.9).toString());
    }
    setTrackingError(null);
    setIsTrackingModalOpen(true);
  };

  // Keyboard accessibility: Escape dismissal, focus trapping, and focus restoration
  useEffect(() => {
    if (!isTrackingModalOpen) return;

    // Auto-focus target price input upon dialog mount
    const timer = setTimeout(() => {
      targetPriceInputRef.current?.focus();
    }, 50);

    const handleKeyDown = (e: KeyboardEvent) => {
      if (e.key === 'Escape') {
        setIsTrackingModalOpen(false);
        return;
      }

      if (e.key === 'Tab') {
        if (!dialogRef.current) return;
        const focusableElements = dialogRef.current.querySelectorAll<HTMLElement>(
          'button:not([disabled]), [href], input:not([disabled]), select:not([disabled]), textarea:not([disabled]), [tabindex]:not([tabindex="-1"])'
        );
        if (focusableElements.length === 0) return;

        const firstElement = focusableElements[0];
        const lastElement = focusableElements[focusableElements.length - 1];

        if (e.shiftKey) {
          if (document.activeElement === firstElement) {
            e.preventDefault();
            lastElement.focus();
          }
        } else {
          if (document.activeElement === lastElement) {
            e.preventDefault();
            firstElement.focus();
          }
        }
      }
    };

    window.addEventListener('keydown', handleKeyDown);

    return () => {
      clearTimeout(timer);
      window.removeEventListener('keydown', handleKeyDown);
      // Restore focus to trigger element when dialog closes
      if (triggerElementRef.current && typeof triggerElementRef.current.focus === 'function') {
        triggerElementRef.current.focus();
      }
    };
  }, [isTrackingModalOpen]);

  const handleSaveTracking = async (e: React.FormEvent) => {
    e.preventDefault();
    const target = parseFloat(targetPriceInput);
    if (isNaN(target) || target <= 0) {
      setTrackingError("Target price must be greater than zero");
      return;
    }

    const bestLocal = lowestPriceLocal || (product && product.prices && product.prices[0] ? getDisplayPrice(product.prices[0].currentPrice, currency) : 0);
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
    } catch (err: unknown) {
      const errorObj = err as { message?: string };
      setTrackingError(errorObj.message || "Failed to update tracking preference");
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
    } catch (err: unknown) {
      const errorObj = err as { message?: string };
      setTrackingError(errorObj.message || "Failed to remove watchlist entry");
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

  // Motion animation config

  if (loading) {
    return (
      <div
        role="status"
        aria-busy="true"
        aria-label="Loading product details"
        className="flex flex-col gap-8 py-6 max-w-5xl mx-auto w-full"
      >
        <span className="sr-only">Loading product details...</span>
        {/* Back Link Skeleton */}
        <div className="h-4 w-28 bg-[var(--color-surface-accent)] rounded-lg animate-pulse" />
        
        {/* Product Info Skeleton */}
        <div className="grid grid-cols-1 md:grid-cols-2 gap-12 border-b border-[var(--color-border)] pb-12">
          {/* Image skeleton */}
          <div className="aspect-[4/3] rounded-2xl bg-[var(--color-surface)] border border-[var(--color-border)] relative overflow-hidden">
            <div className="absolute inset-0 bg-gradient-to-r from-transparent via-[var(--color-surface-accent)]/30 to-transparent -translate-x-full animate-shimmer" />
          </div>
          
          {/* Details skeleton */}
          <div className="flex flex-col gap-6 justify-center">
            <div className="flex flex-col gap-3">
              <div className="h-5 w-24 bg-[var(--color-surface-accent)] rounded-lg relative overflow-hidden">
                <div className="absolute inset-0 bg-gradient-to-r from-transparent via-[var(--color-surface-accent)]/30 to-transparent -translate-x-full animate-shimmer" />
              </div>
              <div className="h-9 w-5/6 bg-[var(--color-surface-accent)] rounded-xl relative overflow-hidden">
                <div className="absolute inset-0 bg-gradient-to-r from-transparent via-[var(--color-surface-accent)]/30 to-transparent -translate-x-full animate-shimmer" />
              </div>
            </div>

            {/* Price stats skeleton */}
            <div className="grid grid-cols-2 sm:grid-cols-4 gap-3 p-4 rounded-2xl bg-[var(--color-surface)] border border-[var(--color-border)]">
              {[1, 2, 3, 4].map(n => (
                <div key={n} className="h-12 bg-[var(--color-surface-accent)] rounded-xl relative overflow-hidden">
                  <div className="absolute inset-0 bg-gradient-to-r from-transparent via-[var(--color-surface-accent)]/30 to-transparent -translate-x-full animate-shimmer" />
                </div>
              ))}
            </div>

            {/* Action buttons skeleton */}
            <div className="flex items-center gap-2 flex-wrap">
              <div className="h-10 w-28 bg-[var(--color-surface-accent)] rounded-xl relative overflow-hidden">
                <div className="absolute inset-0 bg-gradient-to-r from-transparent via-[var(--color-surface-accent)]/30 to-transparent -translate-x-full animate-shimmer" />
              </div>
              <div className="h-10 w-36 bg-[var(--color-surface-accent)] rounded-xl relative overflow-hidden">
                <div className="absolute inset-0 bg-gradient-to-r from-transparent via-[var(--color-surface-accent)]/30 to-transparent -translate-x-full animate-shimmer" />
              </div>
              <div className="h-10 w-24 bg-[var(--color-surface-accent)] rounded-xl relative overflow-hidden">
                <div className="absolute inset-0 bg-gradient-to-r from-transparent via-[var(--color-surface-accent)]/30 to-transparent -translate-x-full animate-shimmer" />
              </div>
            </div>

            {/* Overview skeleton */}
            <div className="flex flex-col gap-2">
              <div className="h-3.5 w-full bg-[var(--color-surface-accent)] rounded relative overflow-hidden">
                <div className="absolute inset-0 bg-gradient-to-r from-transparent via-[var(--color-surface-accent)]/30 to-transparent -translate-x-full animate-shimmer" />
              </div>
              <div className="h-3.5 w-4/5 bg-[var(--color-surface-accent)] rounded relative overflow-hidden">
                <div className="absolute inset-0 bg-gradient-to-r from-transparent via-[var(--color-surface-accent)]/30 to-transparent -translate-x-full animate-shimmer" />
              </div>
            </div>
          </div>
        </div>

        {/* Comparative Table/Cards Skeleton */}
        <div className="flex flex-col gap-4">
          <div className="h-5 w-40 bg-[var(--color-surface-accent)] rounded-lg relative overflow-hidden">
            <div className="absolute inset-0 bg-gradient-to-r from-transparent via-[var(--color-surface-accent)]/30 to-transparent -translate-x-full animate-shimmer" />
          </div>
          <div className="h-[250px] bg-[var(--color-surface)] border border-[var(--color-border)] rounded-2xl relative overflow-hidden">
            <div className="absolute inset-0 bg-gradient-to-r from-transparent via-[var(--color-surface-accent)]/30 to-transparent -translate-x-full animate-shimmer" />
          </div>
        </div>
      </div>
    );
  }

  if (!product) {
    return (
      <div
        role="alert"
        className="flex flex-col items-center justify-center py-16 px-6 gap-5 text-center max-w-md mx-auto my-12 rounded-3xl bg-[var(--color-surface)] border border-[var(--color-border)] shadow-xl"
      >
        <div className="h-12 w-12 rounded-2xl bg-[var(--color-surface-accent)] border border-[var(--color-border)] flex items-center justify-center text-rose-500 shadow-md">
          <AlertCircle className="h-6 w-6" />
        </div>
        <div>
          <h2 className="text-xl font-bold text-[var(--color-foreground)] tracking-tight">Product Unreachable</h2>
          <p className="text-[var(--color-muted-foreground)] text-xs leading-relaxed mt-1.5">
            The product listing data is currently offline or the unique ID does not exist in our catalog indices.
          </p>
        </div>
        <button
          onClick={() => navigate('/search')}
          className="flex items-center gap-2 px-5 py-2.5 bg-[var(--color-foreground)] hover:opacity-90 text-[var(--color-background)] border border-transparent text-xs font-bold rounded-xl transition-all cursor-pointer shadow-md active:scale-95 focus-visible:ring-2 focus-visible:ring-[var(--color-ring)] focus-visible:outline-none"
        >
          <ArrowLeft className="h-4 w-4" /> Back to Search Engine
        </button>
      </div>
    );
  }

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
          className="inline-flex items-center gap-2 text-xs font-bold text-[var(--color-muted-foreground)] hover:text-[var(--color-foreground)] transition-colors cursor-pointer group"
        >
          <ArrowLeft className="h-4 w-4 transition-transform group-hover:-translate-x-0.5" /> 
          Back to listings
        </button>
      </div>

      {/* Product Information */}
      <div className="grid grid-cols-1 md:grid-cols-2 gap-12 items-center border-b border-[var(--color-border)] pb-12">
        {/* Left: Product Image */}
        <motion.div 
          variants={childVariants}
          className="aspect-[4/3] rounded-2xl overflow-hidden bg-[var(--color-surface)] border border-[var(--color-border)] shadow-2xl relative group"
        >
          <div className="absolute inset-0 bg-gradient-to-t from-black/25 to-transparent pointer-events-none z-10" />
          <ProductImage
            src={product.imageUrl}
            alt={product.name}
            className="w-full h-full object-contain p-4 group-hover:scale-102 transition-transform duration-700"
            containerClassName="w-full h-full"
            showFallbackText
            fallbackText={product.brand}
          />
        </motion.div>

        {/* Right: Details */}
        <motion.div 
          variants={childVariants}
          className="flex flex-col gap-6"
        >
          <div>
            <div className="flex items-center gap-3">
              <span className="px-2.5 py-1 rounded bg-[var(--color-surface-accent)] border border-[var(--color-border)] text-[10px] text-[var(--color-muted-foreground)] font-bold tracking-widest uppercase shadow-inner">
                {product.category}
              </span>
              <span className="text-xs font-bold tracking-wider text-[var(--color-muted-foreground)] uppercase">
                Brand: {product.brand}
              </span>
            </div>

            <h1 className="text-2xl sm:text-3xl font-extrabold tracking-tight text-[var(--color-foreground)] leading-tight mt-3 break-words">
              {product.name}
            </h1>
          </div>

          {/* Price Summary & Intelligence Strip */}
          {normalizedOffers.length > 0 ? (
            <div className="grid grid-cols-2 sm:grid-cols-4 gap-3 p-4 rounded-2xl bg-[var(--color-surface)] border border-[var(--color-border)] shadow-xs">
              <div className="flex flex-col">
                <span className="text-[10px] text-[var(--color-muted-foreground)] uppercase font-bold tracking-wider mb-0.5">Best Price</span>
                <span className="text-xl sm:text-2xl font-extrabold text-emerald-500 dark:text-emerald-400">
                  {formatPrice(lowestPriceLocal, currency)}
                </span>
              </div>
              <div className="flex flex-col">
                <span className="text-[10px] text-[var(--color-muted-foreground)] uppercase font-bold tracking-wider mb-0.5">Market Range</span>
                <span className="text-xs font-bold text-[var(--color-foreground)] mt-1 truncate">
                  {formatPrice(lowestPriceLocal, currency)} - {formatPrice(highestPriceLocal, currency)}
                </span>
              </div>
              <div className="flex flex-col">
                <span className="text-[10px] text-[var(--color-muted-foreground)] uppercase font-bold tracking-wider mb-0.5">Intelligence</span>
                <div className="flex items-center gap-1.5 mt-0.5">
                  <span className={`text-[10px] font-bold px-1.5 py-0.5 rounded border uppercase ${
                    analytics?.dealQuality === 'GOOD_DEAL'
                      ? 'bg-emerald-500/10 text-emerald-600 dark:text-emerald-400 border-emerald-500/30'
                      : analytics?.dealQuality === 'FAIR_PRICE'
                      ? 'bg-blue-500/10 text-blue-600 dark:text-blue-400 border-blue-500/30'
                      : 'bg-[var(--color-surface-accent)] text-[var(--color-muted-foreground)] border-[var(--color-border)]'
                  }`}>
                    {analytics?.dealQuality ? analytics.dealQuality.replace(/_/g, ' ') : 'VERIFIED'}
                  </span>
                  {analytics?.trend && (
                    <span className="text-[10px] font-semibold text-[var(--color-muted-foreground)] flex items-center gap-0.5">
                      {analytics.trend === 'FALLING' ? <TrendingDown size={11} className="text-emerald-500 dark:text-emerald-400" /> : analytics.trend === 'RISING' ? <TrendingUp size={11} className="text-rose-500 dark:text-rose-400" /> : null}
                      {analytics.trend}
                    </span>
                  )}
                </div>
              </div>
              <div className="flex flex-col">
                <span className="text-[10px] text-[var(--color-muted-foreground)] uppercase font-bold tracking-wider mb-0.5">Availability</span>
                <span className="text-xs font-semibold text-[var(--color-foreground)] mt-1">
                  {normalizedOffers.length} {normalizedOffers.length === 1 ? 'Offer' : 'Offers'} Available
                </span>
              </div>
            </div>
          ) : (
            <div className="flex items-center gap-2.5 p-3.5 rounded-xl bg-[var(--color-surface-accent)] border border-[var(--color-border)] text-xs text-[var(--color-muted-foreground)]">
              <AlertCircle className="h-4 w-4 shrink-0 text-[var(--color-muted-foreground)]" />
              <span>No active seller offers listed for this product at this time.</span>
            </div>
          )}

          {/* Primary & Supporting Actions */}
          <div className="flex items-center gap-2.5 flex-wrap">
            <button
              onClick={handleOpenTrackingModal}
              className={`flex items-center gap-1.5 min-h-[40px] px-4 py-2.5 rounded-xl border text-xs font-bold transition-all cursor-pointer active:scale-95 focus-visible:ring-2 focus-visible:ring-[var(--color-ring)] focus-visible:outline-none ${
                isTracking
                  ? 'bg-emerald-500/10 border-emerald-500/30 text-emerald-600 dark:text-emerald-400 hover:bg-emerald-500/20'
                  : 'bg-[var(--color-foreground)] hover:opacity-90 text-[var(--color-background)] border-transparent font-extrabold shadow-md'
              }`}
              title="Track Price & Get Notified"
            >
              <Bell className={`h-4 w-4 ${isTracking ? 'fill-current' : ''}`} />
              <span>{isTracking ? `Tracking at ${formatPrice(watchlistEntry?.targetPrice || 0, currency)}` : 'Track Price'}</span>
            </button>

            <button
              onClick={() => {
                const el = document.getElementById('alternatives-section');
                if (el) {
                  el.scrollIntoView({ behavior: 'smooth' });
                } else {
                  navigate(`/product/${product.id}/alternatives`);
                }
              }}
              className="flex items-center gap-1.5 min-h-[40px] px-3.5 py-2.5 rounded-xl border border-emerald-500/30 bg-emerald-500/10 hover:bg-emerald-500/20 text-emerald-600 dark:text-emerald-400 text-xs font-bold transition-all cursor-pointer active:scale-95 focus-visible:ring-2 focus-visible:ring-emerald-500 focus-visible:outline-none"
              title="Discover qualified product alternatives"
            >
              <Split className="h-4 w-4 text-emerald-500 dark:text-emerald-400" />
              <span>Find Alternatives</span>
            </button>

            <button
              onClick={() => navigate(`/compare?ids=${product.id}`)}
              className="flex items-center gap-1.5 min-h-[40px] px-3.5 py-2.5 rounded-xl border border-[var(--color-border)] bg-[var(--color-surface-accent)] hover:bg-[var(--color-surface)] text-[var(--color-foreground)] text-xs font-bold transition-all cursor-pointer active:scale-95 focus-visible:ring-2 focus-visible:ring-[var(--color-ring)] focus-visible:outline-none"
              title="Compare with other products"
            >
              <Layers className="h-4 w-4 text-[var(--color-muted-foreground)]" />
              <span>Compare</span>
            </button>

            <button
              onClick={() => {
                const params = new URLSearchParams({
                  productId: product.id,
                  query: 'Analyze price history, deals, and purchase timing for ' + product.name
                });
                navigate(`/assistant?${params.toString()}`);
              }}
              className="flex items-center gap-1.5 min-h-[40px] px-3.5 py-2.5 rounded-xl border border-indigo-500/30 bg-indigo-500/10 hover:bg-indigo-500/20 text-indigo-600 dark:text-indigo-300 text-xs font-bold transition-all cursor-pointer active:scale-95 focus-visible:ring-2 focus-visible:ring-indigo-500 focus-visible:outline-none"
              title="Ask Shopping Decision Assistant"
            >
              <Bot className="h-4 w-4 text-indigo-500 dark:text-indigo-400" />
              <span>Ask AI</span>
            </button>

            <button
              onClick={handleToggleSave}
              disabled={saving}
              className={`flex items-center justify-center min-h-[40px] min-w-[40px] p-2.5 rounded-xl border transition-all cursor-pointer active:scale-95 shrink-0 focus-visible:ring-2 focus-visible:ring-[var(--color-ring)] focus-visible:outline-none ${
                isSaved
                  ? 'bg-rose-500/10 border-rose-500/30 text-rose-500 dark:text-rose-400 hover:bg-rose-500/20'
                  : 'bg-[var(--color-surface-accent)] border-[var(--color-border)] text-[var(--color-muted-foreground)] hover:text-[var(--color-foreground)] hover:border-[var(--color-border)]'
              }`}
              title={isSaved ? "Remove from Saved" : "Save Product"}
              aria-label={isSaved ? "Remove from Saved" : "Save Product"}
            >
              <Heart className={`h-4.5 w-4.5 ${isSaved ? 'fill-current text-rose-500' : ''} ${saving ? 'animate-pulse' : ''}`} />
            </button>
          </div>

          <div className="flex flex-col gap-2">
            <h3 className="text-xs font-bold text-[var(--color-muted-foreground)] uppercase tracking-widest">Overview</h3>
            <p className="text-sm text-[var(--color-muted-foreground)] leading-relaxed font-normal">
              {product.description}
            </p>
          </div>
        </motion.div>
      </div>

      {/* Comparison Section */}
      <motion.div 
        variants={childVariants}
        className="flex flex-col gap-6"
      >
        <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-4 border-b border-[var(--color-border)] pb-5">
          <div>
            <h2 className="text-xl font-bold text-[var(--color-foreground)] tracking-tight flex items-center gap-2">
              <ShoppingBag className="h-5 w-5 text-[var(--color-muted-foreground)]" />
              Compare Seller Prices
            </h2>
            <p className="text-xs text-[var(--color-muted-foreground)] mt-0.5">Aggregated retail offers from tracked stores</p>
          </div>

          <div className="flex items-center gap-3.5 self-end sm:self-auto">
            {/* Currency Selector */}
            <div
              className="flex items-center rounded-xl bg-[var(--color-surface)] border border-[var(--color-border)] p-1 gap-1"
              role="radiogroup"
              aria-label="Display currency selector"
            >
              {(['USD', 'INR', 'EUR', 'GBP', 'JPY'] as CurrencyCode[]).map((cur) => {
                const isSelected = currency === cur;
                return (
                  <button
                    key={cur}
                    onClick={() => setCurrency(cur)}
                    role="radio"
                    aria-checked={isSelected}
                    aria-label={`Display prices in ${CURRENCY_METADATA[cur]?.name || cur} (${CURRENCY_SYMBOLS[cur]})`}
                    className={`px-2.5 py-1.5 text-[10px] font-bold rounded-lg transition-all cursor-pointer focus-visible:ring-2 focus-visible:ring-[var(--color-ring)] focus-visible:outline-none ${
                      isSelected
                        ? 'bg-[var(--color-foreground)] text-[var(--color-background)] shadow-xs'
                        : 'text-[var(--color-muted-foreground)] hover:text-[var(--color-foreground)] hover:bg-[var(--color-surface-accent)]'
                    }`}
                  >
                    {CURRENCY_SYMBOLS[cur]} {cur}
                  </button>
                );
              })}
            </div>

            {/* Layout View Mode Switcher */}
            <div
              className="flex items-center rounded-xl bg-[var(--color-surface)] border border-[var(--color-border)] p-1 gap-1"
              role="group"
              aria-label="Seller offers view mode"
            >
              <button
                onClick={() => setViewMode('card')}
                aria-pressed={viewMode === 'card'}
                aria-label="Card Grid View"
                className={`p-1.5 rounded-lg transition-all cursor-pointer focus-visible:ring-2 focus-visible:ring-[var(--color-ring)] focus-visible:outline-none ${
                  viewMode === 'card'
                    ? 'bg-[var(--color-foreground)] text-[var(--color-background)] shadow-xs'
                    : 'text-[var(--color-muted-foreground)] hover:text-[var(--color-foreground)] hover:bg-[var(--color-surface-accent)]'
                }`}
                title="Card Grid View"
              >
                <LayoutGrid className="h-4 w-4" />
              </button>
              <button
                onClick={() => setViewMode('table')}
                aria-pressed={viewMode === 'table'}
                aria-label="Detailed Table View"
                className={`p-1.5 rounded-lg transition-all cursor-pointer focus-visible:ring-2 focus-visible:ring-[var(--color-ring)] focus-visible:outline-none ${
                  viewMode === 'table'
                    ? 'bg-[var(--color-foreground)] text-[var(--color-background)] shadow-xs'
                    : 'text-[var(--color-muted-foreground)] hover:text-[var(--color-foreground)] hover:bg-[var(--color-surface-accent)]'
                }`}
                title="Detailed Table View"
              >
                <List className="h-4 w-4" />
              </button>
            </div>
          </div>
        </div>

        {normalizedOffers.length > 0 ? (
          <>
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
                {normalizedOffers.map((offer) => (
                  <SellerCard
                    key={offer.id}
                    offer={offer}
                    isBestDeal={Boolean(offer.isBestDeal)}
                    lowestPrice={lowestPriceUsd}
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
                className="overflow-hidden rounded-2xl border border-[var(--color-border)] bg-[var(--color-surface)] shadow-2xl backdrop-blur-xl animate-fade-in"
              >
                <div className="overflow-x-auto">
                  <table className="w-full text-left border-collapse min-w-[620px]">
                    <thead>
                      <tr className="border-b border-[var(--color-border)] bg-[var(--color-surface-accent)] text-[10px] font-bold uppercase tracking-widest text-[var(--color-muted-foreground)]">
                        <th scope="col" className="px-6 py-4.5">Seller</th>
                        <th scope="col" className="px-6 py-4.5">Listed Base Price</th>
                        <th scope="col" className="px-6 py-4.5">Savings &amp; Difference</th>
                        <th scope="col" className="px-6 py-4.5">Observed At</th>
                        <th scope="col" className="px-6 py-4.5 text-right">Direct Storefront</th>
                      </tr>
                    </thead>
                    <tbody className="divide-y divide-[var(--color-border)] text-sm">
                      {normalizedOffers.map((offer) => {
                        const isLowest = Boolean(offer.isBestDeal);
                        const currentPriceLocal = getDisplayPrice(offer.pricing.basePrice, currency);
                        const originalPriceLocal = offer.pricing.originalPrice !== undefined
                          ? getDisplayPrice(offer.pricing.originalPrice, currency)
                          : 0;
                        const savings = offer.savings ? getDisplayPrice(offer.savings, currency) : 0;
                        const diffFromLowest = offer.priceDifferenceFromLowest
                          ? getDisplayPrice(offer.priceDifferenceFromLowest, currency)
                          : 0;
                        const observationTime = formatOfferObservationTime(
                          offer.provenance.observedAt,
                          offer.provenance.rawLastUpdated
                        );
                        const provBadge = getProvenanceBadge(offer.provenance);

                        return (
                          <tr
                            key={offer.id}
                            className={`transition-colors duration-200 hover:bg-[var(--color-surface-accent)]/50 ${
                              isLowest ? 'bg-emerald-500/[0.03]' : ''
                            }`}
                          >
                            {/* Seller Logo & Name */}
                            <td className="px-6 py-4.5 flex items-center gap-3.5 font-medium text-[var(--color-foreground)]">
                              <div className="h-7 w-14 flex items-center justify-center bg-[var(--color-surface)] border border-[var(--color-border)] rounded-lg p-1 shadow-sm overflow-hidden">
                                <ProductImage
                                   src={offer.source.logoUrl}
                                   alt={offer.source.sellerName || 'Seller'}
                                   className="max-h-full max-w-full object-contain filter brightness-95"
                                   showFallbackText
                                   fallbackText={offer.source.sellerName?.substring(0, 3) || 'SEL'}
                                 />
                               </div>
                               <div className="flex flex-col">
                                 <div className="flex items-center gap-2">
                                   <span className="font-semibold text-[var(--color-foreground)]">{offer.source.sellerName}</span>
                                   <span
                                     title={provBadge.description}
                                     className={`inline-flex items-center gap-0.5 px-1.5 py-0.2 rounded text-[9px] font-semibold border ${
                                       offer.provenance.isLiveVerified
                                         ? 'bg-emerald-500/10 text-emerald-700 dark:text-emerald-300 border-emerald-500/25'
                                         : 'bg-[var(--color-surface-accent)] text-[var(--color-muted-foreground)] border-[var(--color-border)]'
                                     }`}
                                   >
                                     {offer.provenance.isLiveVerified ? (
                                       <ShieldCheck className="h-2 w-2 text-emerald-500" />
                                     ) : (
                                       <Database className="h-2 w-2 text-[var(--color-muted-foreground)]" />
                                     )}
                                     {provBadge.label}
                                   </span>
                                 </div>
                                 {isLowest && (
                                   <span className="inline-flex items-center gap-1 text-[9px] font-extrabold text-emerald-500 dark:text-emerald-400 mt-0.5 tracking-wide uppercase">
                                     <Sparkles className="h-2.5 w-2.5" /> Best Deal
                                   </span>
                                 )}
                               </div>
                             </td>

                             {/* Price & Original Price */}
                             <td className="px-6 py-4.5">
                               <div className="flex items-baseline gap-2">
                                 <span className="font-extrabold text-[var(--color-foreground)] text-base">
                                   {formatPrice(currentPriceLocal, currency)}
                                 </span>
                                 {originalPriceLocal > currentPriceLocal && (
                                   <span className="text-xs text-[var(--color-muted-foreground)] line-through font-normal">
                                     {formatPrice(originalPriceLocal, currency)}
                                   </span>
                                 )}
                               </div>
                               <span className="text-[10px] text-[var(--color-muted-foreground)] block font-medium">Base price</span>
                             </td>

                             {/* Savings & Difference */}
                             <td className="px-6 py-4.5">
                               <div className="flex flex-col gap-1">
                                 {savings > 0 ? (
                                   <span className="inline-flex items-center gap-1.5 text-emerald-500 dark:text-emerald-400 text-xs font-bold">
                                     <Tag className="h-3 w-3" /> Save {formatPrice(savings, currency)} ({offer.pricing.discountPercentage.toFixed(0)}% off)
                                   </span>
                                 ) : (
                                   <span className="text-[var(--color-muted-foreground)] text-xs">Standard Price</span>
                                 )}
                                 {!isLowest && diffFromLowest > 0 && (
                                   <span className="text-[10px] text-rose-500 dark:text-rose-400/90 font-semibold">
                                     {formatPrice(diffFromLowest, currency)} more than best deal
                                   </span>
                                 )}
                                 {isLowest && (
                                   <span className="text-[10px] text-emerald-500 dark:text-emerald-400 font-semibold flex items-center gap-1">
                                     <Sparkles className="h-2.5 w-2.5 animate-pulse" /> Lowest listed base price
                                   </span>
                                 )}
                                 {offer.availability.status !== 'unknown' && (
                                   <span className={`inline-flex items-center gap-1 text-[10px] font-medium ${offer.availability.isAvailable ? 'text-emerald-600 dark:text-emerald-400' : 'text-zinc-500'}`}>
                                     {offer.availability.isAvailable ? <CheckCircle2 className="h-2.5 w-2.5" /> : <AlertCircle className="h-2.5 w-2.5" />}
                                     {offer.availability.isAvailable ? 'In Stock' : 'Out of Stock'}
                                   </span>
                                 )}
                               </div>
                             </td>

                             {/* Last Updated */}
                             <td className="px-6 py-4.5 text-[var(--color-muted-foreground)] text-xs">
                               <span className="inline-flex items-center gap-1.5 font-medium">
                                 <Clock className="h-3.5 w-3.5 text-[var(--color-muted-foreground)]" /> {observationTime}
                               </span>
                             </td>

                             {/* Redirect button */}
                             <td className="px-6 py-4.5 text-right">
                               <a
                                 href={offer.source.offerUrl}
                                 target="_blank"
                                 rel="noopener noreferrer"
                                 onClick={() => apiService.trackSellerClick(offer.id)}
                                 aria-label={`Visit ${offer.source.sellerName || 'Seller'} storefront`}
                                 className={`inline-flex items-center gap-1.5 px-4 py-2 text-xs font-bold rounded-xl border transition-all active:scale-95 focus-visible:ring-2 focus-visible:ring-[var(--color-ring)] focus-visible:outline-none ${
                                   isLowest
                                     ? 'bg-[var(--color-foreground)] text-[var(--color-background)] hover:opacity-90 border-[var(--color-foreground)] shadow-md'
                                     : 'bg-[var(--color-surface-accent)] text-[var(--color-foreground)] hover:bg-[var(--color-surface)] border-[var(--color-border)]'
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
           <div className="flex items-center gap-2 mt-3 text-xs text-[var(--color-muted-foreground)]">
             <Info className="h-3.5 w-3.5 shrink-0 text-[var(--color-muted-foreground)]" />
             <p>
               Listed prices represent base prices in {CURRENCY_METADATA[currency]?.name || currency}. Additional shipping, taxes, and merchant fees may apply at checkout.
             </p>
           </div>
         </>
         ) : (
           <div className="flex flex-col items-center justify-center py-12 px-6 border border-[var(--color-border)] border-dashed rounded-2xl bg-[var(--color-surface)] text-center">
             <ShoppingBag className="h-8 w-8 text-[var(--color-muted-foreground)] mb-3" />
             <h4 className="text-sm font-semibold text-[var(--color-foreground)] mb-1">No Active Sellers</h4>
             <p className="text-xs text-[var(--color-muted-foreground)] max-w-xs leading-relaxed">
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
          <div className="border-b border-[var(--color-border)] pb-5">
            <h2 className="text-xl font-bold text-[var(--color-foreground)] tracking-tight flex items-center gap-2">
              <Activity className="h-5 w-5 text-[var(--color-muted-foreground)]" />
              Product Interaction & Metrics
            </h2>
            <p className="text-xs text-[var(--color-muted-foreground)] mt-0.5">Catalog demand analytics and price trend indicators</p>
          </div>

          {analyticsLoading ? (
            <div className="grid grid-cols-2 sm:grid-cols-4 gap-4">
              {[1, 2, 3, 4].map(n => (
                <div key={n} className="h-20 rounded-xl bg-[var(--color-surface)] border border-[var(--color-border)] animate-pulse" />
              ))}
            </div>
          ) : !analytics ? (
            <div className="p-4 border border-[var(--color-border)] rounded-xl bg-[var(--color-surface)] text-center text-xs text-[var(--color-muted-foreground)]">
              Analytics data is currently unavailable for this product.
            </div>
          ) : (
            <div className="grid grid-cols-2 sm:grid-cols-4 gap-4">
              {/* Views */}
              <div className="p-4 rounded-xl bg-[var(--color-surface)] border border-[var(--color-border)] flex items-center justify-between">
                <div>
                  <span className="text-[10px] text-[var(--color-muted-foreground)] uppercase font-bold tracking-wider block">Views</span>
                  <span className="text-xl font-bold text-[var(--color-foreground)] font-mono">{analytics.viewCount}</span>
                </div>
                <Eye className="h-4 w-4 text-[var(--color-muted-foreground)]" />
              </div>

              {/* Saves */}
              <div className="p-4 rounded-xl bg-[var(--color-surface)] border border-[var(--color-border)] flex items-center justify-between">
                <div>
                  <span className="text-[10px] text-[var(--color-muted-foreground)] uppercase font-bold tracking-wider block">Saves</span>
                  <span className="text-xl font-bold text-rose-500 dark:text-rose-400 font-mono">{analytics.saveCount}</span>
                </div>
                <Heart className="h-4 w-4 text-rose-500/80" />
              </div>

              {/* Watchlists */}
              <div className="p-4 rounded-xl bg-[var(--color-surface)] border border-[var(--color-border)] flex items-center justify-between">
                <div>
                  <span className="text-[10px] text-[var(--color-muted-foreground)] uppercase font-bold tracking-wider block">Watchlists</span>
                  <span className="text-xl font-bold text-emerald-500 dark:text-emerald-400 font-mono">{analytics.watchlistCount}</span>
                </div>
                <Bell className="h-4 w-4 text-emerald-500/80" />
              </div>

              {/* Trending */}
              <div className="p-4 rounded-xl bg-[var(--color-surface)] border border-[var(--color-border)] flex items-center justify-between">
                <div>
                  <span className="text-[10px] text-[var(--color-muted-foreground)] uppercase font-bold tracking-wider block">Trending Score</span>
                  <span className="text-xl font-bold text-amber-500 dark:text-amber-400 font-mono">{analytics.trendingScore}</span>
                </div>
                <Sparkles className="h-4 w-4 text-amber-500/80" />
              </div>
            </div>
          )}
        </motion.div>
      )}

      {product && analytics && (analytics.historicalMin !== undefined || analytics.purchaseSignal) && (
        <motion.div
          variants={childVariants}
          className="w-full rounded-3xl border border-[var(--color-border)] bg-[var(--color-surface)] p-6 shadow-xl backdrop-blur-xl mt-8 space-y-4 text-left"
        >
          <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-3 border-b border-[var(--color-border)] pb-4">
            <div className="flex items-center gap-2.5">
              <div className="h-8 w-8 rounded-xl bg-emerald-500/10 border border-emerald-500/20 flex items-center justify-center text-emerald-600 dark:text-emerald-400">
                <Sparkles className="h-4 w-4" />
              </div>
              <div>
                <h3 className="text-base font-bold text-[var(--color-foreground)] tracking-tight">Price Intelligence</h3>
                <p className="text-xs text-[var(--color-muted-foreground)]">Market valuation and purchase timing analysis</p>
              </div>
            </div>

            <div className="flex items-center gap-2">
              {analytics.purchaseSignal && (
                <span className={`px-3 py-1 rounded-full text-xs font-black uppercase tracking-wider border flex items-center gap-1.5 ${
                  analytics.purchaseSignal === 'BUY_NOW'
                    ? 'bg-emerald-500/10 border-emerald-500/30 text-emerald-600 dark:text-emerald-300'
                    : analytics.purchaseSignal === 'GOOD_TIME'
                    ? 'bg-teal-500/10 border-teal-500/30 text-teal-600 dark:text-teal-300'
                    : analytics.purchaseSignal === 'WAIT'
                    ? 'bg-amber-500/10 border-amber-500/30 text-amber-600 dark:text-amber-300'
                    : 'bg-[var(--color-surface-accent)] border-[var(--color-border)] text-[var(--color-muted-foreground)]'
                }`}>
                  {analytics.purchaseSignal.replace(/_/g, ' ')}
                </span>
              )}
              <Link
                to={`/analytics/${product.id}`}
                className="text-xs font-semibold text-emerald-600 dark:text-emerald-400 hover:text-emerald-500 dark:hover:text-emerald-300 px-3 py-1 rounded-lg bg-[var(--color-surface-accent)] border border-[var(--color-border)] hover:border-[var(--color-border)] transition-colors"
              >
                Full Analytics &rarr;
              </Link>
            </div>
          </div>

          <div className="grid grid-cols-1 sm:grid-cols-3 gap-3">
            <div className="bg-[var(--color-surface-accent)] border border-[var(--color-border)] rounded-xl p-3">
              <span className="text-[11px] text-[var(--color-muted-foreground)] font-medium block">Current Best Price</span>
              <span className="text-lg font-bold font-mono text-[var(--color-foreground)]">
                {analytics?.currentPrice
                  ? formatPrice(getDisplayPrice(analytics.currentPrice, currency), currency)
                  : lowestPriceUsd > 0
                  ? formatPrice(lowestPriceLocal, currency)
                  : 'N/A'}
              </span>
            </div>
            <div className="bg-[var(--color-surface-accent)] border border-[var(--color-border)] rounded-xl p-3">
              <span className="text-[11px] text-[var(--color-muted-foreground)] font-medium block">Historical Average</span>
              <span className="text-lg font-bold font-mono text-indigo-500 dark:text-indigo-400">
                {analytics.historicalAvg
                  ? formatPrice(getDisplayPrice(analytics.historicalAvg, currency), currency)
                  : 'N/A'}
              </span>
            </div>
            <div className="bg-[var(--color-surface-accent)] border border-[var(--color-border)] rounded-xl p-3">
              <span className="text-[11px] text-[var(--color-muted-foreground)] font-medium block">Historical Low</span>
              <span className="text-lg font-bold font-mono text-emerald-500 dark:text-emerald-400">
                {analytics.historicalMin
                  ? formatPrice(getDisplayPrice(analytics.historicalMin, currency), currency)
                  : 'N/A'}
              </span>
            </div>
          </div>

          {analytics.purchaseSignalReason && (
            <p className="text-xs text-[var(--color-muted-foreground)] leading-relaxed italic bg-[var(--color-surface-accent)]/50 p-3 rounded-lg border border-[var(--color-border)]">
              "{analytics.purchaseSignalReason}"
            </p>
          )}
        </motion.div>
      )}

      {product && (
        <PriceHistorySection productId={product.id} currency={currency} />
      )}

      {product && (
        <section id="alternatives-section" aria-label="Alternative Products" className="flex flex-col gap-6 mt-12 border-t border-[var(--color-border)] pt-12 text-left">
          <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-4">
            <div>
              <h2 className="text-xl font-bold text-[var(--color-foreground)] flex items-center gap-2">
                <Layers className="h-5 w-5 text-emerald-500 dark:text-emerald-400" />
                Qualified Alternatives & Upgrades
              </h2>
              <p className="text-xs text-[var(--color-muted-foreground)] mt-1">
                Factual alternatives ranked by semantic similarity, price competitiveness, and deal intelligence
              </p>
            </div>

            <div className="flex items-center gap-3 flex-wrap">
              {/* Personalized Toggle */}
              {isAuthenticated && (
                <button
                  type="button"
                  onClick={() => setAltPersonalized(!altPersonalized)}
                  className={`flex items-center gap-1.5 px-3 py-1.5 rounded-xl text-xs font-semibold transition-all cursor-pointer ${
                    altPersonalized
                      ? 'bg-indigo-500/10 text-indigo-600 dark:text-indigo-200 border border-indigo-500/30 shadow-inner'
                      : 'bg-[var(--color-surface-accent)] text-[var(--color-muted-foreground)] hover:text-[var(--color-foreground)] border border-[var(--color-border)]'
                  }`}
                >
                  <Sparkles className="w-3.5 h-3.5 text-indigo-500 dark:text-indigo-400" />
                  <span>Personalized</span>
                </button>
              )}

              <Link
                to={`/product/${product.id}/alternatives`}
                className="text-xs font-semibold text-emerald-600 dark:text-emerald-400 hover:text-emerald-500 dark:hover:text-emerald-300 px-3 py-1.5 rounded-xl bg-[var(--color-surface-accent)] border border-[var(--color-border)] hover:border-[var(--color-border)] transition-colors"
              >
                Explore All Alternatives &rarr;
              </Link>
            </div>
          </div>

          <AlternativeList
            response={altResponse}
            loading={altLoading}
            error={altError}
            isPersonalized={altPersonalized}
            onRetry={() => {
              setAltLoading(true);
              const fetchRetry = async () => {
                try {
                  const res = altPersonalized && isAuthenticated
                    ? await apiService.getPersonalizedAlternatives(product.id, { type: altType, limit: 6 })
                    : await apiService.getAlternatives(product.id, { type: altType, limit: 6 });
                  setAltResponse(res);
                  setAltError(null);
                } catch (e: unknown) {
                  const errObj = e as { message?: string };
                  setAltError(errObj.message || 'Failed to fetch alternatives.');
                } finally {
                  setAltLoading(false);
                }
              };
              fetchRetry();
            }}
            selectedType={altType}
            onSelectType={(t) => setAltType(t)}
            sourceProductId={product.id}
          />
        </section>
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
              ref={dialogRef}
              role="dialog"
              aria-modal="true"
              aria-labelledby="tracking-modal-title"
              aria-describedby="tracking-modal-description"
              initial={{ opacity: 0, scale: 0.95, y: 10 }}
              animate={{ opacity: 1, scale: 1, y: 0 }}
              exit={{ opacity: 0, scale: 0.95, y: 10 }}
              transition={{ type: 'spring', duration: 0.3 }}
              className="relative z-10 w-full max-w-md overflow-hidden rounded-2xl border border-[var(--color-border)] bg-[var(--color-surface)] p-6 shadow-2xl focus:outline-none"
            >
              {/* Close button */}
              <button
                type="button"
                onClick={() => setIsTrackingModalOpen(false)}
                aria-label="Close tracking modal"
                className="absolute top-4 right-4 p-1.5 rounded-lg text-[var(--color-muted-foreground)] hover:text-[var(--color-foreground)] hover:bg-[var(--color-surface-accent)] focus-visible:ring-2 focus-visible:ring-[var(--color-ring)] focus-visible:outline-none transition-all cursor-pointer"
              >
                <X className="h-4.5 w-4.5" />
              </button>

              <div className="flex items-center gap-2 mb-4">
                <div className="h-10 w-10 rounded-xl bg-emerald-500/10 border border-emerald-500/20 flex items-center justify-center text-emerald-500 dark:text-emerald-400">
                  <Bell className="h-5 w-5" />
                </div>
                <div>
                  <h3 id="tracking-modal-title" className="text-base font-bold text-[var(--color-foreground)] leading-none">
                    {isTracking ? 'Edit Price Watchlist' : 'Set Price Watchlist'}
                  </h3>
                  <p id="tracking-modal-description" className="text-[11px] text-[var(--color-muted-foreground)] mt-1">
                    Get alerted when this product drops below your target price.
                  </p>
                </div>
              </div>

              <div className="mb-4 p-3.5 rounded-xl bg-[var(--color-surface-accent)] border border-[var(--color-border)] flex items-center justify-between">
                <div className="flex flex-col">
                  <span className="text-[9px] text-[var(--color-muted-foreground)] font-bold uppercase tracking-wider">Product</span>
                  <span className="text-xs font-semibold text-[var(--color-foreground)] line-clamp-1 mt-0.5">{product.name}</span>
                </div>
                <div className="flex flex-col text-right shrink-0">
                  <span className="text-[9px] text-[var(--color-muted-foreground)] font-bold uppercase tracking-wider">Best Price</span>
                  <span className="text-sm font-extrabold text-emerald-500 dark:text-emerald-400 mt-0.5">
                    {formatPrice(lowestPriceLocal || (product.prices?.[0] ? getDisplayPrice(product.prices[0].currentPrice, currency) : 0), currency)}
                  </span>
                </div>
              </div>

              <form onSubmit={handleSaveTracking} className="flex flex-col gap-4">
                <div className="flex flex-col gap-2">
                  <label htmlFor="targetPrice" className="text-xs font-bold text-[var(--color-foreground)]">
                    Target Price ({CURRENCY_SYMBOLS[currency]})
                  </label>
                  <div className="relative">
                    <span className="absolute left-3.5 top-1/2 -translate-y-1/2 text-sm text-[var(--color-muted-foreground)] font-bold">
                      {CURRENCY_SYMBOLS[currency]}
                    </span>
                    <input
                      ref={targetPriceInputRef}
                      id="targetPrice"
                      type="number"
                      required
                      placeholder="Enter target price"
                      value={targetPriceInput}
                      onChange={(e) => setTargetPriceInput(e.target.value)}
                      className="w-full pl-8 pr-4 py-2.5 rounded-xl bg-[var(--color-surface-accent)] border border-[var(--color-border)] text-sm font-bold text-[var(--color-foreground)] placeholder-[var(--color-muted-foreground)] focus:outline-none focus-visible:ring-2 focus-visible:ring-[var(--color-ring)] transition-colors"
                    />
                  </div>
                </div>

                {/* Quick Discounts Suggestions */}
                <div className="flex flex-col gap-2">
                  <span className="text-[10px] font-bold text-[var(--color-muted-foreground)] uppercase tracking-wider">Quick Select Target</span>
                  <div className="grid grid-cols-3 gap-2">
                    {[0.95, 0.9, 0.85].map((factor) => {
                      const best = lowestPriceLocal || (product.prices?.[0] ? getDisplayPrice(product.prices[0].currentPrice, currency) : 0);
                      const discounted = Math.floor(best * factor);
                      const pct = Math.round((1 - factor) * 100);
                      return (
                        <button
                          key={factor}
                          type="button"
                          onClick={() => setTargetPriceInput(discounted.toString())}
                          className="px-2 py-1.5 rounded-lg border border-[var(--color-border)] hover:border-[var(--color-border)] bg-[var(--color-surface-accent)] hover:bg-[var(--color-surface)] text-[10px] font-bold text-[var(--color-muted-foreground)] hover:text-[var(--color-foreground)] focus-visible:ring-2 focus-visible:ring-[var(--color-ring)] focus-visible:outline-none transition-all cursor-pointer text-center"
                        >
                          {pct}% Off ({formatPrice(discounted, currency)})
                        </button>
                      );
                    })}
                  </div>
                </div>

                {trackingError && (
                  <div className="p-3 rounded-xl bg-rose-500/10 border border-rose-500/20 text-[11px] text-rose-500 dark:text-rose-400 flex items-start gap-2">
                    <AlertCircle className="h-4 w-4 shrink-0 mt-0.5" />
                    <span>{trackingError}</span>
                  </div>
                )}

                <div className="flex items-center justify-between gap-3 mt-2 pt-4 border-t border-[var(--color-border)]">
                  {isTracking ? (
                    <button
                      type="button"
                      onClick={handleRemoveTracking}
                      disabled={isSubmittingTracking}
                      className="flex items-center gap-1.5 px-4 py-2.5 rounded-xl border border-rose-500/20 bg-rose-500/10 hover:bg-rose-500/20 text-rose-500 dark:text-rose-400 text-xs font-bold focus-visible:ring-2 focus-visible:ring-rose-500 focus-visible:outline-none transition-all cursor-pointer disabled:opacity-50"
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
                      className="px-4 py-2.5 text-xs font-bold text-[var(--color-muted-foreground)] hover:text-[var(--color-foreground)] focus-visible:ring-2 focus-visible:ring-[var(--color-ring)] focus-visible:outline-none transition-colors cursor-pointer"
                    >
                      Cancel
                    </button>
                    <button
                      type="submit"
                      disabled={isSubmittingTracking}
                      className="px-5 py-2.5 rounded-xl bg-[var(--color-foreground)] hover:opacity-90 text-[var(--color-background)] text-xs font-extrabold focus-visible:ring-2 focus-visible:ring-[var(--color-ring)] focus-visible:outline-none transition-all shadow-md cursor-pointer active:scale-95 disabled:opacity-50"
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
