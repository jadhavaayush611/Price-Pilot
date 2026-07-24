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
    p.brand.toLowerCase().includes(searchTerm.toLowerCase())
  );

  const toggleSelect = (id: string) => {
    if (selectedIds.includes(id)) {
      onSelect(selectedIds.filter((i) => i !== id));
    } else {
      if (selectedIds.length < 4) {
        onSelect([...selectedIds, id]);
      }
    }
  };

  return (
    <div className="bg-zinc-950 border border-zinc-900 rounded-xl p-5 space-y-4">
      <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-3">
        <div>
          <h3 className="text-sm font-semibold text-zinc-200">Select Products to Compare</h3>
          <p className="text-xs text-zinc-500">Compare up to 4 products side-by-side (Selected: {selectedIds.length}/4)</p>
        </div>
        <input
          type="text"
          placeholder="Filter candidate products..."
          value={searchTerm}
          onChange={(e) => setSearchTerm(e.target.value)}
          className="px-3 py-1.5 text-xs bg-zinc-900 border border-zinc-800 rounded-lg text-zinc-200 focus:outline-none focus:border-zinc-600 w-full sm:w-64"
        />
      </div>

      <div className="grid grid-cols-2 sm:grid-cols-4 gap-3 max-h-48 overflow-y-auto pr-1">
        {filtered.map((p) => {
          const isSelected = selectedIds.includes(p.id);
          return (
            <button
              key={p.id}
              onClick={() => toggleSelect(p.id)}
              className={`p-2.5 rounded-lg border text-left flex items-center gap-2.5 transition-all ${
                isSelected
                  ? 'bg-zinc-900 border-zinc-500 text-zinc-100 ring-1 ring-zinc-500'
                  : 'bg-zinc-950/60 border-zinc-900 text-zinc-400 hover:border-zinc-800 hover:text-zinc-200'
              }`}
            >
              <img src={p.imageUrl} alt={p.name} className="h-8 w-8 object-contain rounded bg-zinc-900 p-0.5" />
              <div className="truncate text-xs">
                <p className="font-medium truncate">{p.name}</p>
                <p className="text-[10px] text-zinc-500">{p.brand}</p>
              </div>
            </button>
          );
        })}
      </div>
    </div>
  );
};
