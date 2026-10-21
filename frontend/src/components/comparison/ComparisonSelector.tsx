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
    <div className="bg-[var(--color-surface)] border border-[var(--color-border)] rounded-xl p-5 space-y-4 shadow-xl">
      <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-3">
        <div>
          <div className="flex items-center gap-2">
            <h3 className="text-sm font-semibold text-[var(--color-foreground)]">Select Products to Compare</h3>
            <span
              className={`text-[11px] font-mono px-2 py-0.5 rounded-full border ${
                selectedIds.length >= 2 && selectedIds.length <= 5
                  ? 'bg-emerald-500/15 border-emerald-500/30 text-emerald-700 dark:text-emerald-300'
                  : 'bg-amber-500/15 border-amber-500/30 text-amber-800 dark:text-amber-300'
              }`}
            >
              {selectedIds.length}/5 Selected {selectedIds.length < 2 && '(Min 2 required)'}
            </span>
          </div>
          <p className="text-xs text-[var(--color-muted-foreground)] mt-1">
            Pick 2 to 5 products to compare specifications, ratings, and price competitiveness side-by-side.
          </p>
        </div>

        <div className="flex items-center gap-2 w-full sm:w-auto">
          {selectedIds.length > 0 && (
            <button
              onClick={clearAll}
              className="px-3 py-1.5 text-xs bg-[var(--color-surface-accent)] hover:bg-[var(--color-surface)] border border-[var(--color-border)] text-[var(--color-muted-foreground)] hover:text-[var(--color-foreground)] rounded-lg transition-colors cursor-pointer shrink-0"
            >
              Clear Selection
            </button>
          )}
          <div className="relative flex-1 sm:w-64 min-w-0">
            <Search className="absolute left-2.5 top-1/2 -translate-y-1/2 w-3.5 h-3.5 text-[var(--color-muted-foreground)]" />
            <input
              type="text"
              placeholder="Search products, brands..."
              value={searchTerm}
              onChange={(e) => setSearchTerm(e.target.value)}
              className="pl-8 pr-3 py-1.5 text-xs bg-[var(--color-surface-accent)] border border-[var(--color-border)] rounded-lg text-[var(--color-foreground)] placeholder:text-[var(--color-muted-foreground)]/60 focus:outline-none focus:border-[var(--color-primary)] w-full"
            />
          </div>
        </div>
      </div>

      {/* Product Selection Grid */}
      <div className="grid grid-cols-2 sm:grid-cols-3 md:grid-cols-5 gap-3 max-h-60 overflow-y-auto pr-1">
        {isSearching ? (
          <div className="col-span-full py-8 text-center text-xs text-[var(--color-muted-foreground)] font-mono">
            Searching verified catalog...
          </div>
        ) : displayList.length === 0 ? (
          <div className="col-span-full py-8 text-center text-xs text-[var(--color-muted-foreground)]">
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
                className={`p-3 rounded-xl border text-left flex flex-col justify-between transition-all cursor-pointer ${
                  isSelected
                    ? 'bg-[var(--color-surface-accent)] border-emerald-500/60 text-[var(--color-foreground)] ring-1 ring-emerald-500/40 shadow-sm'
                    : isDisabled
                    ? 'bg-[var(--color-surface)]/40 border-[var(--color-border)] text-[var(--color-muted-foreground)]/40 opacity-50 cursor-not-allowed'
                    : 'bg-[var(--color-surface-accent)]/40 border-[var(--color-border)] text-[var(--color-muted-foreground)] hover:border-[var(--color-border)] hover:text-[var(--color-foreground)] hover:bg-[var(--color-surface-accent)]'
                }`}
              >
                <div className="flex items-center gap-2 mb-2">
                  <div className="h-9 w-9 shrink-0 rounded-lg bg-[var(--color-surface)] p-0.5 border border-[var(--color-border)] overflow-hidden flex items-center justify-center">
                    <ProductImage
                      src={p.imageUrl}
                      alt={p.name}
                      category={p.category}
                      className="h-full w-full object-contain"
                    />
                  </div>
                  <div className="min-w-0 flex-1">
                    <p className="font-semibold text-xs text-[var(--color-foreground)] truncate">{p.name}</p>
                    <p className="text-[10px] text-[var(--color-muted-foreground)] truncate">{p.brand}</p>
                  </div>
                </div>
                <div className="flex items-center justify-between pt-1 border-t border-[var(--color-border)] text-[11px]">
                  <span className="text-emerald-600 dark:text-emerald-400 font-mono font-medium">
                    {(p as ProductWithPrices).lowestPrice ? `$${(p as ProductWithPrices).lowestPrice}` : 'N/A'}
                  </span>
                  <span
                    className={`text-[10px] font-mono ${
                      isSelected ? 'text-emerald-600 dark:text-emerald-400 font-bold' : 'text-[var(--color-muted-foreground)]'
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
