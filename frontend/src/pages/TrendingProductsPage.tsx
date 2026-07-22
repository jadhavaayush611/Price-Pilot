import React, { useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { motion, AnimatePresence } from 'framer-motion';
import { Flame, Bell, Heart, TrendingDown, ChevronRight, Inbox, RefreshCw, Sparkles } from 'lucide-react';
import { apiService } from '../services/api';
import type { ProductWithPrices } from '../types';
import { formatPrice, getDisplayPrice, getSavedCurrency } from '../currency';

type ActiveTab = 'trending' | 'watched' | 'saved' | 'drops';

export const TrendingProductsPage: React.FC = () => {
  const navigate = useNavigate();
  const currency = getSavedCurrency();
  const [activeTab, setActiveTab] = useState<ActiveTab>('trending');
  const [products, setProducts] = useState<ProductWithPrices[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(false);

  useEffect(() => {
    let isCancelled = false;
    const loadData = async () => {
      setError(false);
      try {
        let data: ProductWithPrices[] = [];
        if (activeTab === 'trending') {
          data = await apiService.getTrendingProducts(12);
        } else if (activeTab === 'watched') {
          data = await apiService.getMostWatchedProducts(12);
        } else if (activeTab === 'saved') {
          data = await apiService.getMostSavedProducts(12);
        } else if (activeTab === 'drops') {
          data = await apiService.getBiggestDrops(12);
        }
        if (!isCancelled) setProducts(data);
      } catch (err) {
        console.error(`Failed to load rankings for ${activeTab}:`, err);
        if (!isCancelled) setError(true);
      } finally {
        if (!isCancelled) setLoading(false);
      }
    };

    loadData();
    return () => {
      isCancelled = true;
    };
  }, [activeTab]);

  // Framer Motion Animation Variants
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
    }
  };

  return (
    <div className="flex flex-col gap-8 max-w-5xl mx-auto w-full py-6">
      {/* Header */}
      <div className="flex flex-col gap-2">
        <span className="text-xs font-bold tracking-widest text-zinc-500 uppercase flex items-center gap-1.5">
          <Sparkles className="h-3.5 w-3.5 text-zinc-400" />
          Product Intelligence
        </span>
        <h1 className="text-3xl font-extrabold tracking-tight text-white flex items-center gap-3">
          Market Rankings
        </h1>
        <p className="text-xs text-zinc-500">
          Discover high-demand products, community favorites, and substantial discounts
        </p>
      </div>

      {/* Tabs */}
      <div className="flex flex-wrap items-center gap-2 border-b border-zinc-900 pb-px">
        <button
          onClick={() => setActiveTab('trending')}
          className={`flex items-center gap-2 px-4 py-3 text-xs font-bold border-b-2 transition-all cursor-pointer ${
            activeTab === 'trending'
              ? 'border-white text-white'
              : 'border-transparent text-zinc-500 hover:text-zinc-300'
          }`}
        >
          <Flame className="h-4 w-4" />
          <span>Trending Score</span>
        </button>
        <button
          onClick={() => setActiveTab('watched')}
          className={`flex items-center gap-2 px-4 py-3 text-xs font-bold border-b-2 transition-all cursor-pointer ${
            activeTab === 'watched'
              ? 'border-white text-white'
              : 'border-transparent text-zinc-500 hover:text-zinc-300'
          }`}
        >
          <Bell className="h-4 w-4" />
          <span>Most Watched</span>
        </button>
        <button
          onClick={() => setActiveTab('saved')}
          className={`flex items-center gap-2 px-4 py-3 text-xs font-bold border-b-2 transition-all cursor-pointer ${
            activeTab === 'saved'
              ? 'border-white text-white'
              : 'border-transparent text-zinc-500 hover:text-zinc-300'
          }`}
        >
          <Heart className="h-4 w-4" />
          <span>Most Saved</span>
        </button>
        <button
          onClick={() => setActiveTab('drops')}
          className={`flex items-center gap-2 px-4 py-3 text-xs font-bold border-b-2 transition-all cursor-pointer ${
            activeTab === 'drops'
              ? 'border-white text-white'
              : 'border-transparent text-zinc-500 hover:text-zinc-300'
          }`}
        >
          <TrendingDown className="h-4 w-4" />
          <span>Biggest Price Drops</span>
        </button>
      </div>

      {/* Content */}
      <AnimatePresence mode="wait">
        {loading ? (
          <motion.div
            key="loading"
            initial={{ opacity: 0 }}
            animate={{ opacity: 1 }}
            exit={{ opacity: 0 }}
            className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-6"
          >
            {[1, 2, 3, 4, 5, 6].map((n) => (
              <div key={n} className="h-[280px] rounded-2xl bg-zinc-950/40 border border-zinc-900 animate-pulse" />
            ))}
          </motion.div>
        ) : error ? (
          <motion.div
            key="error"
            initial={{ opacity: 0, scale: 0.98 }}
            animate={{ opacity: 1, scale: 1 }}
            exit={{ opacity: 0, scale: 0.98 }}
            className="flex flex-col items-center justify-center py-20 px-4 border border-zinc-900 border-dashed rounded-2xl bg-zinc-950/20 text-center"
          >
            <div className="h-12 w-12 rounded-xl bg-zinc-900 border border-zinc-800 flex items-center justify-center mb-4 text-zinc-500">
              <RefreshCw className="h-5 w-5" />
            </div>
            <h3 className="text-zinc-200 font-bold text-base mb-1">Failed to load rankings</h3>
            <p className="text-xs text-zinc-500 max-w-xs leading-relaxed mb-4">
              We encountered a network error while retrieving catalog stats. Please try again.
            </p>
            <button
              onClick={() => window.location.reload()}
              className="px-4 py-2 bg-white hover:bg-zinc-200 text-black text-xs font-bold rounded-lg shadow transition-all active:scale-95 cursor-pointer"
            >
              Retry Connection
            </button>
          </motion.div>
        ) : products.length === 0 ? (
          <motion.div
            key="empty"
            initial={{ opacity: 0, scale: 0.98 }}
            animate={{ opacity: 1, scale: 1 }}
            exit={{ opacity: 0, scale: 0.98 }}
            className="flex flex-col items-center justify-center py-24 px-4 border border-zinc-900/60 border-dashed rounded-2xl bg-zinc-950/10 text-center"
          >
            <div className="h-14 w-14 rounded-2xl bg-zinc-950 border border-zinc-900 flex items-center justify-center mb-5 text-zinc-500">
              <Inbox className="h-7 w-7" />
            </div>
            <h3 className="text-zinc-200 font-bold text-lg mb-1.5">No products found</h3>
            <p className="text-xs text-zinc-500 max-w-xs leading-relaxed">
              No items match this ranking segment yet. Catalog interactions and price history might be empty.
            </p>
          </motion.div>
        ) : (
          <motion.div
            key="grid"
            variants={containerVariants}
            initial="hidden"
            animate="show"
            className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-6"
          >
            {products.map((product) => {
              const bestPrice = product.prices && product.prices.length > 0 
                ? Math.min(...product.prices.map(p => p.currentPrice))
                : null;
              const originalPrice = product.prices && product.prices.length > 0
                ? product.prices.find(p => p.currentPrice === bestPrice)?.originalPrice
                : null;
              const discount = bestPrice && originalPrice && originalPrice > bestPrice
                ? Math.round(((originalPrice - bestPrice) / originalPrice) * 100)
                : 0;

              return (
                <motion.div
                  key={product.id}
                  variants={itemVariants}
                  whileHover={{ y: -4, borderColor: 'rgba(255,255,255,0.1)' }}
                  onClick={() => navigate(`/product/${product.id}`)}
                  className="flex flex-col h-full rounded-2xl bg-zinc-950/45 border border-zinc-900 hover:shadow-[0_0_30px_rgba(255,255,255,0.01)] transition-all duration-300 cursor-pointer overflow-hidden group"
                >
                  <div className="h-44 w-full bg-zinc-900/40 border-b border-zinc-900 relative flex items-center justify-center overflow-hidden">
                    {product.imageUrl ? (
                      <img
                        src={product.imageUrl}
                        alt={product.name}
                        className="h-full w-full object-cover group-hover:scale-105 transition-transform duration-500"
                      />
                    ) : (
                      <span className="text-zinc-700 text-xs font-medium uppercase font-mono">No image available</span>
                    )}
                    {discount > 0 && (
                      <span className="absolute top-3.5 right-3.5 bg-emerald-500/10 border border-emerald-500/30 text-emerald-400 text-[10px] font-bold uppercase tracking-wider px-2 py-0.5 rounded-full font-mono">
                        -{discount}% OFF
                      </span>
                    )}
                  </div>

                  <div className="flex flex-col flex-grow p-5 justify-between gap-4">
                    <div className="flex flex-col gap-1.5">
                      <span className="text-[10px] font-bold text-zinc-500 uppercase tracking-widest">{product.brand}</span>
                      <h3 className="font-bold text-zinc-100 text-sm group-hover:text-white line-clamp-1 transition-colors">
                        {product.name}
                      </h3>
                      <p className="text-xs text-zinc-500 line-clamp-2 leading-relaxed">
                        {product.description || 'No description provided.'}
                      </p>
                    </div>

                    <div className="flex items-center justify-between mt-1 pt-4 border-t border-zinc-900/80">
                      <div className="flex flex-col">
                        <span className="text-[10px] text-zinc-550 uppercase tracking-wider">Best price</span>
                        <span className="text-sm font-extrabold text-white">
                            {bestPrice ? formatPrice(getDisplayPrice(bestPrice, currency), currency) : 'N/A'}
                        </span>
                        {originalPrice && originalPrice > bestPrice! && (
                          <span className="text-[10px] text-zinc-550 line-through font-mono">
                              {formatPrice(getDisplayPrice(originalPrice, currency), currency)}
                          </span>
                        )}
                      </div>
                      <span className="h-7 w-7 flex items-center justify-center rounded-lg bg-zinc-900 border border-zinc-800 text-zinc-400 group-hover:text-white group-hover:bg-zinc-800 transition-all">
                        <ChevronRight className="h-4 w-4" />
                      </span>
                    </div>
                  </div>
                </motion.div>
              );
            })}
          </motion.div>
        )}
      </AnimatePresence>
    </div>
  );
};

export default TrendingProductsPage;
