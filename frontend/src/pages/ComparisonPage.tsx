import React, { useEffect, useState } from 'react';
import { useSearchParams } from 'react-router-dom';
import { apiService, MOCK_PRODUCTS } from '../services/api';
import type { ComparisonResponse, ProductWithPrices, SavedComparison } from '../types';
import { ComparisonTable } from '../components/comparison/ComparisonTable';
import { ComparisonSelector } from '../components/comparison/ComparisonSelector';
import { ComparisonSkeleton } from '../components/comparison/ComparisonSkeleton';

export const ComparisonPage: React.FC = () => {
  const [searchParams, setSearchParams] = useSearchParams();
  const idsParam = searchParams.get('ids') || '';
  const sessionParam = searchParams.get('sessionId') || '';

  const [selectedIds, setSelectedIds] = useState<string[]>(() => {
    if (idsParam) {
      return idsParam.split(',').map((s) => s.trim()).filter(Boolean);
    }
    return MOCK_PRODUCTS.slice(0, 2).map((p) => p.id);
  });

  const [availableProducts, setAvailableProducts] = useState<ProductWithPrices[]>(MOCK_PRODUCTS);
  const [comparisonData, setComparisonData] = useState<ComparisonResponse | null>(null);
  const [savedComparisons, setSavedComparisons] = useState<SavedComparison[]>([]);
  const [loading, setLoading] = useState<boolean>(true);
  const [error, setError] = useState<string | null>(null);

  // Modal / Drawer state
  const [showSaveModal, setShowSaveModal] = useState<boolean>(false);
  const [showSavedDrawer, setShowSavedDrawer] = useState<boolean>(false);
  const [saveTitle, setSaveTitle] = useState<string>('');
  const [saveNotes, setSaveNotes] = useState<string>('');
  const [saving, setSaving] = useState<boolean>(false);
  const [saveStatusMsg, setSaveStatusMsg] = useState<string | null>(null);

  useEffect(() => {
    // Load products list for selector
    apiService.getProducts(0, 50)
      .then((res) => {
        if (res.content && res.content.length > 0) {
          const formatted = res.content.map((p) => ({
            ...p,
            prices: (p as unknown as ProductWithPrices).prices || [],
          }));
          setAvailableProducts(formatted as ProductWithPrices[]);
        }
      })
      .catch(() => {
        setAvailableProducts(MOCK_PRODUCTS);
      });

    // Load saved comparisons list
    loadSavedComparisonsList();
  }, []);

  const loadSavedComparisonsList = () => {
    apiService.getSavedComparisons()
      .then((res) => {
        if (res && res.content) {
          setSavedComparisons(res.content);
        }
      })
      .catch(() => {
        // Unauthenticated or no saved comparisons yet
      });
  };

  useEffect(() => {
    if (sessionParam) {
      setLoading(true);
      setError(null);
      apiService.getComparisonSession(sessionParam)
        .then((data) => {
          setComparisonData(data);
          if (data.products && data.products.length > 0) {
            setSelectedIds(data.products.map((p) => p.id));
          }
        })
        .catch((err) => {
          setError(err?.response?.data?.message || 'Failed to load comparison session.');
        })
        .finally(() => setLoading(false));
      return;
    }

    if (selectedIds.length === 0) {
      setComparisonData(null);
      setLoading(false);
      return;
    }

    setLoading(true);
    setError(null);
    setSearchParams({ ids: selectedIds.join(',') });

    apiService.getComparison(selectedIds)
      .then((data) => {
        setComparisonData(data);
      })
      .catch(() => {
        // Fallback representation for initial scaffold if backend disconnected
        const mockProducts = availableProducts.filter((p) => selectedIds.includes(p.id));
        setComparisonData({
          comparisonId: 'session-matrix',
          products: mockProducts,
          rows: [
            {
              featureName: 'Brand',
              category: 'General',
              valuesByProductId: mockProducts.reduce<Record<string, string>>((acc, p) => { acc[p.id] = p.brand; return acc; }, {}),
              isHighlight: false,
              rowType: 'GENERAL',
            },
            {
              featureName: 'Category',
              category: 'General',
              valuesByProductId: mockProducts.reduce<Record<string, string>>((acc, p) => { acc[p.id] = p.category; return acc; }, {}),
              isHighlight: false,
              rowType: 'GENERAL',
            },
            {
              featureName: 'Best Price',
              category: 'Pricing',
              valuesByProductId: mockProducts.reduce<Record<string, string>>((acc, p) => { acc[p.id] = p.lowestPrice ? `$${p.lowestPrice}` : 'N/A'; return acc; }, {}),
              isHighlight: true,
              highlightedProductIds: mockProducts.slice(0, 1).map(p => p.id),
              rowType: 'LOWEST_PRICE',
            },
          ],
          scores: mockProducts.reduce<Record<string, any>>((acc, p, idx) => {
            acc[p.id] = {
              productId: p.id,
              productName: p.name,
              overallScore: 92 - idx * 4,
              priceValueScore: 90,
              featureScore: 88,
              popularityScore: 85,
              breakdown: { PriceCompetitiveness: 90, ProductRating: 88 },
              recommendationBadge: idx === 0 ? 'TOP PICK' : 'VALUE OPTION',
            };
            return acc;
          }, {}),
          summary: `Comparing ${mockProducts.length} products with Shopping Intelligence Engine v1.1.`,
          createdAt: new Date().toISOString(),
        });
      })
      .finally(() => setLoading(false));
  }, [selectedIds, sessionParam]);

  const handleSaveComparison = async () => {
    if (!saveTitle.trim()) return;
    setSaving(true);
    setSaveStatusMsg(null);
    try {
      await apiService.saveComparison({
        productIds: selectedIds,
        name: saveTitle.trim(),
        notes: saveNotes.trim(),
      });
      setSaveStatusMsg('Comparison saved successfully!');
      setShowSaveModal(false);
      setSaveTitle('');
      setSaveNotes('');
      loadSavedComparisonsList();
    } catch (err: any) {
      setSaveStatusMsg(err?.response?.data?.message || 'Failed to save comparison. Please ensure you are logged in.');
    } finally {
      setSaving(false);
    }
  };

  const handleDeleteSaved = async (id: string) => {
    try {
      await apiService.deleteSavedComparison(id);
      loadSavedComparisonsList();
    } catch (err: any) {
      alert(err?.response?.data?.message || 'Failed to delete saved comparison.');
    }
  };

  const handleLoadSaved = (saved: SavedComparison) => {
    if (saved.productIds && saved.productIds.length > 0) {
      setSelectedIds(saved.productIds);
      setSearchParams({ ids: saved.productIds.join(',') });
      setShowSavedDrawer(false);
    }
  };

  return (
    <div className="space-y-8 max-w-7xl mx-auto px-4 py-6">
      {/* Top Banner Header */}
      <div className="flex flex-col md:flex-row md:items-center justify-between gap-4 border-b border-zinc-900 pb-6">
        <div className="space-y-2">
          <div className="inline-flex items-center gap-2 px-3 py-1 rounded-full bg-zinc-900 border border-zinc-800 text-xs text-zinc-300 font-mono">
            <span className="h-2 w-2 rounded-full bg-emerald-500 animate-pulse" />
            <span>Shopping Intelligence v1.1 Matrix</span>
          </div>
          <h1 className="text-3xl font-bold tracking-tight text-white">Product Comparison Engine</h1>
          <p className="text-zinc-400 text-sm max-w-2xl">
            Side-by-side comparison of 2–5 products across pricing competitiveness, ratings, seller availability, and specifications.
          </p>
        </div>

        {/* Action Toolbar */}
        <div className="flex items-center gap-3">
          <button
            onClick={() => setShowSavedDrawer(!showSavedDrawer)}
            className="px-4 py-2 text-xs font-semibold bg-zinc-900 hover:bg-zinc-800 border border-zinc-800 text-zinc-200 rounded-xl transition-all flex items-center gap-2 shadow-lg"
          >
            <span>📁 Saved Matrices</span>
            {savedComparisons.length > 0 && (
              <span className="px-1.5 py-0.5 rounded-full bg-emerald-950 text-emerald-400 border border-emerald-800 text-[10px] font-mono">
                {savedComparisons.length}
              </span>
            )}
          </button>

          <button
            onClick={() => setShowSaveModal(true)}
            disabled={selectedIds.length < 2}
            className={`px-4 py-2 text-xs font-semibold rounded-xl transition-all flex items-center gap-2 shadow-lg ${
              selectedIds.length >= 2
                ? 'bg-emerald-600 hover:bg-emerald-500 text-white shadow-emerald-950/40'
                : 'bg-zinc-900 border border-zinc-800 text-zinc-500 cursor-not-allowed'
            }`}
          >
            <span>💾 Save Comparison</span>
          </button>
        </div>
      </div>

      {/* Save Status Notification */}
      {saveStatusMsg && (
        <div className="p-3 rounded-lg bg-zinc-900 border border-emerald-800/60 text-emerald-300 text-xs flex justify-between items-center">
          <span>{saveStatusMsg}</span>
          <button onClick={() => setSaveStatusMsg(null)} className="text-zinc-500 hover:text-white">✕</button>
        </div>
      )}

      {/* Error Alert */}
      {error && (
        <div className="p-4 rounded-xl bg-red-950/40 border border-red-900/60 text-red-300 text-sm flex items-center justify-between">
          <span>{error}</span>
          <button onClick={() => setError(null)} className="text-red-400 font-bold hover:text-red-200">✕</button>
        </div>
      )}

      {/* Saved Comparisons Drawer */}
      {showSavedDrawer && (
        <div className="p-5 bg-zinc-950 border border-zinc-800 rounded-2xl space-y-4 shadow-2xl animate-in fade-in duration-200">
          <div className="flex items-center justify-between border-b border-zinc-900 pb-3">
            <h3 className="text-sm font-bold text-zinc-200">Your Saved Comparisons</h3>
            <button onClick={() => setShowSavedDrawer(false)} className="text-xs text-zinc-500 hover:text-white">Close</button>
          </div>

          {savedComparisons.length === 0 ? (
            <p className="text-xs text-zinc-500 py-4 text-center">No saved comparisons found. Create and save matrix comparisons above.</p>
          ) : (
            <div className="grid grid-cols-1 sm:grid-cols-2 md:grid-cols-3 gap-3">
              {savedComparisons.map((saved) => (
                <div key={saved.id} className="p-3 bg-zinc-900/60 border border-zinc-800 rounded-xl space-y-2 flex flex-col justify-between">
                  <div>
                    <div className="flex items-center justify-between">
                      <h4 className="text-xs font-bold text-zinc-100 truncate">{saved.name}</h4>
                      <span className="text-[10px] text-zinc-500 font-mono">
                        {new Date(saved.createdAt).toLocaleDateString()}
                      </span>
                    </div>
                    {saved.notes && <p className="text-[11px] text-zinc-400 mt-1 line-clamp-2">{saved.notes}</p>}
                    <p className="text-[10px] text-emerald-400 font-mono mt-1">
                      {saved.productIds?.length || 0} Products compared
                    </p>
                  </div>

                  <div className="flex items-center justify-between pt-2 border-t border-zinc-800/60">
                    <button
                      onClick={() => handleLoadSaved(saved)}
                      className="px-2.5 py-1 bg-emerald-950 hover:bg-emerald-900 text-emerald-300 border border-emerald-800/60 rounded text-[11px] font-semibold transition-colors"
                    >
                      Load Matrix
                    </button>
                    <button
                      onClick={() => handleDeleteSaved(saved.id)}
                      className="px-2.5 py-1 bg-zinc-800 hover:bg-red-950 hover:text-red-400 text-zinc-400 border border-zinc-700 rounded text-[11px] transition-colors"
                    >
                      Delete
                    </button>
                  </div>
                </div>
              ))}
            </div>
          )}
        </div>
      )}

      {/* Interactive Product Selector */}
      <ComparisonSelector
        availableProducts={availableProducts}
        selectedIds={selectedIds}
        onSelect={(ids) => setSelectedIds(ids)}
      />

      {/* Matrix Table / Skeleton / Empty State */}
      {loading ? (
        <ComparisonSkeleton />
      ) : comparisonData ? (
        <ComparisonTable comparison={comparisonData} />
      ) : (
        <div className="p-12 text-center bg-zinc-950 border border-zinc-900 rounded-xl text-zinc-500 space-y-2">
          <p className="text-zinc-300 font-semibold">Select 2 to 5 products above to initiate comparison matrix.</p>
        </div>
      )}

      {/* Save Modal */}
      {showSaveModal && (
        <div className="fixed inset-0 bg-black/70 backdrop-blur-sm z-50 flex items-center justify-center p-4">
          <div className="bg-zinc-950 border border-zinc-800 rounded-2xl max-w-md w-full p-6 space-y-4 shadow-2xl">
            <h3 className="text-base font-bold text-zinc-100">Save Product Comparison Matrix</h3>
            <p className="text-xs text-zinc-400">Save this comparison configuration to access it anytime in your saved matrices.</p>

            <div className="space-y-3">
              <div>
                <label className="text-xs font-semibold text-zinc-300 block mb-1">Matrix Title</label>
                <input
                  type="text"
                  placeholder="e.g. Flagship Smartphones 2026"
                  value={saveTitle}
                  onChange={(e) => setSaveTitle(e.target.value)}
                  className="w-full px-3 py-2 text-xs bg-zinc-900 border border-zinc-800 rounded-lg text-zinc-100 focus:outline-none focus:border-emerald-500/50"
                />
              </div>

              <div>
                <label className="text-xs font-semibold text-zinc-300 block mb-1">Notes (Optional)</label>
                <textarea
                  placeholder="e.g. Comparing battery life vs price discount for decision..."
                  value={saveNotes}
                  onChange={(e) => setSaveNotes(e.target.value)}
                  rows={3}
                  className="w-full px-3 py-2 text-xs bg-zinc-900 border border-zinc-800 rounded-lg text-zinc-100 focus:outline-none focus:border-emerald-500/50"
                />
              </div>
            </div>

            <div className="flex items-center justify-end gap-2 pt-2 border-t border-zinc-900">
              <button
                onClick={() => setShowSaveModal(false)}
                className="px-4 py-2 text-xs bg-zinc-900 hover:bg-zinc-800 text-zinc-400 rounded-lg"
              >
                Cancel
              </button>
              <button
                onClick={handleSaveComparison}
                disabled={saving || !saveTitle.trim()}
                className="px-4 py-2 text-xs font-bold bg-emerald-600 hover:bg-emerald-500 text-white rounded-lg transition-all disabled:opacity-50"
              >
                {saving ? 'Saving...' : 'Confirm & Save'}
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
};

export default ComparisonPage;
