import React from 'react';
import { Link } from 'react-router-dom';
import type { AlternativeProduct, AlternativeType } from '../../types';
import { Award, Sparkles, UserCheck, Star, Layers, TrendingDown, TrendingUp, AlertTriangle, Tag } from 'lucide-react';
import { formatPrice, getSavedCurrency, getDisplayPrice } from '../../currency';

export interface AlternativeCardProps {
  alternative: AlternativeProduct;
  sourceProductId?: string;
  sourceProductName?: string;
  sourceProductPrice?: number;
  isPersonalized?: boolean;
}

/**
 * Presentation-only mapping from backend AlternativeType enum to user-friendly label.
 */
export function formatAlternativeType(type?: AlternativeType | string): string {
  if (!type) return 'Alternative';
  switch (type.toUpperCase()) {
    case 'CHEAPER':
      return 'Cheaper Alternative';
    case 'SIMILAR':
      return 'Similar Product';
    case 'BETTER_VALUE':
      return 'Better Value';
    case 'PERFORMANCE_UPGRADE':
      return 'Performance Upgrade';
    case 'PREMIUM':
      return 'Premium Alternative';
    case 'BUDGET_FALLBACK':
      return 'Budget Alternative';
    default:
      return type.replace(/_/g, ' ');
  }
}

