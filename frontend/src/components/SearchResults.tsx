import React from 'react';
import { useNavigate } from 'react-router-dom';
import type { ProductWithPrices } from '../types';
import { ChevronLeft, ChevronRight, Inbox, ArrowRight, Heart } from 'lucide-react';
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

  // Motion variants for container and items
  const containerVariants = {
    hidden: { opacity: 0 },
    show: {
      opacity: 1,
      transition: {
        staggerChildren: 0.08
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
      <div className="flex flex-col gap-6">
        <div className="grid grid-cols-1 md:grid-cols-2 gap-6">
          {[1, 2, 3, 4, 5, 6].map((n) => (
            <div
              key={n}
              className="h-[400px] rounded-2xl bg-zinc-950/20 border border-zinc-900/60 flex flex-col justify-between p-5 relative overflow-hidden"
            >
              {/* Skeleton shine effect */}
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
          <Inbox className="h-6 w-6 text-zinc-500" />
        </div>
        <h3 className="text-zinc-200 font-semibold mb-1">No products found</h3>
        <p className="text-xs text-zinc-500 max-w-xs leading-relaxed">
          We couldn't find any products matching your search criteria. Try adjusting your filters or spelling.
        </p>
      </motion.div>
    );
  }

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
          // Find maximum discount to display
          const maxDiscount = product.prices && product.prices.length > 0
            ? Math.max(...product.prices.map((p) => p.discountPercentage))
            : 0;
          const lowest = product.lowestPrice;
          const isSaved = savedProductIds.includes(product.id);

          return (
            <motion.div
              key={product.id}
              variants={itemVariants}
              whileHover={{ 
                y: -5, 
                borderColor: 'var(--color-zinc-800)',
                backgroundColor: 'rgba(24, 24, 27, 0.15)'
              }}
              className="flex flex-col justify-between p-5 rounded-2xl bg-zinc-950/30 border border-zinc-900/80 hover:shadow-[0_8px_30px_rgb(0,0,0,0.4)] transition-all duration-300 group cursor-pointer"
              onClick={() => navigate(`/product/${product.id}`)}
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
                    >
                      <Heart className={`h-4 w-4 ${isSaved ? 'fill-current text-rose-500' : ''}`} />
                    </button>
                  )}
                  {maxDiscount > 0 && (
                    <span className="absolute top-3 right-3 px-2.5 py-1 rounded bg-rose-500/90 backdrop-blur-sm text-[10px] font-bold text-white tracking-wider uppercase shadow-md">
                      Save {maxDiscount.toFixed(0)}%
                    </span>
                  )}
                </div>

                {/* Brand & Title */}
                <div className="flex items-center gap-1.5">
                  <span className="text-[10px] font-bold tracking-widest uppercase text-zinc-500">
                    {product.brand}
                  </span>
                  <span className="h-1 w-1 rounded-full bg-zinc-800" />
                  <span className="text-[10px] font-semibold text-zinc-500">
                    {product.category}
                  </span>
                </div>
                
                <h3 className="font-semibold text-zinc-100 text-base leading-snug mt-1.5 mb-2 line-clamp-2 group-hover:text-white transition-colors">
                  {product.name}
                </h3>
                <p className="text-xs text-zinc-400 line-clamp-2 mb-4 leading-relaxed">
                  {product.description}
                </p>
              </div>

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

                <button
                  type="button"
                  onClick={(e) => {
                    e.stopPropagation();
                    navigate(`/product/${product.id}`);
                  }}
                  className="inline-flex items-center gap-1 px-4 py-2 bg-zinc-900 group-hover:bg-zinc-100 group-hover:text-black border border-zinc-800 group-hover:border-zinc-100 text-xs font-semibold rounded-xl text-zinc-300 transition-all cursor-pointer shadow-sm active:scale-95"
                >
                  <span>Compare Deals</span>
                  <ArrowRight className="h-3 w-3 transition-transform group-hover:translate-x-0.5" />
                </button>
              </div>
            </motion.div>
          );
        })}
      </motion.div>

      {/* Pagination Controls */}
      {totalPages > 1 && (
        <div className="flex items-center justify-between border-t border-zinc-900/80 pt-6">
          <p className="text-xs text-zinc-500">
            Showing page <span className="font-medium text-zinc-300">{page + 1}</span> of{' '}
            <span className="font-medium text-zinc-300">{totalPages}</span> ({totalElements} total results)
          </p>

          <div className="flex items-center gap-2">
            <button
              onClick={() => onPageChange(page - 1)}
              disabled={page === 0}
              className="p-2 bg-zinc-950 border border-zinc-900 rounded-xl text-zinc-400 hover:text-white disabled:opacity-40 disabled:hover:text-zinc-400 disabled:cursor-not-allowed transition-all cursor-pointer active:scale-95"
            >
              <ChevronLeft className="h-4 w-4" />
            </button>
            
            <button
              onClick={() => onPageChange(page + 1)}
              disabled={page >= totalPages - 1}
              className="p-2 bg-zinc-950 border border-zinc-900 rounded-xl text-zinc-400 hover:text-white disabled:opacity-40 disabled:hover:text-zinc-400 disabled:cursor-not-allowed transition-all cursor-pointer active:scale-95"
            >
              <ChevronRight className="h-4 w-4" />
            </button>
          </div>
        </div>
      )}
    </div>
  );
});
export default SearchResults;
