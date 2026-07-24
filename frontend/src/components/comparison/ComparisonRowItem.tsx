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
      return <span className="text-zinc-300">{val}</span>;
    }

    switch (row.rowType) {
      case 'LOWEST_PRICE':
        return (
          <div className="inline-flex items-center gap-1 font-semibold text-emerald-400 bg-emerald-950/60 border border-emerald-800/60 px-2.5 py-1 rounded-lg text-xs font-mono shadow-sm shadow-emerald-950/30">
            <span className="h-1.5 w-1.5 rounded-full bg-emerald-400 animate-pulse" />
            <span>{val}</span>
            <span className="ml-1 text-[9px] uppercase font-mono tracking-wider text-emerald-300 bg-emerald-900/80 px-1 rounded">Best Price</span>
          </div>
        );
      case 'HIGHEST_DISCOUNT':
        return (
          <div className="inline-flex items-center gap-1 font-semibold text-teal-300 bg-teal-950/60 border border-teal-800/60 px-2.5 py-1 rounded-lg text-xs font-mono shadow-sm shadow-teal-950/30">
            <span>{val}</span>
            <span className="ml-1 text-[9px] uppercase font-mono tracking-wider text-teal-300 bg-teal-900/80 px-1 rounded">Top Discount</span>
          </div>
        );
      case 'HIGHEST_RATING':
        return (
          <div className="inline-flex items-center gap-1 font-semibold text-amber-300 bg-amber-950/60 border border-amber-800/60 px-2.5 py-1 rounded-lg text-xs shadow-sm shadow-amber-950/30">
            <span>★ {val}</span>
            <span className="ml-1 text-[9px] uppercase font-mono tracking-wider text-amber-300 bg-amber-900/80 px-1 rounded">Top Rated</span>
          </div>
        );
      case 'AVAILABILITY':
        return (
          <div className="inline-flex items-center gap-1 font-semibold text-blue-300 bg-blue-950/60 border border-blue-800/60 px-2.5 py-1 rounded-lg text-xs shadow-sm shadow-blue-950/30">
            <span>{val}</span>
          </div>
        );
      default:
        return (
          <span className="font-semibold text-emerald-400 bg-emerald-950/40 border border-emerald-800/40 px-2.5 py-1 rounded-lg text-xs">
            {val}
          </span>
        );
    }
  };

  return (
    <tr className={`border-b border-zinc-900/60 ${row.isHighlight ? 'bg-zinc-900/40' : 'hover:bg-zinc-900/20'} transition-colors`}>
      <td className="py-3.5 px-4 text-xs font-semibold text-zinc-400 uppercase tracking-wider bg-zinc-950/90 sticky left-0 z-10 border-r border-zinc-900 shadow-lg">
        <div className="flex items-center gap-2">
          <span>{row.featureName}</span>
          {row.isHighlight && (
            <span className="h-1.5 w-1.5 rounded-full bg-emerald-400 animate-pulse" title="Highlighted comparison metric" />
          )}
        </div>
      </td>
      {products.map((p) => {
        const val = row.valuesByProductId[p.id] || 'N/A';
        return (
          <td key={p.id} className="py-3.5 px-4 text-sm min-w-[200px]">
            {getHighlightBadge(p.id, val)}
          </td>
        );
      })}
    </tr>
  );
};
