import React from 'react';
import type { RecommendationResponse } from '../../types';
import { RecommendationCard } from './RecommendationCard';
import { RecommendationSkeleton } from './RecommendationSkeleton';

interface RecommendationListProps {
  recommendations: RecommendationResponse | null;
  loading?: boolean;
}

export const RecommendationList: React.FC<RecommendationListProps> = ({ recommendations, loading }) => {
  if (loading) {
    return <RecommendationSkeleton />;
  }

  if (!recommendations || !recommendations.recommendedProducts || recommendations.recommendedProducts.length === 0) {
    return (
      <div className="p-8 text-center bg-zinc-950 border border-zinc-900 rounded-xl text-zinc-500">
        No intelligence recommendations available for this product.
      </div>
    );
  }

  const scoresMap = (recommendations.scores || []).reduce<Record<string, (typeof recommendations.scores)[0]>>((acc, s) => {
    acc[s.productId] = s;
    return acc;
  }, {});

  return (
    <div className="space-y-6">
      {/* Pipeline explanation banner */}
      {recommendations.explanation && (
        <div className="bg-gradient-to-r from-zinc-950 via-zinc-900 to-zinc-950 border border-zinc-800 p-4 rounded-xl flex items-start gap-3">
          <div className="h-2 w-2 rounded-full bg-emerald-400 mt-1.5 animate-pulse shrink-0" />
          <div className="space-y-0.5 text-xs">
            <span className="font-semibold text-zinc-300">Engine v2 Insights ({recommendations.strategyUsed})</span>
            <p className="text-zinc-400">{recommendations.explanation}</p>
          </div>
        </div>
      )}

      {/* Grid */}
      <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-6">
        {recommendations.recommendedProducts.map((p) => (
          <RecommendationCard key={p.id} product={p} score={scoresMap[p.id]} />
        ))}
      </div>
    </div>
  );
};
