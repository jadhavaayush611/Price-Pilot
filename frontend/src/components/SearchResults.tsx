import React from 'react';
import { useNavigate } from 'react-router-dom';
import type { ProductWithPrices } from '../types';
import { 
  ChevronLeft, 
  ChevronRight, 
  Inbox, 
  ArrowRight, 
  Heart, 
  TrendingDown, 
  Sparkles, 
  Award, 
  Tag, 
  CheckCircle,
  HelpCircle
} from 'lucide-react';
import { motion } from 'framer-motion';
import { formatPrice, getDisplayPrice, getSavedCurrency } from '../currency';

interface SearchResultsProps {
  products: ProductWithPrices[];
  loading: boolean;
  page: number;
  totalPages: number;
  totalElements: number;
  onPageChange: (newPage: number) => void;
  savedProductIds?: string[];
  onToggleSave?: (productId: string) => void;
}

export const SearchResults: React.FC<SearchResultsProps> = React.memo(({
  products,
  loading,
  page,
  totalPages,
  totalElements,
  onPageChange,
  savedProductIds = [],
  onToggleSave
}) => {
  const navigate = useNavigate();
  const currency = getSavedCurrency();

  const containerVariants = {
    hidden: { opacity: 0 },
    show: {
      opacity: 1,
      transition: {
        staggerChildren: 0.06
      }
    }
  };

  const itemVariants = {
    hidden: { opacity: 0, y: 15 },
    show: { 
      opacity: 1, 
      y: 0,
      transition: {
        type: 'spring' as const,
        stiffness: 100,
        damping: 15
      }
    }
  };

  if (loading) {
    return (
      <div className="flex flex-col gap-6" aria-busy="true" aria-label="Loading search results">
        <div className="grid grid-cols-1 md:grid-cols-2 gap-6">
          {[1, 2, 3, 4, 5, 6].map((n) => (
            <div
              key={n}
              className="h-[420px] rounded-2xl bg-zinc-950/20 border border-zinc-900/60 flex flex-col justify-between p-5 relative overflow-hidden"
            >
              <div className="absolute inset-0 bg-gradient-to-r from-transparent via-zinc-900/10 to-transparent -translate-x-full animate-[shimmer_1.5s_infinite]" />
              <div>
                <div className="aspect-[4/3] rounded-xl bg-zinc-900/40 border border-zinc-900/60 mb-4 animate-pulse" />
                <div className="h-3 w-16 bg-zinc-900/60 rounded mb-2.5 animate-pulse" />
                <div className="h-5 w-48 bg-zinc-900/60 rounded mb-3.5 animate-pulse" />
                <div className="h-3.5 w-full bg-zinc-900/40 rounded mb-2 animate-pulse" />
                <div className="h-3.5 w-3/4 bg-zinc-900/40 rounded animate-pulse" />
              </div>
              <div className="h-11 w-full bg-zinc-900/60 rounded-xl mt-4 animate-pulse" />
            </div>
          ))}
        </div>
      </div>
    );
  }

  if (products.length === 0) {
    return (
      <motion.div 
        initial={{ opacity: 0, scale: 0.98 }}
        animate={{ opacity: 1, scale: 1 }}
        className="flex flex-col items-center justify-center py-20 px-4 border border-zinc-900/60 border-dashed rounded-2xl bg-zinc-950/10 backdrop-blur-sm text-center"
      >
        <div className="h-12 w-12 rounded-xl bg-zinc-950 border border-zinc-900 flex items-center justify-center mb-4">
          <Inbox className="h-6 w-6 text-zinc-500" aria-hidden="true" />
        </div>
        <h3 className="text-zinc-200 font-semibold mb-1">No products found</h3>
        <p className="text-xs text-zinc-500 max-w-xs leading-relaxed">
          We couldn't find any products matching your search criteria. Try adjusting your filters, budget, or spelling.
        </p>
      </motion.div>
    );
  }

  const renderBadge = (badge: string, index: number) => {
    switch (badge) {
      case 'Best Match':
        return (
          <span key={index} className="inline-flex items-center gap-1 px-2.5 py-0.5 rounded-full text-[10px] font-bold bg-purple-500/20 text-purple-300 border border-purple-500/30">
            <Award className="h-3 w-3" aria-hidden="true" />
            Best Match
          </span>
        );
      case 'Lowest Historical Price':
        return (
          <span key={index} className="inline-flex items-center gap-1 px-2.5 py-0.5 rounded-full text-[10px] font-bold bg-emerald-500/20 text-emerald-300 border border-emerald-500/30">
            <TrendingDown className="h-3 w-3" aria-hidden="true" />
            Historical Low
          </span>
        );
      case 'Best Value':
      case 'Excellent Deal':
        return (
          <span key={index} className="inline-flex items-center gap-1 px-2.5 py-0.5 rounded-full text-[10px] font-bold bg-amber-500/20 text-amber-300 border border-amber-500/30">
            <Sparkles className="h-3 w-3" aria-hidden="true" />
            Excellent Deal
          </span>
        );
      case 'Good Deal':
        return (
          <span key={index} className="inline-flex items-center gap-1 px-2.5 py-0.5 rounded-full text-[10px] font-bold bg-blue-500/20 text-blue-300 border border-blue-500/30">
            <Tag className="h-3 w-3" aria-hidden="true" />
            Good Deal
          </span>
        );
      case 'In Stock':
        return (
          <span key={index} className="inline-flex items-center gap-1 px-2 py-0.5 rounded-full text-[10px] font-semibold bg-zinc-800 text-zinc-300 border border-zinc-700">
            <CheckCircle className="h-2.5 w-2.5 text-emerald-400" aria-hidden="true" />
            In Stock
          </span>
        );
      default:
        return (
          <span key={index} className="inline-flex items-center gap-1 px-2 py-0.5 rounded-full text-[10px] font-medium bg-zinc-900 text-zinc-300 border border-zinc-800">
            {badge}
          </span>
        );
    }
  };

  return (
    <div className="flex flex-col gap-8">
      {/* Product Grid */}
      <motion.div 
        variants={containerVariants}
        initial="hidden"
        animate="show"
        className="grid grid-cols-1 md:grid-cols-2 gap-6"
      >
        {products.map((product) => {
          const maxDiscount = product.prices && product.prices.length > 0
            ? Math.max(...product.prices.map((p) => p.discountPercentage))
            : 0;
          const lowest = product.lowestPrice ?? product.currentBestPrice;
          const isSaved = savedProductIds.includes(product.id);
          const badges = product.discoveryBadges || [];
          const reasons = product.discoveryReasons || [];

          return (
            <motion.article
              key={product.id}
              variants={itemVariants}
              whileHover={{ 
                y: -4, 
                borderColor: 'var(--color-zinc-800)',
                backgroundColor: 'rgba(24, 24, 27, 0.25)'
              }}
              className="flex flex-col justify-between p-5 rounded-2xl bg-zinc-950/40 border border-zinc-900 hover:shadow-[0_8px_30px_rgb(0,0,0,0.4)] transition-all duration-300 group cursor-pointer"
              onClick={() => navigate(`/product/${product.id}`)}
              aria-label={`Product: ${product.name}, price from ${lowest ? formatPrice(getDisplayPrice(lowest, currency), currency) : 'not listed'}`}
            >
              <div>
                {/* Image container */}
                <div className="aspect-[4/3] rounded-xl overflow-hidden bg-zinc-950 border border-zinc-900 mb-4 relative">
                  <img
                    src={product.imageUrl || 'https://images.unsplash.com/photo-1531403009284-440f080d1e12?auto=format&fit=crop&q=80&w=600'}
                    alt={product.name}
                    loading="lazy"
                    decoding="async"
                    className="w-full h-full object-cover group-hover:scale-[1.02] transition-transform duration-500"
                    onError={(e) => {
                      (e.target as HTMLImageElement).src = 'https://images.unsplash.com/photo-1531403009284-440f080d1e12?auto=format&fit=crop&q=80&w=600';
                    }}
                  />
                  {onToggleSave && (
                    <button
                      type="button"
                      onClick={(e) => {
                        e.stopPropagation();
                        onToggleSave(product.id);
                      }}
                      className={`absolute top-3 left-3 p-2 rounded-lg backdrop-blur-md transition-all active:scale-95 shadow-md border ${
                        isSaved
                          ? 'bg-rose-500/20 border-rose-500/40 text-rose-400'
                          : 'bg-zinc-950/60 border-zinc-900/80 text-zinc-400 hover:text-white hover:bg-zinc-950'
                      }`}
                      title={isSaved ? "Remove from Saved" : "Save Product"}
                      aria-label={isSaved ? `Remove ${product.name} from saved` : `Save ${product.name}`}
                    >
                      <Heart className={`h-4 w-4 ${isSaved ? 'fill-current text-rose-500' : ''}`} aria-hidden="true" />
                    </button>
                  )}
                  {maxDiscount > 0 && (
                    <span className="absolute top-3 right-3 px-2.5 py-1 rounded bg-rose-500/90 backdrop-blur-sm text-[10px] font-bold text-white tracking-wider uppercase shadow-md">
                      Save {maxDiscount.toFixed(0)}%
                    </span>
                  )}
                </div>

                {/* Discovery Badges */}
                {badges.length > 0 && (
                  <div className="flex flex-wrap gap-1.5 mb-2.5">
                    {badges.map((b, idx) => renderBadge(b, idx))}
                  </div>
                )}

                {/* Brand & Category */}
                <div className="flex items-center gap-1.5">
                  <span className="text-[10px] font-bold tracking-widest uppercase text-zinc-500">
                    {product.brand}
                  </span>
                  <span className="h-1 w-1 rounded-full bg-zinc-800" aria-hidden="true" />
                  <span className="text-[10px] font-semibold text-zinc-500">
                    {product.category}
                  </span>
                </div>
                
                {/* Title */}
                <h3 className="font-semibold text-zinc-100 text-base leading-snug mt-1.5 mb-2 line-clamp-2 group-hover:text-white transition-colors">
                  {product.name}
                </h3>

                {/* Discovery Reason (Factual Explainability) */}
                {reasons.length > 0 && (
                  <div className="mb-3 px-2.5 py-1.5 rounded-lg bg-zinc-900/50 border border-zinc-800/80 flex items-start gap-1.5">
                    <HelpCircle className="h-3 w-3 text-emerald-400 mt-0.5 flex-shrink-0" aria-hidden="true" />
                    <p className="text-[11px] text-zinc-400 line-clamp-1">
                      {reasons[0]}
                    </p>
                  </div>
                )}

                <p className="text-xs text-zinc-400 line-clamp-2 mb-4 leading-relaxed">
                  {product.description}
                </p>
              </div>

              {/* Pricing & Call to Action */}
              <div className="border-t border-zinc-900/80 pt-4 flex items-center justify-between">
                <div className="flex flex-col">
                  {lowest !== undefined && lowest > 0 ? (
                    <>
                      <span className="text-[9px] text-zinc-500 uppercase font-bold tracking-wider">Prices From</span>
                      <span className="text-lg font-extrabold text-white">
                        {formatPrice(getDisplayPrice(lowest, currency), currency)}
                      </span>
                    </>
                  ) : (
                    <>
                      <span className="text-[9px] text-zinc-500 uppercase font-bold tracking-wider">Status</span>
                      <span className="text-xs font-semibold text-zinc-400">
                        No prices listed
                      </span>
                    </>
                  )}
                </div>

                <div className="flex items-center gap-2">
                  <span className="text-xs font-medium text-zinc-400 group-hover:text-white transition-colors flex items-center gap-1">
                    Compare <ArrowRight className="h-3 w-3 group-hover:translate-x-0.5 transition-transform" aria-hidden="true" />
                  </span>
                </div>
              </div>
            </motion.article>
          );
        })}
      </motion.div>

      {/* Pagination Controls */}
      {totalPages > 1 && (
        <nav 
          aria-label="Search results pagination"
          className="flex items-center justify-between border-t border-zinc-900 pt-6 mt-2"
        >
          <div className="text-xs text-zinc-500">
            Showing Page <span className="font-semibold text-zinc-200">{page + 1}</span> of <span className="font-semibold text-zinc-200">{totalPages}</span> ({totalElements} total products)
          </div>

          <div className="flex items-center gap-2">
            <button
              type="button"
              disabled={page === 0}
              onClick={() => onPageChange(page - 1)}
              aria-label="Go to previous page"
              className="px-3 py-1.5 rounded-lg border border-zinc-800 bg-zinc-900/50 text-xs font-medium text-zinc-300 hover:bg-zinc-800 disabled:opacity-40 disabled:cursor-not-allowed transition-colors flex items-center gap-1"
            >
              <ChevronLeft className="h-3.5 w-3.5" aria-hidden="true" /> Prev
            </button>

            <button
              type="button"
              disabled={page >= totalPages - 1}
              onClick={() => onPageChange(page + 1)}
              aria-label="Go to next page"
              className="px-3 py-1.5 rounded-lg border border-zinc-800 bg-zinc-900/50 text-xs font-medium text-zinc-300 hover:bg-zinc-800 disabled:opacity-40 disabled:cursor-not-allowed transition-colors flex items-center gap-1"
            >
              Next <ChevronRight className="h-3.5 w-3.5" aria-hidden="true" />
            </button>
          </div>
        </nav>
      )}
    </div>
  );
});
