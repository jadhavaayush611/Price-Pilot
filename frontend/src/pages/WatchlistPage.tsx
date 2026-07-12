import React, { useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { apiService } from '../services/api';
import type { Watchlist } from '../types';
import { Bell, Trash2, ArrowLeft, Edit2, AlertCircle, ToggleLeft, ToggleRight, Inbox, ShoppingBag, Eye, CheckCircle2, X } from 'lucide-react';
import { motion, AnimatePresence } from 'framer-motion';
import { formatPrice, getSavedCurrency, type CurrencyCode, CURRENCY_SYMBOLS, getDisplayPrice, convertToUsd } from '../currency';
import { useAuth } from '../context/AuthContext';

export const WatchlistPage: React.FC = () => {
  const navigate = useNavigate();
  const { isAuthenticated } = useAuth();
  const currency: CurrencyCode = getSavedCurrency();
  const [watchlists, setWatchlists] = useState<Watchlist[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  // Edit Modal States
  const [editingItem, setEditingItem] = useState<Watchlist | null>(null);
  const [editTargetPrice, setEditTargetPrice] = useState('');
  const [editError, setEditError] = useState<string | null>(null);
  const [isSubmittingEdit, setIsSubmittingEdit] = useState(false);

  const fetchWatchlist = async () => {
    try {
      setLoading(true);
      setError(null);
      const data = await apiService.getWatchlists();
      setWatchlists(data);
    } catch (err: any) {
      console.error('Error fetching watchlist:', err);
      setError('Could not retrieve watchlist entries. Please try again.');
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    if (!isAuthenticated) {
      navigate('/login?from=/watchlist');
      return;
    }
    fetchWatchlist();
  }, [isAuthenticated, navigate]);

  const handleDelete = async (id: string) => {
    if (!window.confirm('Are you sure you want to stop tracking this product?')) {
      return;
    }

    try {
      await apiService.deleteWatchlist(id);
      setWatchlists(prev => prev.filter(w => w.id !== id));
    } catch (err) {
      console.error('Failed to delete watchlist entry:', err);
      alert('Failed to remove entry. Please try again.');
    }
  };

  const handleToggleActive = async (item: Watchlist) => {
    try {
      const updated = await apiService.updateWatchlist(item.id, item.targetPrice, !item.active);
      setWatchlists(prev => prev.map(w => w.id === item.id ? updated : w));
    } catch (err: any) {
      console.error('Failed to toggle active status:', err);
      alert(err.message || 'Failed to toggle status.');
    }
  };

  const handleOpenEditModal = (item: Watchlist) => {
    setEditingItem(item);
    setEditTargetPrice(getDisplayPrice(item.targetPrice, currency).toString());
    setEditError(null);
  };

  const handleSaveEdit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!editingItem) return;

    const targetLocal = parseFloat(editTargetPrice);
    if (isNaN(targetLocal) || targetLocal <= 0) {
      setEditError('Target price must be greater than zero');
      return;
    }

    const bestLocal = getDisplayPrice(editingItem.currentBestPrice, currency);
    if (targetLocal >= bestLocal) {
      setEditError(`Target price must be strictly less than the current best price (${formatPrice(bestLocal, currency)})`);
      return;
    }

    setIsSubmittingEdit(true);
    setEditError(null);

    try {
      const targetInUsd = convertToUsd(targetLocal, currency);
      const updated = await apiService.updateWatchlist(editingItem.id, targetInUsd, editingItem.active);
      setWatchlists(prev => prev.map(w => w.id === editingItem.id ? updated : w));
      setEditingItem(null);
    } catch (err: any) {
      setEditError(err.message || 'Failed to update target price.');
    } finally {
      setIsSubmittingEdit(false);
    }
  };

  // framer-motion variants
  const containerVariants = {
    hidden: { opacity: 0 },
    show: {
      opacity: 1,
      transition: { staggerChildren: 0.05 }
    }
  };

  const itemVariants = {
    hidden: { opacity: 0, y: 15 },
    show: { 
      opacity: 1, 
      y: 0,
      transition: { type: 'spring' as const, stiffness: 100, damping: 15 }
    },
    exit: { opacity: 0, scale: 0.95, transition: { duration: 0.2 } }
  };

  if (loading) {
    return (
      <div className="flex flex-col gap-6 max-w-5xl mx-auto w-full py-6">
        <div className="h-4 w-28 bg-zinc-900 rounded-lg animate-pulse" />
        <div className="h-9 w-48 bg-zinc-900 rounded-xl mt-2 animate-pulse" />
        
        <div className="flex flex-col gap-4 mt-6">
          {[1, 2, 3].map((n) => (
            <div
              key={n}
              className="h-[100px] rounded-2xl bg-zinc-950/20 border border-zinc-900/60 flex items-center p-4 relative overflow-hidden animate-pulse"
            >
              <div className="h-16 w-16 rounded-xl bg-zinc-900/40 border border-zinc-900/60 mr-4 shrink-0" />
              <div className="flex-grow grid grid-cols-2 md:grid-cols-4 gap-4">
                <div className="flex flex-col gap-2">
                  <div className="h-4 w-32 bg-zinc-900/60 rounded" />
                  <div className="h-3 w-16 bg-zinc-900/40 rounded" />
                </div>
                <div className="h-6 w-20 bg-zinc-900/60 rounded" />
                <div className="h-6 w-20 bg-zinc-900/60 rounded" />
                <div className="h-6 w-24 bg-zinc-900/60 rounded" />
              </div>
            </div>
          ))}
        </div>
      </div>
    );
  }

  if (error) {
    return (
      <div className="flex flex-col items-center justify-center py-24 gap-5 text-center max-w-md mx-auto">
        <div className="h-12 w-12 rounded-2xl bg-zinc-950 border border-zinc-900 flex items-center justify-center text-rose-500 shadow-lg">
          <AlertCircle className="h-6 w-6" />
        </div>
        <h2 className="text-xl font-bold text-white tracking-tight">Watchlist Retrieval Failed</h2>
        <p className="text-zinc-500 text-xs leading-relaxed">{error}</p>
        <button
          onClick={fetchWatchlist}
          className="flex items-center gap-2 px-5 py-2.5 bg-zinc-900 hover:bg-zinc-100 hover:text-black border border-zinc-800 hover:border-white text-xs font-semibold rounded-xl text-zinc-300 transition-all cursor-pointer shadow-md active:scale-95"
        >
          Retry Connection
        </button>
      </div>
    );
  }

  return (
    <div className="flex flex-col gap-6 max-w-5xl mx-auto w-full py-6">
      {/* Header */}
      <div>
        <button
          onClick={() => navigate('/search')}
          className="inline-flex items-center gap-2 text-xs font-bold text-zinc-500 hover:text-zinc-300 transition-colors cursor-pointer group animate-fade-in"
        >
          <ArrowLeft className="h-4 w-4 transition-transform group-hover:-translate-x-0.5" /> 
          Back to Search
        </button>
        <div className="flex items-center justify-between mt-4">
          <div>
            <h1 className="text-3xl font-extrabold tracking-tight text-white flex items-center gap-3">
              <Bell className="h-8 w-8 text-emerald-400 fill-current" />
              Price Watchlist
            </h1>
            <p className="text-xs text-zinc-500 mt-1">
              Monitor active price drops, target matching levels, and trigger analytics.
            </p>
          </div>
          <span className="px-3 py-1.5 rounded-full bg-zinc-950 border border-zinc-900 text-xs font-mono text-zinc-400 font-bold">
            {watchlists.length} Active Tracks
          </span>
        </div>
      </div>

      {/* Main dashboard content */}
      <AnimatePresence mode="popLayout">
        {watchlists.length === 0 ? (
          <motion.div
            initial={{ opacity: 0, scale: 0.98 }}
            animate={{ opacity: 1, scale: 1 }}
            exit={{ opacity: 0, scale: 0.98 }}
            className="flex flex-col items-center justify-center py-24 px-4 border border-zinc-900/60 border-dashed rounded-2xl bg-zinc-955/10 backdrop-blur-sm text-center mt-6"
          >
            <div className="h-14 w-14 rounded-2xl bg-zinc-950 border border-zinc-900 flex items-center justify-center mb-5 text-zinc-500">
              <Inbox className="h-7 w-7" />
            </div>
            <h3 className="text-zinc-200 font-bold text-lg mb-1.5">Your Watchlist is empty</h3>
            <p className="text-xs text-zinc-500 max-w-xs leading-relaxed mb-6">
              Track products from their details pages to monitor price shifts, receive updates, and secure the lowest price.
            </p>
            <button
              onClick={() => navigate('/search')}
              className="inline-flex items-center gap-2 px-5 py-2.5 bg-white hover:bg-zinc-200 text-black text-xs font-bold rounded-xl shadow-md transition-all active:scale-95 cursor-pointer"
            >
              <ShoppingBag className="h-4 w-4" />
              <span>Browse Catalog</span>
            </button>
          </motion.div>
        ) : (
          <motion.div
            variants={containerVariants}
            initial="hidden"
            animate="show"
            className="flex flex-col gap-4 mt-4"
          >
            {watchlists.map((item) => {
              const targetMet = item.currentBestPrice <= item.targetPrice;
              return (
                <motion.div
                  key={item.id}
                  variants={itemVariants}
                  exit="exit"
                  layout
                  className={`flex flex-col md:flex-row items-start md:items-center justify-between p-5 rounded-2xl border transition-all duration-300 relative overflow-hidden gap-4 ${
                    targetMet && item.active
                      ? 'bg-emerald-500/5 border-emerald-500/30 shadow-[0_8px_30px_rgb(16,185,129,0.05)]'
                      : 'bg-zinc-950/30 border-zinc-900 hover:border-zinc-800'
                  }`}
                >
                  <div className="flex items-center gap-4 flex-grow w-full md:w-auto">
                    {/* Image */}
                    <div className="h-16 w-20 rounded-xl overflow-hidden bg-zinc-950 border border-zinc-900 shrink-0">
                      <img
                        src={item.imageUrl || 'https://images.unsplash.com/photo-1531403009284-440f080d1e12?auto=format&fit=crop&q=80&w=600'}
                        alt={item.productName}
                        className="w-full h-full object-cover"
                        onError={(e) => {
                          (e.target as HTMLImageElement).src = 'https://images.unsplash.com/photo-1531403009284-440f080d1e12?auto=format&fit=crop&q=80&w=600';
                        }}
                      />
                    </div>

                    {/* Details */}
                    <div className="flex-grow min-w-0">
                      <span className="text-[9px] font-bold text-zinc-500 uppercase tracking-widest leading-none">
                        {item.brand}
                      </span>
                      <h3
                        onClick={() => navigate(`/product/${item.productId}`)}
                        className="font-bold text-zinc-200 text-sm leading-snug hover:text-white transition-colors truncate cursor-pointer mt-0.5"
                      >
                        {item.productName}
                      </h3>
                      <span className="text-[10px] text-zinc-600 font-mono">
                        Tracked since {new Date(item.createdAt).toLocaleDateString()}
                      </span>
                    </div>
                  </div>

                  {/* Price Metrics Grid */}
                  <div className="grid grid-cols-3 gap-3 md:gap-8 shrink-0 w-full md:w-auto pl-0 md:pl-4 border-t md:border-t-0 md:border-l border-zinc-900/60 pt-4 md:pt-0">
                    <div className="flex flex-col">
                      <span className="text-[9px] text-zinc-500 uppercase font-bold tracking-wider mb-0.5">Best Price</span>
                      <span className={`text-sm font-extrabold ${targetMet && item.active ? 'text-emerald-400' : 'text-zinc-200'}`}>
                        {formatPrice(getDisplayPrice(item.currentBestPrice, currency), currency)}
                      </span>
                    </div>

                    <div className="flex flex-col">
                      <span className="text-[9px] text-zinc-500 uppercase font-bold tracking-wider mb-0.5">Target Price</span>
                      <span className="text-sm font-extrabold text-emerald-400">
                        {formatPrice(getDisplayPrice(item.targetPrice, currency), currency)}
                      </span>
                    </div>

                    <div className="flex flex-col">
                      <span className="text-[9px] text-zinc-500 uppercase font-bold tracking-wider mb-0.5">Difference</span>
                      <span className={`text-sm font-extrabold ${item.priceDifference <= 0 ? 'text-emerald-400' : 'text-zinc-400'}`}>
                        {item.priceDifference <= 0 
                          ? 'Triggered' 
                          : `+${formatPrice(getDisplayPrice(item.priceDifference, currency), currency)}`
                        }
                      </span>
                    </div>
                  </div>

                  {/* Actions & Status */}
                  <div className="flex items-center justify-between md:justify-end gap-3 shrink-0 w-full md:w-auto border-t md:border-t-0 border-zinc-900/60 pt-3.5 md:pt-0">
                    {/* Status Badge */}
                    <div className="flex items-center gap-2">
                      <button
                        onClick={() => handleToggleActive(item)}
                        className={`p-1 rounded-lg transition-colors cursor-pointer text-zinc-500 ${
                          item.active 
                            ? 'text-emerald-400 hover:text-emerald-500' 
                            : 'text-zinc-600 hover:text-zinc-500'
                        }`}
                        title={item.active ? "Pause Monitoring" : "Resume Monitoring"}
                      >
                        {item.active ? <ToggleRight className="h-7 w-7" /> : <ToggleLeft className="h-7 w-7" />}
                      </button>
                      
                      {item.active ? (
                        targetMet ? (
                          <span className="px-2 py-1 rounded bg-emerald-500/10 border border-emerald-500/30 text-[9px] font-bold uppercase tracking-wider text-emerald-400 flex items-center gap-1.5">
                            <CheckCircle2 className="h-3 w-3 animate-bounce" /> Price Met!
                          </span>
                        ) : (
                          <span className="px-2 py-1 rounded bg-zinc-900 border border-zinc-800 text-[9px] font-bold uppercase tracking-wider text-zinc-400">
                            Watching
                          </span>
                        )
                      ) : (
                        <span className="px-2 py-1 rounded bg-zinc-950 border border-zinc-900 text-[9px] font-bold uppercase tracking-wider text-zinc-600">
                          Paused
                        </span>
                      )}
                    </div>

                    <div className="flex items-center gap-1">
                      <button
                        type="button"
                        onClick={() => navigate(`/product/${item.productId}`)}
                        className="p-2 rounded-xl text-zinc-500 hover:text-white hover:bg-zinc-900 transition-colors cursor-pointer active:scale-95"
                        title="View Product Page"
                      >
                        <Eye className="h-4.5 w-4.5" />
                      </button>
                      <button
                        type="button"
                        onClick={() => handleOpenEditModal(item)}
                        className="p-2 rounded-xl text-zinc-500 hover:text-white hover:bg-zinc-900 transition-colors cursor-pointer active:scale-95"
                        title="Update Target Price"
                      >
                        <Edit2 className="h-4.5 w-4.5" />
                      </button>
                      <button
                        type="button"
                        onClick={() => handleDelete(item.id)}
                        className="p-2 rounded-xl text-zinc-500 hover:text-rose-400 hover:bg-rose-500/10 transition-colors cursor-pointer active:scale-95"
                        title="Delete track entry"
                      >
                        <Trash2 className="h-4.5 w-4.5" />
                      </button>
                    </div>
                  </div>
                </motion.div>
              );
            })}
          </motion.div>
        )}
      </AnimatePresence>

      {/* Edit Watchlist Target Price Modal */}
      <AnimatePresence>
        {editingItem && (
          <div className="fixed inset-0 z-50 flex items-center justify-center p-4 bg-black/70 backdrop-blur-md animate-fade-in">
            {/* Background handler to close */}
            <motion.div
              initial={{ opacity: 0 }}
              animate={{ opacity: 1 }}
              exit={{ opacity: 0 }}
              onClick={() => setEditingItem(null)}
              className="absolute inset-0 cursor-default"
            />

            {/* Modal Card */}
            <motion.div
              initial={{ opacity: 0, scale: 0.95, y: 10 }}
              animate={{ opacity: 1, scale: 1, y: 0 }}
              exit={{ opacity: 0, scale: 0.95, y: 10 }}
              className="relative z-10 w-full max-w-md overflow-hidden rounded-2xl border border-zinc-900 bg-zinc-950 p-6 shadow-2xl"
            >
              <button
                onClick={() => setEditingItem(null)}
                className="absolute top-4 right-4 p-1.5 rounded-lg text-zinc-500 hover:text-zinc-300 hover:bg-zinc-900 transition-all cursor-pointer"
              >
                <X className="h-4.5 w-4.5" />
              </button>

              <div className="flex items-center gap-2 mb-4">
                <div className="h-10 w-10 rounded-xl bg-emerald-500/10 border border-emerald-500/20 flex items-center justify-center text-emerald-400">
                  <Edit2 className="h-5 w-5" />
                </div>
                <div>
                  <h3 className="text-base font-bold text-white leading-none">Update Target Price</h3>
                  <p className="text-[11px] text-zinc-500 mt-1">Adjust target tracking limits for your watchlist.</p>
                </div>
              </div>

              <div className="mb-4 p-3.5 rounded-xl bg-zinc-900/40 border border-zinc-900 flex items-center justify-between">
                <div className="flex flex-col">
                  <span className="text-[9px] text-zinc-500 font-bold uppercase tracking-wider">Product</span>
                  <span className="text-xs font-semibold text-zinc-200 line-clamp-1 mt-0.5">{editingItem.productName}</span>
                </div>
                <div className="flex flex-col text-right shrink-0">
                  <span className="text-[9px] text-zinc-500 font-bold uppercase tracking-wider">Best Price</span>
                  <span className="text-sm font-extrabold text-emerald-400 mt-0.5">
                    {formatPrice(getDisplayPrice(editingItem.currentBestPrice, currency), currency)}
                  </span>
                </div>
              </div>

              <form onSubmit={handleSaveEdit} className="flex flex-col gap-4">
                <div className="flex flex-col gap-2">
                  <label htmlFor="editTargetPrice" className="text-xs font-bold text-zinc-400">Target Price</label>
                  <div className="relative">
                    <span className="absolute left-3.5 top-1/2 -translate-y-1/2 text-sm text-zinc-500 font-bold">{CURRENCY_SYMBOLS[currency]}</span>
                    <input
                      id="editTargetPrice"
                      type="number"
                      required
                      placeholder="Enter target price"
                      value={editTargetPrice}
                      onChange={(e) => setEditTargetPrice(e.target.value)}
                      className="w-full pl-8 pr-4 py-2.5 rounded-xl bg-zinc-900 border border-zinc-800 text-sm font-bold text-white placeholder-zinc-600 focus:outline-none focus:border-zinc-700 transition-colors"
                    />
                  </div>
                </div>

                {/* Suggestions */}
                <div className="flex flex-col gap-2">
                  <span className="text-[10px] font-bold text-zinc-555 uppercase tracking-wider">Quick Select Target</span>
                  <div className="grid grid-cols-3 gap-2">
                    {[0.95, 0.9, 0.85].map((factor) => {
                      const bestLocal = getDisplayPrice(editingItem.currentBestPrice, currency);
                      const discounted = Math.floor(bestLocal * factor);
                      const pct = Math.round((1 - factor) * 100);
                      return (
                        <button
                          key={factor}
                          type="button"
                          onClick={() => setEditTargetPrice(discounted.toString())}
                          className="px-2 py-1.5 rounded-lg border border-zinc-900 hover:border-zinc-800 bg-zinc-955 hover:bg-zinc-900 text-[10px] font-bold text-zinc-400 hover:text-white transition-all cursor-pointer text-center"
                        >
                          {pct}% Off ({formatPrice(discounted, currency)})
                        </button>
                      );
                    })}
                  </div>
                </div>

                {editError && (
                  <div className="p-3 rounded-xl bg-rose-500/10 border border-rose-500/20 text-[11px] text-rose-400 flex items-start gap-2">
                    <AlertCircle className="h-4 w-4 shrink-0 mt-0.5" />
                    <span>{editError}</span>
                  </div>
                )}

                <div className="flex items-center justify-end gap-2 mt-2 pt-4 border-t border-zinc-900">
                  <button
                    type="button"
                    onClick={() => setEditingItem(null)}
                    disabled={isSubmittingEdit}
                    className="px-4 py-2.5 text-xs font-bold text-zinc-500 hover:text-zinc-300 transition-colors cursor-pointer"
                  >
                    Cancel
                  </button>
                  <button
                    type="submit"
                    disabled={isSubmittingEdit}
                    className="px-5 py-2.5 rounded-xl bg-white hover:bg-zinc-200 text-black text-xs font-extrabold transition-all shadow-md cursor-pointer active:scale-95 disabled:opacity-50"
                  >
                    {isSubmittingEdit ? 'Saving...' : 'Update Target'}
                  </button>
                </div>
              </form>
            </motion.div>
          </div>
        )}
      </AnimatePresence>
    </div>
  );
};

export default WatchlistPage;
