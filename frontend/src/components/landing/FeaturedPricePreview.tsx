import React, { useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { apiService } from '../../services/api';
import type { ProductWithPrices, ProductAnalytics } from '../../types';
import { formatPrice, getDisplayPrice, getSavedCurrency, type CurrencyCode } from '../../currency';
import { ProductImage } from '../common/ProductImage';
import { TrendingDown, TrendingUp, Minus, ChevronRight, Activity } from 'lucide-react';

interface FeaturedPricePreviewProps {
  initialProducts?: ProductWithPrices[];
}

export const FeaturedPricePreview: React.FC<FeaturedPricePreviewProps> = ({ initialProducts }) => {
  const navigate = useNavigate();
  const currency: CurrencyCode = getSavedCurrency();

  const [product, setProduct] = useState<ProductWithPrices | null>(null);
  const [analytics, setAnalytics] = useState<ProductAnalytics | null>(null);
  const [loading, setLoading] = useState<boolean>(true);
  const [error, setError] = useState<boolean>(false);

  useEffect(() => {
    let isMounted = true;

    async function loadFeaturedData() {
      try {
        setLoading(true);
        setError(false);

        // 1. Obtain real catalog products from existing API if not provided in props
        let candidateList = initialProducts || [];
        if (!candidateList || candidateList.length === 0) {
          candidateList = await apiService.getTrendingProducts(6);
        }

        if (!candidateList || candidateList.length === 0) {
          const res = await apiService.getProducts(0, 6);
          candidateList = (res.content as unknown as ProductWithPrices[]) || [];
        }

        if (!candidateList || candidateList.length === 0) {
          if (isMounted) {
            setError(true);
            setLoading(false);
          }
          return;
        }

        // 2. Select a deterministic product that has price history records
        let selectedProd: ProductWithPrices | null = null;
        let selectedAnalytics: ProductAnalytics | null = null;

        for (const candidate of candidateList) {
          if (!candidate.id) continue;
          try {
            const ana = await apiService.getIntelligenceAnalytics(candidate.id);
            if (ana && ana.priceSeries && ana.priceSeries.length >= 2) {
              selectedProd = candidate;
              selectedAnalytics = ana;
              break;
            } else if (ana && !selectedAnalytics) {
              // Keep as candidate if no better one found
              selectedProd = candidate;
              selectedAnalytics = ana;
            }
          } catch {
            // Continue trying other candidates
          }
        }

        // If no candidate had analytics from the loop, fallback to the first candidate with basic analytics
        if (!selectedProd && candidateList.length > 0) {
          selectedProd = candidateList[0];
          try {
            selectedAnalytics = await apiService.getIntelligenceAnalytics(selectedProd.id);
          } catch {
            selectedAnalytics = null;
          }
        }

        if (isMounted) {
          if (selectedProd) {
            setProduct(selectedProd);
            setAnalytics(selectedAnalytics);
          } else {
            setError(true);
          }
          setLoading(false);
        }
      } catch (err) {
        if (isMounted) {
          console.error('Failed to load featured price preview:', err);
          setError(true);
          setLoading(false);
        }
      }
    }

    loadFeaturedData();

    return () => {
      isMounted = false;
    };
  }, [initialProducts]);

  // Loading State
  if (loading) {
    return (
      <div 
        role="status"
        aria-label="Loading price intelligence preview"
        className="w-full rounded-2xl bg-zinc-950/80 border border-zinc-900 p-4 sm:p-5 shadow-xl animate-pulse space-y-3.5"
      >
        <div className="flex items-center justify-between gap-3">
          <div className="flex items-center gap-2.5">
            <div className="h-10 w-10 rounded-xl bg-zinc-900/80 border border-zinc-800/80" />
            <div className="space-y-1.5">
              <div className="h-2.5 w-16 bg-zinc-900 rounded" />
              <div className="h-3.5 w-32 bg-zinc-900 rounded" />
            </div>
          </div>
          <div className="h-4 w-20 bg-zinc-900 rounded" />
        </div>
        <div className="h-20 w-full bg-zinc-900/40 rounded-xl border border-zinc-900/50" />
        <div className="flex items-center justify-between pt-2 border-t border-zinc-900/60">
          <div className="h-2.5 w-24 bg-zinc-900 rounded" />
          <div className="h-2.5 w-16 bg-zinc-900 rounded" />
        </div>
      </div>
    );
  }

  // Graceful Error / Unavailable State
  if (error || !product) {
    return (
      <div 
        role="region"
        aria-label="Price history preview"
        className="w-full rounded-2xl bg-zinc-950/60 border border-zinc-900/80 p-5 text-center space-y-2"
      >
        <div className="flex items-center justify-center gap-2 text-zinc-400 text-xs font-semibold">
          <Activity className="w-4 h-4 text-emerald-400" />
          <span>Price Intelligence Preview</span>
        </div>
        <p className="text-xs text-zinc-500">
          Real-time catalog pricing data is continually monitored across all verified retailers.
        </p>
      </div>
    );
  }

  // Prepare price data with single conversion boundary (canonical USD -> active display currency)
  const rawCurrent = analytics?.currentPrice ?? product.lowestPrice ?? (product.prices && product.prices.length > 0 ? product.prices[0].currentPrice : 0);
  const displayCurrent = getDisplayPrice(rawCurrent, currency);

  const rawOriginal = product.prices && product.prices.length > 0 
    ? product.prices.find(p => p.currentPrice === product.lowestPrice)?.originalPrice 
    : undefined;
  const displayOriginal = rawOriginal ? getDisplayPrice(rawOriginal, currency) : undefined;

  // Chart series in active display currency
  const rawSeries = analytics?.priceSeries || [];
  const displaySeries = rawSeries.map(pt => ({
    ...pt,
    displayPrice: getDisplayPrice(pt.price, currency),
  }));

  // Trend determination
  const trend = analytics?.trend;
  const trendPct = analytics?.trendPercentage;
  let trendLabel = 'Stable';
  let trendColorClass = 'text-zinc-400';
  let TrendIcon = Minus;

  if (trend === 'FALLING' || (trendPct !== undefined && trendPct < 0)) {
    trendLabel = trendPct !== undefined ? `↓ ${Math.abs(trendPct).toFixed(0)}%` : 'Falling';
    trendColorClass = 'text-emerald-400';
    TrendIcon = TrendingDown;
  } else if (trend === 'RISING' || (trendPct !== undefined && trendPct > 0)) {
    trendLabel = trendPct !== undefined ? `↑ ${trendPct.toFixed(0)}%` : 'Rising';
    trendColorClass = 'text-rose-400';
    TrendIcon = TrendingUp;
  } else if (displaySeries.length >= 2) {
    const firstP = displaySeries[0].displayPrice;
    const lastP = displaySeries[displaySeries.length - 1].displayPrice;
    const deltaPct = firstP > 0 ? ((lastP - firstP) / firstP) * 100 : 0;
    if (deltaPct < -1) {
      trendLabel = `↓ ${Math.abs(deltaPct).toFixed(0)}%`;
      trendColorClass = 'text-emerald-400';
      TrendIcon = TrendingDown;
    } else if (deltaPct > 1) {
      trendLabel = `↑ ${deltaPct.toFixed(0)}%`;
      trendColorClass = 'text-rose-400';
      TrendIcon = TrendingUp;
    }
  }

  // SVG Chart Calculation
  const chartWidth = 320;
  const chartHeight = 80;
  const paddingX = 10;
  const paddingY = 12;

  const innerW = chartWidth - paddingX * 2;
  const innerH = chartHeight - paddingY * 2;

  let linePathD = '';
  let areaPathD = '';
  let lastX = paddingX;
  let lastY = chartHeight / 2;

  if (displaySeries.length >= 2) {
    const prices = displaySeries.map(p => p.displayPrice);
    const minP = Math.min(...prices);
    const maxP = Math.max(...prices);
    const spread = maxP - minP > 0 ? maxP - minP : 1;

    const getX = (idx: number) => paddingX + (idx / (displaySeries.length - 1)) * innerW;
    const getY = (price: number) => paddingY + innerH - ((price - minP) / spread) * innerH;

    linePathD = displaySeries.reduce((acc, pt, idx) => {
      const x = getX(idx);
      const y = getY(pt.displayPrice);
      if (idx === displaySeries.length - 1) {
        lastX = x;
        lastY = y;
      }
      return idx === 0 ? `M ${x} ${y}` : `${acc} L ${x} ${y}`;
    }, '');

    areaPathD = `${linePathD} L ${getX(displaySeries.length - 1)} ${chartHeight - paddingY + 6} L ${getX(0)} ${chartHeight - paddingY + 6} Z`;
  } else {
    // Single baseline horizontal trajectory
    const midY = chartHeight / 2;
    linePathD = `M ${paddingX} ${midY} L ${chartWidth - paddingX} ${midY}`;
    areaPathD = `M ${paddingX} ${midY} L ${chartWidth - paddingX} ${midY} L ${chartWidth - paddingX} ${chartHeight - 4} L ${paddingX} ${chartHeight - 4} Z`;
    lastX = chartWidth - paddingX;
    lastY = midY;
  }

  return (
    <div 
      onClick={() => navigate(`/product/${product.id}`)}
      className="w-full rounded-2xl bg-zinc-950/80 border border-zinc-800/80 hover:border-zinc-700/80 p-4 sm:p-5 shadow-xl transition-all duration-300 group cursor-pointer text-left"
    >
      {/* Product Header */}
      <div className="flex items-start justify-between gap-3">
        <div className="flex items-center gap-2.5 min-w-0">
          <div className="h-10 w-10 rounded-xl overflow-hidden bg-zinc-900/60 border border-zinc-800 p-1 shrink-0 flex items-center justify-center">
            <ProductImage
              src={product.imageUrl}
              alt={product.name}
              className="h-full w-full object-contain group-hover:scale-105 transition-transform duration-300"
              showFallbackText
              fallbackText={product.brand}
            />
          </div>
          <div className="min-w-0">
            <div className="flex items-center gap-1.5 text-[10px] text-zinc-500 font-bold uppercase tracking-wider">
              <span>{product.brand}</span>
              <span className="text-zinc-600">·</span>
              <span className="text-zinc-400 font-medium">Price History</span>
            </div>
            <h4 className="text-xs sm:text-sm font-bold text-zinc-100 group-hover:text-white truncate mt-0.5">
              {product.name}
            </h4>
          </div>
        </div>

        {/* Current Price */}
        <div className="text-right shrink-0">
          <span className="text-xs sm:text-sm font-extrabold font-mono text-white block">
            {formatPrice(displayCurrent, currency)}
          </span>
          {displayOriginal && displayOriginal > displayCurrent && (
            <span className="text-[10px] text-zinc-500 line-through font-mono block">
              {formatPrice(displayOriginal, currency)}
            </span>
          )}
        </div>
      </div>

      {/* Mini Trajectory Chart */}
      <div className="my-2.5 h-18 sm:h-20 w-full relative">
        <svg 
          viewBox={`0 0 ${chartWidth} ${chartHeight}`} 
          className="w-full h-full overflow-visible select-none" 
          preserveAspectRatio="none"
          aria-hidden="true"
        >
          <defs>
            <linearGradient id="landingChartGradient" x1="0" y1="0" x2="0" y2="1">
              <stop offset="0%" stopColor="#10b981" stopOpacity="0.22" />
              <stop offset="100%" stopColor="#10b981" stopOpacity="0.0" />
            </linearGradient>
          </defs>
          <path d={areaPathD} fill="url(#landingChartGradient)" />
          <path 
            d={linePathD} 
            fill="none" 
            stroke="#10b981" 
            strokeWidth="2.25" 
            strokeLinecap="round" 
            strokeLinejoin="round" 
          />
          <circle 
            cx={lastX} 
            cy={lastY} 
            r="3.5" 
            className="fill-emerald-400 stroke-zinc-950 stroke-2" 
          />
        </svg>
      </div>

      {/* Footer: Timeline & Trend */}
      <div className="flex items-center justify-between pt-2 border-t border-zinc-900/80 text-[11px] font-mono">
        <span className="text-zinc-500 text-[10px]">
          {displaySeries.length > 1 ? `${displaySeries.length} recorded points` : 'Recorded trajectory'}
        </span>

        <div className="flex items-center gap-3">
          <span className={`font-semibold flex items-center gap-1 text-[11px] ${trendColorClass}`}>
            <TrendIcon className="w-3.5 h-3.5" />
            {trendLabel}
          </span>
          <span className="text-zinc-400 group-hover:text-emerald-400 transition-colors flex items-center font-sans font-medium text-xs">
            View <ChevronRight className="w-3.5 h-3.5 ml-0.5" />
          </span>
        </div>
      </div>
    </div>
  );
};

export default FeaturedPricePreview;
