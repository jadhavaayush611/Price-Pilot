import { describe, it, expect, vi, beforeEach } from 'vitest';
import React from 'react';
import { renderToString } from 'react-dom/server';
import {
  getSavedTheme,
  saveTheme,
  applyThemeToDocument,
  THEME_STORAGE_KEY,
  DEFAULT_THEME,
  ThemeProvider,
  useTheme,
} from './ThemeContext';
import { ThemeToggle } from '../components/ThemeToggle';

class LocalStorageMock {
  private store: Record<string, string> = {};

  clear() {
    this.store = {};
  }

  getItem(key: string) {
    return this.store[key] || null;
  }

  setItem(key: string, value: string) {
    this.store[key] = value.toString();
  }

  removeItem(key: string) {
    delete this.store[key];
  }
}

if (typeof window === 'undefined') {
  const g = globalThis as unknown as { localStorage: LocalStorageMock; window: unknown };
  g.localStorage = new LocalStorageMock();
  g.window = { localStorage: g.localStorage };
}

describe('Theme Foundation & Persistence', () => {
  beforeEach(() => {
    vi.restoreAllMocks();
    localStorage.clear();
    if (typeof document !== 'undefined') {
      document.documentElement.className = '';
      document.documentElement.removeAttribute('data-theme');
      document.documentElement.style.colorScheme = '';
    }
  });

  it('uses pricepilot_theme as storage key and defaults to dark', () => {
    expect(THEME_STORAGE_KEY).toBe('pricepilot_theme');
    expect(DEFAULT_THEME).toBe('dark');
    expect(getSavedTheme()).toBe('dark');
  });

  it('restores valid saved theme from localStorage', () => {
    localStorage.setItem(THEME_STORAGE_KEY, 'light');
    expect(getSavedTheme()).toBe('light');

    localStorage.setItem(THEME_STORAGE_KEY, 'dark');
    expect(getSavedTheme()).toBe('dark');
  });

  it('falls back to dark when an invalid or corrupt value is stored', () => {
    localStorage.setItem(THEME_STORAGE_KEY, 'neon');
    expect(getSavedTheme()).toBe('dark');

    localStorage.setItem(THEME_STORAGE_KEY, '');
    expect(getSavedTheme()).toBe('dark');

    localStorage.setItem(THEME_STORAGE_KEY, 'null');
    expect(getSavedTheme()).toBe('dark');
  });

  it('persists theme changes to localStorage', () => {
    saveTheme('light');
    expect(localStorage.getItem(THEME_STORAGE_KEY)).toBe('light');

    saveTheme('dark');
    expect(localStorage.getItem(THEME_STORAGE_KEY)).toBe('dark');
  });

  it('synchronizes document classes, data-theme attribute, and colorScheme on documentElement', () => {
    if (typeof document === 'undefined') return;

    applyThemeToDocument('light');
    expect(document.documentElement.classList.contains('light')).toBe(true);
    expect(document.documentElement.classList.contains('dark')).toBe(false);
    expect(document.documentElement.getAttribute('data-theme')).toBe('light');
    expect(document.documentElement.style.colorScheme).toBe('light');

    applyThemeToDocument('dark');
    expect(document.documentElement.classList.contains('dark')).toBe(true);
    expect(document.documentElement.classList.contains('light')).toBe(false);
    expect(document.documentElement.getAttribute('data-theme')).toBe('dark');
    expect(document.documentElement.style.colorScheme).toBe('dark');
  });

  it('renders children within ThemeProvider', () => {
    const TestConsumer = () => {
      const { theme } = useTheme();
      return React.createElement('div', { 'data-testid': 'current-theme' }, theme);
    };

    const html = renderToString(
      React.createElement(
        ThemeProvider,
        { defaultTheme: 'dark' },
        React.createElement(TestConsumer, null)
      )
    );

    expect(html).toContain('dark');
  });

  it('throws helpful error when useTheme is used outside of ThemeProvider', () => {
    const TestOrphan = () => {
      useTheme();
      return null;
    };

    expect(() => {
      renderToString(React.createElement(TestOrphan, null));
    }).toThrow('useTheme must be used within a ThemeProvider');
  });

  it('renders ThemeToggle with accessible dark mode attributes', () => {
    const html = renderToString(
      React.createElement(
        ThemeProvider,
        { defaultTheme: 'dark' },
        React.createElement(ThemeToggle, null)
      )
    );

    expect(html).toContain('Switch to light theme');
    expect(html).toContain('aria-pressed="true"');
  });

  it('renders ThemeToggle with accessible light mode attributes', () => {
    const html = renderToString(
      React.createElement(
        ThemeProvider,
        { defaultTheme: 'light' },
        React.createElement(ThemeToggle, null)
      )
    );

    expect(html).toContain('Switch to dark theme');
    expect(html).toContain('aria-pressed="false"');
  });
});
