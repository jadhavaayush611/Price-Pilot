import React from 'react';
import type { RecommendationResponse } from '../../types';
import { RecommendationCard } from './RecommendationCard';
import { RecommendationSkeleton } from './RecommendationSkeleton';
import { Sparkles, ShieldCheck, UserCheck } from 'lucide-react';

export interface RecommendationListProps {
  recommendations: RecommendationResponse | null;
  loading?: boolean;
  error?: string | null;
  isPersonalized?: boolean;
  onRetry?: () => void;
}

export const RecommendationList: React.FC<RecommendationListProps> = ({
  recommendations,
  loading,
  error,
  isPersonalized = false,
  onRetry,
}) => {
  if (loading) {
    return <RecommendationSkeleton />;
  }

  if (error) {
    return (
      <div role="alert" className="p-8 text-center bg-red-950/20 border border-red-900/50 rounded-2xl text-red-400 text-sm space-y-3">
        <p>{error}</p>
        {onRetry && (
          <button
            type="button"
            onClick={onRetry}
            className="px-4 py-1.5 text-xs font-semibold text-white bg-red-900/40 hover:bg-red-800/60 border border-red-700/50 rounded-lg transition-colors cursor-pointer"
          >
            Retry
          </button>
        )}
      </div>
    );
  }

  if (!recommendations || !recommendations.recommendedProducts || recommendations.recommendedProducts.length === 0) {
    return (
      <div className="p-8 text-center bg-[var(--color-surface)] border border-[var(--color-border)] rounded-2xl text-[var(--color-muted-foreground)] text-sm">
        {isPersonalized
          ? 'No personalized product recommendations found matching your criteria.'
          : 'No intelligence recommendations available for this product.'}
      </div>
    );
  }

  const scoresMap = (recommendations.scores || []).reduce<Record<string, (typeof recommendations.scores)[0]>>((acc, s) => {
    acc[s.productId] = s;
    return acc;
  }, {});

  const recommendedId = recommendations.recommendedProduct?.id || recommendations.recommendedProducts[0]?.id;

  // Group personalization evidence by productId if specific product IDs are present
  const personalizationEvidenceByProduct: Record<string, NonNullable<typeof recommendations.personalizationEvidence>> = {};
  for (const ev of recommendations.personalizationEvidence || []) {
    if (ev.productId) {
      const list = personalizationEvidenceByProduct[ev.productId] || [];
      list.push(ev);
      personalizationEvidenceByProduct[ev.productId] = list;
    }
  }

  return (
    <section aria-label={isPersonalized ? "Personalized Product Recommendations" : "Shopping Intelligence Recommendations"} className="space-y-6">
      {/* Pipeline explanation banner */}
      {recommendations.explanation && (
        <div className="bg-[var(--color-surface)] border border-[var(--color-border)] p-4 rounded-2xl flex items-start gap-3 shadow-sm">
          {isPersonalized ? (
            <UserCheck className="w-5 h-5 text-indigo-600 dark:text-indigo-400 mt-0.5 shrink-0" />
          ) : (
            <Sparkles className="w-5 h-5 text-emerald-600 dark:text-emerald-400 mt-0.5 shrink-0" />
          )}
          <div className="space-y-1 text-xs flex-1">
            <div className="flex items-center justify-between gap-2 flex-wrap">
              <span className="font-semibold text-[var(--color-foreground)]">
                {isPersonalized ? 'Personalized Intelligence Insights' : `Explainable AI Insights (${recommendations.recommendationType || 'BEST OVERALL'})`}
              </span>
              {recommendations.confidence !== undefined && (
                <span className="text-[11px] text-[var(--color-muted-foreground)] flex items-center gap-1 font-mono">
                  <ShieldCheck className="w-3.5 h-3.5 text-indigo-600 dark:text-indigo-400" />
                  Confidence: {Math.round(recommendations.confidence * 100)}%
                </span>
              )}
            </div>
            <p className="text-[var(--color-muted-foreground)] leading-relaxed">{recommendations.explanation}</p>
          </div>
        </div>
      )}

      {/* Grid */}
      <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-6" role="list">
        {(recommendations.recommendedProducts || []).map((p, index) => {
          const isTopPick = p.id === recommendedId || index === 0;
          const productPersonalizationEvidence = personalizationEvidenceByProduct[p.id] || (isTopPick ? recommendations.personalizationEvidence : undefined);

          return (
            <div key={p.id} role="listitem">
              <RecommendationCard
                product={p}
                score={scoresMap[p.id]}
                isRecommended={isTopPick}
                recommendationType={recommendations.recommendationType}
                confidence={isTopPick ? recommendations.confidence : undefined}
                explanation={isTopPick ? recommendations.explanation : undefined}
                supportingFactors={isTopPick ? recommendations.supportingFactors : undefined}
                tradeOffs={isTopPick ? recommendations.tradeOffs : undefined}
                evidence={isTopPick ? recommendations.evidence : undefined}
                personalizationEvidence={productPersonalizationEvidence}
                isPersonalized={isPersonalized}
              />
            </div>
          );
        })}
      </div>
    </section>
  );
};

