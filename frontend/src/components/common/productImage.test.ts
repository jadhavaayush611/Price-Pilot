import { describe, it, expect } from 'vitest';
import React from 'react';
import { renderToString } from 'react-dom/server';
import { ProductImage } from './ProductImage';

describe('ProductImage Pipeline & Fallback Rendering', () => {
  it('renders fallback container with package icon when src is undefined or null', () => {
    const htmlNull = renderToString(React.createElement(ProductImage, { src: null, alt: 'Test Product' }));
    expect(htmlNull).toContain('aria-label="Test Product"');
    expect(htmlNull).toContain('lucide-package');
    expect(htmlNull).not.toContain('<img');

    const htmlUndefined = renderToString(React.createElement(ProductImage, { src: undefined, alt: 'Test Product' }));
    expect(htmlUndefined).toContain('lucide-package');
    expect(htmlUndefined).not.toContain('<img');
  });

  it('renders fallback container when src is an empty or whitespace string', () => {
    const htmlEmpty = renderToString(React.createElement(ProductImage, { src: '', alt: 'Empty Product' }));
    expect(htmlEmpty).toContain('lucide-package');
    expect(htmlEmpty).not.toContain('<img');

    const htmlWhitespace = renderToString(React.createElement(ProductImage, { src: '   ', alt: 'Whitespace Product' }));
    expect(htmlWhitespace).toContain('lucide-package');
    expect(htmlWhitespace).not.toContain('<img');
  });

  it('renders fallback container when src is literal "null" or "undefined" string', () => {
    const htmlLiteralNull = renderToString(React.createElement(ProductImage, { src: 'null', alt: 'Null String' }));
    expect(htmlLiteralNull).toContain('lucide-package');
    expect(htmlLiteralNull).not.toContain('<img');

    const htmlLiteralUndefined = renderToString(React.createElement(ProductImage, { src: 'undefined', alt: 'Undefined String' }));
    expect(htmlLiteralUndefined).toContain('lucide-package');
    expect(htmlLiteralUndefined).not.toContain('<img');
  });

  it('renders <img> element when valid src URL is provided', () => {
    const validUrl = 'https://images.example.com/products/phone.jpg';
    const html = renderToString(React.createElement(ProductImage, { src: validUrl, alt: 'Smartphone' }));
    expect(html).toContain('<img');
    expect(html).toContain(`src="${validUrl}"`);
    expect(html).toContain('alt="Smartphone"');
  });

  it('applies aspect ratio classes correctly to prevent layout collapse', () => {
    const htmlSquare = renderToString(React.createElement(ProductImage, { src: null, alt: 'Square', aspectRatio: 'square' }));
    expect(htmlSquare).toContain('aspect-square');

    const htmlVideo = renderToString(React.createElement(ProductImage, { src: null, alt: 'Video', aspectRatio: 'video' }));
    expect(htmlVideo).toContain('aspect-video');

    const htmlFourThirds = renderToString(React.createElement(ProductImage, { src: null, alt: '4:3', aspectRatio: '4/3' }));
    expect(htmlFourThirds).toContain('aspect-[4/3]');
  });

  it('displays brand fallback text when showFallbackText is enabled', () => {
    const html = renderToString(React.createElement(ProductImage, {
      src: null,
      alt: 'Sony WH-1000XM5',
      showFallbackText: true,
      fallbackText: 'Sony',
    }));
    expect(html).toContain('Sony');
  });
});
