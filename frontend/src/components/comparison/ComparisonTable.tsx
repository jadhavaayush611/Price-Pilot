import React from 'react';
import type { ComparisonResponse } from '../../types';
import { ComparisonRowItem } from './ComparisonRowItem';

interface ComparisonTableProps {
  comparison: ComparisonResponse;
}

export const ComparisonTable: React.FC<ComparisonTableProps> = ({ comparison }) => {
  if (!comparison.products || comparison.products.length === 0) {
    return (
      <div className="p-8 text-center bg-zinc-950 border border-zinc-900 rounded-xl text-zinc-500">
        No products selected for matrix comparison.
      </div>
    );
  }

  return (
    <div className="w-full overflow-x-auto border border-zinc-900 rounded-xl bg-zinc-950 shadow-2xl">
      <table className="w-full text-left border-collapse">
        <thead>
          <tr className="border-b border-zinc-900 bg-zinc-900/60">
            <th className="py-4 px-4 text-xs font-bold text-zinc-400 uppercase tracking-wider sticky left-0 bg-zinc-900 z-20 border-r border-zinc-800">
              Features & Specifications
            </th>
            {comparison.products.map((p) => {
              const score = comparison.scores[p.id];
              return (
                <th key={p.id} className="py-4 px-4 min-w-[200px] align-top">
                  <div className="space-y-2">
                    <div className="h-28 w-full bg-zinc-900 rounded-lg overflow-hidden flex items-center justify-center p-2 border border-zinc-800">
                      <img src={p.imageUrl} alt={p.name} className="h-full object-contain" />
                    </div>
                    <h4 className="font-semibold text-zinc-100 text-sm line-clamp-2" title={p.name}>
                      {p.name}
                    </h4>
                    {score && (
                      <div className="flex items-center justify-between">
                        <span className="text-[10px] uppercase font-bold tracking-wider px-2 py-0.5 rounded bg-zinc-900 border border-zinc-800 text-zinc-300">
                          {score.recommendationBadge}
                        </span>
                        <span className="text-xs font-bold text-emerald-400 font-mono">
                          {score.overallScore.toFixed(0)}/100
                        </span>
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
  );
};
