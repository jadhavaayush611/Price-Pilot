import React, { useEffect, useState, useCallback, useRef } from 'react';
import { useSearchParams, useNavigate, Link } from 'react-router-dom';
import { apiService } from '../services/api';
import type { ProductWithPrices, InterpretedQuery } from '../types';
import { SearchBar } from '../components/SearchBar';
import { SearchFilters } from '../components/SearchFilters';
import { SearchResults } from '../components/SearchResults';
import { SlidersHorizontal, Sparkles, X, Filter, ShieldAlert, LogIn } from 'lucide-react';
import { motion, AnimatePresence } from 'framer-motion';
import { useAuth } from '../context/AuthContext';
import { getSavedCurrency, CURRENCY_SYMBOLS } from '../currency';

export const SearchPage: React.FC = () => {
  const [searchParams, setSearchParams] = useSearchParams();
  const navigate = useNavigate();
  const { isAuthenticated, user } = useAuth();
  const currency = getSavedCurrency();
  
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
  const urlPersonalized = searchParams.get('personalized') === 'true';

  const [products, setProducts] = useState<ProductWithPrices[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(false);
  const [retryTrigger, setRetryTrigger] = useState(0);
  const [totalPages, setTotalPages] = useState(0);
  const [totalElements, setTotalElements] = useState(0);
  const [interpretedQuery, setInterpretedQuery] = useState<InterpretedQuery | null>(null);
  const [showMobileFilters, setShowMobileFilters] = useState(false);
  const [savedProductIds, setSavedProductIds] = useState<string[]>([]);

  // Available categories and brands extracted from active search results
  const [availableCategories, setAvailableCategories] = useState<string[]>([]);
  const [availableBrands, setAvailableBrands] = useState<string[]>([]);

  // Race safety and user isolation tracking refs
  const activeRequestRef = useRef<string>('');
  const activeUserIdRef = useRef<string | null>(user?.id || null);

  // Mobile filter sheet refs
  const filterTriggerRef = useRef<HTMLButtonElement>(null);
  const filterDialogRef = useRef<HTMLDivElement>(null);
  const closeButtonRef = useRef<HTMLButtonElement>(null);

  const hasActiveFilters = 
    urlCategory !== 'All' || 
    urlBrand !== 'All' || 
    urlMinPrice !== '' || 
    urlMaxPrice !== '' || 
    urlInStock || 
    urlDealQuality !== 'All' || 
    (urlSort !== 'default' && urlSort !== 'relevance');

  // Active filter count calculated strictly from URL state
  const activeFilterCount = [
    urlCategory !== 'All',
    urlBrand !== 'All',
    urlMinPrice !== '',
    urlMaxPrice !== '',
    urlInStock,
    urlDealQuality !== 'All',
    urlSort !== 'default' && urlSort !== 'relevance'
  ].filter(Boolean).length;

  // Manage background scroll lock and focus restoration when mobile filter sheet opens/closes
  useEffect(() => {
    if (showMobileFilters) {
      const prevOverflow = document.body.style.overflow;
      document.body.style.overflow = 'hidden';

      const timer = setTimeout(() => {
        closeButtonRef.current?.focus();
      }, 50);

      return () => {
        clearTimeout(timer);
        document.body.style.overflow = prevOverflow;
        filterTriggerRef.current?.focus();
      };
    }
  }, [showMobileFilters]);

  // Dialog keydown handler for Escape dismissal and Tab focus trapping
  const handleDialogKeyDown = (e: React.KeyboardEvent) => {
    if (e.key === 'Escape') {
      e.preventDefault();
      setShowMobileFilters(false);
      return;
    }

    if (e.key === 'Tab' && filterDialogRef.current) {
      const focusable = filterDialogRef.current.querySelectorAll<HTMLElement>(
        'button:not([disabled]), [href], input:not([disabled]), select:not([disabled]), textarea:not([disabled]), [tabindex]:not([tabindex="-1"])'
      );
      if (focusable.length === 0) return;

      const first = focusable[0];
      const last = focusable[focusable.length - 1];

      if (e.shiftKey) {
        if (document.activeElement === first) {
          e.preventDefault();
          last.focus();
        }
      } else {
        if (document.activeElement === last) {
          e.preventDefault();
          first.focus();
        }
      }
    }
  };

  // Handle user authentication transitions (login/logout)
  useEffect(() => {
    const currentUserId = user?.id || null;
    if (activeUserIdRef.current !== currentUserId) {
      activeUserIdRef.current = currentUserId;
      if (!isAuthenticated && urlPersonalized) {
        // Reset personalized mode on logout
        const nextParams = new URLSearchParams(searchParams);
        nextParams.delete('personalized');
        setSearchParams(nextParams);
      }
    }
  }, [user, isAuthenticated, urlPersonalized, searchParams, setSearchParams]);

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
    
    // Always reset page to 0 if filters, keyword, personalized, or sort changes
    if (!('page' in newParams)) {
      nextParams.delete('page');
    }

    setSearchParams(nextParams);
  }, [searchParams, setSearchParams]);

  // Fetch paginated, filtered, sorted results from backend via Intelligent Discovery
  useEffect(() => {
    setLoading(true);
    setError(false);

    const requestId = `search-${query}-${urlCategory}-${urlBrand}-${urlMinPrice}-${urlMaxPrice}-${urlInStock}-${urlDealQuality}-${urlSort}-${urlPage}-${urlPersonalized ? 'p' : 'g'}-${Date.now()}`;
    activeRequestRef.current = requestId;

    const minPriceNum = urlMinPrice ? parseFloat(urlMinPrice) : undefined;
    const maxPriceNum = urlMaxPrice ? parseFloat(urlMaxPrice) : undefined;

    const requestParams = {
      query: query || undefined,
      category: urlCategory !== 'All' ? urlCategory : undefined,
      brand: urlBrand !== 'All' ? urlBrand : undefined,
      minPrice: minPriceNum,
      maxPrice: maxPriceNum,
      inStock: urlInStock || undefined,
      dealQuality: urlDealQuality !== 'All' ? urlDealQuality : undefined,
      sort: urlSort,
      page: urlPage,
      size: 6,
      personalized: urlPersonalized && isAuthenticated ? true : undefined,
    };

    const fetchPromise = urlPersonalized && isAuthenticated
      ? apiService.discoverPersonalizedProducts(requestParams)
      : apiService.discoverProducts(requestParams);

    fetchPromise
      .then((data) => {
        if (activeRequestRef.current !== requestId) return;
        setProducts(data.content || []);
        setTotalPages(data.totalPages || 0);
        setTotalElements(data.totalElements || 0);
        setInterpretedQuery(data.interpretedQuery || null);
        if (data.availableCategories && data.availableCategories.length > 0) {
          setAvailableCategories(data.availableCategories);
        }
        if (data.availableBrands && data.availableBrands.length > 0) {
          setAvailableBrands(data.availableBrands);
        }
      })
      .catch((err) => {
        if (activeRequestRef.current !== requestId) return;
        console.warn("Discovery API error, attempting fallback search:", err);
        // If not personalized, attempt fallback to legacy endpoint
        if (!urlPersonalized) {
          apiService.searchProductsWithFilters({
            keyword: query,
            category: urlCategory !== 'All' ? urlCategory : undefined,
            brand: urlBrand !== 'All' ? urlBrand : undefined,
            page: urlPage,
            size: 6,
            sort: urlSort
          })
            .then((fallbackData) => {
              if (activeRequestRef.current !== requestId) return;
              setProducts(fallbackData.content || []);
              setTotalPages(fallbackData.totalPages || 0);
              setTotalElements(fallbackData.totalElements || 0);
            })
            .catch(() => {
              if (activeRequestRef.current === requestId) setError(true);
            });
        } else {
          setError(true);
        }
      })
      .finally(() => {
        if (activeRequestRef.current === requestId) {
          setLoading(false);
        }
      });
  }, [query, urlCategory, urlBrand, urlMinPrice, urlMaxPrice, urlInStock, urlDealQuality, urlPage, urlSort, urlPersonalized, isAuthenticated, retryTrigger]);


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
      {/* Sticky Search Header */}
      <header className="sticky top-16 z-30 backdrop-blur-md bg-[var(--color-background)]/90 py-3 border-b border-[var(--color-border)] -mx-4 px-4 sm:-mx-6 sm:px-6 flex flex-col gap-3 md:flex-row md:items-center md:justify-between transition-all">
        <div>
          <h1 className="text-lg font-bold tracking-tight text-[var(--color-foreground)] m-0">
            {query ? `Search: "${query}"` : 'Discover Products'}
          </h1>
          <p className="text-xs text-[var(--color-muted-foreground)] mt-0.5">
            {urlPersonalized && isAuthenticated
              ? 'Personalized for your preferred brands and budget'
              : 'Find products and compare real-time retailer prices'}
          </p>
        </div>

        <div className="flex flex-col sm:flex-row items-stretch sm:items-center gap-2.5 sm:gap-3">
          {/* Mode Switcher */}
          <div className="flex items-center gap-1 bg-[var(--color-surface)] p-1 rounded-xl border border-[var(--color-border)] shrink-0 self-start sm:self-auto">
            <button
              type="button"
              onClick={() => updateParams({ personalized: null })}
              className={`px-3 py-1.5 text-xs font-semibold rounded-lg transition-all cursor-pointer ${
                !urlPersonalized
                  ? 'bg-[var(--color-surface-accent)] text-[var(--color-foreground)] border border-[var(--color-border)] shadow-inner font-bold'
                  : 'text-[var(--color-muted-foreground)] hover:text-[var(--color-foreground)]'
              }`}
            >
              All Results
            </button>
            <button
              type="button"
              onClick={() => {
                if (!isAuthenticated) {
                  navigate('/login', { state: { from: { pathname: '/search', search: searchParams.toString() } } });
                  return;
                }
                updateParams({ personalized: 'true' });
              }}
              className={`flex items-center gap-1.5 px-3 py-1.5 text-xs font-semibold rounded-lg transition-all cursor-pointer ${
                urlPersonalized
                  ? 'bg-indigo-500/10 text-indigo-600 dark:text-indigo-200 border border-indigo-500/30 shadow-inner font-bold'
                  : 'text-[var(--color-muted-foreground)] hover:text-[var(--color-foreground)]'
              }`}
              title="Personalize discovery results based on your shopping preferences"
            >
              <Sparkles className="h-3 w-3 text-indigo-500 dark:text-indigo-400" />
              <span>Personalized</span>
            </button>
          </div>

          <div className="flex items-center gap-2 w-full sm:w-auto">
            <div className="flex-1 sm:w-64 md:w-80 min-w-0">
              <SearchBar value={query} onChange={handleKeywordChange} />
            </div>

            {/* Mobile Filters Trigger Button */}
            <button
              ref={filterTriggerRef}
              type="button"
              onClick={() => setShowMobileFilters(true)}
              aria-expanded={showMobileFilters}
              aria-controls="mobile-filter-sheet"
              aria-label={activeFilterCount > 0 ? `Filters (${activeFilterCount} active)` : "Filters"}
              className="lg:hidden shrink-0 flex items-center justify-center gap-1.5 px-3.5 py-2.5 bg-[var(--color-surface)] border border-[var(--color-border)] rounded-xl hover:bg-[var(--color-surface-accent)] text-xs font-semibold text-[var(--color-foreground)] cursor-pointer active:scale-95 transition-all focus-visible:ring-2 focus-visible:ring-[var(--color-ring)] focus-visible:outline-none"
            >
              <SlidersHorizontal className="h-3.5 w-3.5 text-emerald-500" aria-hidden="true" />
              <span>Filters</span>
              {activeFilterCount > 0 && (
                <span className="inline-flex items-center justify-center min-w-[18px] h-[18px] px-1 text-[10px] font-bold rounded-full bg-emerald-500 text-white dark:bg-emerald-500 dark:text-zinc-950">
                  {activeFilterCount}
                </span>
              )}
            </button>
          </div>
        </div>
      </header>

      {/* Unauthenticated Personalization Banner */}
      {!isAuthenticated && urlPersonalized && (
        <div className="bg-[var(--color-surface)] border border-[var(--color-border)] rounded-2xl p-4 flex items-center justify-between gap-4">
          <div className="flex items-center gap-3">
            <ShieldAlert className="w-5 h-5 text-indigo-500 dark:text-indigo-400 shrink-0" />
            <div className="text-xs">
              <p className="font-semibold text-[var(--color-foreground)]">Sign in for personalized product discovery</p>
              <p className="text-[var(--color-muted-foreground)]">Ranks results matching your preferred brands, budget, and shopping signals.</p>
            </div>
          </div>
          <Link
            to="/login"
            state={{ from: { pathname: '/search', search: searchParams.toString() } }}
            className="inline-flex items-center gap-1.5 px-3.5 py-1.5 text-xs font-bold text-[var(--color-background)] bg-[var(--color-foreground)] hover:opacity-90 rounded-xl transition-all shrink-0"
          >
            <LogIn className="w-3.5 h-3.5" />
            <span>Sign In</span>
          </Link>
        </div>
      )}


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
          <span className="text-[11px] font-semibold text-[var(--color-muted-foreground)] uppercase tracking-wider flex items-center gap-1">
            <Filter className="h-3 w-3" aria-hidden="true" /> Active:
          </span>
          {urlCategory !== 'All' && (
            <span className="inline-flex items-center gap-1 px-2.5 py-1 rounded-lg bg-[var(--color-surface)] border border-[var(--color-border)] text-xs text-[var(--color-foreground)] shadow-sm">
              Category: {urlCategory}
              <button type="button" onClick={() => handleCategoryChange('All')} className="text-[var(--color-muted-foreground)] hover:text-[var(--color-foreground)]" aria-label="Remove category filter">
                <X className="h-3 w-3" />
              </button>
            </span>
          )}
          {urlBrand !== 'All' && (
            <span className="inline-flex items-center gap-1 px-2.5 py-1 rounded-lg bg-[var(--color-surface)] border border-[var(--color-border)] text-xs text-[var(--color-foreground)] shadow-sm">
              Brand: {urlBrand}
              <button type="button" onClick={() => handleBrandChange('All')} className="text-[var(--color-muted-foreground)] hover:text-[var(--color-foreground)]" aria-label="Remove brand filter">
                <X className="h-3 w-3" />
              </button>
            </span>
          )}
          {urlMinPrice && (
            <span className="inline-flex items-center gap-1 px-2.5 py-1 rounded-lg bg-[var(--color-surface)] border border-[var(--color-border)] text-xs text-[var(--color-foreground)] shadow-sm">
              Min {CURRENCY_SYMBOLS[currency]}{urlMinPrice}
              <button type="button" onClick={() => handleMinPriceChange('')} className="text-[var(--color-muted-foreground)] hover:text-[var(--color-foreground)]" aria-label="Remove min price filter">
                <X className="h-3 w-3" />
              </button>
            </span>
          )}
          {urlMaxPrice && (
            <span className="inline-flex items-center gap-1 px-2.5 py-1 rounded-lg bg-[var(--color-surface)] border border-[var(--color-border)] text-xs text-[var(--color-foreground)] shadow-sm">
              Max {CURRENCY_SYMBOLS[currency]}{urlMaxPrice}
              <button type="button" onClick={() => handleMaxPriceChange('')} className="text-[var(--color-muted-foreground)] hover:text-[var(--color-foreground)]" aria-label="Remove max price filter">
                <X className="h-3 w-3" />
              </button>
            </span>
          )}
          {urlInStock && (
            <span className="inline-flex items-center gap-1 px-2.5 py-1 rounded-lg bg-[var(--color-surface)] border border-[var(--color-border)] text-xs text-[var(--color-foreground)] shadow-sm">
              In Stock Only
              <button type="button" onClick={() => handleInStockToggle(false)} className="text-[var(--color-muted-foreground)] hover:text-[var(--color-foreground)]" aria-label="Remove in-stock filter">
                <X className="h-3 w-3" />
              </button>
            </span>
          )}
          {urlDealQuality !== 'All' && (
            <span className="inline-flex items-center gap-1 px-2.5 py-1 rounded-lg bg-[var(--color-surface)] border border-[var(--color-border)] text-xs text-[var(--color-foreground)] shadow-sm">
              {urlDealQuality === 'EXCELLENT_DEAL' ? 'Excellent Deals' : 'Good Deals'}
              <button type="button" onClick={() => handleDealQualityChange('All')} className="text-[var(--color-muted-foreground)] hover:text-[var(--color-foreground)]" aria-label="Remove deal filter">
                <X className="h-3 w-3" />
              </button>
            </span>
          )}
          <button
            type="button"
            onClick={handleResetFilters}
            className="text-xs font-semibold text-emerald-600 dark:text-emerald-400 hover:underline cursor-pointer ml-1"
          >
            Clear all
          </button>
        </div>
      )}

      {/* Mobile Dedicated Filter Sheet / Modal Dialog */}
      <AnimatePresence>
        {showMobileFilters && (
          <div className="lg:hidden fixed inset-0 z-[60] flex justify-end" onKeyDown={handleDialogKeyDown}>
            {/* Backdrop */}
            <motion.div
              initial={{ opacity: 0 }}
              animate={{ opacity: 1 }}
              exit={{ opacity: 0 }}
              transition={{ duration: 0.2 }}
              onClick={() => setShowMobileFilters(false)}
              className="fixed inset-0 bg-black/60 backdrop-blur-xs"
              aria-hidden="true"
            />

            {/* Slide-over Sheet */}
            <motion.div
              ref={filterDialogRef}
              role="dialog"
              aria-modal="true"
              id="mobile-filter-sheet"
              aria-labelledby="mobile-filter-heading"
              initial={{ x: '100%' }}
              animate={{ x: 0 }}
              exit={{ x: '100%' }}
              transition={{ type: 'spring', damping: 28, stiffness: 300 }}
              className="relative w-full max-w-sm h-full bg-[var(--color-surface)] border-l border-[var(--color-border)] shadow-2xl flex flex-col z-10"
            >
              {/* Sheet Header */}
              <div className="flex items-center justify-between px-5 py-4 border-b border-[var(--color-border)] bg-[var(--color-surface)]/95 backdrop-blur-md shrink-0">
                <div className="flex items-center gap-2">
                  <SlidersHorizontal className="h-4 w-4 text-emerald-500" aria-hidden="true" />
                  <h2 id="mobile-filter-heading" className="text-sm font-bold uppercase tracking-wider text-[var(--color-foreground)]">
                    Filters &amp; Sort
                  </h2>
                  {activeFilterCount > 0 && (
                    <span className="text-xs font-semibold text-emerald-600 dark:text-emerald-400">
                      ({activeFilterCount} active)
                    </span>
                  )}
                </div>

                <div className="flex items-center gap-2">
                  {hasActiveFilters && (
                    <button
                      type="button"
                      onClick={handleResetFilters}
                      className="text-xs font-semibold text-[var(--color-muted-foreground)] hover:text-emerald-500 transition-colors focus-visible:ring-2 focus-visible:ring-[var(--color-ring)] focus-visible:outline-none rounded-lg px-2 py-1 cursor-pointer"
                    >
                      Reset
                    </button>
                  )}
                  <button
                    ref={closeButtonRef}
                    type="button"
                    onClick={() => setShowMobileFilters(false)}
                    aria-label="Close filters"
                    className="p-1.5 rounded-xl text-[var(--color-muted-foreground)] hover:text-[var(--color-foreground)] hover:bg-[var(--color-surface-accent)] focus-visible:ring-2 focus-visible:ring-[var(--color-ring)] focus-visible:outline-none transition-colors cursor-pointer"
                  >
                    <X className="h-5 w-5" />
                  </button>
                </div>
              </div>

              {/* Sheet Body (Scrollable filters) */}
              <div className="flex-1 overflow-y-auto px-5 py-4 overscroll-contain">
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
                  className="flex flex-col gap-6"
                  showTitle={false}
                  idPrefix="mobile"
                />
              </div>

              {/* Sheet Footer */}
              <div className="flex items-center gap-3 p-4 border-t border-[var(--color-border)] bg-[var(--color-surface)]/95 backdrop-blur-md shrink-0">
                {hasActiveFilters && (
                  <button
                    type="button"
                    onClick={handleResetFilters}
                    className="px-4 py-2.5 rounded-xl border border-[var(--color-border)] text-xs font-semibold text-[var(--color-muted-foreground)] hover:text-[var(--color-foreground)] hover:bg-[var(--color-surface-accent)] transition-all cursor-pointer focus-visible:ring-2 focus-visible:ring-[var(--color-ring)] focus-visible:outline-none active:scale-95"
                  >
                    Reset
                  </button>
                )}
                <button
                  type="button"
                  onClick={() => setShowMobileFilters(false)}
                  className="flex-1 py-2.5 px-4 rounded-xl bg-[var(--color-primary)] hover:opacity-90 text-[var(--color-primary-foreground)] text-xs font-bold transition-all shadow-sm active:scale-[0.98] focus-visible:ring-2 focus-visible:ring-[var(--color-ring)] focus-visible:outline-none text-center cursor-pointer"
                >
                  {!loading ? `View ${totalElements} ${totalElements === 1 ? 'Result' : 'Results'}` : 'Apply Filters'}
                </button>
              </div>
            </motion.div>
          </div>
        )}
      </AnimatePresence>

      {/* Main Responsive Grid Layout */}
      <div className="grid grid-cols-1 lg:grid-cols-4 gap-8 items-start mt-2">
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
            idPrefix="desktop"
          />
        </div>

        {/* Search Results Display */}
        <section className="lg:col-span-3" aria-label="Search Results">
          {/* Result Count Banner */}
          {!loading && !error && (
            <div className="flex items-center justify-between pb-3 text-xs text-[var(--color-muted-foreground)] border-b border-[var(--color-border)] mb-6">
              <div>
                Found <span className="font-semibold text-[var(--color-foreground)]">{totalElements}</span> matching {totalElements === 1 ? 'product' : 'products'}
              </div>
            </div>
          )}

          {error ? (
            <div className="flex flex-col items-center justify-center py-16 px-4 rounded-2xl bg-[var(--color-surface)] border border-[var(--color-border)] text-center backdrop-blur-sm shadow-sm">
              <p className="text-[var(--color-foreground)] font-medium mb-4">Unable to load discovery search results.</p>
              <button
                type="button"
                onClick={() => setRetryTrigger(prev => prev + 1)}
                className="px-6 py-2.5 rounded-xl bg-[var(--color-primary)] hover:opacity-90 text-sm font-semibold text-[var(--color-primary-foreground)] transition-all cursor-pointer active:scale-95 shadow-sm"
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
              isPersonalized={urlPersonalized && isAuthenticated}
            />
          )}
        </section>
      </div>
    </div>
  );
};

export default SearchPage;
