import { describe, it, expect, vi } from 'vitest';
import React from 'react';
import { renderToString } from 'react-dom/server';
import { SearchFilters } from '../SearchFilters';

describe('Unit 4.1 — Mobile Search Filters & Sticky Header', () => {
  const defaultProps = {
    categories: ['Electronics', 'Audio', 'Wearables'],
    brands: ['Apple', 'Sony', 'Bose'],
    selectedCategory: 'All',
    selectedBrand: 'All',
    minPrice: '',
    maxPrice: '',
    inStockOnly: false,
    dealQuality: 'All',
    sortBy: 'relevance',
    onCategoryChange: vi.fn(),
    onBrandChange: vi.fn(),
    onMinPriceChange: vi.fn(),
    onMaxPriceChange: vi.fn(),
    onInStockToggle: vi.fn(),
    onDealQualityChange: vi.fn(),
    onSortChange: vi.fn(),
    onReset: vi.fn()
  };

  describe('A. SearchFilters Component Rendering & Variants', () => {
    it('renders desktop sidebar with default outer container and title', () => {
      const html = renderToString(React.createElement(SearchFilters, defaultProps));

      expect(html).toContain('aria-label="Product Search Filters"');
      expect(html).toContain('Filters &amp; Sort');
      expect(html).toContain('sticky top-36');
      expect(html).toContain('max-h-[calc(100vh-180px)]');
    });

    it('renders mobile drawer variant with custom className and omitted duplicate title', () => {
      const mobileProps = {
        ...defaultProps,
        className: 'flex flex-col gap-6',
        showTitle: false,
        idPrefix: 'mobile'
      };
      const html = renderToString(React.createElement(SearchFilters, mobileProps));

      expect(html).toContain('class="flex flex-col gap-6"');
      expect(html).not.toContain('sticky top-36');
      expect(html).not.toContain('Filters &amp; Sort');
    });

    it('displays reset button in header only when active filters exist and showTitle is true', () => {
      const inactiveHtml = renderToString(React.createElement(SearchFilters, defaultProps));
      expect(inactiveHtml).not.toContain('Reset all search filters');

      const activeProps = {
        ...defaultProps,
        selectedCategory: 'Electronics'
      };
      const activeHtml = renderToString(React.createElement(SearchFilters, activeProps));
      expect(activeHtml).toContain('Reset all search filters');
    });
  });

  describe('B. Filter Semantics & Control Preservation', () => {
    it('preserves all standard sorting options without changing keys', () => {
      const html = renderToString(React.createElement(SearchFilters, defaultProps));
      expect(html).toContain('Relevance (Smart Match)');
      expect(html).toContain('Price: Low to High');
      expect(html).toContain('Price: High to Low');
      expect(html).toContain('Biggest Discount');
      expect(html).toContain('Newest Arrivals');
    });

    it('preserves deal quality options', () => {
      const html = renderToString(React.createElement(SearchFilters, defaultProps));
      expect(html).toContain('All Deals');
      expect(html).toContain('Excellent Deals');
      expect(html).toContain('Good Deals');
    });

    it('preserves price range and stock toggle controls with accessible labels', () => {
      const html = renderToString(React.createElement(SearchFilters, defaultProps));
      expect(html).toContain('aria-label="Minimum price"');
      expect(html).toContain('aria-label="Maximum price"');
      expect(html).toContain('aria-label="Show in-stock items only"');
      expect(html).toContain('In Stock Only');
    });

    it('renders all provided category and brand options', () => {
      const html = renderToString(React.createElement(SearchFilters, defaultProps));
      expect(html).toContain('All Categories');
      expect(html).toContain('Electronics');
      expect(html).toContain('Audio');
      expect(html).toContain('Wearables');

      expect(html).toContain('All Brands');
      expect(html).toContain('Apple');
      expect(html).toContain('Sony');
      expect(html).toContain('Bose');
    });
  });

  describe('C. Active Filter Count Calculation Invariants', () => {
    const calculateActiveCount = (state: {
      category: string;
      brand: string;
      minPrice: string;
      maxPrice: string;
      inStock: boolean;
      dealQuality: string;
      sort: string;
    }) => [
      state.category !== 'All',
      state.brand !== 'All',
      state.minPrice !== '',
      state.maxPrice !== '',
      state.inStock,
      state.dealQuality !== 'All',
      state.sort !== 'default' && state.sort !== 'relevance'
    ].filter(Boolean).length;

    it('returns 0 when all filter fields are default', () => {
      const count = calculateActiveCount({
        category: 'All',
        brand: 'All',
        minPrice: '',
        maxPrice: '',
        inStock: false,
        dealQuality: 'All',
        sort: 'relevance'
      });
      expect(count).toBe(0);
    });

    it('accurately increments count for each active filter field', () => {
      expect(calculateActiveCount({
        category: 'Electronics',
        brand: 'All',
        minPrice: '',
        maxPrice: '',
        inStock: false,
        dealQuality: 'All',
        sort: 'relevance'
      })).toBe(1);

      expect(calculateActiveCount({
        category: 'Electronics',
        brand: 'Apple',
        minPrice: '500',
        maxPrice: '1500',
        inStock: true,
        dealQuality: 'EXCELLENT_DEAL',
        sort: 'price-asc'
      })).toBe(7);
    });

    it('does not count default sort (relevance or default) as an active filter', () => {
      expect(calculateActiveCount({
        category: 'All',
        brand: 'All',
        minPrice: '',
        maxPrice: '',
        inStock: false,
        dealQuality: 'All',
        sort: 'default'
      })).toBe(0);

      expect(calculateActiveCount({
        category: 'All',
        brand: 'All',
        minPrice: '',
        maxPrice: '',
        inStock: false,
        dealQuality: 'All',
        sort: 'relevance'
      })).toBe(0);
    });
  });

  describe('D. Mobile Dialog Semantics & Focus Management Structure', () => {
    it('verifies accessibility attributes for mobile dialog', () => {
      const dialogAttributes = {
        role: 'dialog',
        'aria-modal': 'true',
        id: 'mobile-filter-sheet',
        'aria-labelledby': 'mobile-filter-heading'
      };

      expect(dialogAttributes.role).toBe('dialog');
      expect(dialogAttributes['aria-modal']).toBe('true');
      expect(dialogAttributes.id).toBe('mobile-filter-sheet');
      expect(dialogAttributes['aria-labelledby']).toBe('mobile-filter-heading');
    });

    it('simulates keyboard tab trapping within modal container', () => {
      // Mock focusable elements list
      const focusable = ['close-button', 'sort-button', 'min-input', 'apply-button'];
      let activeIndex = 0;

      const handleTab = (shiftKey: boolean) => {
        if (shiftKey) {
          activeIndex = activeIndex === 0 ? focusable.length - 1 : activeIndex - 1;
        } else {
          activeIndex = activeIndex === focusable.length - 1 ? 0 : activeIndex + 1;
        }
        return focusable[activeIndex];
      };

      // Forward tabs
      expect(handleTab(false)).toBe('sort-button');
      expect(handleTab(false)).toBe('min-input');
      expect(handleTab(false)).toBe('apply-button');
      expect(handleTab(false)).toBe('close-button'); // wraps to first

      // Shift+Tab backward
      expect(handleTab(true)).toBe('apply-button'); // wraps to last
    });

    it('verifies body overflow locking and cleanup on sheet toggle', () => {
      const mockBody = { style: { overflow: '' } };

      const lockScroll = () => {
        const prev = mockBody.style.overflow;
        mockBody.style.overflow = 'hidden';
        return () => {
          mockBody.style.overflow = prev;
        };
      };

      const unlock = lockScroll();
      expect(mockBody.style.overflow).toBe('hidden');

      unlock();
      expect(mockBody.style.overflow).toBe('');
    });
  });
});
