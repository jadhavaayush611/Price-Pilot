import React from 'react';
import { motion } from 'framer-motion';
import { 
  SlidersHorizontal, 
  Tag, 
  Layers, 
  ArrowUpDown,
  Check,
  RotateCcw
} from 'lucide-react';

interface SearchFiltersProps {
  categories: string[];
  brands: string[];
  selectedCategory: string;
  selectedBrand: string;
  sortBy: string;
  onCategoryChange: (category: string) => void;
  onBrandChange: (brand: string) => void;
  onSortChange: (sort: string) => void;
  onReset: () => void;
}

export const SearchFilters: React.FC<SearchFiltersProps> = React.memo(({
  categories,
  brands,
  selectedCategory,
  selectedBrand,
  sortBy,
  onCategoryChange,
  onBrandChange,
  onSortChange,
  onReset
}) => {
  const hasActiveFilters = selectedCategory !== 'All' || selectedBrand !== 'All' || sortBy !== 'default';

  const sortOptions = [
    { value: 'default', label: 'Relevance' },
    { value: 'price-asc', label: 'Lowest Price' },
    { value: 'price-desc', label: 'Highest Price' },
    { value: 'discount-desc', label: 'Biggest Discount' },
  ];

  return (
    <aside className="flex flex-col gap-8 p-6 rounded-2xl bg-zinc-950/40 border border-zinc-900 backdrop-blur-xl sticky top-36 z-25 max-h-[calc(100vh-180px)] overflow-y-auto pr-3">
      {/* Header */}
      <div className="flex items-center justify-between border-b border-zinc-900/80 pb-4">
        <div className="flex items-center gap-2.5">
          <SlidersHorizontal className="h-4 w-4 text-zinc-400" />
          <span className="text-xs font-bold uppercase tracking-wider text-zinc-400">Filter Engine</span>
        </div>
        {hasActiveFilters && (
          <button
            onClick={onReset}
            className="flex items-center gap-1 text-[10px] font-semibold text-zinc-500 hover:text-zinc-200 transition-colors cursor-pointer group"
          >
            <RotateCcw className="h-3 w-3 group-hover:rotate-[-45deg] transition-transform" />
            Reset
          </button>
        )}
      </div>

      {/* Sort By Section */}
      <div className="flex flex-col gap-3.5">
        <label className="text-[11px] font-bold text-zinc-500 uppercase tracking-wider flex items-center gap-2">
          <ArrowUpDown className="h-3.5 w-3.5 text-zinc-500" /> Sort Order
        </label>
        <div className="flex flex-col gap-1">
          {sortOptions.map((opt) => {
            const isActive = sortBy === opt.value;
            return (
              <button
                key={opt.value}
                onClick={() => onSortChange(opt.value)}
                className={`relative flex items-center justify-between px-3 py-2 rounded-xl text-left text-xs font-medium transition-all cursor-pointer ${
                  isActive 
                    ? 'bg-zinc-900 text-white' 
                    : 'text-zinc-400 hover:text-zinc-200 hover:bg-zinc-900/30'
                }`}
              >
                {opt.label}
                {isActive && (
                  <motion.span layoutId="activeSortTick">
                    <Check className="h-3.5 w-3.5 text-zinc-200" />
                  </motion.span>
                )}
              </button>
            );
          })}
        </div>
      </div>

      {/* Filter Category Section */}
      <div className="flex flex-col gap-3.5">
        <label className="text-[11px] font-bold text-zinc-500 uppercase tracking-wider flex items-center gap-2">
          <Layers className="h-3.5 w-3.5 text-zinc-500" /> Category
        </label>
        <div className="flex flex-col gap-1 max-h-[180px] overflow-y-auto pr-1">
          <button
            onClick={() => onCategoryChange('All')}
            className={`flex items-center justify-between px-3 py-2 rounded-xl text-left text-xs font-medium transition-all cursor-pointer ${
              selectedCategory === 'All'
                ? 'bg-zinc-900 text-white'
                : 'text-zinc-400 hover:text-zinc-200 hover:bg-zinc-900/30'
            }`}
          >
            All Categories
            {selectedCategory === 'All' && <Check className="h-3.5 w-3.5 text-zinc-200" />}
          </button>
          
          {categories.map((cat) => {
            const isActive = selectedCategory === cat;
            return (
              <button
                key={cat}
                onClick={() => onCategoryChange(cat)}
                className={`flex items-center justify-between px-3 py-2 rounded-xl text-left text-xs font-medium transition-all cursor-pointer ${
                  isActive
                    ? 'bg-zinc-900 text-white'
                    : 'text-zinc-400 hover:text-zinc-200 hover:bg-zinc-900/30'
                }`}
              >
                <span className="truncate">{cat}</span>
                {isActive && <Check className="h-3.5 w-3.5 text-zinc-200" />}
              </button>
            );
          })}
        </div>
      </div>

      {/* Filter Brand Section */}
      <div className="flex flex-col gap-3.5">
        <label className="text-[11px] font-bold text-zinc-500 uppercase tracking-wider flex items-center gap-2">
          <Tag className="h-3.5 w-3.5 text-zinc-500" /> Brand
        </label>
        <div className="flex flex-col gap-1 max-h-[180px] overflow-y-auto pr-1">
          <button
            onClick={() => onBrandChange('All')}
            className={`flex items-center justify-between px-3 py-2 rounded-xl text-left text-xs font-medium transition-all cursor-pointer ${
              selectedBrand === 'All'
                ? 'bg-zinc-900 text-white'
                : 'text-zinc-400 hover:text-zinc-200 hover:bg-zinc-900/30'
            }`}
          >
            All Brands
            {selectedBrand === 'All' && <Check className="h-3.5 w-3.5 text-zinc-200" />}
          </button>
          
          {brands.map((brand) => {
            const isActive = selectedBrand === brand;
            return (
              <button
                key={brand}
                onClick={() => onBrandChange(brand)}
                className={`flex items-center justify-between px-3 py-2 rounded-xl text-left text-xs font-medium transition-all cursor-pointer ${
                  isActive
                    ? 'bg-zinc-900 text-white'
                    : 'text-zinc-400 hover:text-zinc-200 hover:bg-zinc-900/30'
                }`}
              >
                <span className="truncate">{brand}</span>
                {isActive && <Check className="h-3.5 w-3.5 text-zinc-200" />}
              </button>
            );
          })}
        </div>
      </div>
    </aside>
  );
});
