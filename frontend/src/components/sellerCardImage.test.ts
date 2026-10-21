import { describe, it, expect } from 'vitest';
import React from 'react';
import { renderToString } from 'react-dom/server';
import { SellerCard } from './SellerCard';
import type { NormalizedOffer, ProductPrice, Product } from '../types';

describe('SellerCard Product Image Context Verification', () => {
  const baseOffer: NormalizedOffer = {
    id: 'test-offer-1',
    productId: 'prod-101',
    productTitle: 'Apple iPhone 15 Pro',
    source: {
      sellerId: 'amazon-in',
      sellerName: 'Amazon India',
      websiteUrl: 'https://amazon.in',
      logoUrl: 'https://images.example.com/sellers/amazon.png',
      offerUrl: 'https://amazon.in/dp/iphone15pro',
    },
    pricing: {
      basePrice: 124900,
      originalPrice: 134900,
      discountPercentage: 7.4,
      currency: 'INR',
      isBasePriceOnly: true,
      pricingDisclaimer: 'Listed base price.',
    },
    provenance: {
      sourceType: 'live',
      verificationStatus: 'verified',
      isLiveVerified: true,
      observedAt: '2026-10-18T10:00:00Z',
      isStale: false,
      trustLabel: 'Live Verified',
      trustDescription: 'Verified direct feed.',
    },
    availability: {
      status: 'in_stock',
      isAvailable: true,
    },
    isBestDeal: true,
    savings: 10000,
    priceDifferenceFromLowest: 0,
  };

  const sampleProduct: Product = {
    id: 'prod-101',
    name: 'Apple iPhone 15 Pro',
    brand: 'Apple',
    description: 'Flagship smartphone with titanium design and A17 Pro chip.',
    category: 'Smartphones',
    imageUrl: 'https://images.example.com/products/iphone15pro.jpg',
  };

  it('renders product image when productImageUrl is passed explicitly', () => {
    const html = renderToString(
      React.createElement(SellerCard, {
        offer: baseOffer,
        isBestDeal: true,
        lowestPrice: 124900,
        productImageUrl: 'https://images.example.com/products/iphone15pro.jpg',
        productCategory: 'Smartphones',
        productName: 'Apple iPhone 15 Pro',
      })
    );

    // Product image element
    expect(html).toContain('src="https://images.example.com/products/iphone15pro.jpg"');
    expect(html).toContain('alt="Apple iPhone 15 Pro"');

    // Seller logo remains intact
    expect(html).toContain('src="https://images.example.com/sellers/amazon.png"');
    expect(html).toContain('alt="Amazon India"');
  });

  it('renders product image when full product prop is passed', () => {
    const html = renderToString(
      React.createElement(SellerCard, {
        offer: baseOffer,
        isBestDeal: true,
        lowestPrice: 124900,
        product: sampleProduct,
      })
    );

    expect(html).toContain('src="https://images.example.com/products/iphone15pro.jpg"');
    expect(html).toContain('alt="Apple iPhone 15 Pro"');
  });

  it('renders product image when price entity with populated product is passed', () => {
    const samplePrice: ProductPrice = {
      id: 'price-1',
      productId: 'prod-101',
      sellerId: 's1',
      currentPrice: 124900,
      originalPrice: 134900,
      discountPercentage: 7.4,
      productUrl: 'https://amazon.in/dp/iphone15pro',
      lastUpdated: '2026-10-18T10:00:00Z',
      product: sampleProduct,
      seller: {
        id: 's1',
        name: 'Amazon India',
        websiteUrl: 'https://amazon.in',
        logoUrl: 'https://images.example.com/sellers/amazon.png',
      },
    };

    const html = renderToString(
      React.createElement(SellerCard, {
        price: samplePrice,
        isBestDeal: true,
        lowestPrice: 124900,
      })
    );

    expect(html).toContain('src="https://images.example.com/products/iphone15pro.jpg"');
    expect(html).toContain('alt="Apple iPhone 15 Pro"');
  });

  it('renders category fallback icon when productImageUrl is null but category is provided', () => {
    const html = renderToString(
      React.createElement(SellerCard, {
        offer: baseOffer,
        isBestDeal: true,
        lowestPrice: 124900,
        productImageUrl: null,
        productCategory: 'Smartphones',
        productName: 'Apple iPhone 15 Pro',
      })
    );

    // Should render the Smartphone icon fallback for the product
    expect(html).toContain('lucide-smartphone');
    expect(html).toContain('aria-label="Apple iPhone 15 Pro"');
  });

  it('does not render product image preview container when product context is completely absent', () => {
    const offerWithoutProduct: NormalizedOffer = {
      ...baseOffer,
      productTitle: undefined,
      rawPrice: undefined,
    };

    const html = renderToString(
      React.createElement(SellerCard, {
        offer: offerWithoutProduct,
        isBestDeal: false,
        lowestPrice: 124900,
      })
    );

    // Seller logo is still rendered
    expect(html).toContain('src="https://images.example.com/sellers/amazon.png"');
    expect(html).toContain('Amazon India');

    // But no product image preview block exists
    expect(html).not.toContain('alt="Product"');
    expect(html).not.toContain('group-hover:scale-105');
  });

  it('renders SVG illustration asset path correctly when provided', () => {
    const html = renderToString(
      React.createElement(SellerCard, {
        offer: baseOffer,
        isBestDeal: true,
        lowestPrice: 124900,
        productImageUrl: '/products/apple-iphone-15-pro.svg',
        productCategory: 'Smartphones',
        productName: 'Apple iPhone 15 Pro',
      })
    );

    expect(html).toContain('src="/products/apple-iphone-15-pro.svg"');
    expect(html).toContain('alt="Apple iPhone 15 Pro"');
  });
});
