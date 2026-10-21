import { describe, it, expect } from 'vitest';
import React from 'react';
import { renderToString } from 'react-dom/server';
import { ProductImage, getCategoryIcon } from './ProductImage';
import {
  Smartphone,
  Laptop,
  Tablet,
  Headphones,
  AudioLines,
  Watch,
  Gamepad2,
  Monitor,
  Keyboard,
  Mouse,
  HardDrive,
  Router,
  Camera,
  Package,
} from 'lucide-react';

describe('ProductImage Pipeline & Fallback Rendering', () => {
  it('renders fallback container with package icon when src is undefined or null and category is omitted', () => {
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

  describe('Category-specific fallback icons for all 14 catalog categories', () => {
    it('maps all catalog categories to corresponding domain icons', () => {
      expect(getCategoryIcon('Smartphone')).toBe(Smartphone);
      expect(getCategoryIcon('Laptop')).toBe(Laptop);
      expect(getCategoryIcon('Tablet')).toBe(Tablet);
      expect(getCategoryIcon('Headphones')).toBe(Headphones);
      expect(getCategoryIcon('Earbuds')).toBe(AudioLines);
      expect(getCategoryIcon('Smartwatch')).toBe(Watch);
      expect(getCategoryIcon('Gaming Console')).toBe(Gamepad2);
      expect(getCategoryIcon('Monitor')).toBe(Monitor);
      expect(getCategoryIcon('Keyboard')).toBe(Keyboard);
      expect(getCategoryIcon('Mouse')).toBe(Mouse);
      expect(getCategoryIcon('Storage')).toBe(HardDrive);
      expect(getCategoryIcon('Networking')).toBe(Router);
      expect(getCategoryIcon('Camera')).toBe(Camera);
      expect(getCategoryIcon('Accessory')).toBe(Package);
    });

    it('renders category-specific icon in DOM when image is missing', () => {
      const htmlPhone = renderToString(React.createElement(ProductImage, {
        src: null,
        alt: 'OnePlus 12',
        category: 'Smartphone',
      }));
      expect(htmlPhone).toContain('lucide-smartphone');

      const htmlLaptop = renderToString(React.createElement(ProductImage, {
        src: null,
        alt: 'MacBook Pro 16 M3',
        category: 'Laptop',
      }));
      expect(htmlLaptop).toContain('lucide-laptop');

      const htmlMonitor = renderToString(React.createElement(ProductImage, {
        src: null,
        alt: 'LG UltraFine',
        category: 'Monitor',
      }));
      expect(htmlMonitor).toContain('lucide-monitor');

      const htmlStorage = renderToString(React.createElement(ProductImage, {
        src: null,
        alt: 'Samsung 990 Pro',
        category: 'Storage',
      }));
      expect(htmlStorage).toContain('lucide-hard-drive');

      const htmlNetworking = renderToString(React.createElement(ProductImage, {
        src: null,
        alt: 'Netgear Nighthawk',
        category: 'Networking',
      }));
      expect(htmlNetworking).toContain('lucide-router');
    });

    it('handles case-insensitivity and whitespace in category name gracefully', () => {
      expect(getCategoryIcon('  smartphone  ')).toBe(Smartphone);
      expect(getCategoryIcon('gaming console')).toBe(Gamepad2);
      expect(getCategoryIcon('MONITOR')).toBe(Monitor);
      expect(getCategoryIcon('unknown category')).toBe(Package);
      expect(getCategoryIcon(null)).toBe(Package);
      expect(getCategoryIcon(undefined)).toBe(Package);
    });
  });

  describe('Dedicated Product Asset Coverage (Option A)', () => {
    it('renders dedicated product illustration asset path without error', () => {
      const html = renderToString(React.createElement(ProductImage, {
        src: '/products/laptop-macbook-pro-16-m3.svg',
        alt: 'MacBook Pro 16 M3',
        category: 'Laptop',
      }));
      expect(html).toContain('src="/products/laptop-macbook-pro-16-m3.svg"');
      expect(html).toContain('alt="MacBook Pro 16 M3"');
      // Should render image element, not the missing icon placeholder
      expect(html).toContain('<img');
    });

    it('renders genuine external photograph URL cleanly without error', () => {
      const html = renderToString(React.createElement(ProductImage, {
        src: 'https://upload.wikimedia.org/wikipedia/commons/1/19/Apple_iPhone_15_Pro.jpg',
        alt: 'iPhone 15 Pro',
        category: 'Smartphone',
      }));
      expect(html).toContain('src="https://upload.wikimedia.org/wikipedia/commons/1/19/Apple_iPhone_15_Pro.jpg"');
      expect(html).toContain('alt="iPhone 15 Pro"');
      expect(html).toContain('<img');
    });
  });
});
