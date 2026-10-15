import { describe, it, expect, vi } from 'vitest';
import React from 'react';
import { renderToString } from 'react-dom/server';
import { ComparisonTable } from './ComparisonTable';
import { ComparisonRowItem } from './ComparisonRowItem';
import { ComparisonSelector } from './ComparisonSelector';
import type { ComparisonResponse, ComparisonRow, ProductScore, ProductWithPrices } from '../../types';

describe('Unit 4.2 — Responsive Comparison Experience', () => {
  const mockProductA: ProductWithPrices = {
    id: '11111111-1111-1111-1111-111111111111',
    name: 'Flagship Headphones ANC-X',
    brand: 'AudioPro',
    description: 'High performance noise cancelling headphones',
    imageUrl: 'https://images.unsplash.com/photo-1505740420928-5e560c06d30e?w=200',
    category: 'Audio',
    lowestPrice: 299.99,
    prices: [],
    rating: 4.8,
    reviewCount: 1250,
    inStock: true
  };

  const mockProductB: ProductWithPrices = {
    id: '22222222-2222-2222-2222-222222222222',
    name: 'Wireless Studio Monitor Pro',
    brand: 'SoundCraft',
    description: 'Professional reference monitoring headphones',
    imageUrl: 'https://images.unsplash.com/photo-1583394838336-acd977736f90?w=200',
    category: 'Audio',
    lowestPrice: 249.50,
    prices: [],
    rating: 4.5,
    reviewCount: 840,
    inStock: true
  };

  const mockProductC: ProductWithPrices = {
    id: '33333333-3333-3333-3333-333333333333',
    name: 'Earbuds Elite Wireless',
    brand: 'AudioPro',
    description: 'True wireless earbuds with charging case',
    imageUrl: 'https://images.unsplash.com/photo-1590658268037-6bf12165a8df?w=200',
    category: 'Audio',
    lowestPrice: 159.00,
    prices: [],
    rating: 4.6,
    reviewCount: 420,
    inStock: true
  };

  const mockScores: Record<string, ProductScore> = {
    '11111111-1111-1111-1111-111111111111': {
      productId: '11111111-1111-1111-1111-111111111111',
      productName: 'Flagship Headphones ANC-X',
      overallScore: 92,
      priceValueScore: 85,
      featureScore: 95,
      popularityScore: 90,
      breakdown: {},
      recommendationBadge: 'Top Pick'
    },
    '22222222-2222-2222-2222-222222222222': {
      productId: '22222222-2222-2222-2222-222222222222',
      productName: 'Wireless Studio Monitor Pro',
      overallScore: 86,
      priceValueScore: 88,
      featureScore: 84,
      popularityScore: 82,
      breakdown: {},
      recommendationBadge: 'Runner Up'
    },
    '33333333-3333-3333-3333-333333333333': {
      productId: '33333333-3333-3333-3333-333333333333',
      productName: 'Earbuds Elite Wireless',
      overallScore: 79,
      priceValueScore: 95,
      featureScore: 70,
      popularityScore: 75,
      breakdown: {},
      recommendationBadge: 'Budget Pick'
    }
  };

  const mockRows: ComparisonRow[] = [
    {
      featureName: 'Battery Life',
      category: 'Specifications',
      valuesByProductId: {
        '11111111-1111-1111-1111-111111111111': '30 hours',
        '22222222-2222-2222-2222-222222222222': '24 hours',
        '33333333-3333-3333-3333-333333333333': '8 hours (32h with case)'
      },
      isHighlight: true,
      highlightedProductIds: ['11111111-1111-1111-1111-111111111111']
    },
    {
      featureName: 'Active Noise Cancellation',
      category: 'Features',
      valuesByProductId: {
        '11111111-1111-1111-1111-111111111111': 'Adaptive ANC',
        '22222222-2222-2222-2222-222222222222': 'Hybrid ANC',
        '33333333-3333-3333-3333-333333333333': 'Standard ANC'
      },
      isHighlight: false,
      highlightedProductIds: []
    }
  ];

  const mockComparisonData: ComparisonResponse = {
    comparisonId: 'comp-abc-123',
    products: [mockProductA, mockProductB, mockProductC],
    rows: mockRows,
    scores: mockScores,
    summary: 'Flagship Headphones ANC-X delivers the best overall score with highest battery life and ratings.',
    createdAt: new Date().toISOString()
  };

  describe('A. Comparison Table Scrollable Region & Accessibility', () => {
    it('renders scrollable table container with keyboard accessibility and semantic attributes', () => {
      const html = renderToString(
        React.createElement(ComparisonTable, {
          comparison: mockComparisonData
        })
      );

      // Semantic region attributes
      expect(html).toContain('role="region"');
      expect(html).toContain('tabindex="0"');
      expect(html).toContain('aria-label="Product comparison table, scroll horizontally for more products"');
      // Scroll containment and styling
      expect(html).toContain('overflow-x-auto');
      expect(html).toContain('overscroll-x-contain');
    });

    it('renders mobile scroll hint with product count on small screens', () => {
      const html = renderToString(
        React.createElement(ComparisonTable, {
          comparison: mockComparisonData
        })
      );

      expect(html).toContain('sm:hidden flex items-center justify-between');
      expect(html).toContain('Scroll horizontally to compare all');
      expect(html).toContain('Products');
    });

    it('uses semantic table headers with scope="col" for products and sticky left header', () => {
      const html = renderToString(
        React.createElement(ComparisonTable, {
          comparison: mockComparisonData
        })
      );

      expect(html).toContain('scope="col"');
      expect(html).toContain('sticky left-0');
      expect(html).toContain('w-32 sm:w-48 md:w-56');
    });

    it('sets responsive min-width on product columns to prevent cramped columns on narrow screens', () => {
      const html = renderToString(
        React.createElement(ComparisonTable, {
          comparison: mockComparisonData
        })
      );

      expect(html).toContain('min-w-[180px] sm:min-w-[220px]');
    });
  });

  describe('B. Direct Product Removal in Comparison Table', () => {
    it('renders removal button on each product column when more than 1 product is compared', () => {
      const onRemoveMock = vi.fn();
      const html = renderToString(
        React.createElement(ComparisonTable, {
          comparison: mockComparisonData,
          onRemoveProduct: onRemoveMock
        })
      );

      expect(html).toContain('aria-label="Remove Flagship Headphones ANC-X from comparison"');
      expect(html).toContain('aria-label="Remove Wireless Studio Monitor Pro from comparison"');
      expect(html).toContain('aria-label="Remove Earbuds Elite Wireless from comparison"');
    });

    it('omits removal buttons when only 1 product remains in comparison', () => {
      const singleProductComparison: ComparisonResponse = {
        ...mockComparisonData,
        products: [mockProductA]
      };

      const html = renderToString(
        React.createElement(ComparisonTable, {
          comparison: singleProductComparison,
          onRemoveProduct: vi.fn()
        })
      );

      expect(html).not.toContain('aria-label="Remove Flagship Headphones ANC-X from comparison"');
    });
  });

  describe('C. Semantic Comparison Row Item & Badge Wrapping', () => {
    const sampleRow: ComparisonRow = {
      featureName: 'Lowest Listed Price',
      category: 'Pricing',
      rowType: 'LOWEST_PRICE',
      valuesByProductId: {
        '11111111-1111-1111-1111-111111111111': '$299.99',
        '22222222-2222-2222-2222-222222222222': '$249.50'
      },
      isHighlight: true,
      highlightedProductIds: ['22222222-2222-2222-2222-222222222222']
    };

    it('renders feature title using semantic th element with scope="row"', () => {
      const html = renderToString(
        React.createElement(
          'tbody',
          null,
          React.createElement(ComparisonRowItem, {
            row: sampleRow,
            products: [mockProductA, mockProductB]
          })
        )
      );

      expect(html).toContain('<th scope="row"');
      expect(html).toContain('sticky left-0');
      expect(html).toContain('w-32 sm:w-48 md:w-56');
      expect(html).toContain('Lowest Listed Price');
    });

    it('wraps feature highlight badge cleanly to prevent overflow', () => {
      const html = renderToString(
        React.createElement(
          'tbody',
          null,
          React.createElement(ComparisonRowItem, {
            row: sampleRow,
            products: [mockProductA, mockProductB]
          })
        )
      );

      expect(html).toContain('flex-wrap gap-1');
      expect(html).toContain('Best Price');
    });

    it('applies responsive min-width and padding to product data cells', () => {
      const html = renderToString(
        React.createElement(
          'tbody',
          null,
          React.createElement(ComparisonRowItem, {
            row: sampleRow,
            products: [mockProductA, mockProductB]
          })
        )
      );

      expect(html).toContain('min-w-[180px] sm:min-w-[200px]');
      expect(html).toContain('py-3 sm:py-3.5 px-3 sm:px-4');
    });
  });

  describe('D. ComparisonSelector Responsive Layout & Toolbar', () => {
    const mockAvailable = [mockProductA, mockProductB, mockProductC];

    it('renders search input and clear button responsive layout container', () => {
      const html = renderToString(
        React.createElement(ComparisonSelector, {
          availableProducts: mockAvailable,
          selectedIds: [mockProductA.id, mockProductB.id],
          onSelect: vi.fn()
        })
      );

      expect(html).toContain('w-full sm:w-auto');
      expect(html).toContain('flex-1 sm:w-64 min-w-0');
      expect(html).toContain('Clear Selection');
    });

    it('preserves selection count indicator and max 5 limit notice', () => {
      const html = renderToString(
        React.createElement(ComparisonSelector, {
          availableProducts: mockAvailable,
          selectedIds: [mockProductA.id, mockProductB.id],
          onSelect: vi.fn()
        })
      );

      expect(html).toContain('/5 Selected');
    });
  });

  describe('E. Data & Invariant Preservation', () => {
    it('preserves complete summary narrative and AI matrix scores', () => {
      const html = renderToString(
        React.createElement(ComparisonTable, {
          comparison: mockComparisonData
        })
      );

      expect(html).toContain('Comparison Intelligence Summary');
      expect(html).toContain(mockComparisonData.summary);
      expect(html).toContain('Top Pick');
      expect(html).toContain('92.0');
      expect(html).toContain('/100');
    });

    it('displays product recommendation badges based on comparison scores', () => {
      const html = renderToString(
        React.createElement(ComparisonTable, {
          comparison: mockComparisonData
        })
      );

      expect(html).toContain('Top Pick');
      expect(html).toContain('Runner Up');
      expect(html).toContain('Budget Pick');
    });
  });
});
