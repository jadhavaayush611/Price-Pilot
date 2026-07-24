import React, { useState } from 'react';
import type { ProductWithPrices } from '../../types';

interface ComparisonSelectorProps {
  availableProducts: ProductWithPrices[];
  selectedIds: string[];
  onSelect: (ids: string[]) => void;
}

export const ComparisonSelector: React.FC<ComparisonSelectorProps> = ({
  availableProducts,
  selectedIds,
  onSelect,
}) => {
  const [searchTerm, setSearchTerm] = useState('');

  const filtered = availableProducts.filter((p) =>
    p.name.toLowerCase().includes(searchTerm.toLowerCase()) ||
    p.brand.toLowerCase().includes(searchTerm.toLowerCase()) ||
    p.category.toLowerCase().includes(searchTerm.toLowerCase())
  );

  const toggleSelect = (id: string) => {
    if (selectedIds.includes(id)) {
      onSelect(selectedIds.filter((i) => i !== id));
    } else {
      if (selectedIds.length < 5) {
        onSelect([...selectedIds, id]);
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
            <span className={`text-[11px] font-mono px-2 py-0.5 rounded-full border ${
              selectedIds.length >= 2 && selectedIds.length <= 5
                ? 'bg-emerald-950/80 border-emerald-800 text-emerald-300'
                : 'bg-amber-950/80 border-amber-800 text-amber-300'
            }`}>
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
          <input
            type="text"
            placeholder="Search products, brands, category..."
            value={searchTerm}
            onChange={(e) => setSearchTerm(e.target.value)}
            className="px-3 py-1.5 text-xs bg-zinc-900 border border-zinc-800 rounded-lg text-zinc-200 focus:outline-none focus:border-emerald-500/50 w-full sm:w-64"
          />
        </div>
      </div>

      {/* Product Selection Grid */}
      <div className="grid grid-cols-2 sm:grid-cols-3 md:grid-cols-5 gap-3 max-h-56 overflow-y-auto pr-1">
        {filtered.map((p) => {
          const isSelected = selectedIds.includes(p.id);
          const isDisabled = !isSelected && selectedIds.length >= 5;

          return (
            <button
              key={p.id}
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
                <img src={p.imageUrl} alt={p.name} className="h-9 w-9 object-contain rounded-lg bg-zinc-900 p-1 border border-zinc-800" />
                <div className="min-w-0 flex-1">
                  <p className="font-semibold text-xs text-zinc-200 truncate">{p.name}</p>
                  <p className="text-[10px] text-zinc-500">{p.brand}</p>
                </div>
              </div>
              <div className="flex items-center justify-between pt-1 border-t border-zinc-900 text-[11px]">
                <span className="text-emerald-400 font-mono font-medium">
                  {p.lowestPrice ? `$${p.lowestPrice}` : 'N/A'}
                </span>
                <span className={`text-[10px] font-mono ${isSelected ? 'text-emerald-400 font-bold' : 'text-zinc-500'}`}>
                  {isSelected ? '✓ Selected' : '+ Add'}
                </span>
              </div>
            </button>
          );
        })}
      </div>
    </div>
  );
};
