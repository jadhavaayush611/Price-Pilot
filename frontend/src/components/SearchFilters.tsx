import React from 'react';
import { motion } from 'framer-motion';
import { 
  SlidersHorizontal, 
  Tag, 
  Layers, 
  ArrowUpDown,
  Check,
  RotateCcw,
  DollarSign,
  Sparkles,
  CheckCircle2
} from 'lucide-react';

interface SearchFiltersProps {
  categories: string[];
  brands: string[];
  selectedCategory: string;
  selectedBrand: string;
  minPrice: string;
  maxPrice: string;
  inStockOnly: boolean;
  dealQuality: string;
  sortBy: string;
  onCategoryChange: (category: string) => void;
  onBrandChange: (brand: string) => void;
  onMinPriceChange: (val: string) => void;
  onMaxPriceChange: (val: string) => void;
  onInStockToggle: (checked: boolean) => void;
  onDealQualityChange: (quality: string) => void;
  onSortChange: (sort: string) => void;
  onReset: () => void;
}

export const SearchFilters: React.FC<SearchFiltersProps> = React.memo(({
  categories,
  brands,
  selectedCategory,
  selectedBrand,
  minPrice,
  maxPrice,
  inStockOnly,
  dealQuality,
  sortBy,
  onCategoryChange,
  onBrandChange,
  onMinPriceChange,
  onMaxPriceChange,
  onInStockToggle,
  onDealQualityChange,
  onSortChange,
  onReset
}) => {
  const hasActiveFilters = 
    selectedCategory !== 'All' || 
    selectedBrand !== 'All' || 
    minPrice !== '' || 
    maxPrice !== '' || 
    inStockOnly || 
    dealQuality !== 'All' || 
    (sortBy !== 'default' && sortBy !== 'relevance');

  const sortOptions = [
    { value: 'relevance', label: 'Relevance (Smart Match)' },
    { value: 'price-asc', label: 'Price: Low to High' },
    { value: 'price-desc', label: 'Price: High to Low' },
    { value: 'discount-desc', label: 'Biggest Discount' },
    { value: 'newest', label: 'Newest Arrivals' }
  ];

  const dealOptions = [
    { value: 'All', label: 'All Deals' },
    { value: 'EXCELLENT_DEAL', label: 'Excellent Deals' },
    { value: 'GOOD_DEAL', label: 'Good Deals' }
  ];

  return (
    <aside 
      aria-label="Product Search Filters"
      className="flex flex-col gap-6 p-5 rounded-2xl bg-zinc-950/50 border border-zinc-900 backdrop-blur-xl sticky top-36 z-25 max-h-[calc(100vh-180px)] overflow-y-auto pr-3"
    >
      {/* Header */}
      <div className="flex items-center justify-between border-b border-zinc-900/80 pb-3">
        <div className="flex items-center gap-2">
          <SlidersHorizontal className="h-4 w-4 text-emerald-400" aria-hidden="true" />
          <span className="text-xs font-bold uppercase tracking-wider text-zinc-300">Filters & Sort</span>
        </div>
        {hasActiveFilters && (
          <button
            type="button"
            onClick={onReset}
            aria-label="Reset all search filters"
            className="flex items-center gap-1 text-[11px] font-semibold text-zinc-400 hover:text-emerald-400 transition-colors cursor-pointer group"
          >
            <RotateCcw className="h-3 w-3 group-hover:rotate-[-45deg] transition-transform" aria-hidden="true" />
            Reset
          </button>
        )}
      </div>

      {/* Sort By Section */}
      <div className="flex flex-col gap-2.5">
        <label className="text-[11px] font-bold text-zinc-500 uppercase tracking-wider flex items-center gap-1.5">
          <ArrowUpDown className="h-3.5 w-3.5 text-zinc-500" aria-hidden="true" /> Sort Order
        </label>
        <div className="flex flex-col gap-1">
          {sortOptions.map((opt) => {
            const isActive = sortBy === opt.value || (opt.value === 'relevance' && sortBy === 'default');
            return (
              <button
                type="button"
                key={opt.value}
                onClick={() => onSortChange(opt.value)}
                className={`relative flex items-center justify-between px-3 py-2 rounded-xl text-left text-xs font-medium transition-all cursor-pointer ${
                  isActive 
                    ? 'bg-zinc-900 text-emerald-400 font-semibold border border-zinc-800' 
                    : 'text-zinc-400 hover:text-zinc-200 hover:bg-zinc-900/40'
                }`}
              >
                <span>{opt.label}</span>
                {isActive && (
                  <motion.span layoutId="activeSortTick">
                    <Check className="h-3.5 w-3.5 text-emerald-400" aria-hidden="true" />
                  </motion.span>
                )}
              </button>
            );
          })}
        </div>
      </div>

      {/* Price Range Filter */}
      <div className="flex flex-col gap-2.5 border-t border-zinc-900/80 pt-4">
        <label className="text-[11px] font-bold text-zinc-500 uppercase tracking-wider flex items-center gap-1.5">
          <DollarSign className="h-3.5 w-3.5 text-zinc-500" aria-hidden="true" /> Price Range
        </label>
        <div className="grid grid-cols-2 gap-2">
          <div className="relative">
            <input
              type="number"
              placeholder="Min $"
              value={minPrice}
              onChange={(e) => onMinPriceChange(e.target.value)}
              aria-label="Minimum price"
              className="w-full px-2.5 py-1.5 bg-zinc-900/60 border border-zinc-800 rounded-lg text-xs text-white placeholder-zinc-500 focus:outline-none focus:border-zinc-700"
            />
          </div>
          <div className="relative">
            <input
              type="number"
              placeholder="Max $"
              value={maxPrice}
              onChange={(e) => onMaxPriceChange(e.target.value)}
              aria-label="Maximum price"
              className="w-full px-2.5 py-1.5 bg-zinc-900/60 border border-zinc-800 rounded-lg text-xs text-white placeholder-zinc-500 focus:outline-none focus:border-zinc-700"
            />
          </div>
        </div>
      </div>

      {/* Deal Quality Filter */}
      <div className="flex flex-col gap-2.5 border-t border-zinc-900/80 pt-4">
        <label className="text-[11px] font-bold text-zinc-500 uppercase tracking-wider flex items-center gap-1.5">
          <Sparkles className="h-3.5 w-3.5 text-amber-400" aria-hidden="true" /> Deal Quality
        </label>
        <div className="flex flex-col gap-1">
          {dealOptions.map((d) => {
            const isActive = dealQuality === d.value;
            return (
              <button
                type="button"
                key={d.value}
                onClick={() => onDealQualityChange(d.value)}
                className={`flex items-center justify-between px-3 py-1.5 rounded-lg text-left text-xs font-medium transition-all cursor-pointer ${
                  isActive
                    ? 'bg-zinc-900 text-amber-300 font-semibold border border-zinc-800'
                    : 'text-zinc-400 hover:text-zinc-200 hover:bg-zinc-900/40'
                }`}
              >
                <span>{d.label}</span>
                {isActive && <Check className="h-3.5 w-3.5 text-amber-300" aria-hidden="true" />}
              </button>
            );
          })}
        </div>
      </div>

      {/* In Stock Only Toggle */}
      <div className="flex items-center justify-between border-t border-zinc-900/80 pt-4">
        <span className="text-xs font-medium text-zinc-300 flex items-center gap-1.5">
          <CheckCircle2 className="h-3.5 w-3.5 text-emerald-400" aria-hidden="true" />
          In Stock Only
        </span>
        <label className="relative inline-flex items-center cursor-pointer">
          <input
            type="checkbox"
            checked={inStockOnly}
            onChange={(e) => onInStockToggle(e.target.checked)}
            className="sr-only peer"
            aria-label="Show in-stock items only"
          />
          <div className="w-9 h-5 bg-zinc-800 peer-focus:outline-none rounded-full peer peer-checked:after:translate-x-full peer-checked:after:border-white after:content-[''] after:absolute after:top-[2px] after:left-[2px] after:bg-white after:rounded-full after:h-4 after:w-4 after:transition-all peer-checked:bg-emerald-500"></div>
        </label>
      </div>

      {/* Filter Category Section */}
      <div className="flex flex-col gap-2.5 border-t border-zinc-900/80 pt-4">
        <label className="text-[11px] font-bold text-zinc-500 uppercase tracking-wider flex items-center gap-1.5">
          <Layers className="h-3.5 w-3.5 text-zinc-500" aria-hidden="true" /> Category
        </label>
        <div className="flex flex-col gap-1 max-h-[160px] overflow-y-auto pr-1">
          <button
            type="button"
            onClick={() => onCategoryChange('All')}
            className={`flex items-center justify-between px-3 py-1.5 rounded-lg text-left text-xs font-medium transition-all cursor-pointer ${
              selectedCategory === 'All'
                ? 'bg-zinc-900 text-white font-semibold'
                : 'text-zinc-400 hover:text-zinc-200 hover:bg-zinc-900/40'
            }`}
          >
            All Categories
            {selectedCategory === 'All' && <Check className="h-3.5 w-3.5 text-zinc-200" aria-hidden="true" />}
          </button>
          
          {categories.map((cat) => {
            const isActive = selectedCategory === cat;
            return (
              <button
                type="button"
                key={cat}
                onClick={() => onCategoryChange(cat)}
                className={`flex items-center justify-between px-3 py-1.5 rounded-lg text-left text-xs font-medium transition-all cursor-pointer ${
                  isActive
                    ? 'bg-zinc-900 text-white font-semibold'
                    : 'text-zinc-400 hover:text-zinc-200 hover:bg-zinc-900/40'
                }`}
              >
                <span className="truncate">{cat}</span>
                {isActive && <Check className="h-3.5 w-3.5 text-zinc-200" aria-hidden="true" />}
              </button>
            );
          })}
        </div>
      </div>

      {/* Filter Brand Section */}
      <div className="flex flex-col gap-2.5 border-t border-zinc-900/80 pt-4">
        <label className="text-[11px] font-bold text-zinc-500 uppercase tracking-wider flex items-center gap-1.5">
          <Tag className="h-3.5 w-3.5 text-zinc-500" aria-hidden="true" /> Brand
        </label>
        <div className="flex flex-col gap-1 max-h-[160px] overflow-y-auto pr-1">
          <button
            type="button"
            onClick={() => onBrandChange('All')}
            className={`flex items-center justify-between px-3 py-1.5 rounded-lg text-left text-xs font-medium transition-all cursor-pointer ${
              selectedBrand === 'All'
                ? 'bg-zinc-900 text-white font-semibold'
                : 'text-zinc-400 hover:text-zinc-200 hover:bg-zinc-900/40'
            }`}
          >
            All Brands
            {selectedBrand === 'All' && <Check className="h-3.5 w-3.5 text-zinc-200" aria-hidden="true" />}
          </button>
          
          {brands.map((brand) => {
            const isActive = selectedBrand === brand;
            return (
              <button
                type="button"
                key={brand}
                onClick={() => onBrandChange(brand)}
                className={`flex items-center justify-between px-3 py-1.5 rounded-lg text-left text-xs font-medium transition-all cursor-pointer ${
                  isActive
                    ? 'bg-zinc-900 text-white font-semibold'
                    : 'text-zinc-400 hover:text-zinc-200 hover:bg-zinc-900/40'
                }`}
              >
                <span className="truncate">{brand}</span>
                {isActive && <Check className="h-3.5 w-3.5 text-zinc-200" aria-hidden="true" />}
              </button>
            );
          })}
        </div>
      </div>
    </aside>
  );
});
