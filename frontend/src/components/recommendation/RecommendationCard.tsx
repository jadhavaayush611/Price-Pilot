import React from 'react';
import { Link } from 'react-router-dom';
import type { ProductWithPrices, ProductScore } from '../../types';

interface RecommendationCardProps {
  product: ProductWithPrices;
  score?: ProductScore;
}

export const RecommendationCard: React.FC<RecommendationCardProps> = ({ product, score }) => {
  return (
    <div className="group bg-zinc-950 border border-zinc-900 hover:border-zinc-700 rounded-xl p-5 transition-all duration-300 flex flex-col justify-between hover:shadow-xl hover:shadow-emerald-950/20">
      <div className="space-y-4">
        {/* Header & Badges */}
        <div className="flex items-center justify-between">
          <span className="text-[10px] font-bold uppercase tracking-wider text-zinc-400 bg-zinc-900 border border-zinc-800 px-2 py-0.5 rounded">
            {product.brand}
          </span>
          {score && (
            <span className="text-[10px] font-bold uppercase tracking-wider text-emerald-400 bg-emerald-950/50 border border-emerald-800/40 px-2 py-0.5 rounded flex items-center gap-1">
              <span>{score.recommendationBadge}</span>
              <span className="font-mono text-xs">({score.overallScore.toFixed(0)}%)</span>
            </span>
          )}
        </div>

        {/* Product Image */}
        <div className="h-44 w-full bg-zinc-900/60 rounded-lg p-3 flex items-center justify-center group-hover:scale-[1.02] transition-transform">
          <img src={product.imageUrl} alt={product.name} className="h-full object-contain" />
        </div>

        {/* Info */}
        <div className="space-y-1">
          <h4 className="text-sm font-semibold text-zinc-100 line-clamp-2 group-hover:text-white transition-colors">
            {product.name}
          </h4>
          <p className="text-xs text-zinc-500 line-clamp-2">{product.description}</p>
        </div>
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
    </div>
  );
};
