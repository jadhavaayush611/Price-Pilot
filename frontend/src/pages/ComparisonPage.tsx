import React, { useEffect, useState } from 'react';
import { useSearchParams } from 'react-router-dom';
import { apiService, MOCK_PRODUCTS } from '../services/api';
import type { ComparisonResponse, ProductWithPrices } from '../types';
import { ComparisonTable } from '../components/comparison/ComparisonTable';
import { ComparisonSelector } from '../components/comparison/ComparisonSelector';
import { ComparisonSkeleton } from '../components/comparison/ComparisonSkeleton';

export const ComparisonPage: React.FC = () => {
  const [searchParams, setSearchParams] = useSearchParams();
  const idsParam = searchParams.get('ids') || '';
  
  const [selectedIds, setSelectedIds] = useState<string[]>(
    idsParam ? idsParam.split(',').filter(Boolean) : [MOCK_PRODUCTS[0]?.id, MOCK_PRODUCTS[1]?.id].filter(Boolean)
  );
  
  const [availableProducts, setAvailableProducts] = useState<ProductWithPrices[]>(MOCK_PRODUCTS);
  const [comparisonData, setComparisonData] = useState<ComparisonResponse | null>(null);
  const [loading, setLoading] = useState<boolean>(true);

  useEffect(() => {
    // Load products list for selector
    apiService.getProducts(0, 50)
      .then((res) => {
        if (res.content && res.content.length > 0) {
          const formatted = res.content.map((p) => ({ ...p, prices: [] }));
          setAvailableProducts(formatted as ProductWithPrices[]);
        }
      })
      .catch(() => {
        // Fallback to MOCK_PRODUCTS
        setAvailableProducts(MOCK_PRODUCTS);
      });
  }, []);

  useEffect(() => {
    if (selectedIds.length === 0) {
      setComparisonData(null);
      setLoading(false);
      return;
    }

    setLoading(true);
    setSearchParams({ ids: selectedIds.join(',') });

    apiService.getComparison(selectedIds)
      .then((data) => {
        setComparisonData(data);
      })
      .catch(() => {
        // Fallback mock representation for initial scaffold
        const mockProducts = availableProducts.filter((p) => selectedIds.includes(p.id));
        setComparisonData({
          comparisonId: 'mock-session-1',
          products: mockProducts,
          rows: [
            {
              featureName: 'Brand',
              category: 'General',
              valuesByProductId: mockProducts.reduce<Record<string, string>>((acc, p) => { acc[p.id] = p.brand; return acc; }, {}),
              isHighlight: false,
            },
            {
              featureName: 'Category',
              category: 'General',
              valuesByProductId: mockProducts.reduce<Record<string, string>>((acc, p) => { acc[p.id] = p.category; return acc; }, {}),
              isHighlight: false,
            },
            {
              featureName: 'Best Price',
              category: 'Pricing',
              valuesByProductId: mockProducts.reduce<Record<string, string>>((acc, p) => { acc[p.id] = p.lowestPrice ? `$${p.lowestPrice}` : 'N/A'; return acc; }, {}),
              isHighlight: true,
            },
          ],
          scores: mockProducts.reduce<Record<string, any>>((acc, p) => {
            acc[p.id] = {
              productId: p.id,
              productName: p.name,
              overallScore: 88,
              priceValueScore: 90,
              featureScore: 85,
              popularityScore: 92,
              breakdown: { PriceValue: 90 },
              recommendationBadge: 'TOP PICK',
            };
            return acc;
          }, {}),
          summary: `Comparing ${mockProducts.length} products with Shopping Intelligence Engine v1.1.`,
          createdAt: new Date().toISOString(),
        });
      })
      .finally(() => setLoading(false));
  }, [selectedIds]);

  return (
    <div className="space-y-8 max-w-7xl mx-auto px-4 py-6">
      {/* Hero / Header */}
      <div className="space-y-2">
        <div className="inline-flex items-center gap-2 px-3 py-1 rounded-full bg-zinc-900 border border-zinc-800 text-xs text-zinc-400 font-mono">
          <span className="h-2 w-2 rounded-full bg-emerald-500 animate-pulse" />
          <span>Shopping Intelligence Matrix</span>
        </div>
        <h1 className="text-3xl font-bold tracking-tight text-white">Product Comparison Engine</h1>
        <p className="text-zinc-400 text-sm max-w-2xl">
          Compare specifications, price historical variance, and AI deal quality scores side-by-side.
        </p>
      </div>

      {/* Interactive Selector */}
      <ComparisonSelector
        availableProducts={availableProducts}
        selectedIds={selectedIds}
        onSelect={(ids) => setSelectedIds(ids)}
      />

      {/* Comparison Matrix Table */}
      {loading ? (
        <ComparisonSkeleton />
      ) : comparisonData ? (
        <ComparisonTable comparison={comparisonData} />
      ) : (
        <div className="p-12 text-center bg-zinc-950 border border-zinc-900 rounded-xl text-zinc-500">
          Select products above to initiate comparison matrix.
        </div>
      )}
    </div>
  );
};

export default ComparisonPage;
