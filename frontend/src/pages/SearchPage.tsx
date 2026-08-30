import React, { useEffect, useState, useCallback } from 'react';
import { useSearchParams, useNavigate } from 'react-router-dom';
import { apiService } from '../services/api';
import type { ProductWithPrices, InterpretedQuery } from '../types';
import { SearchBar } from '../components/SearchBar';
import { SearchFilters } from '../components/SearchFilters';
import { SearchResults } from '../components/SearchResults';
import { SlidersHorizontal, Sparkles, X, Filter } from 'lucide-react';
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
  const urlMinPrice = searchParams.get('minPrice') || '';
  const urlMaxPrice = searchParams.get('maxPrice') || '';
  const urlInStock = searchParams.get('inStock') === 'true';
  const urlDealQuality = searchParams.get('dealQuality') || 'All';
  const urlPage = parseInt(searchParams.get('page') || '0', 10);
  const urlSort = searchParams.get('sort') || 'relevance';

  const [products, setProducts] = useState<ProductWithPrices[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(false);
  const [retryTrigger, setRetryTrigger] = useState(0);
  const [totalPages, setTotalPages] = useState(0);
  const [totalElements, setTotalElements] = useState(0);
  const [executionTimeMs, setExecutionTimeMs] = useState<number | null>(null);
  const [interpretedQuery, setInterpretedQuery] = useState<InterpretedQuery | null>(null);
  const [showMobileFilters, setShowMobileFilters] = useState(false);
  const [savedProductIds, setSavedProductIds] = useState<string[]>([]);

  // Available categories and brands extracted from active search results
  const [availableCategories, setAvailableCategories] = useState<string[]>([]);
  const [availableBrands, setAvailableBrands] = useState<string[]>([]);

  const hasActiveFilters = 
    urlCategory !== 'All' || 
    urlBrand !== 'All' || 
    urlMinPrice !== '' || 
    urlMaxPrice !== '' || 
    urlInStock || 
    urlDealQuality !== 'All' || 
    (urlSort !== 'default' && urlSort !== 'relevance');

  // Update URL parameters helper in a clean, immutable way
  const updateParams = useCallback((newParams: Record<string, string | number | boolean | null>) => {
    const nextParams = new URLSearchParams(searchParams);
    
    // Clear q parameter in favor of keyword parameter
    nextParams.delete('q');

    Object.entries(newParams).forEach(([key, val]) => {
      if (val === null || val === 'All' || val === '' || val === false) {
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

  // Fetch paginated, filtered, sorted results from backend via Intelligent Discovery
  useEffect(() => {
    let active = true;
    setLoading(true);
    setError(false);

    const minPriceNum = urlMinPrice ? parseFloat(urlMinPrice) : undefined;
    const maxPriceNum = urlMaxPrice ? parseFloat(urlMaxPrice) : undefined;

    apiService.discoverProducts({
      query: query || undefined,
      category: urlCategory !== 'All' ? urlCategory : undefined,
      brand: urlBrand !== 'All' ? urlBrand : undefined,
      minPrice: minPriceNum,
      maxPrice: maxPriceNum,
      inStock: urlInStock || undefined,
      dealQuality: urlDealQuality !== 'All' ? urlDealQuality : undefined,
      sort: urlSort,
      page: urlPage,
      size: 6
    })
      .then((data) => {
        if (!active) return;
        setProducts(data.content || []);
        setTotalPages(data.totalPages || 0);
        setTotalElements(data.totalElements || 0);
        setExecutionTimeMs(data.executionTimeMs || null);
        setInterpretedQuery(data.interpretedQuery || null);
        if (data.availableCategories && data.availableCategories.length > 0) {
          setAvailableCategories(data.availableCategories);
        }
        if (data.availableBrands && data.availableBrands.length > 0) {
          setAvailableBrands(data.availableBrands);
        }
      })
      .catch((err) => {
        console.warn("Discovery API error, falling back to standard search:", err);
        // Fallback to legacy endpoint if discovery endpoint encountered an unexpected error
        apiService.searchProductsWithFilters({
          keyword: query,
          category: urlCategory !== 'All' ? urlCategory : undefined,
          brand: urlBrand !== 'All' ? urlBrand : undefined,
          page: urlPage,
          size: 6,
          sort: urlSort
        })
          .then((fallbackData) => {
            if (!active) return;
            setProducts(fallbackData.content || []);
            setTotalPages(fallbackData.totalPages || 0);
            setTotalElements(fallbackData.totalElements || 0);
          })
          .catch(() => {
            if (active) setError(true);
          });
      })
      .finally(() => {
        if (active) setLoading(false);
      });

    return () => {
      active = false;
    };
  }, [query, urlCategory, urlBrand, urlMinPrice, urlMaxPrice, urlInStock, urlDealQuality, urlPage, urlSort, retryTrigger]);

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

  const handleMinPriceChange = (val: string) => {
    updateParams({ minPrice: val });
  };

  const handleMaxPriceChange = (val: string) => {
    updateParams({ maxPrice: val });
  };

  const handleInStockToggle = (checked: boolean) => {
    updateParams({ inStock: checked });
  };

  const handleDealQualityChange = (quality: string) => {
    updateParams({ dealQuality: quality });
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
      minPrice: null,
      maxPrice: null,
      inStock: null,
      dealQuality: null,
      sort: null,
      page: null
    });
  };

  return (
    <div className="flex flex-col gap-6 py-2">
      {/* Sticky Glassmorphic Search Header */}
      <header className="sticky top-16 z-30 backdrop-blur-md bg-[#030303]/85 py-4 border-b border-zinc-900/60 -mx-4 px-4 sm:-mx-6 sm:px-6 flex flex-col gap-4 md:flex-row md:items-center md:justify-between transition-all">
        <div>
          <div className="flex items-center gap-2">
            <h1 className="text-xl font-bold tracking-tight text-white m-0">
              {query ? `Search: "${query}"` : 'Intelligent Product Discovery'}
            </h1>
            <span className="px-2 py-0.5 rounded-full text-[10px] font-semibold bg-emerald-500/10 text-emerald-400 border border-emerald-500/20">
              AI Discovery
            </span>
          </div>
          <p className="text-xs text-zinc-400 mt-0.5">
            Deterministic relevance scoring and real-time deal intelligence
          </p>
        </div>

        <div className="w-full md:max-w-md flex flex-col sm:flex-row items-stretch sm:items-center gap-3">
          <div className="flex-grow">
            <SearchBar value={query} onChange={handleKeywordChange} />
          </div>
          
          {/* Mobile Filters Toggle Button */}
          <button
            type="button"
            onClick={() => setShowMobileFilters(!showMobileFilters)}
            aria-expanded={showMobileFilters}
            className="lg:hidden flex items-center justify-center gap-2 px-4 py-2.5 bg-zinc-950 border border-zinc-900 rounded-xl hover:border-zinc-800 text-sm font-semibold text-zinc-300 cursor-pointer active:scale-95 transition-all"
          >
            <SlidersHorizontal className="h-4 w-4" aria-hidden="true" />
            <span>Filters</span>
            {hasActiveFilters && (
              <span className="h-1.5 w-1.5 rounded-full bg-emerald-400 animate-pulse" />
            )}
          </button>
        </div>
      </header>

      {/* Query Understanding / Interpretation Banner */}
      {interpretedQuery && interpretedQuery.interpretationNotes && interpretedQuery.interpretationNotes.length > 0 && (
        <div className="flex items-center gap-2 px-4 py-2.5 rounded-xl bg-emerald-950/20 border border-emerald-900/40 text-xs text-emerald-300">
          <Sparkles className="h-4 w-4 text-emerald-400 flex-shrink-0" aria-hidden="true" />
          <div className="flex flex-wrap items-center gap-2">
            <span className="font-semibold">Query intent detected:</span>
            {interpretedQuery.interpretationNotes.map((note, idx) => (
              <span key={idx} className="px-2 py-0.5 rounded bg-emerald-900/40 text-[11px] font-medium border border-emerald-800/50">
                {note}
              </span>
            ))}
          </div>
        </div>
      )}

      {/* Active Filter Chips */}
      {hasActiveFilters && (
        <div className="flex flex-wrap items-center gap-2">
          <span className="text-[11px] font-semibold text-zinc-500 uppercase tracking-wider flex items-center gap-1">
            <Filter className="h-3 w-3" aria-hidden="true" /> Active:
          </span>
          {urlCategory !== 'All' && (
            <span className="inline-flex items-center gap-1 px-2.5 py-1 rounded-lg bg-zinc-900 border border-zinc-800 text-xs text-zinc-300">
              Category: {urlCategory}
              <button type="button" onClick={() => handleCategoryChange('All')} className="text-zinc-500 hover:text-white" aria-label="Remove category filter">
                <X className="h-3 w-3" />
              </button>
            </span>
          )}
          {urlBrand !== 'All' && (
            <span className="inline-flex items-center gap-1 px-2.5 py-1 rounded-lg bg-zinc-900 border border-zinc-800 text-xs text-zinc-300">
              Brand: {urlBrand}
              <button type="button" onClick={() => handleBrandChange('All')} className="text-zinc-500 hover:text-white" aria-label="Remove brand filter">
                <X className="h-3 w-3" />
              </button>
            </span>
          )}
          {urlMinPrice && (
            <span className="inline-flex items-center gap-1 px-2.5 py-1 rounded-lg bg-zinc-900 border border-zinc-800 text-xs text-zinc-300">
              Min ${urlMinPrice}
              <button type="button" onClick={() => handleMinPriceChange('')} className="text-zinc-500 hover:text-white" aria-label="Remove min price filter">
                <X className="h-3 w-3" />
              </button>
            </span>
          )}
          {urlMaxPrice && (
            <span className="inline-flex items-center gap-1 px-2.5 py-1 rounded-lg bg-zinc-900 border border-zinc-800 text-xs text-zinc-300">
              Max ${urlMaxPrice}
              <button type="button" onClick={() => handleMaxPriceChange('')} className="text-zinc-500 hover:text-white" aria-label="Remove max price filter">
                <X className="h-3 w-3" />
              </button>
            </span>
          )}
          {urlInStock && (
            <span className="inline-flex items-center gap-1 px-2.5 py-1 rounded-lg bg-zinc-900 border border-zinc-800 text-xs text-zinc-300">
              In Stock Only
              <button type="button" onClick={() => handleInStockToggle(false)} className="text-zinc-500 hover:text-white" aria-label="Remove in-stock filter">
                <X className="h-3 w-3" />
              </button>
            </span>
          )}
          {urlDealQuality !== 'All' && (
            <span className="inline-flex items-center gap-1 px-2.5 py-1 rounded-lg bg-zinc-900 border border-zinc-800 text-xs text-zinc-300">
              {urlDealQuality === 'EXCELLENT_DEAL' ? 'Excellent Deals' : 'Good Deals'}
              <button type="button" onClick={() => handleDealQualityChange('All')} className="text-zinc-500 hover:text-white" aria-label="Remove deal filter">
                <X className="h-3 w-3" />
              </button>
            </span>
          )}
          <button
            type="button"
            onClick={handleResetFilters}
            className="text-xs font-semibold text-emerald-400 hover:underline cursor-pointer ml-1"
          >
            Clear all
          </button>
        </div>
      )}

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
                minPrice={urlMinPrice}
                maxPrice={urlMaxPrice}
                inStockOnly={urlInStock}
                dealQuality={urlDealQuality}
                sortBy={urlSort}
                onCategoryChange={handleCategoryChange}
                onBrandChange={handleBrandChange}
                onMinPriceChange={handleMinPriceChange}
                onMaxPriceChange={handleMaxPriceChange}
                onInStockToggle={handleInStockToggle}
                onDealQualityChange={handleDealQualityChange}
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
            minPrice={urlMinPrice}
            maxPrice={urlMaxPrice}
            inStockOnly={urlInStock}
            dealQuality={urlDealQuality}
            sortBy={urlSort}
            onCategoryChange={handleCategoryChange}
            onBrandChange={handleBrandChange}
            onMinPriceChange={handleMinPriceChange}
            onMaxPriceChange={handleMaxPriceChange}
            onInStockToggle={handleInStockToggle}
            onDealQualityChange={handleDealQualityChange}
            onSortChange={handleSortChange}
            onReset={handleResetFilters}
          />
        </div>

        {/* Search Results Display */}
        <section className="lg:col-span-3" aria-label="Search Results">
          {/* Result Count and Latency Banner */}
          {!loading && !error && (
            <div className="flex items-center justify-between pb-3 text-xs text-zinc-400 border-b border-zinc-900/60 mb-6">
              <div>
                Found <span className="font-semibold text-white">{totalElements}</span> matching {totalElements === 1 ? 'product' : 'products'}
              </div>
              {executionTimeMs !== null && (
                <div className="text-[11px] text-zinc-500">
                  Discovery latency: <span className="font-mono text-zinc-400">{executionTimeMs}ms</span>
                </div>
              )}
            </div>
          )}

          {error ? (
            <div className="flex flex-col items-center justify-center py-16 px-4 rounded-2xl bg-zinc-950/40 border border-zinc-900/80 text-center backdrop-blur-sm">
              <p className="text-zinc-300 font-medium mb-4">Unable to load discovery search results.</p>
              <button
                type="button"
                onClick={() => setRetryTrigger(prev => prev + 1)}
                className="px-6 py-2.5 rounded-xl bg-zinc-900 hover:bg-zinc-800 border border-zinc-800 hover:border-zinc-700 text-sm font-semibold text-white transition-all cursor-pointer active:scale-95"
              >
                Retry Search
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
