import React from 'react';
import type { ComparisonResponse } from '../../types';
import { ComparisonRowItem } from './ComparisonRowItem';

interface ComparisonTableProps {
  comparison: ComparisonResponse;
}

export const ComparisonTable: React.FC<ComparisonTableProps> = ({ comparison }) => {
  if (!comparison.products || comparison.products.length === 0) {
    return (
      <div className="p-12 text-center bg-zinc-950 border border-zinc-900 rounded-xl text-zinc-500 space-y-2">
        <p className="text-zinc-300 font-semibold">No products selected for comparison.</p>
        <p className="text-xs text-zinc-500">Select 2 to 5 products above to render comparison matrix.</p>
      </div>
    );
  }

  return (
    <div className="space-y-6">
      {/* Dynamic Comparison Summary Banner */}
      <div className="p-4 rounded-xl bg-gradient-to-r from-emerald-950/40 via-zinc-900/60 to-zinc-950 border border-emerald-900/40 flex flex-col sm:flex-row items-start sm:items-center justify-between gap-3 shadow-xl">
        <div className="flex items-start gap-3">
          <div className="p-2 rounded-lg bg-emerald-900/30 border border-emerald-800/40 text-emerald-400 text-sm">
            ⚡
          </div>
          <div>
            <h4 className="text-xs uppercase font-bold tracking-wider text-emerald-400">Comparison Intelligence Summary</h4>
            <p className="text-sm text-zinc-200 mt-0.5 leading-relaxed font-medium">
              {comparison.summary}
            </p>
          </div>
        </div>
        <div className="text-xs text-zinc-500 font-mono whitespace-nowrap">
          {new Date(comparison.createdAt).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' })}
        </div>
      </div>

      {/* Sticky Matrix Table */}
      <div className="w-full overflow-x-auto border border-zinc-900 rounded-xl bg-zinc-950 shadow-2xl relative">
        <table className="w-full text-left border-collapse min-w-[700px]">
          <thead>
            {/* Sticky Table Header */}
            <tr className="border-b border-zinc-900 bg-zinc-950/95 backdrop-blur sticky top-0 z-30 shadow-md">
              <th className="py-4 px-4 text-xs font-bold text-zinc-400 uppercase tracking-wider sticky left-0 bg-zinc-950/95 backdrop-blur z-40 border-r border-zinc-900 w-56">
                Features & Metrics
              </th>
              {comparison.products.map((p) => {
                const score = comparison.scores[p.id];
                return (
                  <th key={p.id} className="py-4 px-4 min-w-[220px] align-top border-r border-zinc-900/50 last:border-r-0">
                    <div className="space-y-3">
                      <div className="h-32 w-full bg-zinc-900/70 rounded-xl overflow-hidden flex items-center justify-center p-3 border border-zinc-800/80 shadow-inner group">
                        <img
                          src={p.imageUrl}
                          alt={p.name}
                          className="h-full object-contain transition-transform group-hover:scale-105"
                        />
                      </div>

                      <div>
                        <span className="text-[10px] uppercase font-semibold text-zinc-500 tracking-wider">
                          {p.brand}
                        </span>
                        <h4 className="font-bold text-zinc-100 text-sm line-clamp-2 leading-snug" title={p.name}>
                          {p.name}
                        </h4>
                      </div>

                      {score && (
                        <div className="p-2.5 rounded-lg bg-zinc-900/60 border border-zinc-800/80 space-y-2">
                          <div className="flex items-center justify-between">
                            <span className="text-[10px] uppercase font-bold tracking-wider px-2 py-0.5 rounded bg-emerald-950 border border-emerald-800/60 text-emerald-300">
                              {score.recommendationBadge}
                            </span>
                            <div className="text-right">
                              <span className="text-sm font-bold text-emerald-400 font-mono">
                                {score.overallScore.toFixed(1)}
                              </span>
                              <span className="text-[10px] text-zinc-500">/100</span>
                            </div>
                          </div>

                          {/* Factor Progress Bar */}
                          <div className="w-full bg-zinc-800 rounded-full h-1.5 overflow-hidden">
                            <div
                              className="bg-emerald-400 h-full rounded-full transition-all duration-500"
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