export const AlternativeCard: React.FC<AlternativeCardProps> = ({
  alternative,
  sourceProductId,
  isPersonalized = false,
}) => {
  const currency = getSavedCurrency();

  // Price conversion
  const rawPrice = alternative.currentBestPrice ?? (alternative.prices && alternative.prices.length > 0 ? alternative.prices[0].currentPrice : undefined);
  const displayPrice = rawPrice !== undefined ? getDisplayPrice(rawPrice, currency) : undefined;
  const originalDisplayPrice = alternative.originalPrice !== undefined ? getDisplayPrice(alternative.originalPrice, currency) : undefined;

  // Personalization detection from backend response
  const hasPersonalization = isPersonalized || 
    alternative.personalizedScore !== undefined || 
    (alternative.personalizedEvidence && (
      (alternative.personalizedEvidence.reasons && alternative.personalizedEvidence.reasons.length > 0) ||
      Boolean(alternative.personalizedEvidence.summary)
    ));

  // Score display - authoritative backend values
  const displayedScore = alternative.personalizedScore !== undefined
    ? Math.round(alternative.personalizedScore)
    : Math.round(alternative.alternativeScore);

  return (
    <article
      aria-label={`Alternative product: ${alternative.name}`}
      className={`group bg-zinc-950 border rounded-2xl p-5 transition-all duration-300 flex flex-col justify-between hover:shadow-xl ${
        hasPersonalization
          ? 'border-indigo-900/50 hover:border-indigo-700/70'
          : 'border-zinc-900 hover:border-zinc-700'
      }`}
    >
      <div className="space-y-4">
        {/* Top Header & Badges */}
        <div className="flex items-center justify-between gap-2 flex-wrap">
          <div className="flex items-center gap-1.5 flex-wrap">
            {/* Alternative Type Badge */}
            {alternative.reasonCodes && alternative.reasonCodes.length > 0 ? (
              <span className="text-[10px] font-bold uppercase tracking-wider text-emerald-300 bg-emerald-950/80 border border-emerald-700/50 px-2 py-0.5 rounded-full flex items-center gap-1 shadow-sm">
                <Award className="w-3 h-3 text-emerald-400" />
                <span>{formatAlternativeType(alternative.reasonCodes[0])}</span>
              </span>
            ) : (
              <span className="text-[10px] font-bold uppercase tracking-wider text-emerald-300 bg-emerald-950/80 border border-emerald-700/50 px-2 py-0.5 rounded-full flex items-center gap-1 shadow-sm">
                <Award className="w-3 h-3 text-emerald-400" />
                <span>Alternative</span>
              </span>
            )}

            {/* Personalized Indicator */}
            {hasPersonalization && (
              <span className="text-[10px] font-bold uppercase tracking-wider text-indigo-300 bg-indigo-950/60 border border-indigo-700/40 px-2 py-0.5 rounded-full flex items-center gap-1">
                <Sparkles className="w-2.5 h-2.5 text-indigo-400" />
                <span>Personalized</span>
              </span>
            )}

            <span className="text-[10px] font-bold uppercase tracking-wider text-zinc-400 bg-zinc-900 border border-zinc-800 px-2 py-0.5 rounded">
              {alternative.brand}
            </span>
          </div>

          {/* Backend Authoritative Score */}
          <div className="flex items-center gap-1.5 font-mono text-xs">
            <span
              title={hasPersonalization
                ? `Personalized Score: ${displayedScore}/100 (Base: ${Math.round(alternative.alternativeScore)}${alternative.personalizationAdjustment ? `, Adjustment: ${alternative.personalizationAdjustment >= 0 ? '+' : ''}${alternative.personalizationAdjustment.toFixed(1)}` : ''})`
                : `Alternative Score: ${displayedScore}/100`}
              className="text-zinc-200 font-semibold bg-zinc-900/80 border border-zinc-800 px-2 py-0.5 rounded flex items-center gap-1"
            >
              <span>Score:</span>
              <strong className={hasPersonalization ? 'text-indigo-400' : 'text-emerald-400'}>
                {displayedScore}
              </strong>
              <span className="text-zinc-500">/100</span>
            </span>
          </div>
        </div>

        {/* Product Image */}
        <div className="h-44 w-full bg-zinc-900/40 rounded-xl p-3 flex items-center justify-center group-hover:scale-[1.02] transition-transform overflow-hidden border border-zinc-900/60">
          {alternative.imageUrl ? (
            <img src={alternative.imageUrl} alt={alternative.name} className="h-full w-full object-contain" />
          ) : (
            <span className="text-xs text-zinc-600 font-mono">No Image Available</span>
          )}
        </div>

        {/* Product Identity & Objective Facts */}
        <div className="space-y-1">
          <div className="flex items-start justify-between gap-2">
            <h3 className="text-sm font-semibold text-zinc-100 line-clamp-2 group-hover:text-white transition-colors">
              {alternative.name}
            </h3>
            {alternative.rating !== undefined && alternative.rating > 0 && (
              <span className="shrink-0 flex items-center gap-1 text-xs font-semibold text-amber-400 bg-amber-950/30 border border-amber-800/30 px-1.5 py-0.5 rounded">
                <Star className="w-3 h-3 fill-amber-400 text-amber-400" />
                <span>{alternative.rating.toFixed(1)}</span>
              </span>
            )}
          </div>
          {alternative.description && (
            <p className="text-xs text-zinc-400 line-clamp-2">{alternative.description}</p>
          )}
        </div>

        {/* Backend Price Comparison Callout */}
        {alternative.priceDifference !== undefined && (
          <div className="flex items-center gap-2 p-2 rounded-xl bg-zinc-900/40 border border-zinc-800/60 text-xs">
            {alternative.priceDifference < 0 ? (
              <span className="inline-flex items-center gap-1 text-emerald-400 font-bold">
                <TrendingDown className="w-3.5 h-3.5 text-emerald-400 shrink-0" />
                Save {formatPrice(getDisplayPrice(Math.abs(alternative.priceDifference), currency), currency)}
                {alternative.priceDifferencePercentage !== undefined && (
                  <span className="text-emerald-500/80 font-normal">
                    ({Math.abs(Math.round(alternative.priceDifferencePercentage))}% less)
                  </span>
                )}
              </span>
            ) : alternative.priceDifference > 0 ? (
              <span className="inline-flex items-center gap-1 text-zinc-300 font-medium">
                <TrendingUp className="w-3.5 h-3.5 text-zinc-400 shrink-0" />
                +{formatPrice(getDisplayPrice(alternative.priceDifference, currency), currency)}
                {alternative.priceDifferencePercentage !== undefined && (
                  <span className="text-zinc-500 font-normal">
                    (+{Math.round(alternative.priceDifferencePercentage)}%)
                  </span>
                )}
              </span>
            ) : (
              <span className="text-zinc-400 font-medium">Same price as current product</span>
            )}
            {alternative.semanticSimilarityScore !== undefined && alternative.semanticSimilarityScore > 0 && (
              <span className="ml-auto text-[10px] text-zinc-400 bg-zinc-900 border border-zinc-800 px-1.5 py-0.5 rounded font-mono">
                {Math.round(alternative.semanticSimilarityScore * 100)}% Match
              </span>
            )}
          </div>
        )}

        {/* Backend Primary Explanation */}
        {alternative.primaryExplanation && (
          <p className="text-xs text-zinc-300 italic bg-zinc-900/40 p-2.5 rounded-xl border border-zinc-800/60 leading-relaxed">
            "{alternative.primaryExplanation}"
          </p>
        )}

        {/* Personalized Evidence Section ("Matches Your Preferences") */}
        {alternative.personalizedEvidence && (
          <section aria-label="Why this matches your preferences" className="bg-indigo-950/20 border border-indigo-900/40 rounded-xl p-3 space-y-2">
            <h4 className="text-xs font-bold text-indigo-300 flex items-center gap-1.5 uppercase tracking-wide">
              <UserCheck className="w-3.5 h-3.5 text-indigo-400 shrink-0" />
              Matches Your Preferences
            </h4>
            {alternative.personalizedEvidence.summary && (
              <p className="text-xs text-indigo-200/90 leading-relaxed">
                {alternative.personalizedEvidence.summary}
              </p>
            )}
            {alternative.personalizedEvidence.reasons && alternative.personalizedEvidence.reasons.length > 0 && (
              <ul className="space-y-1 text-xs text-zinc-300">
                {alternative.personalizedEvidence.reasons.map((reason, idx) => (
                  <li key={idx} className="flex items-start gap-1.5">
                    <span className="text-indigo-400 font-bold shrink-0">★</span>
                    <span className="text-zinc-200">{reason.reason || reason.dimension}</span>
                  </li>
                ))}
              </ul>
            )}
            {alternative.personalizedEvidence.supportingFactors && alternative.personalizedEvidence.supportingFactors.length > 0 && (
              <ul className="space-y-1 text-xs text-zinc-300">
                {alternative.personalizedEvidence.supportingFactors.map((factor, idx) => (
                  <li key={idx} className="flex items-start gap-1.5">
                    <span className="text-emerald-400 font-bold shrink-0">✓</span>
                    <span className="text-zinc-300">{factor}</span>
                  </li>
                ))}
              </ul>
            )}
          </section>
        )}

        {/* Trade-offs Section (if provided by backend) */}
        {alternative.personalizedEvidence?.tradeOffs && alternative.personalizedEvidence.tradeOffs.length > 0 && (
          <section aria-label="Trade-offs" className="bg-amber-950/20 border border-amber-900/40 rounded-xl p-3 space-y-2">
            <h4 className="text-xs font-bold text-amber-400 flex items-center gap-1.5 uppercase tracking-wide">
              <AlertTriangle className="w-3.5 h-3.5 text-amber-400 shrink-0" />
              Trade-offs to Consider
            </h4>
            <ul className="space-y-1 text-xs text-zinc-300">
              {alternative.personalizedEvidence.tradeOffs.map((tradeOff, idx) => (
                <li key={idx} className="flex items-start gap-1.5">
                  <span className="text-amber-400 shrink-0">•</span>
                  <span>{tradeOff}</span>
                </li>
              ))}
            </ul>
          </section>
        )}

        {/* Factual Backend Evidence Badges */}
        {alternative.evidence && alternative.evidence.length > 0 && (
          <div className="flex flex-wrap gap-1.5 pt-1">
            {alternative.evidence.map((ev, idx) => (
              <span
                key={idx}
                className="text-[10px] px-2 py-0.5 rounded border flex items-center gap-1 text-zinc-300 bg-zinc-900/60 border-zinc-800"
              >
                <Tag className="w-2.5 h-2.5 text-zinc-400" />
                <span>{ev.description || ev.category}</span>
              </span>
            ))}
          </div>
        )}
      </div>

      {/* Footer / Factual Price & Actions */}
      <div className="pt-4 mt-4 border-t border-zinc-900 flex items-center justify-between gap-2">
        <div>
          <span className="text-[10px] text-zinc-500 block uppercase font-medium">Best Offer</span>
          <div className="flex items-baseline gap-1.5">
            <span className="text-sm font-bold font-mono text-zinc-100">
              {displayPrice !== undefined ? formatPrice(displayPrice, currency) : 'Check sellers'}
            </span>
            {originalDisplayPrice !== undefined && displayPrice !== undefined && originalDisplayPrice > displayPrice && (
              <span className="text-xs text-zinc-500 line-through font-mono">
                {formatPrice(originalDisplayPrice, currency)}
              </span>
            )}
          </div>
        </div>

        <div className="flex items-center gap-1.5">
          {sourceProductId && (
            <Link
              to={`/compare?ids=${sourceProductId},${alternative.id}`}
              className="p-2 text-zinc-400 hover:text-white bg-zinc-900/80 border border-zinc-800 hover:border-zinc-700 rounded-xl transition-all"
              title="Compare with current product"
            >
              <Layers className="w-3.5 h-3.5" />
            </Link>
          )}
          <Link
            to={`/product/${alternative.id}`}
            className="px-3.5 py-1.5 text-xs font-semibold text-zinc-100 bg-zinc-900 border border-zinc-800 rounded-xl hover:border-zinc-600 hover:bg-zinc-800 active:scale-[0.98] transition-all"
          >
            View Details
          </Link>
        </div>
      </div>
    </article>
  );
};
