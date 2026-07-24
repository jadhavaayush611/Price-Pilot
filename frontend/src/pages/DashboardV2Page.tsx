import React, { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { apiService, MOCK_PRODUCTS } from '../services/api';
import type { RecommendationResponse } from '../types';
import { RecommendationList } from '../components/recommendation/RecommendationList';

export const DashboardV2Page: React.FC = () => {
  const [activeTab, setActiveTab] = useState<'overview' | 'recommendations' | 'comparison' | 'analytics'>('overview');
  const [recommendations, setRecommendations] = useState<RecommendationResponse | null>(null);
  const [loading, setLoading] = useState<boolean>(false);

  useEffect(() => {
    if (activeTab === 'recommendations' && !recommendations) {
      setLoading(true);
      const sampleId = MOCK_PRODUCTS[0]?.id || 'p1';
      apiService.getIntelligenceRecommendations(sampleId)
        .then((res) => setRecommendations(res))
        .catch(() => {
          setRecommendations({
            targetProductId: sampleId,
            recommendedProducts: MOCK_PRODUCTS,
            scores: MOCK_PRODUCTS.map((p) => ({
              productId: p.id,
              productName: p.name,
              overallScore: 92,
              priceValueScore: 94,
              featureScore: 89,
              popularityScore: 93,
              breakdown: { Value: 94 },
              recommendationBadge: 'TOP MATCH',
            })),
            explanation: 'Engine v2 foundation matrix matching current market trends.',
            strategyUsed: 'HYBRID_V2_FOUNDATION',
            generatedAt: new Date().toISOString(),
          });
        })
        .finally(() => setLoading(false));
    }
  }, [activeTab]);

  return (
    <div className="space-y-8 max-w-7xl mx-auto px-4 py-6">
      {/* Header */}
      <div className="flex flex-col md:flex-row md:items-center justify-between gap-4 border-b border-zinc-900 pb-6">
        <div>
          <div className="inline-flex items-center gap-2 px-3 py-1 rounded-full bg-zinc-900 border border-zinc-800 text-xs text-zinc-400 font-mono mb-2">
            <span className="h-2 w-2 rounded-full bg-indigo-500 animate-pulse" />
            <span>PricePilot v1.1 Shopping Intelligence</span>
          </div>
          <h1 className="text-3xl font-bold tracking-tight text-white">Dashboard v2</h1>
          <p className="text-zinc-400 text-xs">Unified Shopping Intelligence control center</p>
        </div>

        {/* Tab Navigation */}
        <div className="flex bg-zinc-950 border border-zinc-900 rounded-xl p-1 gap-1">
          <button
            onClick={() => setActiveTab('overview')}
            className={`px-3.5 py-1.5 text-xs font-semibold rounded-lg transition-all ${
              activeTab === 'overview' ? 'bg-zinc-800 text-white shadow' : 'text-zinc-400 hover:text-zinc-200'
            }`}
          >
            Overview
          </button>
          <button
            onClick={() => setActiveTab('recommendations')}
            className={`px-3.5 py-1.5 text-xs font-semibold rounded-lg transition-all ${
              activeTab === 'recommendations' ? 'bg-zinc-800 text-white shadow' : 'text-zinc-400 hover:text-zinc-200'
            }`}
          >
            AI Recommendations
          </button>
          <Link
            to="/compare"
            className="px-3.5 py-1.5 text-xs font-semibold rounded-lg text-zinc-400 hover:text-zinc-200 transition-all flex items-center gap-1"
          >
            Comparison &rarr;
          </Link>
          <Link
            to="/analytics"
            className="px-3.5 py-1.5 text-xs font-semibold rounded-lg text-zinc-400 hover:text-zinc-200 transition-all flex items-center gap-1"
          >
            Analytics &rarr;
          </Link>
        </div>
      </div>

      {/* Main Tab Content */}
      {activeTab === 'overview' && (
        <div className="space-y-6">
          {/* Quick Metrics */}
          <div className="grid grid-cols-1 sm:grid-cols-3 gap-6">
            <div className="bg-zinc-950 border border-zinc-900 rounded-xl p-5 space-y-2">
              <span className="text-xs text-zinc-500 font-medium">Comparison Engine</span>
              <p className="text-2xl font-bold text-zinc-100">v1.1 Active</p>
              <Link to="/compare" className="text-xs text-emerald-400 hover:underline block pt-1">
                Open Matrix &rarr;
              </Link>
            </div>

            <div className="bg-zinc-950 border border-zinc-900 rounded-xl p-5 space-y-2">
              <span className="text-xs text-zinc-500 font-medium">AI Engine Pipeline</span>
              <p className="text-2xl font-bold text-zinc-100">Engine v2 Foundation</p>
              <button onClick={() => setActiveTab('recommendations')} className="text-xs text-emerald-400 hover:underline block pt-1">
                View Recommendations &rarr;
              </button>
            </div>

            <div className="bg-zinc-950 border border-zinc-900 rounded-xl p-5 space-y-2">
              <span className="text-xs text-zinc-500 font-medium">Price Analytics</span>
              <p className="text-2xl font-bold text-zinc-100">Volatility Diagnostics</p>
              <Link to="/analytics" className="text-xs text-emerald-400 hover:underline block pt-1">
                Open Shell &rarr;
              </Link>
            </div>
          </div>
        </div>
      )}

      {activeTab === 'recommendations' && (
        <div className="space-y-6">
          <div className="flex items-center justify-between">
            <h2 className="text-lg font-bold text-zinc-100">Shopping Intelligence Recommendations</h2>
          </div>
          <RecommendationList recommendations={recommendations} loading={loading} />
        </div>
      )}
    </div>
  );
};

export default DashboardV2Page;
