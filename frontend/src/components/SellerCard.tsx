import React, { useMemo } from 'react';
import type { ProductPrice, NormalizedOffer, Product, ProductWithPrices } from '../types';
import { normalizeOffer, formatOfferObservationTime, getProvenanceBadge } from '../marketplace';
import { ExternalLink, Sparkles, Tag, Clock, TrendingUp, CheckCircle2, AlertCircle, ShieldCheck, Database } from 'lucide-react';
import { motion } from 'framer-motion';
import { formatPrice, getDisplayPrice, type CurrencyCode } from '../currency';
import { apiService } from '../services/api';
import { ProductImage } from './common/ProductImage';

export interface SellerCardProps {
  price?: ProductPrice;
  offer?: NormalizedOffer;
  isBestDeal: boolean;
  lowestPrice: number;
  currency?: CurrencyCode;
  product?: Product | ProductWithPrices | null;
  productImageUrl?: string | null;
  productCategory?: string | null;
  productName?: string | null;
}

export const SellerCard: React.FC<SellerCardProps> = React.memo(({
  price,
  offer,
  isBestDeal,
  lowestPrice,
  currency = 'INR',
  product,
  productImageUrl,
  productCategory,
  productName,
}) => {
  const normalizedOffer = useMemo(() => {
    if (offer) return offer;
    if (price) return normalizeOffer(price);
    return null;
  }, [offer, price]);

  if (!normalizedOffer) return null;

  const currentPriceLocal = getDisplayPrice(normalizedOffer.pricing.basePrice, currency);
  const originalPriceLocal = normalizedOffer.pricing.originalPrice !== undefined
    ? getDisplayPrice(normalizedOffer.pricing.originalPrice, currency)
    : 0;
  const lowestPriceLocal = getDisplayPrice(lowestPrice, currency);

  const savings = originalPriceLocal > currentPriceLocal 
    ? originalPriceLocal - currentPriceLocal 
    : 0;

  const priceDiffFromLowest = currentPriceLocal - lowestPriceLocal;
  const observationTime = formatOfferObservationTime(
    normalizedOffer.provenance.observedAt,
    normalizedOffer.provenance.rawLastUpdated
  );
  const provBadge = getProvenanceBadge(normalizedOffer.provenance);

  const isSimulated = normalizedOffer.provenance.sourceType?.toLowerCase() === 'simulated' ||
    normalizedOffer.provenance.trustLabel?.toLowerCase().includes('simulat') ||
    normalizedOffer.source.providerName === 'mock-marketplace' ||
    normalizedOffer.source.providerName === 'mock';

  const isStaleOffer = Boolean(normalizedOffer.provenance.isStale);

  const resolvedProductImageUrl =
    (productImageUrl && productImageUrl.trim()) ||
    (product?.imageUrl && product.imageUrl.trim()) ||
    (price?.product?.imageUrl && price.product.imageUrl.trim()) ||
    (offer?.rawPrice?.product?.imageUrl && offer.rawPrice.product.imageUrl.trim()) ||
    null;

  const resolvedProductCategory =
    (productCategory && productCategory.trim()) ||
    (product?.category && product.category.trim()) ||
    (price?.product?.category && price.product.category.trim()) ||
    (offer?.rawPrice?.product?.category && offer.rawPrice.product.category.trim()) ||
    null;

  const resolvedProductName =
    (productName && productName.trim()) ||
    (product?.name && product.name.trim()) ||
    (price?.product?.name && price.product.name.trim()) ||
    (offer?.rawPrice?.product?.name && offer.rawPrice.product.name.trim()) ||
    (offer?.productTitle && offer.productTitle.trim()) ||
    'Product';

  const hasProductContext = Boolean(resolvedProductImageUrl || resolvedProductCategory);

  return (
    <motion.div
      data-testid="seller-card"
      initial={{ opacity: 0, y: 15 }}
      animate={{ opacity: 1, y: 0 }}
      exit={{ opacity: 0, y: -15 }}
      whileHover={{ y: -4, scale: 1.01 }}
      transition={{ type: 'spring', stiffness: 300, damping: 20 }}
      className={`relative overflow-hidden rounded-2xl border p-5 transition-all duration-300 ${
        isBestDeal
          ? isSimulated
            ? 'bg-[var(--color-surface)] border-amber-500/40 shadow-[0_8px_30px_rgba(245,158,11,0.08)]'
            : isStaleOffer
            ? 'bg-[var(--color-surface)] border-amber-500/40 shadow-[0_8px_30px_rgba(245,158,11,0.08)]'
            : 'bg-[var(--color-surface)] border-emerald-500/40 shadow-[0_8px_30px_rgba(16,185,129,0.08)]'
          : 'bg-[var(--color-surface)] border-[var(--color-border)] hover:border-[var(--color-ring)]/40 shadow-sm'
      }`}
    >
      {/* Glow Effect for Best Deal */}
      {isBestDeal && (
        <div className={`absolute top-0 right-0 h-[100px] w-[100px] blur-[50px] rounded-full pointer-events-none ${
          (isSimulated || isStaleOffer) ? 'bg-amber-500/10' : 'bg-emerald-500/10'
        }`} />
      )}

      {/* Top Section: Seller and Badges */}
      <div className="flex items-start justify-between gap-4 mb-4">
        <div className="flex items-center gap-3.5">
          {/* Logo container */}
          <div className="h-9 w-18 flex items-center justify-center bg-[var(--color-surface-accent)] border border-[var(--color-border)] rounded-xl p-1.5 shadow-inner overflow-hidden">
            <ProductImage
              src={normalizedOffer.source.logoUrl}
              alt={normalizedOffer.source.sellerName || 'Seller'}
              className="max-h-full max-w-full object-contain filter brightness-95"
              showFallbackText
              fallbackText={normalizedOffer.source.sellerName?.substring(0, 3) || 'SEL'}
            />
          </div>
          
          <div className="flex flex-col">
            <span className="font-bold text-[var(--color-foreground)] text-sm">{normalizedOffer.source.sellerName}</span>
            <span className="inline-flex items-center gap-1 text-[10px] text-[var(--color-muted-foreground)] font-medium mt-0.5">
              <Clock className="h-3 w-3 text-[var(--color-muted-foreground)]" /> {observationTime}
            </span>
          </div>
        </div>

        {/* Highlights & Badges */}
        <div className="flex flex-col gap-1.5 items-end">
          {isBestDeal && (
            isSimulated ? (
              <span className="inline-flex items-center gap-1 px-2.5 py-1 rounded-full bg-amber-500/15 border border-amber-500/30 text-[9px] font-extrabold text-amber-700 dark:text-amber-300 uppercase tracking-wider shadow-sm">
                <Sparkles className="h-3 w-3" /> Simulated Deal (Testing)
              </span>
            ) : isStaleOffer ? (
              <span className="inline-flex items-center gap-1 px-2.5 py-1 rounded-full bg-amber-500/15 border border-amber-500/30 text-[9px] font-extrabold text-amber-700 dark:text-amber-300 uppercase tracking-wider shadow-sm">
                <Sparkles className="h-3 w-3" /> Lowest Stale Listed Price
              </span>
            ) : (
              <span className="inline-flex items-center gap-1 px-2.5 py-1 rounded-full bg-emerald-500/15 border border-emerald-500/30 text-[9px] font-extrabold text-emerald-700 dark:text-emerald-300 uppercase tracking-wider shadow-sm">
                <Sparkles className="h-3 w-3" /> Best Deal
              </span>
            )
          )}
          {normalizedOffer.pricing.discountPercentage > 0 && (
            <span className="inline-flex items-center gap-1 px-2.5 py-1 rounded-full bg-rose-500/15 border border-rose-500/30 text-[9px] font-extrabold text-rose-700 dark:text-rose-300 uppercase tracking-wider">
              <Tag className="h-2.5 w-2.5" /> Save {normalizedOffer.pricing.discountPercentage.toFixed(0)}%
            </span>
          )}
          {/* Honest Provenance Badge */}
          <span
            title={provBadge.description}
            className={`inline-flex items-center gap-1 px-2 py-0.5 rounded-full text-[9px] font-semibold border ${
              normalizedOffer.provenance.isLiveVerified && !isSimulated
                ? 'bg-emerald-500/10 text-emerald-700 dark:text-emerald-300 border-emerald-500/25'
                : provBadge.tone === 'amber'
                ? 'bg-amber-500/10 text-amber-700 dark:text-amber-300 border-amber-500/25'
                : 'bg-[var(--color-surface-accent)] text-[var(--color-muted-foreground)] border-[var(--color-border)]'
            }`}
          >
            {normalizedOffer.provenance.isLiveVerified && !isSimulated ? (
              <ShieldCheck className="h-2.5 w-2.5 text-emerald-500" />
            ) : (
              <Database className="h-2.5 w-2.5 text-[var(--color-muted-foreground)]" />
            )}
            {provBadge.label}
          </span>
        </div>
      </div>

      {/* Product Image Preview (when product context is available) */}
      {hasProductContext && (
        <div className="relative h-36 w-full bg-[var(--color-surface-accent)]/50 rounded-xl p-3 mb-3 flex items-center justify-center overflow-hidden border border-[var(--color-border)] group">
          <ProductImage
            src={resolvedProductImageUrl}
            alt={resolvedProductName}
            category={resolvedProductCategory}
            className="h-full w-full object-contain transition-transform duration-300 group-hover:scale-105"
            showFallbackText
            fallbackText={resolvedProductName.substring(0, 3)}
          />
        </div>
      )}

      {/* Middle Section: Prices & Calculations */}
      <div className="flex flex-col gap-2 py-3.5 border-t border-b border-[var(--color-border)] my-2">
        <div className="flex items-baseline justify-between">
          <div className="flex flex-col">
            <span className="text-2xl font-black text-[var(--color-foreground)] tracking-tight">
              {formatPrice(currentPriceLocal, currency)}
            </span>
            <span className="text-[10px] font-medium text-[var(--color-muted-foreground)]">
              Listed base price
            </span>
          </div>
          {originalPriceLocal > currentPriceLocal && (
            <span className="text-xs text-[var(--color-muted-foreground)] line-through font-normal">
              {formatPrice(originalPriceLocal, currency)}
            </span>
          )}
        </div>

        {/* Calculations & Status */}
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
            <div className={`flex items-center gap-1.5 ${isSimulated ? 'text-amber-600 dark:text-amber-400' : 'text-emerald-600 dark:text-emerald-400'}`}>
              <Sparkles className="h-3.5 w-3.5" />
              <span>{isSimulated ? 'Lowest simulated base price (Testing)' : 'Lowest listed base price'}</span>
            </div>
          )}

          {/* Availability / Stock Status ONLY when explicitly known */}
          {normalizedOffer.availability.status !== 'unknown' && (
            <div className={`flex items-center gap-1.5 ${normalizedOffer.availability.isAvailable ? 'text-emerald-600 dark:text-emerald-400' : 'text-zinc-500'}`}>
              {normalizedOffer.availability.isAvailable ? <CheckCircle2 className="h-3.5 w-3.5" /> : <AlertCircle className="h-3.5 w-3.5" />}
              <span>{normalizedOffer.availability.isAvailable ? 'In Stock' : 'Out of Stock'}</span>
            </div>
          )}

          <p className="text-[10px] text-[var(--color-muted-foreground)] font-normal mt-0.5">
            Base price &middot; Fees may apply at checkout
          </p>
        </div>
      </div>

      {/* Bottom Section: CTA */}
      <div className="mt-4 pt-1">
        <a
          href={normalizedOffer.source.offerUrl}
          target="_blank"
          rel="noopener noreferrer"
          onClick={() => apiService.trackSellerClick(normalizedOffer.id)}
          aria-label={`Visit ${normalizedOffer.source.sellerName || 'Seller'} storefront`}
          className={`flex items-center justify-center gap-2 w-full py-3 px-4 text-xs font-bold rounded-xl border transition-all active:scale-[0.98] focus-visible:ring-2 focus-visible:ring-[var(--color-ring)] focus-visible:outline-none ${
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
