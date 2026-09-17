import React from 'react';
import type { AlternativeResponse, AlternativeType } from '../../types';
import { AlternativeCard, formatAlternativeType } from './AlternativeCard';
import { AlternativeSkeleton } from './AlternativeSkeleton';
import { Sparkles, UserCheck, Layers, AlertCircle } from 'lucide-react';
import { formatPrice, getSavedCurrency, getDisplayPrice } from '../../currency';

export interface AlternativeListProps {
  response: AlternativeResponse | null;
  loading?: boolean;
  error?: string | null;
  isPersonalized?: boolean;
  onRetry?: () => void;
  selectedType?: AlternativeType;
  onSelectType?: (type: AlternativeType) => void;
  sourceProductId?: string;
}

const ALTERNATIVE_TYPES: { type: AlternativeType; label: string }[] = [
  { type: 'SIMILAR', label: 'Similar' },
  { type: 'CHEAPER', label: 'Cheaper' },
  { type: 'BETTER_VALUE', label: 'Better Value' },
  { type: 'PERFORMANCE_UPGRADE', label: 'Upgrades' },
  { type: 'PREMIUM', label: 'Premium' },
  { type: 'BUDGET_FALLBACK', label: 'Budget' },
];

export const AlternativeList: React.FC<AlternativeListProps> = ({
  response,
  loading,
  error,
  isPersonalized = false,
  onRetry,
  selectedType,
  onSelectType,
  sourceProductId,
}) => {
  const currency = getSavedCurrency();

  if (loading) {
    return <AlternativeSkeleton />;
  }

  if (error) {
    return (
      <div role="alert" className="p-8 text-center bg-red-950/20 border border-red-900/50 rounded-2xl text-red-400 text-sm space-y-3">
        <div className="flex justify-center">
          <AlertCircle className="w-6 h-6 text-rose-400" />
        </div>
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

  const alternatives = response?.content || [];
  const sourceContext = response?.sourceProductContext;

  return (
    <section aria-label="Alternative Products Finder" className="space-y-6">
      {/* Strategy / Type Filter Tabs */}
      {onSelectType && (
        <div className="flex items-center gap-2 overflow-x-auto pb-2 scrollbar-none">
          <span className="text-xs font-semibold text-zinc-500 shrink-0 mr-1">Strategy:</span>
          {ALTERNATIVE_TYPES.map(({ type, label }) => {
            const isActive = selectedType === type;
            return (
              <button
                key={type}
                type="button"
                onClick={() => onSelectType(type)}
                className={`px-3 py-1.5 rounded-xl text-xs font-semibold transition-all cursor-pointer shrink-0 ${
                  isActive
                    ? 'bg-zinc-100 text-black shadow-sm font-bold'
                    : 'bg-zinc-900/80 text-zinc-400 hover:text-zinc-200 border border-zinc-800 hover:border-zinc-700'
                }`}
              >
                {label}
              </button>
            );
          })}
        </div>
      )}

      {/* Source Product Context Banner */}
      {sourceContext && (
        <div className="bg-zinc-950/80 border border-zinc-900 p-4 rounded-2xl flex items-center justify-between gap-4 flex-wrap">
          <div className="flex items-center gap-3">
            {sourceContext.imageUrl && (
              <img
                src={sourceContext.imageUrl}
                alt={sourceContext.name}
                className="w-12 h-12 rounded-xl object-contain bg-zinc-900 border border-zinc-800 p-1"
              />
            )}
            <div>
              <span className="text-[10px] uppercase tracking-wider text-zinc-500 font-bold block">
                Finding alternatives for
              </span>
              <h3 className="text-sm font-bold text-white line-clamp-1">{sourceContext.name}</h3>
              <span className="text-xs text-zinc-400">
                {sourceContext.brand} {sourceContext.category ? `· ${sourceContext.category}` : ''}
              </span>
            </div>
          </div>

          {sourceContext.currentBestPrice !== undefined && (
            <div className="text-right">
              <span className="text-[10px] text-zinc-500 uppercase font-medium block">Current Best Price</span>
              <span className="text-sm font-bold font-mono text-zinc-200">
                {formatPrice(getDisplayPrice(sourceContext.currentBestPrice, currency), currency)}
              </span>
            </div>
          )}
        </div>
      )}

      {/* Pipeline Explanation / Applied Notes Banner */}
      {response && response.appliedNotes && response.appliedNotes.length > 0 && (
        <div className="bg-gradient-to-r from-zinc-950 via-zinc-900 to-zinc-950 border border-zinc-800 p-4 rounded-2xl flex items-start gap-3 shadow-md">
          {isPersonalized ? (
            <UserCheck className="w-5 h-5 text-indigo-400 mt-0.5 shrink-0" />
          ) : (
            <Sparkles className="w-5 h-5 text-emerald-400 mt-0.5 shrink-0" />
          )}
          <div className="space-y-1 text-xs flex-1">
            <span className="font-semibold text-zinc-200">
              {isPersonalized
                ? 'Personalized Alternative Intelligence'
                : `Discovery Insights (${formatAlternativeType(response.alternativeType)})`}
            </span>
            <ul className="space-y-0.5 text-zinc-300">
              {response.appliedNotes.map((note, idx) => (
                <li key={idx} className="flex items-start gap-1.5">
                  <span className="text-emerald-400">•</span>
                  <span>{note}</span>
                </li>
              ))}
            </ul>
          </div>
        </div>
      )}

      {/* Empty State */}
      {alternatives.length === 0 ? (
        <div className="p-12 text-center bg-zinc-955 border border-zinc-900 rounded-2xl space-y-2">
          <Layers className="w-8 h-8 text-zinc-600 mx-auto" />
          <h4 className="text-sm font-semibold text-zinc-300">No alternatives found</h4>
          <p className="text-xs text-zinc-500 max-w-sm mx-auto">
            {isPersonalized
              ? 'No personalized alternatives found matching your preferences for this category.'
              : 'No qualifying product alternatives found for this selection.'}
          </p>
        </div>
      ) : (
        /* Strict Backend-Ordered Grid (No client-side sorting) */
        <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-6" role="list">
          {alternatives.map((alt) => (
            <div key={alt.id} role="listitem">
              <AlternativeCard
                alternative={alt}
                sourceProductId={sourceProductId || sourceContext?.id}
                sourceProductName={sourceContext?.name}
                sourceProductPrice={sourceContext?.currentBestPrice}
                isPersonalized={isPersonalized}
              />
            </div>
          ))}
        </div>
      )}
    </section>
  );
};
