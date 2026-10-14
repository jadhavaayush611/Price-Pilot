import React from 'react';
import { Sun, Moon } from 'lucide-react';
import { useTheme } from '../context/ThemeContext';

export interface ThemeToggleProps {
  className?: string;
}

export const ThemeToggle: React.FC<ThemeToggleProps> = ({ className = '' }) => {
  const { theme, toggleTheme } = useTheme();
  const isDark = theme === 'dark';

  return (
    <button
      type="button"
      onClick={toggleTheme}
      aria-label={isDark ? 'Switch to light theme' : 'Switch to dark theme'}
      title={isDark ? 'Switch to light theme' : 'Switch to dark theme'}
      aria-pressed={isDark}
      className={`relative inline-flex items-center justify-center p-2 rounded-xl transition-all duration-200 border border-[var(--color-border)] bg-[var(--color-surface)] text-[var(--color-foreground)] hover:border-[var(--color-primary)] hover:text-[var(--color-primary)] active:scale-95 focus-visible:outline-hidden focus-visible:ring-2 focus-visible:ring-[var(--color-ring)] cursor-pointer ${className}`}
    >
      {isDark ? (
        <Sun size={17} className="transition-transform duration-200" aria-hidden="true" />
      ) : (
        <Moon size={17} className="transition-transform duration-200" aria-hidden="true" />
      )}
      <span className="sr-only">
        {isDark ? 'Switch to light theme' : 'Switch to dark theme'}
      </span>
    </button>
  );
};

export default ThemeToggle;
