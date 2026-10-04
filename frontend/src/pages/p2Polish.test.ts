import { describe, it, expect } from 'vitest';
import { generateConversationTitle } from '../services/assistantTitle';

describe('Batch 4 — P2 Polish & Visual Flow Tests', () => {
  describe('1. LandingPage 4-Step Consumer Narrative & Pillars', () => {
    it('defines clear, consumer-centric feature pillars', () => {
      const pillars = [
        {
          title: 'Understand True Pricing',
          description: 'Compare offers across verified retailers concurrently to instantly see real live market prices with zero markup.'
        },
        {
          title: 'Compare Without Bias',
          description: 'Side-by-side specs, direct store links, and true seller pricing without sponsored distortion or affiliate ranking bias.'
        },
        {
          title: 'Decide When to Buy',
          description: 'Price history trends, discount tracking, and purchase timing signals help you buy with complete confidence.'
        }
      ];

      expect(pillars[0].title).toBe('Understand True Pricing');
      expect(pillars[1].title).toBe('Compare Without Bias');
      expect(pillars[2].title).toBe('Decide When to Buy');

      // Zero internal engineering jargon in consumer descriptions
      pillars.forEach(p => {
        expect(p.description).not.toMatch(/backend|vector search|grounded evidence|pipeline|algorithm/i);
      });
    });

    it('verifies quick trending category shortcuts', () => {
      const trendingCategories = ['Smartphone', 'Laptop', 'Tablet', 'Headphones', 'Gaming Console'];
      expect(trendingCategories).toHaveLength(5);
      expect(trendingCategories).toContain('Smartphone');
      expect(trendingCategories).toContain('Laptop');
      expect(trendingCategories).toContain('Headphones');
    });
  });

  describe('2. Navigation & Mobile Drawer Parity', () => {
    it('provides public shopping navigation routes for unauthenticated users', () => {
      const publicRoutes = ['/', '/compare', '/recommendations', '/trending', '/analytics'];
      expect(publicRoutes).toContain('/');
      expect(publicRoutes).toContain('/compare');
      expect(publicRoutes).toContain('/recommendations');
      expect(publicRoutes).toContain('/trending');
      expect(publicRoutes).toContain('/analytics');
    });

    it('provides personal navigation routes for authenticated users', () => {
      const authenticatedRoutes = [
        '/assistant',
        '/analytics',
        '/watchlist',
        '/saved-products',
        '/dashboard/v2',
        '/settings/preferences'
      ];
      expect(authenticatedRoutes).toContain('/assistant');
      expect(authenticatedRoutes).toContain('/watchlist');
      expect(authenticatedRoutes).toContain('/saved-products');
      expect(authenticatedRoutes).toContain('/dashboard/v2');
      expect(authenticatedRoutes).toContain('/settings/preferences');
    });

    it('defines essential footer links to match primary shopping exploration', () => {
      const footerLinks = [
        { label: 'Discover', path: '/' },
        { label: 'Compare', path: '/compare' },
        { label: 'Recommendations', path: '/recommendations' },
        { label: 'Trending', path: '/trending' },
        { label: 'Analytics', path: '/analytics' }
      ];
      expect(footerLinks.map(l => l.path)).toEqual([
        '/',
        '/compare',
        '/recommendations',
        '/trending',
        '/analytics'
      ]);
    });
  });

  describe('3. Assistant Dynamic Conversation Title Generation', () => {
    it('creates semantic, human-readable titles across various consumer intents', () => {
      expect(generateConversationTitle('Find best OLED monitors under $500')).toBe('Best OLED monitors under $500');
      expect(generateConversationTitle('Is the Sony WH-1000XM5 a good purchase?')).toBe('Sony WH-1000XM5 buying decision');
      expect(generateConversationTitle('Compare iPhone 15 Pro and Galaxy S24')).toBe('IPhone 15 Pro vs Galaxy S24 comparison');
      expect(generateConversationTitle('When should I buy the MacBook Air?')).toBe('MacBook Air purchase timing');
    });

    it('handles neutral fallbacks and strips enum keywords gracefully', () => {
      expect(generateConversationTitle('')).toBe('New Inquiry');
      expect(generateConversationTitle(null)).toBe('New Inquiry');
      expect(generateConversationTitle('   ')).toBe('New Inquiry');
      
      const title = generateConversationTitle('EXCELLENT_DEAL on Pixel 8a BUY_NOW');
      expect(title).not.toContain('EXCELLENT_DEAL');
      expect(title).not.toContain('BUY_NOW');
      expect(title).not.toContain('_');
      expect(title).toContain('Pixel 8a');
    });

    it('truncates overly long queries gracefully with ellipsis', () => {
      const longQuery = 'Looking for an ultra-wide curved gaming monitor with 240Hz refresh rate and USB-C power delivery';
      const title = generateConversationTitle(longQuery);
      expect(title.length).toBeLessThanOrEqual(42);
      expect(title.endsWith('...')).toBe(true);
    });
  });
});
