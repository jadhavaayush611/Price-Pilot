import React, { useState } from 'react';
import type { HistoricalPricePoint } from '../../types';
import { formatPrice, getDisplayPrice, type CurrencyCode } from '../../currency';

interface HistoricalPriceChartProps {
  priceSeries?: HistoricalPricePoint[];
  currentPrice?: number;
  historicalAvg?: number;
  historicalMin?: number;
  historicalMax?: number;
  currency?: CurrencyCode;
}

export const HistoricalPriceChart: React.FC<HistoricalPriceChartProps> = ({
  priceSeries = [],
  currentPrice,
  historicalAvg,
  historicalMin,
  historicalMax,
  currency = 'INR',
}) => {
  const [hoveredPoint, setHoveredPoint] = useState<HistoricalPricePoint | null>(null);

  if (!priceSeries || priceSeries.length < 2) {
    return (
      <div
        role="region"
        aria-label="Price history chart"
        className="h-64 rounded-2xl bg-zinc-950/60 border border-dashed border-zinc-800 flex flex-col items-center justify-center p-6 text-center text-zinc-500"
      >
        <p className="text-sm font-semibold text-zinc-300">Insufficient Price History for Trajectory Chart</p>
        <p className="text-xs text-zinc-500 mt-1 max-w-md">
          At least two verified historical observations are required to render trend lines. Recorded checkpoint data is preserved.
        </p>
      </div>
    );
  }

  // Convert all canonical values to active display currency
  const displaySeries = priceSeries.map((pt) => ({
    ...pt,
    displayPrice: getDisplayPrice(pt.price, currency),
  }));
  const displayCurrent = currentPrice !== undefined ? getDisplayPrice(currentPrice, currency) : undefined;
  const displayAvg = historicalAvg !== undefined ? getDisplayPrice(historicalAvg, currency) : undefined;
  const displayMin = historicalMin !== undefined ? getDisplayPrice(historicalMin, currency) : undefined;
  const displayMax = historicalMax !== undefined ? getDisplayPrice(historicalMax, currency) : undefined;

  // Chart dimensions & padding
  const width = 800;
  const height = 300;
  const padding = { top: 30, right: 65, bottom: 40, left: 60 };

  const innerWidth = width - padding.left - padding.right;
  const innerHeight = height - padding.top - padding.bottom;

  // Compute min and max for scaling in display currency
  const prices = displaySeries.map((p) => p.displayPrice);
  const minVal = Math.min(...prices, displayCurrent ?? Infinity, displayMin ?? Infinity);
  const maxVal = Math.max(...prices, displayCurrent ?? -Infinity, displayMax ?? -Infinity);
  const spread = maxVal - minVal > 0 ? maxVal - minVal : 1;
  const yMin = Math.max(0, minVal - spread * 0.1);
  const yMax = maxVal + spread * 0.1;
  const yRange = yMax - yMin;

  const getX = (index: number) => padding.left + (index / (displaySeries.length - 1)) * innerWidth;
  const getY = (price: number) => padding.top + innerHeight - ((price - yMin) / yRange) * innerHeight;

  // Build SVG path
  const pathD = displaySeries.reduce((acc, pt, idx) => {
    const x = getX(idx);
    const y = getY(pt.displayPrice);
    return idx === 0 ? `M ${x} ${y}` : `${acc} L ${x} ${y}`;
  }, '');

  return (
    <div className="space-y-3" role="region" aria-label="Historical Price Trajectory Chart">
      <div className="relative w-full overflow-hidden bg-zinc-950/80 border border-zinc-800/80 rounded-2xl p-4 shadow-inner">
        {/* Tooltip header */}
        <div className="flex items-center justify-between text-xs font-mono text-zinc-400 mb-2 border-b border-zinc-800/60 pb-2.5">
          <span className="text-zinc-400 font-medium">Recorded Checkpoints: <strong className="text-zinc-200">{displaySeries.length}</strong></span>
          {hoveredPoint ? (
            <span className="text-emerald-400 font-semibold">
              {new Date(hoveredPoint.timestamp).toLocaleDateString(undefined, { month: 'short', day: 'numeric', year: 'numeric' })} — {formatPrice(getDisplayPrice(hoveredPoint.price, currency), currency)}
              {hoveredPoint.sellerName ? ` (${hoveredPoint.sellerName})` : ''}
            </span>
          ) : (
            <span className="text-zinc-500 hidden sm:inline">Hover over data points for checkpoint details</span>
          )}
        </div>

        <svg
          viewBox={`0 0 ${width} ${height}`}
          className="w-full h-auto overflow-visible select-none"
          aria-hidden="true"
        >
          {/* Grid lines */}
          {[0, 0.25, 0.5, 0.75, 1].map((pct, idx) => {
            const y = padding.top + innerHeight * pct;
            const priceVal = yMax - yRange * pct;
            return (
              <g key={idx} className="text-zinc-800">
                <line
                  x1={padding.left}
                  y1={y}
                  x2={width - padding.right}
                  y2={y}
                  stroke="currentColor"
                  strokeDasharray="4 4"
                  strokeWidth="1"
                />
                <text
                  x={width - padding.right + 8}
                  y={y + 4}
                  className="fill-zinc-500 font-mono text-[10px]"
                >
                  {formatPrice(priceVal, currency)}
                </text>
              </g>
            );
          })}

          {/* Historical Average reference line */}
          {displayAvg !== undefined && (
            <g className="text-indigo-400/60">
              <line
                x1={padding.left}
                y1={getY(displayAvg)}
                x2={width - padding.right}
                y2={getY(displayAvg)}
                stroke="currentColor"
                strokeDasharray="3 3"
                strokeWidth="1.5"
              />
              <text
                x={padding.left + 8}
                y={getY(displayAvg) - 6}
                className="fill-indigo-400 font-mono text-[10px] font-medium"
              >
                Historical Avg: {formatPrice(displayAvg, currency)}
              </text>
            </g>
          )}

          {/* Current Price reference line */}
          {displayCurrent !== undefined && (
            <g className="text-emerald-400/70">
              <line
                x1={padding.left}
                y1={getY(displayCurrent)}
                x2={width - padding.right}
                y2={getY(displayCurrent)}
                stroke="currentColor"
                strokeWidth="1.5"
              />
              <text
                x={width - padding.right - 140}
                y={getY(displayCurrent) - 6}
                className="fill-emerald-400 font-mono text-[10px] font-bold"
              >
                Current Offer: {formatPrice(displayCurrent, currency)}
              </text>
            </g>
          )}

          {/* Gradient area under line */}
          <defs>
            <linearGradient id="priceGradient" x1="0" y1="0" x2="0" y2="1">
              <stop offset="0%" stopColor="#10b981" stopOpacity="0.22" />
              <stop offset="100%" stopColor="#10b981" stopOpacity="0.0" />
            </linearGradient>
          </defs>
          <path
            d={`${pathD} L ${getX(displaySeries.length - 1)} ${padding.top + innerHeight} L ${getX(0)} ${padding.top + innerHeight} Z`}
            fill="url(#priceGradient)"
          />

          {/* Main trend line */}
          <path
            d={pathD}
            fill="none"
            stroke="#10b981"
            strokeWidth="2.5"
            strokeLinecap="round"
            strokeLinejoin="round"
          />

          {/* Data point circles */}
          {displaySeries.map((pt, idx) => {
            const cx = getX(idx);
            const cy = getY(pt.displayPrice);
            const isHovered = hoveredPoint === priceSeries[idx];
            const isMin = pt.displayPrice === minVal;
            const isMax = pt.displayPrice === maxVal;

            return (
              <g key={idx}>
                <circle
                  cx={cx}
                  cy={cy}
                  r={isHovered ? 6 : isMin || isMax ? 4.5 : 3}
                  className={`cursor-pointer transition-all duration-200 ${
                    isMin
                      ? 'fill-emerald-400 stroke-emerald-950 stroke-2'
                      : isMax
                      ? 'fill-amber-400 stroke-amber-950 stroke-2'
                      : 'fill-zinc-200 stroke-zinc-950 stroke-2 hover:fill-emerald-300'
                  }`}
                  onMouseEnter={() => setHoveredPoint(priceSeries[idx])}
                  onMouseLeave={() => setHoveredPoint(null)}
                />
              </g>
            );
          })}
        </svg>

        {/* Legend */}
        <div className="flex items-center justify-between flex-wrap gap-3 text-[11px] text-zinc-400 font-mono pt-3 border-t border-zinc-800/60">
          <div className="flex items-center gap-4 flex-wrap">
            <span className="flex items-center gap-1.5">
              <span className="w-2.5 h-2.5 rounded-full bg-emerald-500" />
              Price Trajectory
            </span>
            {displayAvg !== undefined && (
              <span className="flex items-center gap-1.5">
                <span className="w-3 border-t border-dashed border-indigo-400" />
                Historical Average
              </span>
            )}
            {displayCurrent !== undefined && (
              <span className="flex items-center gap-1.5">
                <span className="w-3 border-t border-emerald-400" />
                Current Offer
              </span>
            )}
          </div>
          <div className="flex items-center gap-3">
            <span className="flex items-center gap-1 text-emerald-400 font-medium">
              <span className="w-2 h-2 rounded-full bg-emerald-400" />
              Low: {formatPrice(minVal, currency)}
            </span>
            <span className="flex items-center gap-1 text-amber-400 font-medium">
              <span className="w-2 h-2 rounded-full bg-amber-400" />
              High: {formatPrice(maxVal, currency)}
            </span>
          </div>
        </div>
      </div>

      {/* Screen Reader Accessible Table */}
      <table className="sr-only">
        <caption>Historical Price Points</caption>
        <thead>
          <tr>
            <th>Date</th>
            <th>Price</th>
            <th>Seller</th>
          </tr>
        </thead>
        <tbody>
          {displaySeries.map((p, i) => (
            <tr key={i}>
              <td>{new Date(p.timestamp).toLocaleDateString()}</td>
              <td>{formatPrice(p.displayPrice, currency)}</td>
              <td>{p.sellerName || 'Verified Seller'}</td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
};

