import React, { useEffect, useState } from 'react';
import { useSearchParams, useNavigate } from 'react-router-dom';
import { apiService } from '../services/api';
import type { ProductWithPrices } from '../types';
import { Search, Filter, SlidersHorizontal, Tag, ArrowUpDown } from 'lucide-react';

export const SearchPage: React.FC = () => {
  const [searchParams, setSearchParams] = useSearchParams();
  const query = searchParams.get('q') || '';
  const navigate = useNavigate();

  const [products, setProducts] = useState<ProductWithPrices[]>([]);
  const [loading, setLoading] = useState(true);
  const [searchInput, setSearchInput] = useState(query);

  // Filters & Sorting states
  const [selectedBrand, setSelectedBrand] = useState('All');
  const [selectedCategory, setSelectedCategory] = useState('All');
  const [sortBy, setSortBy] = useState('default'); // default, price-asc, price-desc, discount-desc

  useEffect(() => {
    setSearchInput(query);
    setLoading(true);
    apiService.searchProducts(query)
      .then((data) => {
        setProducts(data);
      })
      .finally(() => {
        setLoading(false);
      });
  }, [query]);

  const handleSearchSubmit = (e: React.FormEvent) => {
    e.preventDefault();
    setSearchParams(searchInput.trim() ? { q: searchInput } : {});
  };

  // Extract unique brands and categories for filter dropdowns
  const allBrands = ['All', ...Array.from(new Set(products.map((p) => p.brand)))];
  const allCategories = ['All', ...Array.from(new Set(products.map((p) => p.category)))];

  // Apply filters and sorting
  const filteredProducts = products
    .filter((p) => selectedBrand === 'All' || p.brand === selectedBrand)
    .filter((p) => selectedCategory === 'All' || p.category === selectedCategory)
    .sort((a, b) => {
      if (sortBy === 'price-asc') {
        return (a.lowestPrice || 0) - (b.lowestPrice || 0);
      }
      if (sortBy === 'price-desc') {
        return (b.lowestPrice || 0) - (a.lowestPrice || 0);
      }
      if (sortBy === 'discount-desc') {
        // Find maximum discount in prices array
        const maxDiscount = (p: ProductWithPrices) =>
          Math.max(...p.prices.map((pr) => pr.discountPercentage));
        return maxDiscount(b) - maxDiscount(a);
      }
      return 0; // default order
    });

  return (
    <div className="flex flex-col gap-8 py-6">
      {/* Search Header */}
      <div className="flex flex-col gap-4 md:flex-row md:items-center md:justify-between">
        <div>
          <h1 className="text-2xl font-bold tracking-tight text-white m-0 md:mb-1">
            {query ? `Search results for "${query}"` : 'Discover Products'}
          </h1>
          <p className="text-sm text-zinc-400">
            Compare prices across online sellers instantly
          </p>
        </div>

        <form onSubmit={handleSearchSubmit} className="w-full md:max-w-md">
          <div className="relative flex items-center p-1 rounded-lg bg-zinc-950 border border-zinc-900 focus-within:border-zinc-800">
            <Search className="h-4 w-4 text-zinc-500 ml-3 flex-shrink-0" />
            <input
              type="text"
              placeholder="Search products, brands..."
              value={searchInput}
              onChange={(e) => setSearchInput(e.target.value)}
              className="w-full px-3 py-2 bg-transparent text-zinc-100 placeholder-zinc-500 focus:outline-none text-sm"
            />
            <button
              type="submit"
              className="px-4 py-1.5 bg-zinc-900 hover:bg-zinc-800 border border-zinc-800 hover:text-white text-xs font-semibold rounded-md transition-colors cursor-pointer"
            >
              Search
            </button>
          </div>
        </form>
      </div>

      {/* Main Grid */}
      <div className="grid grid-cols-1 lg:grid-cols-4 gap-8 items-start">
        {/* Sidebar Filters */}
        <aside className="flex flex-col gap-6 p-5 rounded-xl bg-zinc-950/40 border border-zinc-900 backdrop-blur-xl">
          <div className="flex items-center gap-2 border-b border-zinc-900 pb-3">
            <SlidersHorizontal className="h-4 w-4 text-zinc-400" />
            <span className="text-xs font-bold uppercase tracking-wider text-zinc-400">Filters & Sorting</span>
          </div>

          {/* Sort By */}
          <div className="flex flex-col gap-2">
            <label className="text-xs font-semibold text-zinc-500 flex items-center gap-1">
              <ArrowUpDown className="h-3.5 w-3.5" /> Sort By
            </label>
            <select
              value={sortBy}
              onChange={(e) => setSortBy(e.target.value)}
              className="w-full bg-[#070708] border border-zinc-900 rounded-lg px-3 py-2 text-sm text-zinc-300 focus:outline-none focus:border-zinc-700 cursor-pointer"
            >
              <option value="default">Relevance</option>
              <option value="price-asc">Lowest Price</option>
              <option value="price-desc">Highest Price</option>
              <option value="discount-desc">Biggest Discount</option>
            </select>
          </div>

          {/* Filter Category */}
          <div className="flex flex-col gap-2">
            <label className="text-xs font-semibold text-zinc-500 flex items-center gap-1">
              <Filter className="h-3.5 w-3.5" /> Category
            </label>
            <select
              value={selectedCategory}
              onChange={(e) => setSelectedCategory(e.target.value)}
              className="w-full bg-[#070708] border border-zinc-900 rounded-lg px-3 py-2 text-sm text-zinc-300 focus:outline-none focus:border-zinc-700 cursor-pointer"
            >
              {allCategories.map((cat) => (
                <option key={cat} value={cat}>{cat}</option>
              ))}
            </select>
          </div>

          {/* Filter Brand */}
          <div className="flex flex-col gap-2">
            <label className="text-xs font-semibold text-zinc-500 flex items-center gap-1">
              <Tag className="h-3.5 w-3.5" /> Brand
            </label>
            <select
              value={selectedBrand}
              onChange={(e) => setSelectedBrand(e.target.value)}
              className="w-full bg-[#070708] border border-zinc-900 rounded-lg px-3 py-2 text-sm text-zinc-300 focus:outline-none focus:border-zinc-700 cursor-pointer"
            >
              {allBrands.map((brand) => (
                <option key={brand} value={brand}>{brand}</option>
              ))}
            </select>
          </div>
        </aside>

        {/* Search Results */}
        <section className="lg:col-span-3 flex flex-col gap-6">
          {loading ? (
            <div className="grid grid-cols-1 md:grid-cols-2 gap-6">
              {[1, 2, 3].map((n) => (
                <div key={n} className="h-[380px] rounded-2xl bg-zinc-950/20 border border-zinc-900 animate-pulse" />
              ))}
            </div>
          ) : filteredProducts.length === 0 ? (
            <div className="flex flex-col items-center justify-center py-20 border border-zinc-900 border-dashed rounded-2xl bg-zinc-950/10">
              <p className="text-sm text-zinc-500">No products found matching your search filters.</p>
            </div>
          ) : (
            <div className="grid grid-cols-1 md:grid-cols-2 gap-6">
              {filteredProducts.map((product) => {
                // Find maximum discount to display
                const maxDiscount = Math.max(...product.prices.map((p) => p.discountPercentage));
                const lowest = product.lowestPrice;

                return (
                  <div
                    key={product.id}
                    className="flex flex-col justify-between p-5 rounded-2xl bg-zinc-950/30 border border-zinc-900 hover:border-zinc-800 hover:bg-zinc-900/10 transition-all duration-300 group"
                  >
                    <div>
                      {/* Image container */}
                      <div className="aspect-[4/3] rounded-xl overflow-hidden bg-zinc-950 border border-zinc-900 mb-4 relative">
                        <img
                          src={product.imageUrl}
                          alt={product.name}
                          className="w-full h-full object-cover group-hover:scale-[1.03] transition-transform duration-300"
                        />
                        {maxDiscount > 0 && (
                          <span className="absolute top-3 right-3 px-2 py-1 rounded bg-rose-500/90 text-[10px] font-bold text-white tracking-wider uppercase">
                            Up to {maxDiscount.toFixed(0)}% Off
                          </span>
                        )}
                      </div>

                      {/* Brand & Title */}
                      <span className="text-[10px] font-semibold tracking-widest uppercase text-zinc-500">
                        {product.brand}
                      </span>
                      <h3 className="font-semibold text-zinc-100 text-base leading-tight mt-1 mb-2 line-clamp-2">
                        {product.name}
                      </h3>
                      <p className="text-xs text-zinc-400 line-clamp-2 mb-4">
                        {product.description}
                      </p>
                    </div>

                    <div className="border-t border-zinc-900 pt-4 flex items-center justify-between">
                      <div className="flex flex-col">
                        <span className="text-[10px] text-zinc-500 uppercase font-semibold">Prices From</span>
                        <span className="text-lg font-bold text-white">
                          ${lowest}
                        </span>
                      </div>

                      <button
                        onClick={() => navigate(`/product/${product.id}`)}
                        className="px-4 py-2 bg-zinc-900 hover:bg-white hover:text-black border border-zinc-800 hover:border-white text-xs font-semibold rounded-lg transition-all cursor-pointer"
                      >
                        Compare Deals
                      </button>
                    </div>
                  </div>
                );
              })}
            </div>
          )}
        </section>
      </div>
    </div>
  );
};
