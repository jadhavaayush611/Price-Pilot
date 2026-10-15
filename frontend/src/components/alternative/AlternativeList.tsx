import React from 'react';
import type { AlternativeResponse, AlternativeType } from '../../types';
import { AlternativeCard, formatAlternativeType } from './AlternativeCard';
import { AlternativeSkeleton } from './AlternativeSkeleton';
import { Sparkles, UserCheck, Layers, AlertCircle } from 'lucide-react';
import { formatPrice, getSavedCurrency, getDisplayPrice } from '../../currency';
import { ProductImage } from '../common/ProductImage';

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
          <span className="text-xs font-semibold text-[var(--color-muted-foreground)] shrink-0 mr-1">Strategy:</span>
          {ALTERNATIVE_TYPES.map(({ type, label }) => {
            const isActive = selectedType === type;
            return (
              <button
                key={type}
                type="button"
                onClick={() => onSelectType(type)}
                className={`px-3 py-1.5 rounded-xl text-xs font-semibold transition-all cursor-pointer shrink-0 ${
                  isActive
                    ? 'bg-[var(--color-primary)] text-[var(--color-primary-foreground)] shadow-sm font-bold'
                    : 'bg-[var(--color-surface)] text-[var(--color-muted-foreground)] hover:text-[var(--color-foreground)] border border-[var(--color-border)] hover:bg-[var(--color-surface-accent)]'
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
        <div className="bg-[var(--color-surface)] border border-[var(--color-border)] p-4 rounded-2xl flex items-center justify-between gap-4 flex-wrap shadow-sm">
          <div className="flex items-center gap-3">
            <div className="w-12 h-12 rounded-xl bg-[var(--color-surface-accent)] border border-[var(--color-border)] p-1 flex items-center justify-center shrink-0 overflow-hidden">
              <ProductImage
                src={sourceContext.imageUrl}
                alt={sourceContext.name}
                className="h-full w-full object-contain"
                showFallbackText
                fallbackText={sourceContext.brand}
              />
            </div>
            <div>
              <span className="text-[10px] uppercase tracking-wider text-[var(--color-muted-foreground)] font-bold block">
                Finding alternatives for
              </span>
              <h3 className="text-sm font-bold text-[var(--color-foreground)] line-clamp-1">{sourceContext.name}</h3>
              <span className="text-xs text-[var(--color-muted-foreground)]">
                {sourceContext.brand} {sourceContext.category ? `· ${sourceContext.category}` : ''}
              </span>
            </div>
          </div>

          {sourceContext.currentBestPrice !== undefined && (
            <div className="text-right">
              <span className="text-[10px] text-[var(--color-muted-foreground)] uppercase font-medium block">Current Best Price</span>
              <span className="text-sm font-bold font-mono text-[var(--color-foreground)]">
                {formatPrice(getDisplayPrice(sourceContext.currentBestPrice, currency), currency)}
              </span>
            </div>
          )}
        </div>
      )}

      {/* Pipeline Explanation / Applied Notes Banner */}
      {response && response.appliedNotes && response.appliedNotes.length > 0 && (
        <div className="bg-[var(--color-surface-accent)] border border-[var(--color-border)] p-4 rounded-2xl flex items-start gap-3 shadow-sm">
          {isPersonalized ? (
            <UserCheck className="w-5 h-5 text-indigo-600 dark:text-indigo-400 mt-0.5 shrink-0" />
          ) : (
            <Sparkles className="w-5 h-5 text-emerald-600 dark:text-emerald-400 mt-0.5 shrink-0" />
          )}
          <div className="space-y-1 text-xs flex-1">
            <span className="font-semibold text-[var(--color-foreground)]">
              {isPersonalized
                ? 'Personalized Alternative Intelligence'
                : `Discovery Insights (${formatAlternativeType(response.alternativeType)})`}
            </span>
            <ul className="space-y-0.5 text-[var(--color-muted-foreground)]">
              {response.appliedNotes.map((note, idx) => (
                <li key={idx} className="flex items-start gap-1.5">
                  <span className="text-emerald-600 dark:text-emerald-400">•</span>
                  <span>{note}</span>
                </li>
              ))}
            </ul>
          </div>
        </div>
      )}

      {/* Empty State */}
      {alternatives.length === 0 ? (
        <div className="p-12 text-center bg-[var(--color-surface)] border border-[var(--color-border)] rounded-2xl space-y-2">
          <Layers className="w-8 h-8 text-[var(--color-muted-foreground)] mx-auto" />
          <h4 className="text-sm font-semibold text-[var(--color-foreground)]">No alternatives found</h4>
          <p className="text-xs text-[var(--color-muted-foreground)] max-w-sm mx-auto">
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
