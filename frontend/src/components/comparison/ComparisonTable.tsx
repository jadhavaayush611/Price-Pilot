import React from 'react';
import type { ComparisonResponse } from '../../types';
import { ComparisonRowItem } from './ComparisonRowItem';
import { ProductImage } from '../common/ProductImage';
import { ArrowLeftRight, X, Sparkles } from 'lucide-react';

interface ComparisonTableProps {
  comparison: ComparisonResponse;
  onRemoveProduct?: (productId: string) => void;
}

export const ComparisonTable: React.FC<ComparisonTableProps> = ({ comparison, onRemoveProduct }) => {
  if (!comparison.products || comparison.products.length === 0) {
    return (
      <div className="p-12 text-center bg-[var(--color-surface)] border border-[var(--color-border)] rounded-xl text-[var(--color-muted-foreground)] space-y-2">
        <p className="text-[var(--color-foreground)] font-semibold">No products selected for comparison.</p>
        <p className="text-xs text-[var(--color-muted-foreground)]">Select 2 to 5 products above to view side-by-side comparison.</p>
      </div>
    );
  }

  return (
    <div className="space-y-4 sm:space-y-6">
      {/* Dynamic Comparison Summary Banner */}
      <div className="p-4 rounded-xl bg-[var(--color-surface)] border border-emerald-500/30 flex flex-col sm:flex-row items-start sm:items-center justify-between gap-3 shadow-md">
        <div className="flex items-start gap-3">
          <div className="p-2 rounded-lg bg-emerald-500/15 border border-emerald-500/30 text-emerald-600 dark:text-emerald-400 text-sm shrink-0">
            <Sparkles className="h-4 w-4" aria-hidden="true" />
          </div>
          <div>
            <h4 className="text-xs uppercase font-bold tracking-wider text-emerald-600 dark:text-emerald-400">Comparison Intelligence Summary</h4>
            <p className="text-sm text-[var(--color-foreground)] mt-0.5 leading-relaxed font-medium">
              {comparison.summary}
            </p>
          </div>
        </div>
        <div className="text-xs text-[var(--color-muted-foreground)] font-mono whitespace-nowrap self-end sm:self-center">
          {new Date(comparison.createdAt).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' })}
        </div>
      </div>

      {/* Mobile Horizontal Scroll Hint */}
      <div className="sm:hidden flex items-center justify-between px-1 text-[11px] text-[var(--color-muted-foreground)]">
        <span className="flex items-center gap-1.5 font-medium">
          <ArrowLeftRight className="h-3.5 w-3.5 text-emerald-500 shrink-0" aria-hidden="true" />
          <span>Scroll horizontally to compare all {comparison.products.length} products</span>
        </span>
        <span className="text-[10px] font-mono text-[var(--color-muted-foreground)]">
          {comparison.products.length} Products
        </span>
      </div>

      {/* Horizontally Contained Matrix Table */}
      <div
        tabIndex={0}
        role="region"
        aria-label="Product comparison table, scroll horizontally for more products"
        className="w-full overflow-x-auto overscroll-x-contain border border-[var(--color-border)] rounded-xl bg-[var(--color-surface)] shadow-xl relative focus-visible:ring-2 focus-visible:ring-[var(--color-ring)] focus-visible:outline-none"
      >
        <table className="w-full text-left border-collapse min-w-[580px] sm:min-w-[700px]">
          <thead>
            {/* Sticky Table Header */}
            <tr className="border-b border-[var(--color-border)] bg-[var(--color-surface)]/95 backdrop-blur sticky top-0 z-30 shadow-sm">
              <th
                scope="col"
                className="py-3 sm:py-4 px-3 sm:px-4 text-[11px] sm:text-xs font-bold text-[var(--color-muted-foreground)] uppercase tracking-wider sticky left-0 bg-[var(--color-surface)]/95 backdrop-blur-md z-40 border-r border-[var(--color-border)] shadow-[2px_0_5px_-2px_rgba(0,0,0,0.1)] dark:shadow-[2px_0_5px_-2px_rgba(0,0,0,0.3)] w-32 sm:w-48 md:w-56"
              >
                Features &amp; Metrics
              </th>
              {comparison.products.map((p) => {
                const score = comparison.scores[p.id];
                return (
                  <th
                    key={p.id}
                    scope="col"
                    className="py-3 sm:py-4 px-3 sm:px-4 min-w-[180px] sm:min-w-[220px] align-top border-r border-[var(--color-border)] last:border-r-0 relative"
                  >
                    <div className="space-y-3">
                      {/* Product Image & Remove Action */}
                      <div className="relative">
                        <div className="h-28 sm:h-32 w-full bg-[var(--color-surface-accent)] rounded-xl overflow-hidden flex items-center justify-center p-3 border border-[var(--color-border)] shadow-inner group">
                          <ProductImage
                            src={p.imageUrl}
                            alt={p.name}
                            className="h-full object-contain transition-transform group-hover:scale-105"
                            showFallbackText
                            fallbackText={p.brand}
                          />
                        </div>

                        {onRemoveProduct && comparison.products.length > 1 && (
                          <button
                            type="button"
                            onClick={() => onRemoveProduct(p.id)}
                            aria-label={`Remove ${p.name} from comparison`}
                            title={`Remove ${p.name} from comparison`}
                            className="absolute top-2 right-2 p-1 rounded-lg bg-[var(--color-surface)]/80 hover:bg-red-500/15 text-[var(--color-muted-foreground)] hover:text-red-500 border border-[var(--color-border)] transition-colors focus-visible:ring-2 focus-visible:ring-[var(--color-ring)] focus-visible:outline-none cursor-pointer"
                          >
                            <X className="h-3.5 w-3.5" aria-hidden="true" />
                          </button>
                        )}
                      </div>

                      <div>
                        <span className="text-[10px] uppercase font-semibold text-[var(--color-muted-foreground)] tracking-wider">
                          {p.brand}
                        </span>
                        <h4 className="font-bold text-[var(--color-foreground)] text-xs sm:text-sm line-clamp-2 leading-snug" title={p.name}>
                          {p.name}
                        </h4>
                      </div>

                      {score && (
                        <div className="p-2 sm:p-2.5 rounded-lg bg-[var(--color-surface-accent)]/60 border border-[var(--color-border)] space-y-1.5 sm:space-y-2">
                          <div className="flex items-center justify-between">
                            <span className="text-[9px] sm:text-[10px] uppercase font-bold tracking-wider px-1.5 sm:px-2 py-0.5 rounded bg-emerald-500/15 border border-emerald-500/30 text-emerald-700 dark:text-emerald-300">
                              {score.recommendationBadge}
                            </span>
                            <div className="text-right">
                              <span className="text-xs sm:text-sm font-bold text-emerald-600 dark:text-emerald-400 font-mono">
                                {score.overallScore.toFixed(1)}
                              </span>
                              <span className="text-[10px] text-[var(--color-muted-foreground)]">/100</span>
                            </div>
                          </div>

                          {/* Factor Progress Bar */}
                          <div className="w-full bg-[var(--color-surface)] border border-[var(--color-border)] rounded-full h-1.5 overflow-hidden">
                            <div
                              className="bg-emerald-500 dark:bg-emerald-400 h-full rounded-full transition-all duration-500"
                              style={{ width: `${Math.min(100, score.overallScore)}%` }}
                            />
                          </div>
                        </div>
                      )}
                    </div>
                  </th>
                );
              })}
            </tr>
          </thead>
          <tbody>
            {comparison.rows.map((row, idx) => (
              <ComparisonRowItem key={idx} row={row} products={comparison.products} />
            ))}
          </tbody>
        </table>
      </div>
    </div>
  );
};
