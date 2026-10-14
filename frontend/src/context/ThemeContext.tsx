import React, { createContext, useContext, useState, useEffect } from 'react';
import type { ReactNode } from 'react';

export type Theme = 'dark' | 'light';

export const THEME_STORAGE_KEY = 'pricepilot_theme';
export const DEFAULT_THEME: Theme = 'dark';

export function getSavedTheme(): Theme {
  if (typeof window === 'undefined' || !window.localStorage) {
    return DEFAULT_THEME;
  }
  try {
    const saved = localStorage.getItem(THEME_STORAGE_KEY);
    if (saved === 'dark' || saved === 'light') {
      return saved;
    }
  } catch {
    // Return default on storage access failure (e.g. sandboxed iframe or disabled cookies)
  }
  return DEFAULT_THEME;
}

export function saveTheme(theme: Theme): void {
  if (typeof window !== 'undefined' && window.localStorage) {
    try {
      localStorage.setItem(THEME_STORAGE_KEY, theme);
    } catch {
      // Ignore write errors (quota or sandbox restrictions)
    }
  }
}

export function applyThemeToDocument(theme: Theme): void {
  if (typeof document === 'undefined') return;
  const root = document.documentElement;
  root.classList.remove('dark', 'light');
  root.classList.add(theme);
  root.setAttribute('data-theme', theme);
  root.style.colorScheme = theme;
}

export interface ThemeContextType {
  theme: Theme;
  setTheme: (theme: Theme) => void;
  toggleTheme: () => void;
}

const ThemeContext = createContext<ThemeContextType | undefined>(undefined);

export interface ThemeProviderProps {
  children?: ReactNode;
  defaultTheme?: Theme;
}

export const ThemeProvider: React.FC<ThemeProviderProps> = ({ children, defaultTheme }) => {
  const [theme, setThemeState] = useState<Theme>(() => {
    return defaultTheme ?? getSavedTheme();
  });

  useEffect(() => {
    applyThemeToDocument(theme);
  }, [theme]);

  // Synchronize theme across multiple tabs/windows
  useEffect(() => {
    if (typeof window === 'undefined') return;
    const handleStorage = (e: StorageEvent) => {
      if (e.key === THEME_STORAGE_KEY && (e.newValue === 'dark' || e.newValue === 'light')) {
        setThemeState(e.newValue);
        applyThemeToDocument(e.newValue);
      }
    };
    window.addEventListener('storage', handleStorage);
    return () => window.removeEventListener('storage', handleStorage);
  }, []);

  const setTheme = (newTheme: Theme) => {
    if (newTheme !== 'dark' && newTheme !== 'light') return;
    setThemeState(newTheme);
    saveTheme(newTheme);
    applyThemeToDocument(newTheme);
  };

  const toggleTheme = () => {
    const next: Theme = theme === 'dark' ? 'light' : 'dark';
    setTheme(next);
  };

  return (
    <ThemeContext.Provider value={{ theme, setTheme, toggleTheme }}>
      {children}
    </ThemeContext.Provider>
  );
};

export const useTheme = (): ThemeContextType => {
  const context = useContext(ThemeContext);
  if (!context) {
    throw new Error('useTheme must be used within a ThemeProvider');
  }
  return context;
};
