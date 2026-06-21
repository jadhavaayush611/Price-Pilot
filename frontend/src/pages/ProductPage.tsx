import React, { useEffect, useState } from 'react';
import { useParams, useNavigate } from 'react-router-dom';
import { apiService } from '../services/api';
import type { ProductWithPrices } from '../types';
import { ArrowLeft, Clock, ExternalLink, Sparkles, Tag, AlertCircle, ShoppingBag, LayoutGrid, List } from 'lucide-react';
import { motion, AnimatePresence } from 'framer-motion';
import { formatPrice } from '../lib/utils';
import { SellerCard } from '../components/SellerCard';

export const ProductPage: React.FC = () => {
  const { id } = useParams<{ id: string }>();
  const navigate = useNavigate();
  const [product, setProduct] = useState<ProductWithPrices | null>(null);
  const [loading, setLoading] = useState(true);
  const [viewMode, setViewMode] = useState<'card' | 'table'>('card');
  const [currency, setCurrency] = useState<'INR' | 'USD'>('INR');

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
    }
  }, [id]);

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

  // Helper to check original currency and display correctly
  const getDisplayPrice = (val: number) => {
    const isOriginallyUSD = val < 5000;
    if (currency === 'INR') {
      return isOriginallyUSD ? val * 80 : val;
    } else {
      return isOriginallyUSD ? val : val / 80;
    }
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
        currentPrice: getDisplayPrice(p.currentPrice),
        originalPrice: getDisplayPrice(p.originalPrice),
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
            <h1 className="text-3xl font-extrabold tracking-tight text-white mt-4 mb-2 leading-tight">
              {product.name}
            </h1>
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
            <div className="flex items-center rounded-xl bg-zinc-950/80 border border-zinc-900 p-1">
              <button
                onClick={() => setCurrency('INR')}
                className={`px-3 py-1.5 text-[10px] font-bold rounded-lg transition-all ${
                  currency === 'INR'
                    ? 'bg-zinc-850 text-white border border-zinc-800 shadow-inner'
                    : 'text-zinc-500 hover:text-zinc-300'
                }`}
              >
                ₹ INR
              </button>
              <button
                onClick={() => setCurrency('USD')}
                className={`px-3 py-1.5 text-[10px] font-bold rounded-lg transition-all ${
                  currency === 'USD'
                    ? 'bg-zinc-850 text-white border border-zinc-800 shadow-inner'
                    : 'text-zinc-500 hover:text-zinc-300'
                }`}
              >
                $ USD
              </button>
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
    </motion.div>
  );
};

export default ProductPage;
