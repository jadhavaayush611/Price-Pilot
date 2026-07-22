import React, { useEffect, useState, useCallback } from 'react';
import { useSearchParams, useNavigate } from 'react-router-dom';
import { apiService } from '../services/api';
import type { ProductWithPrices } from '../types';
import { SearchBar } from '../components/SearchBar';
import { SearchFilters } from '../components/SearchFilters';
import { SearchResults } from '../components/SearchResults';
import { SlidersHorizontal } from 'lucide-react';
import { motion, AnimatePresence } from 'framer-motion';
import { useAuth } from '../context/AuthContext';

export const SearchPage: React.FC = () => {
  const [searchParams, setSearchParams] = useSearchParams();
  const navigate = useNavigate();
  const { isAuthenticated } = useAuth();
  
  // Extract state from URL query parameters (supports 'keyword' or legacy 'q')
  const query = searchParams.get('keyword') || searchParams.get('q') || '';
  const urlCategory = searchParams.get('category') || 'All';
  const urlBrand = searchParams.get('brand') || 'All';
  const urlPage = parseInt(searchParams.get('page') || '0', 10);
  const urlSort = searchParams.get('sort') || 'default';

  const [products, setProducts] = useState<ProductWithPrices[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(false);
  const [retryTrigger, setRetryTrigger] = useState(0);
  const [totalPages, setTotalPages] = useState(0);
  const [totalElements, setTotalElements] = useState(0);
  const [showMobileFilters, setShowMobileFilters] = useState(false);
  const [savedProductIds, setSavedProductIds] = useState<string[]>([]);

  // Available categories and brands extracted from active search results
  const [availableCategories, setAvailableCategories] = useState<string[]>([]);
  const [availableBrands, setAvailableBrands] = useState<string[]>([]);

  const hasActiveFilters = urlCategory !== 'All' || urlBrand !== 'All' || urlSort !== 'default';

  // Update URL parameters helper in a clean, immutable way
  const updateParams = useCallback((newParams: Record<string, string | number | null>) => {
    const nextParams = new URLSearchParams(searchParams);
    
    // Clear q parameter in favor of keyword parameter
    nextParams.delete('q');

    Object.entries(newParams).forEach(([key, val]) => {
      if (val === null || val === 'All' || val === '') {
        nextParams.delete(key);
      } else {
        nextParams.set(key, String(val));
      }
    });
    
    // Always reset page to 0 if filters, keyword, or sort changes
    if (!('page' in newParams)) {
      nextParams.delete('page');
    }

    setSearchParams(nextParams);
  }, [searchParams, setSearchParams]);

  // 1. Fetch available filter options matching the keyword (unfiltered by cat/brand to show all options)
  useEffect(() => {
    let active = true;
    
    apiService.searchProductsWithFilters({ keyword: query, size: 200 })
      .then((data) => {
        if (!active) return;
        const cats = Array.from(new Set(data.content.map(p => p.category))).filter(Boolean);
        const brs = Array.from(new Set(data.content.map(p => p.brand))).filter(Boolean);
        setAvailableCategories(cats);
        setAvailableBrands(brs);
      })
      .catch(err => console.error("Error loading filter options:", err));

    return () => {
      active = false;
    };
  }, [query]);

  // 2. Fetch paginated, filtered, sorted results from backend
  useEffect(() => {
    apiService.searchProductsWithFilters({
      keyword: query,
      category: urlCategory,
      brand: urlBrand,
      page: urlPage,
      size: 6, // 6 products per page for neat grid display
      sort: urlSort
    })
      .then((data) => {
        setProducts(data.content);
        setTotalPages(data.totalPages);
        setTotalElements(data.totalElements);
      })
      .catch(err => {
        console.error("Error loading search results:", err);
        setError(true);
      })
      .finally(() => {
        setLoading(false);
      });
  }, [query, urlCategory, urlBrand, urlPage, urlSort, retryTrigger]);

  useEffect(() => {
    if (isAuthenticated) {
      apiService.getSavedProducts()
        .then((saved) => {
          setSavedProductIds(saved.map(sp => sp.productId));
        })
        .catch(err => console.error("Error loading saved products:", err));
    }
  }, [isAuthenticated]);

  const handleToggleSave = async (productId: string) => {
    if (!isAuthenticated) {
      navigate('/login', { state: { from: { pathname: '/search', search: searchParams.toString() } } });
      return;
    }

    const isAlreadySaved = savedProductIds.includes(productId);
    try {
      if (isAlreadySaved) {
        await apiService.removeProduct(productId);
        setSavedProductIds(prev => prev.filter(id => id !== productId));
      } else {
        await apiService.saveProduct(productId);
        setSavedProductIds(prev => [...prev, productId]);
      }
    } catch (err) {
      console.error("Failed to toggle save:", err);
    }
  };

  // Handlers
  const handleKeywordChange = (newKeyword: string) => {
    updateParams({ keyword: newKeyword });
  };

  const handleCategoryChange = (newCategory: string) => {
    updateParams({ category: newCategory });
  };

  const handleBrandChange = (newBrand: string) => {
    updateParams({ brand: newBrand });
  };

  const handleSortChange = (newSort: string) => {
    updateParams({ sort: newSort });
  };

  const handlePageChange = (newPage: number) => {
    updateParams({ page: newPage });
  };

  const handleResetFilters = () => {
    updateParams({
      category: null,
      brand: null,
      sort: null,
      page: null
    });
  };

  return (
    <div className="flex flex-col gap-6 py-2">
      {/* Sticky Glassmorphic Search Header */}
      <div className="sticky top-16 z-30 backdrop-blur-md bg-[#030303]/85 py-4 border-b border-zinc-900/60 -mx-4 px-4 sm:-mx-6 sm:px-6 flex flex-col gap-4 md:flex-row md:items-center md:justify-between transition-all">
        <div>
          <h1 className="text-xl font-bold tracking-tight text-white m-0">
            {query ? `Search: "${query}"` : 'Discover Products'}
          </h1>
          <p className="text-xs text-zinc-500 mt-0.5">
            Compare prices across major online sellers instantly
          </p>
        </div>

        <div className="w-full md:max-w-md flex flex-col sm:flex-row items-stretch sm:items-center gap-3">
          <div className="flex-grow">
            <SearchBar value={query} onChange={handleKeywordChange} />
          </div>
          
          {/* Mobile Filters Toggle Button */}
          <button
            onClick={() => setShowMobileFilters(!showMobileFilters)}
            className="lg:hidden flex items-center justify-center gap-2 px-4 py-2.5 bg-zinc-950 border border-zinc-900 rounded-xl hover:border-zinc-800 text-sm font-semibold text-zinc-300 cursor-pointer active:scale-95 transition-all"
          >
            <SlidersHorizontal className="h-4 w-4" />
            <span>Filters</span>
            {hasActiveFilters && (
              <span className="h-1.5 w-1.5 rounded-full bg-emerald-400 animate-pulse" />
            )}
          </button>
        </div>
      </div>

      {/* Main Responsive Grid Layout */}
      <div className="grid grid-cols-1 lg:grid-cols-4 gap-8 items-start mt-2">
        {/* Mobile Expandable Filter Panel */}
        <AnimatePresence>
          {showMobileFilters && (
            <motion.div
              initial={{ opacity: 0, height: 0 }}
              animate={{ opacity: 1, height: 'auto' }}
              exit={{ opacity: 0, height: 0 }}
              transition={{ duration: 0.25 }}
              className="lg:hidden col-span-1 overflow-hidden"
            >
              <SearchFilters
                categories={availableCategories}
                brands={availableBrands}
                selectedCategory={urlCategory}
                selectedBrand={urlBrand}
                sortBy={urlSort}
                onCategoryChange={handleCategoryChange}
                onBrandChange={handleBrandChange}
                onSortChange={handleSortChange}
                onReset={handleResetFilters}
              />
            </motion.div>
          )}
        </AnimatePresence>

        {/* Sidebar Filters (Desktop only) */}
        <div className="hidden lg:block lg:col-span-1">
          <SearchFilters
            categories={availableCategories}
            brands={availableBrands}
            selectedCategory={urlCategory}
            selectedBrand={urlBrand}
            sortBy={urlSort}
            onCategoryChange={handleCategoryChange}
            onBrandChange={handleBrandChange}
            onSortChange={handleSortChange}
            onReset={handleResetFilters}
          />
        </div>

        {/* Search Results Display */}
        <section className="lg:col-span-3">
          {error ? (
            <div className="flex flex-col items-center justify-center py-16 px-4 rounded-2xl bg-zinc-950/40 border border-zinc-900/80 text-center backdrop-blur-sm">
              <p className="text-zinc-400 font-medium mb-4">Unable to load products.</p>
              <button
                onClick={() => setRetryTrigger(prev => prev + 1)}
                className="px-6 py-2.5 rounded-xl bg-zinc-900 hover:bg-zinc-800 border border-zinc-800 hover:border-zinc-700 text-sm font-semibold text-white transition-all cursor-pointer active:scale-95"
              >
                Retry
              </button>
            </div>
          ) : (
            <SearchResults
              products={products}
              loading={loading}
              page={urlPage}
              totalPages={totalPages}
              totalElements={totalElements}
              onPageChange={handlePageChange}
              savedProductIds={savedProductIds}
              onToggleSave={handleToggleSave}
            />
          )}
        </section>
      </div>
    </div>
  );
};

export default SearchPage;
