import React from 'react';
import { Link } from 'react-router-dom';
import type { ProductWithPrices, ProductScore, EvidenceItem } from '../../types';
import { Award, CheckCircle2, AlertTriangle, ShieldCheck, Tag } from 'lucide-react';

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
}) => {
  const badgeText = isRecommended
    ? (recommendationType || 'BEST OVERALL').toUpperCase()
    : score?.recommendationBadge || 'ALTERNATIVE';

  const scoreValue = score?.overallScore ? Math.round(score.overallScore) : 85;
  const confidencePercent = confidence !== undefined ? Math.round(confidence * 100) : null;

  return (
    <article
      aria-label={`Recommendation for ${product.name}`}
      className={`group bg-zinc-950 border rounded-xl p-5 transition-all duration-300 flex flex-col justify-between hover:shadow-xl ${
        isRecommended
          ? 'border-emerald-600/60 shadow-lg shadow-emerald-950/30 ring-1 ring-emerald-500/20'
          : 'border-zinc-900 hover:border-zinc-700'
      }`}
    >
      <div className="space-y-4">
        {/* Top Header & Badges */}
        <div className="flex items-center justify-between gap-2 flex-wrap">
          <div className="flex items-center gap-2">
            <span className="text-[10px] font-bold uppercase tracking-wider text-zinc-400 bg-zinc-900 border border-zinc-800 px-2 py-0.5 rounded">
              {product.brand}
            </span>
            {isRecommended && (
              <span className="text-[10px] font-bold uppercase tracking-wider text-emerald-300 bg-emerald-950 border border-emerald-700/50 px-2.5 py-0.5 rounded-full flex items-center gap-1 shadow-sm">
                <Award className="w-3 h-3 text-emerald-400" />
                <span>{badgeText}</span>
              </span>
            )}
          </div>

          <div className="flex items-center gap-2 font-mono text-xs">
            <span className="text-zinc-200 font-semibold bg-zinc-900/80 border border-zinc-800 px-2 py-0.5 rounded">
              Score: <strong className="text-emerald-400">{scoreValue}</strong>/100
            </span>
            {confidencePercent !== null && (
              <span
                title="Confidence calculated from score separation, data completeness, and evidence volume"
                className="text-zinc-400 bg-zinc-900/60 border border-zinc-800 px-1.5 py-0.5 rounded flex items-center gap-1"
              >
                <ShieldCheck className="w-3 h-3 text-indigo-400" />
                <span>{confidencePercent}% Conf.</span>
              </span>
            )}
          </div>
        </div>

        {/* Product Image */}
        <div className="h-44 w-full bg-zinc-900/40 rounded-lg p-3 flex items-center justify-center group-hover:scale-[1.02] transition-transform">
          <img src={product.imageUrl} alt={product.name} className="h-full object-contain" />
        </div>

        {/* Info */}
        <div className="space-y-1">
          <h3 className="text-sm font-semibold text-zinc-100 line-clamp-2 group-hover:text-white transition-colors">
            {product.name}
          </h3>
          <p className="text-xs text-zinc-400 line-clamp-2">{product.description}</p>
        </div>

        {/* AI Explanation Callout */}
        {isRecommended && explanation && (
          <p className="text-xs text-zinc-300 italic bg-zinc-900/40 p-2.5 rounded-lg border border-zinc-800/60 leading-relaxed">
            "{explanation}"
          </p>
        )}

        {/* Supporting Factors ("Why this product?") */}
        {isRecommended && supportingFactors && supportingFactors.length > 0 && (
          <section aria-label="Why this product" className="bg-zinc-900/50 border border-zinc-800/80 rounded-lg p-3 space-y-2">
            <h4 className="text-xs font-bold text-emerald-400 flex items-center gap-1.5 uppercase tracking-wide">
              <CheckCircle2 className="w-3.5 h-3.5 text-emerald-400 shrink-0" />
              Why this product?
            </h4>
            <ul className="space-y-1 text-xs text-zinc-300">
              {supportingFactors.map((factor, idx) => (
                <li key={idx} className="flex items-start gap-1.5">
                  <span className="text-emerald-400 font-bold shrink-0">✓</span>
                  <span>{factor}</span>
                </li>
              ))}
            </ul>
          </section>
        )}

        {/* Trade-offs Section */}
        {isRecommended && tradeOffs && tradeOffs.length > 0 && (
          <section aria-label="Trade-offs" className="bg-amber-950/20 border border-amber-900/40 rounded-lg p-3 space-y-2">
            <h4 className="text-xs font-bold text-amber-400 flex items-center gap-1.5 uppercase tracking-wide">
              <AlertTriangle className="w-3.5 h-3.5 text-amber-400 shrink-0" />
              Trade-off:
            </h4>
            <ul className="space-y-1 text-xs text-zinc-300">
              {tradeOffs.map((tradeOff, idx) => (
                <li key={idx} className="flex items-start gap-1.5">
                  <span className="text-amber-400 shrink-0">•</span>
                  <span>{tradeOff}</span>
                </li>
              ))}
            </ul>
          </section>
        )}

        {/* Evidence Badges */}
        {evidence && evidence.length > 0 && (
          <div className="flex flex-wrap gap-1.5 pt-1">
            {evidence.map((ev, idx) => (
              <span
                key={idx}
                className={`text-[10px] px-2 py-0.5 rounded border flex items-center gap-1 ${
                  ev.positive
                    ? 'text-emerald-300 bg-emerald-950/30 border-emerald-800/30'
                    : 'text-zinc-400 bg-zinc-900/50 border-zinc-800'
                }`}
              >
                <Tag className="w-2.5 h-2.5" />
                <span>{ev.type.replace(/_/g, ' ')}</span>
              </span>
            ))}
          </div>
        )}
      </div>

      {/* Footer / CTA */}
      <div className="pt-4 mt-4 border-t border-zinc-900 flex items-center justify-between">
        <div>
          <span className="text-[10px] text-zinc-500 block">Best Current Offer</span>
          <span className="text-sm font-bold font-mono text-zinc-100">
            {product.lowestPrice ? `$${product.lowestPrice}` : 'Check sellers'}
          </span>
        </div>

        <Link
          to={`/product/${product.id}`}
          className="px-3 py-1.5 text-xs font-semibold text-zinc-100 bg-zinc-900 border border-zinc-800 rounded-lg hover:border-zinc-600 hover:bg-zinc-800 transition-colors"
        >
          View Details
        </Link>
      </div>
    </article>
  );
};
