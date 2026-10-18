import React from 'react';
import type { ComparisonRow, ProductWithPrices } from '../../types';

interface ComparisonRowItemProps {
  row: ComparisonRow;
  products: ProductWithPrices[];
}

export const ComparisonRowItem: React.FC<ComparisonRowItemProps> = ({ row, products }) => {
  const getHighlightBadge = (productId: string, val: string) => {
    const isHighlighted = row.highlightedProductIds?.includes(productId) || (row.isHighlight && row.highlightedProductIds?.length === 0);

    if (!isHighlighted) {
      return <span className="text-[var(--color-foreground)]">{val}</span>;
    }

    switch (row.rowType) {
      case 'LOWEST_PRICE':
        return (
          <div className="inline-flex items-center flex-wrap gap-1 font-semibold text-emerald-700 dark:text-emerald-400 bg-emerald-500/15 border border-emerald-500/30 px-2 sm:px-2.5 py-1 rounded-lg text-xs font-mono shadow-sm">
            <span className="h-1.5 w-1.5 rounded-full bg-emerald-500 animate-pulse shrink-0" />
            <span>{val}</span>
            <span className="text-[9px] uppercase font-mono tracking-wider text-emerald-800 dark:text-emerald-300 bg-emerald-500/20 px-1 rounded">Best Price</span>
          </div>
        );
      case 'HIGHEST_DISCOUNT':
        return (
          <div className="inline-flex items-center flex-wrap gap-1 font-semibold text-teal-700 dark:text-teal-300 bg-teal-500/15 border border-teal-500/30 px-2 sm:px-2.5 py-1 rounded-lg text-xs font-mono shadow-sm">
            <span>{val}</span>
            <span className="text-[9px] uppercase font-mono tracking-wider text-teal-800 dark:text-teal-300 bg-teal-500/20 px-1 rounded">Top Discount</span>
          </div>
        );
      case 'HIGHEST_RATING':
        return (
          <div className="inline-flex items-center flex-wrap gap-1 font-semibold text-amber-800 dark:text-amber-300 bg-amber-500/15 border border-amber-500/30 px-2 sm:px-2.5 py-1 rounded-lg text-xs shadow-sm">
            <span>★ {val}</span>
            <span className="text-[9px] uppercase font-mono tracking-wider text-amber-900 dark:text-amber-300 bg-amber-500/20 px-1 rounded">Top Rated</span>
          </div>
        );
      case 'AVAILABILITY':
        return (
          <div className="inline-flex items-center gap-1 font-semibold text-blue-700 dark:text-blue-300 bg-blue-500/15 border border-blue-500/30 px-2 sm:px-2.5 py-1 rounded-lg text-xs shadow-sm">
            <span>{val}</span>
          </div>
        );
      default:
        return (
          <span className="font-semibold text-emerald-700 dark:text-emerald-400 bg-emerald-500/15 border border-emerald-500/30 px-2 sm:px-2.5 py-1 rounded-lg text-xs">
            {val}
          </span>
        );
    }
  };

  return (
    <tr className={`border-b border-[var(--color-border)] ${row.isHighlight ? 'bg-[var(--color-surface-accent)]/40' : 'hover:bg-[var(--color-surface-accent)]/20'} transition-colors`}>
      <th
        scope="row"
        className="py-3 sm:py-3.5 px-3 sm:px-4 text-[11px] sm:text-xs font-semibold text-[var(--color-muted-foreground)] uppercase tracking-wider bg-[var(--color-surface)]/95 backdrop-blur-md sticky left-0 z-10 border-r border-[var(--color-border)] shadow-[2px_0_5px_-2px_rgba(0,0,0,0.1)] dark:shadow-[2px_0_5px_-2px_rgba(0,0,0,0.3)] w-32 sm:w-48 md:w-56 text-left"
      >
        <div className="flex items-center gap-1.5 sm:gap-2">
          <span className="line-clamp-2">{row.featureName}</span>
          {row.isHighlight && (
            <span className="h-1.5 w-1.5 rounded-full bg-emerald-500 animate-pulse shrink-0" title="Highlighted comparison metric" />
          )}
        </div>
      </th>
      {products.map((p) => {
        const val = row.valuesByProductId[p.id] || 'N/A';
        return (
          <td
            key={p.id}
            className="py-3 sm:py-3.5 px-3 sm:px-4 text-xs sm:text-sm min-w-[180px] sm:min-w-[200px] border-r border-[var(--color-border)] last:border-r-0"
          >
            {getHighlightBadge(p.id, val)}
          </td>
        );
      })}
    </tr>
  );
};
