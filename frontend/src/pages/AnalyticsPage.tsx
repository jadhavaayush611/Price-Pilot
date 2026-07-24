import React, { useEffect, useState } from 'react';
import { useParams, Link } from 'react-router-dom';
import { apiService, MOCK_PRODUCTS } from '../services/api';
import type { ProductAnalytics, ProductWithPrices } from '../types';

export const AnalyticsPage: React.FC = () => {
  const { productId } = useParams<{ productId: string }>();
  const [product, setProduct] = useState<ProductWithPrices | null>(null);
  const [analytics, setAnalytics] = useState<ProductAnalytics | null>(null);
  const [loading, setLoading] = useState<boolean>(true);

  useEffect(() => {
    const id = productId || MOCK_PRODUCTS[0]?.id;
    if (!id) return;

    setLoading(true);
    Promise.all([
      apiService.getProduct(id).catch(() => MOCK_PRODUCTS[0]),
      apiService.getIntelligenceAnalytics(id).catch(() => null),
    ])
      .then(([prod, ana]) => {
        setProduct(prod || MOCK_PRODUCTS[0]);
        setAnalytics(ana);
      })
      .finally(() => setLoading(false));
  }, [productId]);

  return (
    <div className="space-y-8 max-w-7xl mx-auto px-4 py-6">
      {/* Header */}
      <div className="flex flex-col md:flex-row md:items-center justify-between gap-4 border-b border-zinc-900 pb-6">
        <div>
          <div className="inline-flex items-center gap-2 px-3 py-1 rounded-full bg-zinc-900 border border-zinc-800 text-xs text-zinc-400 font-mono mb-2">
            <span>Price Analytics & Volatility Shell</span>
          </div>
          <h1 className="text-3xl font-bold tracking-tight text-white">
            {product ? product.name : 'Price Analytics Shell'}
          </h1>
          <p className="text-xs text-zinc-400">Deep market trend diagnostics and volatility prediction metrics.</p>
        </div>

        {product && (
          <Link
            to={`/product/${product.id}`}
            className="px-4 py-2 text-xs font-semibold bg-zinc-900 border border-zinc-800 text-zinc-200 rounded-lg hover:border-zinc-700 self-start md:self-auto"
          >
            View Product Page &rarr;
          </Link>
        )}
      </div>

      {loading ? (
        <div className="animate-pulse space-y-6">
          <div className="grid grid-cols-1 sm:grid-cols-4 gap-4">
            {[1, 2, 3, 4].map((i) => (
              <div key={i} className="h-24 bg-zinc-950 border border-zinc-900 rounded-xl" />
            ))}
          </div>
          <div className="h-64 bg-zinc-950 border border-zinc-900 rounded-xl" />
        </div>
      ) : (
        <div className="space-y-8">
          {/* Key Metrics Shell Cards */}
          <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-4">
            <div className="bg-zinc-950 border border-zinc-900 rounded-xl p-5 space-y-1">
              <span className="text-xs text-zinc-500 font-medium">Market Volatility Index</span>
              <p className="text-2xl font-bold font-mono text-emerald-400">LOW (2.4%)</p>
              <span className="text-[10px] text-zinc-500">Stable pricing over 30d</span>
            </div>

            <div className="bg-zinc-950 border border-zinc-900 rounded-xl p-5 space-y-1">
              <span className="text-xs text-zinc-500 font-medium font-mono">Trending Score</span>
              <p className="text-2xl font-bold font-mono text-zinc-100">
                {analytics?.trendingScore ? analytics.trendingScore.toFixed(1) : '85.0'} / 100
              </p>
              <span className="text-[10px] text-emerald-400">+12% consumer demand</span>
            </div>

            <div className="bg-zinc-950 border border-zinc-900 rounded-xl p-5 space-y-1">
              <span className="text-xs text-zinc-500 font-medium">Recorded Views</span>
              <p className="text-2xl font-bold font-mono text-zinc-100">
                {analytics?.viewCount ? analytics.viewCount : '142'}
              </p>
              <span className="text-[10px] text-zinc-500">Total consumer interactions</span>
            </div>

            <div className="bg-zinc-950 border border-zinc-900 rounded-xl p-5 space-y-1">
              <span className="text-xs text-zinc-500 font-medium">Deal Rating</span>
              <p className="text-2xl font-bold font-mono text-indigo-400">GREAT DEAL</p>
              <span className="text-[10px] text-zinc-500">Currently 8% below 90-day avg</span>
            </div>
          </div>

          {/* Analytics Chart & Placeholder Container */}
          <div className="bg-zinc-950 border border-zinc-900 rounded-xl p-6 space-y-6">
            <div className="flex items-center justify-between border-b border-zinc-900 pb-4">
              <div>
                <h3 className="text-base font-semibold text-zinc-100">Historical Price Trend & Predictions</h3>
                <p className="text-xs text-zinc-500">Aggregated multi-seller pricing dynamics</p>
              </div>
              <div className="flex gap-2">
                <span className="px-2.5 py-1 text-xs rounded bg-zinc-900 border border-zinc-800 text-zinc-300 font-mono">30D</span>
                <span className="px-2.5 py-1 text-xs rounded bg-zinc-900 border border-zinc-800 text-zinc-500 font-mono">90D</span>
                <span className="px-2.5 py-1 text-xs rounded bg-zinc-900 border border-zinc-800 text-zinc-500 font-mono">1Y</span>
              </div>
            </div>

            {/* Skeleton Chart Shell */}
            <div className="h-64 bg-zinc-900/40 rounded-lg border border-dashed border-zinc-800 flex flex-col items-center justify-center p-6 text-center space-y-3">
              <div className="h-10 w-10 rounded-full bg-zinc-900 border border-zinc-800 flex items-center justify-center text-zinc-400">
                &tilde;
              </div>
              <p className="text-xs font-mono text-zinc-400">Price Trend Analytics Chart (v1.1 Scaffolding Shell)</p>
              <span className="text-[11px] text-zinc-600 max-w-md">
                Interactive multi-model trend charts and automated price drop predictor widgets will wire here in Phase 2.
              </span>
            </div>
          </div>
        </div>
      )}
    </div>
  );
};

export default AnalyticsPage;
