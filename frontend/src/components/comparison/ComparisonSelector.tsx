import React, { useState, useEffect } from 'react';
import type { ProductWithPrices, Product } from '../../types';
import { ProductImage } from '../common/ProductImage';
import { apiService } from '../../services/api';
import { Search } from 'lucide-react';

interface ComparisonSelectorProps {
  availableProducts: (ProductWithPrices | Product)[];
  selectedIds: string[];
  onSelect: (ids: string[]) => void;
}

export const ComparisonSelector: React.FC<ComparisonSelectorProps> = ({
  availableProducts,
  selectedIds,
  onSelect,
}) => {
  const [searchTerm, setSearchTerm] = useState('');
  const [searchResults, setSearchResults] = useState<(ProductWithPrices | Product)[]>([]);
  const [isSearching, setIsSearching] = useState(false);

  // If user searches, fetch dynamically from catalog API
  useEffect(() => {
    if (!searchTerm.trim()) {
      setSearchResults([]);
      setIsSearching(false);
      return;
    }

    const timer = setTimeout(async () => {
      setIsSearching(true);
      try {
        const res = await apiService.getProducts(0, 30, undefined, undefined, searchTerm.trim());
        setSearchResults(res.content || []);
      } catch {
        setSearchResults([]);
      } finally {
        setIsSearching(false);
      }
    }, 250);

    return () => clearTimeout(timer);
  }, [searchTerm]);

  const displayList = searchTerm.trim() ? searchResults : availableProducts;

  const toggleSelect = (id: string) => {
    if (selectedIds.includes(id)) {
      onSelect(selectedIds.filter((i) => i !== id));
    } else {
      if (selectedIds.length < 5) {
        onSelect(Array.from(new Set([...selectedIds, id])));
      }
    }
  };

  const clearAll = () => {
    onSelect([]);
  };

  return (
    <div className="bg-zinc-950 border border-zinc-900 rounded-xl p-5 space-y-4 shadow-xl">
      <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-3">
        <div>
          <div className="flex items-center gap-2">
            <h3 className="text-sm font-semibold text-zinc-200">Select Products for Matrix Comparison</h3>
            <span
              className={`text-[11px] font-mono px-2 py-0.5 rounded-full border ${
                selectedIds.length >= 2 && selectedIds.length <= 5
                  ? 'bg-emerald-950/80 border-emerald-800 text-emerald-300'
                  : 'bg-amber-950/80 border-amber-800 text-amber-300'
              }`}
            >
              {selectedIds.length}/5 Selected {selectedIds.length < 2 && '(Min 2 required)'}
            </span>
          </div>
          <p className="text-xs text-zinc-400 mt-1">
            Pick 2 to 5 products to compare specifications, ratings, and price competitiveness side-by-side.
          </p>
        </div>

        <div className="flex items-center gap-2">
          {selectedIds.length > 0 && (
            <button
              onClick={clearAll}
              className="px-3 py-1.5 text-xs bg-zinc-900 hover:bg-zinc-800 border border-zinc-800 text-zinc-400 hover:text-zinc-200 rounded-lg transition-colors"
            >
              Clear Selection
            </button>
          )}
          <div className="relative w-full sm:w-64">
            <Search className="absolute left-2.5 top-1/2 -translate-y-1/2 w-3.5 h-3.5 text-zinc-500" />
            <input
              type="text"
              placeholder="Search products, brands..."
              value={searchTerm}
              onChange={(e) => setSearchTerm(e.target.value)}
              className="pl-8 pr-3 py-1.5 text-xs bg-zinc-900 border border-zinc-800 rounded-lg text-zinc-200 focus:outline-none focus:border-emerald-500/50 w-full"
            />
          </div>
        </div>
      </div>

      {/* Product Selection Grid */}
      <div className="grid grid-cols-2 sm:grid-cols-3 md:grid-cols-5 gap-3 max-h-60 overflow-y-auto pr-1">
        {isSearching ? (
          <div className="col-span-full py-8 text-center text-xs text-zinc-500 font-mono">
            Searching verified catalog...
          </div>
        ) : displayList.length === 0 ? (
          <div className="col-span-full py-8 text-center text-xs text-zinc-500">
            No products found matching "{searchTerm}".
          </div>
        ) : (
          displayList.map((p) => {
            const isSelected = selectedIds.includes(p.id);
            const isDisabled = !isSelected && selectedIds.length >= 5;

            return (
              <button
                key={p.id}
                type="button"
                onClick={() => !isDisabled && toggleSelect(p.id)}
                disabled={isDisabled}
                aria-label={`Select ${p.name}`}
                className={`p-3 rounded-xl border text-left flex flex-col justify-between transition-all ${
                  isSelected
                    ? 'bg-zinc-900 border-emerald-500/60 text-zinc-100 ring-1 ring-emerald-500/40 shadow-lg shadow-emerald-950/20'
                    : isDisabled
                    ? 'bg-zinc-950/40 border-zinc-900 text-zinc-600 opacity-50 cursor-not-allowed'
                    : 'bg-zinc-900/40 border-zinc-900 text-zinc-400 hover:border-zinc-800 hover:text-zinc-200 hover:bg-zinc-900/80'
                }`}
              >
                <div className="flex items-center gap-2 mb-2">
                  <div className="h-9 w-9 shrink-0 rounded-lg bg-zinc-900 p-0.5 border border-zinc-800 overflow-hidden flex items-center justify-center">
                    <ProductImage
                      src={p.imageUrl}
                      alt={p.name}
                      className="h-full w-full object-contain"
                    />
                  </div>
                  <div className="min-w-0 flex-1">
                    <p className="font-semibold text-xs text-zinc-200 truncate">{p.name}</p>
                    <p className="text-[10px] text-zinc-500 truncate">{p.brand}</p>
                  </div>
                </div>
                <div className="flex items-center justify-between pt-1 border-t border-zinc-900 text-[11px]">
                  <span className="text-emerald-400 font-mono font-medium">
                    {(p as ProductWithPrices).lowestPrice ? `$${(p as ProductWithPrices).lowestPrice}` : 'N/A'}
                  </span>
                  <span
                    className={`text-[10px] font-mono ${
                      isSelected ? 'text-emerald-400 font-bold' : 'text-zinc-500'
                    }`}
                  >
                    {isSelected ? '✓ Selected' : '+ Add'}
                  </span>
                </div>
              </button>
            );
          })
        )}
      </div>
    </div>
  );
};
