import React from 'react';
import type { ProductPrice } from '../types';
import { ExternalLink, Sparkles, Tag, Clock, TrendingUp } from 'lucide-react';
import { motion } from 'framer-motion';
import { formatPrice, getDisplayPrice, type CurrencyCode } from '../currency';
import { apiService } from '../services/api';

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
          ? 'bg-gradient-to-br from-emerald-500/[0.03] via-zinc-950/90 to-emerald-500/[0.01] border-emerald-500/40 shadow-[0_8px_30px_rgb(16,185,129,0.06)]'
          : 'bg-zinc-950/40 border-zinc-900 hover:border-zinc-800 shadow-[0_8px_30px_rgb(0,0,0,0.2)]'
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
          {price.seller?.logoUrl ? (
            <div className="h-9 w-18 flex items-center justify-center bg-zinc-950/80 border border-zinc-900 rounded-xl p-2 shadow-inner">
              <img
                src={price.seller.logoUrl}
                alt={price.seller.name}
                className="max-h-full max-w-full object-contain filter brightness-95"
              />
            </div>
          ) : (
            <div className="h-9 w-18 flex items-center justify-center bg-zinc-900 border border-zinc-800 rounded-xl text-[10px] font-extrabold text-zinc-400 uppercase tracking-widest">
              {price.seller?.name?.substring(0, 3) || 'SEL'}
            </div>
          )}
          
          <div className="flex flex-col">
            <span className="font-bold text-zinc-100 text-sm">{price.seller?.name}</span>
            <span className="inline-flex items-center gap-1 text-[10px] text-zinc-500 font-medium mt-0.5">
              <Clock className="h-3 w-3 text-zinc-600" /> {price.lastUpdated}
            </span>
          </div>
        </div>

        {/* Highlights */}
        <div className="flex flex-col gap-1.5 items-end">
          {isBestDeal && (
            <span className="inline-flex items-center gap-1 px-2.5 py-1 rounded-full bg-emerald-500/10 border border-emerald-500/20 text-[9px] font-extrabold text-emerald-400 uppercase tracking-wider shadow-[0_2px_10px_rgba(16,185,129,0.15)]">
              <Sparkles className="h-3 w-3" /> Best Deal
            </span>
          )}
          {price.discountPercentage > 0 && (
            <span className="inline-flex items-center gap-1 px-2.5 py-1 rounded-full bg-rose-500/10 border border-rose-500/15 text-[9px] font-extrabold text-rose-400 uppercase tracking-wider">
              <Tag className="h-2.5 w-2.5" /> Save {price.discountPercentage.toFixed(0)}%
            </span>
          )}
        </div>
      </div>

      {/* Middle Section: Prices & Calculations */}
      <div className="flex flex-col gap-2 py-3.5 border-t border-b border-zinc-900/60 my-2">
        <div className="flex items-baseline justify-between">
          <span className="text-2xl font-black text-white tracking-tight">
            {formatPrice(currentPriceLocal, currency)}
          </span>
          {originalPriceLocal > currentPriceLocal && (
            <span className="text-xs text-zinc-500 line-through font-normal">
              {formatPrice(originalPriceLocal, currency)}
            </span>
          )}
        </div>

        {/* Calculations */}
        <div className="flex flex-col gap-1 text-[11px] font-semibold">
          {/* Savings calculation */}
          {savings > 0 && (
            <div className="flex items-center gap-1.5 text-emerald-400/90">
              <Tag className="h-3.5 w-3.5" />
              <span>Save {formatPrice(savings, currency)}</span>
            </div>
          )}

          {/* Difference from best price calculation */}
          {!isBestDeal && priceDiffFromLowest > 0 && (
            <div className="flex items-center gap-1.5 text-rose-400/80">
              <TrendingUp className="h-3.5 w-3.5" />
              <span>{formatPrice(priceDiffFromLowest, currency)} more than best deal</span>
            </div>
          )}

          {isBestDeal && (
            <div className="flex items-center gap-1.5 text-emerald-400">
              <Sparkles className="h-3.5 w-3.5" />
              <span>Lowest price guaranteed</span>
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
              ? 'bg-white hover:bg-zinc-200 text-black border-white shadow-[0_4px_12px_rgba(255,255,255,0.08)]'
              : 'bg-zinc-900 hover:bg-zinc-800 text-zinc-200 border-zinc-800 hover:text-white hover:border-zinc-700'
          }`}
        >
          Visit Seller
          <ExternalLink className="h-3.5 w-3.5" />
        </a>
      </div>
    </motion.div>
  );
});
