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
import { ProductImage } from './common/ProductImage';

interface SearchResultsProps {
  products: ProductWithPrices[];
  loading: boolean;
  page: number;
  totalPages: number;
  totalElements: number;
  onPageChange: (newPage: number) => void;
  savedProductIds?: string[];
  onToggleSave?: (productId: string) => void;
  isPersonalized?: boolean;
}

export const SearchResults: React.FC<SearchResultsProps> = React.memo(({
  products,
  loading,
  page,
  totalPages,
  totalElements,
  onPageChange,
  savedProductIds = [],
  onToggleSave,
  isPersonalized = false,
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
              className="h-[420px] rounded-2xl bg-[var(--color-surface)] border border-[var(--color-border)] flex flex-col justify-between p-5 relative overflow-hidden"
            >
              <div className="absolute inset-0 bg-gradient-to-r from-transparent via-[var(--color-surface-accent)]/20 to-transparent -translate-x-full animate-[shimmer_1.5s_infinite]" />
              <div>
                <div className="aspect-[4/3] rounded-xl bg-[var(--color-surface-accent)] border border-[var(--color-border)] mb-4 animate-pulse" />
                <div className="h-3 w-16 bg-[var(--color-surface-accent)] rounded mb-2.5 animate-pulse" />
                <div className="h-5 w-48 bg-[var(--color-surface-accent)] rounded mb-3.5 animate-pulse" />
                <div className="h-3.5 w-full bg-[var(--color-surface-accent)] rounded mb-2 animate-pulse" />
                <div className="h-3.5 w-3/4 bg-[var(--color-surface-accent)] rounded animate-pulse" />
              </div>
              <div className="h-11 w-full bg-[var(--color-surface-accent)] rounded-xl mt-4 animate-pulse" />
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
        className="flex flex-col items-center justify-center py-20 px-4 border border-[var(--color-border)] border-dashed rounded-2xl bg-[var(--color-surface)]/50 backdrop-blur-sm text-center"
      >
        <div className="h-12 w-12 rounded-xl bg-[var(--color-surface-accent)] border border-[var(--color-border)] flex items-center justify-center mb-4">
          <Inbox className="h-6 w-6 text-[var(--color-muted-foreground)]" aria-hidden="true" />
        </div>
        <h3 className="text-[var(--color-foreground)] font-semibold mb-1">No products found</h3>
        <p className="text-xs text-[var(--color-muted-foreground)] max-w-xs leading-relaxed">
          We couldn't find any products matching your search criteria. Try adjusting your filters, budget, or spelling.
        </p>
      </motion.div>
    );
  }

  const renderBadge = (badge: string, index: number) => {
    switch (badge) {
      case 'Best Match':
        return (
          <span key={index} className="inline-flex items-center gap-1 px-2.5 py-0.5 rounded-full text-[10px] font-bold bg-purple-500/15 text-purple-700 dark:text-purple-300 border border-purple-500/30">
            <Award className="h-3 w-3" aria-hidden="true" />
            Best Match
          </span>
        );
      case 'Lowest Historical Price':
        return (
          <span key={index} className="inline-flex items-center gap-1 px-2.5 py-0.5 rounded-full text-[10px] font-bold bg-emerald-500/15 text-emerald-700 dark:text-emerald-300 border border-emerald-500/30">
            <TrendingDown className="h-3 w-3" aria-hidden="true" />
            Historical Low
          </span>
        );
      case 'Best Value':
      case 'Excellent Deal':
        return (
          <span key={index} className="inline-flex items-center gap-1 px-2.5 py-0.5 rounded-full text-[10px] font-bold bg-amber-500/15 text-amber-800 dark:text-amber-300 border border-amber-500/30">
            <Sparkles className="h-3 w-3" aria-hidden="true" />
            Excellent Deal
          </span>
        );
      case 'Good Deal':
        return (
          <span key={index} className="inline-flex items-center gap-1 px-2.5 py-0.5 rounded-full text-[10px] font-bold bg-blue-500/15 text-blue-700 dark:text-blue-300 border border-blue-500/30">
            <Tag className="h-3 w-3" aria-hidden="true" />
            Good Deal
          </span>
        );
      case 'In Stock':
        return (
          <span key={index} className="inline-flex items-center gap-1 px-2 py-0.5 rounded-full text-[10px] font-semibold bg-[var(--color-surface-accent)] text-[var(--color-foreground)] border border-[var(--color-border)]">
            <CheckCircle className="h-2.5 w-2.5 text-emerald-500" aria-hidden="true" />
            In Stock
          </span>
        );
      default:
        return (
          <span key={index} className="inline-flex items-center gap-1 px-2 py-0.5 rounded-full text-[10px] font-medium bg-[var(--color-surface-accent)] text-[var(--color-foreground)] border border-[var(--color-border)]">
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

          const isItemPersonalized = Boolean(
            isPersonalized && (
              Boolean(product.personalizedEvidence?.summary) || 
              (product.personalizedEvidence?.reasons && product.personalizedEvidence.reasons.length > 0) || 
              (product.personalizedScore !== undefined && product.personalizedScore > 0)
            )
          );

          return (
            <motion.article
              key={product.id}
              variants={itemVariants}
              whileHover={{ 
                y: -4, 
                borderColor: isItemPersonalized ? 'rgba(99, 102, 241, 0.4)' : 'var(--color-ring)',
              }}
              className={`flex flex-col justify-between p-5 rounded-2xl bg-[var(--color-surface)] border transition-all duration-300 group cursor-pointer shadow-sm ${
                isItemPersonalized
                  ? 'border-indigo-500/40 hover:border-indigo-500/70 hover:shadow-md'
                  : 'border-[var(--color-border)] hover:border-[var(--color-ring)]/40 hover:shadow-md'
              }`}
              onClick={() => navigate(`/product/${product.id}`)}
              aria-label={`Product: ${product.name}, price from ${lowest ? formatPrice(getDisplayPrice(lowest, currency), currency) : 'not listed'}`}
            >
              <div>
                {/* Image container */}
                <div className="aspect-[4/3] rounded-xl overflow-hidden bg-[var(--color-surface-accent)] border border-[var(--color-border)] mb-4 relative">
                  <ProductImage
                    src={product.imageUrl}
                    alt={product.name}
                    category={product.category}
                    className="w-full h-full object-cover group-hover:scale-[1.02] transition-transform duration-500"
                    showFallbackText
                    fallbackText={product.brand}
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
                          ? 'bg-rose-500/20 border-rose-500/40 text-rose-500'
                          : 'bg-[var(--color-surface)]/80 border-[var(--color-border)] text-[var(--color-muted-foreground)] hover:text-[var(--color-foreground)] hover:bg-[var(--color-surface)]'
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

                {/* Discovery Badges & Personalized Badges */}
                <div className="flex flex-wrap items-center gap-1.5 mb-2.5">
                  {isItemPersonalized && (
                    <span className="inline-flex items-center gap-1 px-2.5 py-0.5 rounded-full text-[10px] font-bold bg-indigo-500/15 text-indigo-700 dark:text-indigo-300 border border-indigo-500/30">
                      <Sparkles className="h-3 w-3 text-indigo-600 dark:text-indigo-400" aria-hidden="true" />
                      Personalized
                    </span>
                  )}
                  {isItemPersonalized && product.personalizedScore !== undefined && product.personalizedScore > 0 && (
                    <span 
                      title={product.personalizationAdjustment ? `Personalized Match Score: ${Math.round(product.personalizedScore)}/100 (Adjustment: +${product.personalizationAdjustment.toFixed(1)})` : `Match Score: ${Math.round(product.personalizedScore)}/100`}
                      className="inline-flex items-center gap-1 px-2 py-0.5 rounded text-[10px] font-mono font-bold bg-[var(--color-surface-accent)] border border-[var(--color-border)] text-indigo-700 dark:text-indigo-300"
                    >
                      Score: {Math.round(product.personalizedScore)}/100
                    </span>
                  )}
                  {badges.map((b, idx) => renderBadge(b, idx))}
                </div>

                {/* Brand & Category */}
                <div className="flex items-center gap-1.5">
                  <span className="text-[10px] font-bold tracking-widest uppercase text-[var(--color-muted-foreground)]">
                    {product.brand}
                  </span>
                  <span className="h-1 w-1 rounded-full bg-[var(--color-border)]" aria-hidden="true" />
                  <span className="text-[10px] font-semibold text-[var(--color-muted-foreground)]">
                    {product.category}
                  </span>
                </div>
                
                {/* Title */}
                <h3 className="font-semibold text-[var(--color-foreground)] text-base leading-snug mt-1.5 mb-2 line-clamp-2 transition-colors">
                  {product.name}
                </h3>

                {/* Personalized Evidence (Preference Alignment) */}
                {isItemPersonalized && product.personalizedEvidence && product.personalizedEvidence.reasons && product.personalizedEvidence.reasons.length > 0 ? (
                  <div className="mb-3 px-2.5 py-1.5 rounded-lg bg-indigo-500/10 border border-indigo-500/20 flex items-start gap-1.5">
                    <Sparkles className="h-3 w-3 text-indigo-600 dark:text-indigo-400 mt-0.5 flex-shrink-0" aria-hidden="true" />
                    <p className="text-[11px] text-indigo-700 dark:text-indigo-300 line-clamp-1">
                      {product.personalizedEvidence.reasons[0].reason || product.personalizedEvidence.summary}
                    </p>
                  </div>
                ) : isItemPersonalized && product.personalizedEvidence?.summary ? (
                  <div className="mb-3 px-2.5 py-1.5 rounded-lg bg-indigo-500/10 border border-indigo-500/20 flex items-start gap-1.5">
                    <Sparkles className="h-3 w-3 text-indigo-600 dark:text-indigo-400 mt-0.5 flex-shrink-0" aria-hidden="true" />
                    <p className="text-[11px] text-indigo-700 dark:text-indigo-300 line-clamp-1">
                      {product.personalizedEvidence.summary}
                    </p>
                  </div>
                ) : reasons.length > 0 ? (
                  /* Standard Discovery Reason (Factual Explainability) */
                  <div className="mb-3 px-2.5 py-1.5 rounded-lg bg-[var(--color-surface-accent)] border border-[var(--color-border)] flex items-start gap-1.5">
                    <HelpCircle className="h-3 w-3 text-emerald-600 dark:text-emerald-400 mt-0.5 flex-shrink-0" aria-hidden="true" />
                    <p className="text-[11px] text-[var(--color-muted-foreground)] line-clamp-1">
                      {reasons[0]}
                    </p>
                  </div>
                ) : null}

                <p className="text-xs text-[var(--color-muted-foreground)] line-clamp-2 mb-4 leading-relaxed">
                  {product.description}
                </p>
              </div>

              {/* Pricing & Call to Action */}
              <div className="border-t border-[var(--color-border)] pt-4 flex items-center justify-between">
                <div className="flex flex-col">
                  {lowest !== undefined && lowest > 0 ? (
                    <>
                      <span className="text-[9px] text-[var(--color-muted-foreground)] uppercase font-bold tracking-wider">Prices From</span>
                      <span className="text-lg font-extrabold text-[var(--color-foreground)]">
                        {formatPrice(getDisplayPrice(lowest, currency), currency)}
                      </span>
                    </>
                  ) : (
                    <>
                      <span className="text-[9px] text-[var(--color-muted-foreground)] uppercase font-bold tracking-wider">Status</span>
                      <span className="text-xs font-semibold text-[var(--color-muted-foreground)]">
                        No prices listed
                      </span>
                    </>
                  )}
                </div>

                <div className="flex items-center gap-2">
                  <span className="text-xs font-medium text-[var(--color-muted-foreground)] group-hover:text-[var(--color-foreground)] transition-colors flex items-center gap-1">
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
          className="flex items-center justify-between border-t border-[var(--color-border)] pt-6 mt-2"
        >
          <div className="text-xs text-[var(--color-muted-foreground)]">
            Showing Page <span className="font-semibold text-[var(--color-foreground)]">{page + 1}</span> of <span className="font-semibold text-[var(--color-foreground)]">{totalPages}</span> ({totalElements} total products)
          </div>

          <div className="flex items-center gap-2">
            <button
              type="button"
              disabled={page === 0}
              onClick={() => onPageChange(page - 1)}
              aria-label="Go to previous page"
              className="px-3 py-1.5 rounded-lg border border-[var(--color-border)] bg-[var(--color-surface)] text-xs font-medium text-[var(--color-foreground)] hover:bg-[var(--color-surface-accent)] disabled:opacity-40 disabled:cursor-not-allowed transition-colors flex items-center gap-1 shadow-sm"
            >
              <ChevronLeft className="h-3.5 w-3.5" aria-hidden="true" /> Prev
            </button>

            <button
              type="button"
              disabled={page >= totalPages - 1}
              onClick={() => onPageChange(page + 1)}
              aria-label="Go to next page"
              className="px-3 py-1.5 rounded-lg border border-[var(--color-border)] bg-[var(--color-surface)] text-xs font-medium text-[var(--color-foreground)] hover:bg-[var(--color-surface-accent)] disabled:opacity-40 disabled:cursor-not-allowed transition-colors flex items-center gap-1 shadow-sm"
            >
              Next <ChevronRight className="h-3.5 w-3.5" aria-hidden="true" />
            </button>
          </div>
        </nav>
      )}
    </div>
  );
});
