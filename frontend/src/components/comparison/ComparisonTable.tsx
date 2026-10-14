import React from 'react';
import type { ComparisonResponse } from '../../types';
import { ComparisonRowItem } from './ComparisonRowItem';
import { ProductImage } from '../common/ProductImage';

interface ComparisonTableProps {
  comparison: ComparisonResponse;
}

export const ComparisonTable: React.FC<ComparisonTableProps> = ({ comparison }) => {
  if (!comparison.products || comparison.products.length === 0) {
    return (
      <div className="p-12 text-center bg-[var(--color-surface)] border border-[var(--color-border)] rounded-xl text-[var(--color-muted-foreground)] space-y-2">
        <p className="text-[var(--color-foreground)] font-semibold">No products selected for comparison.</p>
        <p className="text-xs text-[var(--color-muted-foreground)]">Select 2 to 5 products above to view side-by-side comparison.</p>
      </div>
    );
  }

  return (
    <div className="space-y-6">
      {/* Dynamic Comparison Summary Banner */}
      <div className="p-4 rounded-xl bg-[var(--color-surface)] border border-emerald-500/30 flex flex-col sm:flex-row items-start sm:items-center justify-between gap-3 shadow-md">
        <div className="flex items-start gap-3">
          <div className="p-2 rounded-lg bg-emerald-500/15 border border-emerald-500/30 text-emerald-600 dark:text-emerald-400 text-sm">
            ⚡
          </div>
          <div>
            <h4 className="text-xs uppercase font-bold tracking-wider text-emerald-600 dark:text-emerald-400">Comparison Intelligence Summary</h4>
            <p className="text-sm text-[var(--color-foreground)] mt-0.5 leading-relaxed font-medium">
              {comparison.summary}
            </p>
          </div>
        </div>
        <div className="text-xs text-[var(--color-muted-foreground)] font-mono whitespace-nowrap">
          {new Date(comparison.createdAt).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' })}
        </div>
      </div>

      {/* Sticky Matrix Table */}
      <div className="w-full overflow-x-auto border border-[var(--color-border)] rounded-xl bg-[var(--color-surface)] shadow-xl relative">
        <table className="w-full text-left border-collapse min-w-[700px]">
          <thead>
            {/* Sticky Table Header */}
            <tr className="border-b border-[var(--color-border)] bg-[var(--color-surface)]/95 backdrop-blur sticky top-0 z-30 shadow-sm">
              <th className="py-4 px-4 text-xs font-bold text-[var(--color-muted-foreground)] uppercase tracking-wider sticky left-0 bg-[var(--color-surface)]/95 backdrop-blur z-40 border-r border-[var(--color-border)] w-56">
                Features & Metrics
              </th>
              {comparison.products.map((p) => {
                const score = comparison.scores[p.id];
                return (
                  <th key={p.id} className="py-4 px-4 min-w-[220px] align-top border-r border-[var(--color-border)] last:border-r-0">
                    <div className="space-y-3">
                      <div className="h-32 w-full bg-[var(--color-surface-accent)] rounded-xl overflow-hidden flex items-center justify-center p-3 border border-[var(--color-border)] shadow-inner group">
                        <ProductImage
                          src={p.imageUrl}
                          alt={p.name}
                          className="h-full object-contain transition-transform group-hover:scale-105"
                          showFallbackText
                          fallbackText={p.brand}
                        />
                      </div>

                      <div>
                        <span className="text-[10px] uppercase font-semibold text-[var(--color-muted-foreground)] tracking-wider">
                          {p.brand}
                        </span>
                        <h4 className="font-bold text-[var(--color-foreground)] text-sm line-clamp-2 leading-snug" title={p.name}>
                          {p.name}
                        </h4>
                      </div>

                      {score && (
                        <div className="p-2.5 rounded-lg bg-[var(--color-surface-accent)]/60 border border-[var(--color-border)] space-y-2">
                          <div className="flex items-center justify-between">
                            <span className="text-[10px] uppercase font-bold tracking-wider px-2 py-0.5 rounded bg-emerald-500/15 border border-emerald-500/30 text-emerald-700 dark:text-emerald-300">
                              {score.recommendationBadge}
                            </span>
                            <div className="text-right">
                              <span className="text-sm font-bold text-emerald-600 dark:text-emerald-400 font-mono">
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
