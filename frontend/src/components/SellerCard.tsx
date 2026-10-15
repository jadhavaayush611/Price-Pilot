import React from 'react';
import type { ProductPrice } from '../types';
import { ExternalLink, Sparkles, Tag, Clock, TrendingUp } from 'lucide-react';
import { motion } from 'framer-motion';
import { formatPrice, getDisplayPrice, type CurrencyCode } from '../currency';
import { apiService } from '../services/api';
import { ProductImage } from './common/ProductImage';

interface SellerCardProps {
  price: ProductPrice;
  isBestDeal: boolean;
  lowestPrice: number;
  currency?: CurrencyCode;
}

export const SellerCard: React.FC<SellerCardProps> = React.memo(({
  price,
  isBestDeal,
  lowestPrice,
  currency = 'INR',
}) => {
  const currentPriceLocal = getDisplayPrice(price.currentPrice, currency);
  const originalPriceLocal = getDisplayPrice(price.originalPrice, currency);
  const lowestPriceLocal = getDisplayPrice(lowestPrice, currency);

  const savings = originalPriceLocal > currentPriceLocal 
    ? originalPriceLocal - currentPriceLocal 
    : 0;

  const priceDiffFromLowest = currentPriceLocal - lowestPriceLocal;

  return (
    <motion.div
      initial={{ opacity: 0, y: 15 }}
      animate={{ opacity: 1, y: 0 }}
      exit={{ opacity: 0, y: -15 }}
      whileHover={{ y: -4, scale: 1.01 }}
      transition={{ type: 'spring', stiffness: 300, damping: 20 }}
      className={`relative overflow-hidden rounded-2xl border p-5 transition-all duration-300 ${
        isBestDeal
          ? 'bg-[var(--color-surface)] border-emerald-500/40 shadow-[0_8px_30px_rgba(16,185,129,0.08)]'
          : 'bg-[var(--color-surface)] border-[var(--color-border)] hover:border-[var(--color-ring)]/40 shadow-sm'
      }`}
    >
      {/* Glow Effect for Best Deal */}
      {isBestDeal && (
        <div className="absolute top-0 right-0 h-[100px] w-[100px] bg-emerald-500/10 blur-[50px] rounded-full pointer-events-none" />
      )}

      {/* Top Section: Seller and Badges */}
      <div className="flex items-start justify-between gap-4 mb-4">
        <div className="flex items-center gap-3.5">
          {/* Logo container */}
          <div className="h-9 w-18 flex items-center justify-center bg-[var(--color-surface-accent)] border border-[var(--color-border)] rounded-xl p-1.5 shadow-inner overflow-hidden">
            <ProductImage
              src={price.seller?.logoUrl}
              alt={price.seller?.name || 'Seller'}
              className="max-h-full max-w-full object-contain filter brightness-95"
              showFallbackText
              fallbackText={price.seller?.name?.substring(0, 3) || 'SEL'}
            />
          </div>
          
          <div className="flex flex-col">
            <span className="font-bold text-[var(--color-foreground)] text-sm">{price.seller?.name}</span>
            <span className="inline-flex items-center gap-1 text-[10px] text-[var(--color-muted-foreground)] font-medium mt-0.5">
              <Clock className="h-3 w-3 text-[var(--color-muted-foreground)]" /> {price.lastUpdated}
            </span>
          </div>
        </div>

        {/* Highlights */}
        <div className="flex flex-col gap-1.5 items-end">
          {isBestDeal && (
            <span className="inline-flex items-center gap-1 px-2.5 py-1 rounded-full bg-emerald-500/15 border border-emerald-500/30 text-[9px] font-extrabold text-emerald-700 dark:text-emerald-300 uppercase tracking-wider shadow-sm">
              <Sparkles className="h-3 w-3" /> Best Deal
            </span>
          )}
          {price.discountPercentage > 0 && (
            <span className="inline-flex items-center gap-1 px-2.5 py-1 rounded-full bg-rose-500/15 border border-rose-500/30 text-[9px] font-extrabold text-rose-700 dark:text-rose-300 uppercase tracking-wider">
              <Tag className="h-2.5 w-2.5" /> Save {price.discountPercentage.toFixed(0)}%
            </span>
          )}
        </div>
      </div>

      {/* Middle Section: Prices & Calculations */}
      <div className="flex flex-col gap-2 py-3.5 border-t border-b border-[var(--color-border)] my-2">
        <div className="flex items-baseline justify-between">
          <span className="text-2xl font-black text-[var(--color-foreground)] tracking-tight">
            {formatPrice(currentPriceLocal, currency)}
          </span>
          {originalPriceLocal > currentPriceLocal && (
            <span className="text-xs text-[var(--color-muted-foreground)] line-through font-normal">
              {formatPrice(originalPriceLocal, currency)}
            </span>
          )}
        </div>

        {/* Calculations */}
        <div className="flex flex-col gap-1 text-[11px] font-semibold">
          {/* Savings calculation */}
          {savings > 0 && (
            <div className="flex items-center gap-1.5 text-emerald-600 dark:text-emerald-400">
              <Tag className="h-3.5 w-3.5" />
              <span>Save {formatPrice(savings, currency)}</span>
            </div>
          )}

          {/* Difference from best price calculation */}
          {!isBestDeal && priceDiffFromLowest > 0 && (
            <div className="flex items-center gap-1.5 text-rose-600 dark:text-rose-400">
              <TrendingUp className="h-3.5 w-3.5" />
              <span>{formatPrice(priceDiffFromLowest, currency)} more than best deal</span>
            </div>
          )}

          {isBestDeal && (
            <div className="flex items-center gap-1.5 text-emerald-600 dark:text-emerald-400">
              <Sparkles className="h-3.5 w-3.5" />
              <span>Lowest verified price</span>
            </div>
          )}
        </div>
      </div>

      {/* Bottom Section: CTA */}
      <div className="mt-4 pt-1">
        <a
          href={price.productUrl}
          target="_blank"
          rel="noopener noreferrer"
          onClick={() => apiService.trackSellerClick(price.id)}
          className={`flex items-center justify-center gap-2 w-full py-3 px-4 text-xs font-bold rounded-xl border transition-all active:scale-[0.98] ${
            isBestDeal
              ? 'bg-[var(--color-primary)] hover:opacity-90 text-[var(--color-primary-foreground)] border-transparent shadow-sm'
              : 'bg-[var(--color-surface)] hover:bg-[var(--color-surface-accent)] text-[var(--color-foreground)] border-[var(--color-border)]'
          }`}
        >
          Visit Seller
          <ExternalLink className="h-3.5 w-3.5" />
        </a>
      </div>
    </motion.div>
  );
});
