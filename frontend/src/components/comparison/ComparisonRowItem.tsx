import React from 'react';
import type { ComparisonRow, ProductWithPrices } from '../../types';

interface ComparisonRowItemProps {
  row: ComparisonRow;
  products: ProductWithPrices[];
}

export const ComparisonRowItem: React.FC<ComparisonRowItemProps> = ({ row, products }) => {
  return (
    <tr className={`border-b border-zinc-900/60 ${row.isHighlight ? 'bg-zinc-900/40 font-medium' : 'hover:bg-zinc-900/20'} transition-colors`}>
      <td className="py-3 px-4 text-xs font-semibold text-zinc-400 uppercase tracking-wider bg-zinc-950/80 sticky left-0 z-10 border-r border-zinc-900">
        <div className="flex items-center gap-1.5">
          <span>{row.featureName}</span>
          {row.isHighlight && (
            <span className="h-1.5 w-1.5 rounded-full bg-emerald-400 animate-pulse" title="Key Metric" />
          )}
        </div>
      </td>
      {products.map((p) => {
        const val = row.valuesByProductId[p.id] || 'N/A';
        return (
          <td key={p.id} className="py-3 px-4 text-sm text-zinc-200 min-w-[180px]">
            {row.isHighlight ? (
              <span className="font-semibold text-emerald-400 bg-emerald-950/40 border border-emerald-800/40 px-2 py-0.5 rounded">
                {val}
              </span>
            ) : (
              <span>{val}</span>
            )}
          </td>
        );
      })}
    </tr>
  );
};
