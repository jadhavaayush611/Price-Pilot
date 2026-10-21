import React from 'react';
import { Link } from 'react-router-dom';
import type { ProductWithPrices, ProductScore, EvidenceItem } from '../../types';
import { Award, CheckCircle2, AlertTriangle, ShieldCheck, Tag, Sparkles, UserCheck } from 'lucide-react';
import { formatPrice, getSavedCurrency, getDisplayPrice } from '../../currency';
import { ProductImage } from '../common/ProductImage';

export interface RecommendationCardProps {
  product: ProductWithPrices;
  score?: ProductScore;
  isRecommended?: boolean;
  recommendationType?: string;
  confidence?: number;
  explanation?: string;
  supportingFactors?: string[];
  tradeOffs?: string[];
  evidence?: EvidenceItem[];
  personalizationEvidence?: EvidenceItem[];
  isPersonalized?: boolean;
}

export const RecommendationCard: React.FC<RecommendationCardProps> = ({
  product,
  score,
  isRecommended = false,
  recommendationType = 'BEST OVERALL',
  confidence,
  explanation,
  supportingFactors,
  tradeOffs,
  evidence,
  personalizationEvidence,
  isPersonalized = false,
}) => {
  const currency = getSavedCurrency();
  const badgeText = isRecommended
    ? (recommendationType || 'BEST OVERALL').toUpperCase()
    : score?.recommendationBadge || 'ALTERNATIVE';

  const scoreValue = score?.overallScore !== undefined 
    ? Math.round(score.overallScore) 
    : (isRecommended ? 92 : 85);
  const confidencePercent = confidence !== undefined ? Math.round(confidence * 100) : null;

  // Determine display price
  const rawPrice = product.lowestPrice ?? (product.prices && product.prices.length > 0 ? product.prices[0].currentPrice : undefined);
  const displayPrice = rawPrice !== undefined ? getDisplayPrice(rawPrice, currency) : undefined;

  const hasPersonalization = isPersonalized || (personalizationEvidence && personalizationEvidence.length > 0) || (score?.personalizationContribution && score.personalizationContribution !== 0);

  return (
    <article
      aria-label={`Recommendation for ${product.name}`}
      className={`group bg-[var(--color-surface)] backdrop-blur-sm border rounded-2xl p-5 transition-all duration-300 flex flex-col justify-between hover:shadow-xl ${
        isRecommended
          ? 'border-emerald-600/60 shadow-lg shadow-emerald-950/20 ring-1 ring-emerald-500/20'
          : hasPersonalization
          ? 'border-indigo-500/40 hover:border-indigo-500/60'
          : 'border-[var(--color-border)] hover:border-[var(--color-border)]/80'
      }`}
    >
      <div className="space-y-4">
        {/* Top Header & Badges */}
        <div className="flex items-center justify-between gap-2 flex-wrap">
          <div className="flex items-center gap-2 flex-wrap">
            <span className="text-[10px] font-bold uppercase tracking-wider text-[var(--color-muted-foreground)] bg-[var(--color-surface-accent)] border border-[var(--color-border)] px-2 py-0.5 rounded">
              {product.brand}
            </span>
            {product.category && (
              <span className="text-[10px] font-medium text-[var(--color-muted-foreground)] bg-[var(--color-surface-accent)]/60 border border-[var(--color-border)] px-2 py-0.5 rounded">
                {product.category}
              </span>
            )}
            {isRecommended && (
              <span className="text-[10px] font-bold uppercase tracking-wider text-emerald-700 dark:text-emerald-300 bg-emerald-500/15 border border-emerald-500/30 px-2.5 py-0.5 rounded-full flex items-center gap-1 shadow-sm">
                <Award className="w-3 h-3 text-emerald-600 dark:text-emerald-400" />
                <span>{badgeText}</span>
              </span>
            )}
            {hasPersonalization && !isRecommended && (
              <span className="text-[10px] font-bold uppercase tracking-wider text-indigo-700 dark:text-indigo-300 bg-indigo-500/15 border border-indigo-500/30 px-2.5 py-0.5 rounded-full flex items-center gap-1">
                <Sparkles className="w-2.5 h-2.5 text-indigo-600 dark:text-indigo-400" />
                <span>Personalized</span>
              </span>
            )}
          </div>

          <div className="flex items-center gap-2 font-mono text-xs">
            <span 
              title={score?.baseScore !== undefined && score?.personalizationContribution !== undefined
                ? `Base Score: ${Math.round(score.baseScore)} | Preference Match: ${score.personalizationContribution >= 0 ? '+' : ''}${score.personalizationContribution.toFixed(1)}`
                : 'Deterministic Recommendation Score'}
              className="text-[var(--color-foreground)] font-semibold bg-[var(--color-surface-accent)] border border-[var(--color-border)] px-2 py-0.5 rounded flex items-center gap-1"
            >
              <span>Score:</span>
              <strong className={hasPersonalization ? "text-indigo-600 dark:text-indigo-400" : "text-emerald-600 dark:text-emerald-400"}>
                {scoreValue}
              </strong>
              <span className="text-[var(--color-muted-foreground)]">/100</span>
            </span>
            {confidencePercent !== null && (
              <span
                title="Confidence calculated from score separation, data completeness, and evidence volume"
                className="text-[var(--color-muted-foreground)] bg-[var(--color-surface-accent)]/60 border border-[var(--color-border)] px-1.5 py-0.5 rounded flex items-center gap-1"
              >
                <ShieldCheck className="w-3 h-3 text-indigo-600 dark:text-indigo-400" />
                <span>{confidencePercent}%</span>
              </span>
            )}
          </div>
        </div>

        {/* Product Image */}
        <div className="h-44 w-full bg-[var(--color-surface-accent)]/50 rounded-xl p-3 flex items-center justify-center group-hover:scale-[1.02] transition-transform overflow-hidden border border-[var(--color-border)]">
          <ProductImage
            src={product.imageUrl}
            alt={product.name}
            category={product.category}
            className="h-full w-full object-contain"
            showFallbackText
            fallbackText={product.brand}
          />
        </div>

        {/* Product Identity & Objective Facts */}
        <div className="space-y-1">
          <h3 className="text-sm font-semibold text-[var(--color-foreground)] line-clamp-2 transition-colors">
            {product.name}
          </h3>
          <p className="text-xs text-[var(--color-muted-foreground)] line-clamp-2">{product.description}</p>
        </div>

        {/* AI/Deterministic Explanation Callout */}
        {explanation && (
          <p className="text-xs text-[var(--color-muted-foreground)] italic bg-[var(--color-surface-accent)]/50 p-2.5 rounded-xl border border-[var(--color-border)] leading-relaxed">
            "{explanation}"
          </p>
        )}

        {/* Personalized Evidence Section ("Why this matches you") */}
        {personalizationEvidence && personalizationEvidence.length > 0 && (
          <section aria-label="Why this matches your preferences" className="bg-indigo-500/10 border border-indigo-500/25 rounded-xl p-3 space-y-2">
            <h4 className="text-xs font-bold text-indigo-700 dark:text-indigo-300 flex items-center gap-1.5 uppercase tracking-wide">
              <UserCheck className="w-3.5 h-3.5 text-indigo-600 dark:text-indigo-400 shrink-0" />
              Matches Your Preferences
            </h4>
            <ul className="space-y-1.5 text-xs text-[var(--color-foreground)]">
              {personalizationEvidence.map((ev, idx) => (
                <li key={idx} className="flex items-start gap-1.5">
                  <span className={ev.positive ? "text-indigo-600 dark:text-indigo-400 font-bold shrink-0" : "text-amber-600 dark:text-amber-400 font-bold shrink-0"}>
                    {ev.positive ? "★" : "•"}
                  </span>
                  <span>{ev.description}</span>
                </li>
              ))}
            </ul>
          </section>
        )}

        {/* Supporting Factors ("Objective Product Strengths") */}
        {isRecommended && supportingFactors && supportingFactors.length > 0 && (
          <section aria-label="Why this product" className="bg-[var(--color-surface-accent)]/50 border border-[var(--color-border)] rounded-xl p-3 space-y-2">
            <h4 className="text-xs font-bold text-emerald-600 dark:text-emerald-400 flex items-center gap-1.5 uppercase tracking-wide">
              <CheckCircle2 className="w-3.5 h-3.5 text-emerald-600 dark:text-emerald-400 shrink-0" />
              Product Intelligence Facts
            </h4>
            <ul className="space-y-1 text-xs text-[var(--color-muted-foreground)]">
              {supportingFactors.map((factor, idx) => (
                <li key={idx} className="flex items-start gap-1.5">
                  <span className="text-emerald-600 dark:text-emerald-400 font-bold shrink-0">✓</span>
                  <span>{factor}</span>
                </li>
              ))}
            </ul>
          </section>
        )}

        {/* Trade-offs Section */}
        {isRecommended && tradeOffs && tradeOffs.length > 0 && (
          <section aria-label="Trade-offs" className="bg-amber-500/10 border border-amber-500/25 rounded-xl p-3 space-y-2">
            <h4 className="text-xs font-bold text-amber-700 dark:text-amber-400 flex items-center gap-1.5 uppercase tracking-wide">
              <AlertTriangle className="w-3.5 h-3.5 text-amber-600 dark:text-amber-400 shrink-0" />
              Trade-offs to Consider:
            </h4>
            <ul className="space-y-1 text-xs text-[var(--color-muted-foreground)]">
              {tradeOffs.map((tradeOff, idx) => (
                <li key={idx} className="flex items-start gap-1.5">
                  <span className="text-amber-600 dark:text-amber-400 shrink-0">•</span>
                  <span>{tradeOff}</span>
                </li>
              ))}
            </ul>
          </section>
        )}

        {/* Objective Evidence Badges */}
        {evidence && evidence.length > 0 && (
          <div className="flex flex-wrap gap-1.5 pt-1">
            {evidence.map((ev, idx) => (
              <span
                key={idx}
                className={`text-[10px] px-2 py-0.5 rounded border flex items-center gap-1 ${
                  ev.positive
                    ? 'text-emerald-700 dark:text-emerald-300 bg-emerald-500/15 border-emerald-500/30'
                    : 'text-[var(--color-muted-foreground)] bg-[var(--color-surface-accent)] border border-[var(--color-border)]'
                }`}
              >
                <Tag className="w-2.5 h-2.5" />
                <span>{ev.description || ev.type.replace(/_/g, ' ')}</span>
              </span>
            ))}
          </div>
        )}
      </div>

      {/* Footer / Factual Price & CTA */}
      <div className="pt-4 mt-4 border-t border-[var(--color-border)] flex items-center justify-between">
        <div>
          <span className="text-[10px] text-[var(--color-muted-foreground)] block uppercase font-medium">Best Market Price</span>
          <span className="text-sm font-bold font-mono text-[var(--color-foreground)]">
            {displayPrice !== undefined ? formatPrice(displayPrice, currency) : 'Check sellers'}
          </span>
        </div>

        <Link
          to={`/product/${product.id}`}
          className="px-3.5 py-1.5 text-xs font-semibold text-[var(--color-foreground)] bg-[var(--color-surface-accent)] border border-[var(--color-border)] rounded-xl hover:bg-[var(--color-surface)] active:scale-[0.98] transition-all"
        >
          View Details
        </Link>
      </div>
    </article>
  );
};

