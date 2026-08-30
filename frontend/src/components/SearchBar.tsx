import React, { useState, useEffect, useRef } from 'react';
import { Search, X, Tag, Layers, Package, Loader2 } from 'lucide-react';
import { apiService } from '../services/api';
import type { SearchSuggestion } from '../types';

interface SearchBarProps {
  value: string;
  onChange: (value: string) => void;
  placeholder?: string;
  onSelectSuggestion?: (suggestion: SearchSuggestion) => void;
}

export const SearchBar: React.FC<SearchBarProps> = ({
  value,
  onChange,
  placeholder = "Search products, brands, categories (e.g. 'iphone under 70000')...",
  onSelectSuggestion
}) => {
  const [localInput, setLocalInput] = useState(value);
  const [prevValue, setPrevValue] = useState(value);
  const [suggestions, setSuggestions] = useState<SearchSuggestion[]>([]);
  const [showDropdown, setShowDropdown] = useState(false);
  const [loadingSuggestions, setLoadingSuggestions] = useState(false);
  const [highlightedIndex, setHighlightedIndex] = useState(-1);
  const dropdownRef = useRef<HTMLDivElement>(null);

  // Sync state if parent value changes (e.g. from URL parameters)
  if (value !== prevValue) {
    setPrevValue(value);
    setLocalInput(value);
  }

  // Debounced notification to parent and suggestion fetching
  useEffect(() => {
    const handler = setTimeout(() => {
      if (localInput !== value) {
        onChange(localInput);
      }

      if (localInput.trim().length >= 2) {
        setLoadingSuggestions(true);
        apiService.getSearchSuggestions(localInput.trim(), 6)
          .then((res) => {
            setSuggestions(res);
            setShowDropdown(res.length > 0);
          })
          .catch(() => setSuggestions([]))
          .finally(() => setLoadingSuggestions(false));
      } else {
        setSuggestions([]);
        setShowDropdown(false);
      }
    }, 350);

    return () => {
      clearTimeout(handler);
    };
  }, [localInput, onChange, value]);

  // Click outside to close dropdown
  useEffect(() => {
    const handleClickOutside = (e: MouseEvent) => {
      if (dropdownRef.current && !dropdownRef.current.contains(e.target as Node)) {
        setShowDropdown(false);
      }
    };
    document.addEventListener('mousedown', handleClickOutside);
    return () => document.removeEventListener('mousedown', handleClickOutside);
  }, []);

  const handleClear = () => {
    setLocalInput('');
    setSuggestions([]);
    setShowDropdown(false);
    onChange('');
  };

  const handleSelect = (s: SearchSuggestion) => {
    setLocalInput(s.text);
    setShowDropdown(false);
    if (onSelectSuggestion) {
      onSelectSuggestion(s);
    } else {
      onChange(s.text);
    }
  };

  const handleKeyDown = (e: React.KeyboardEvent) => {
    if (!showDropdown || suggestions.length === 0) return;

    if (e.key === 'ArrowDown') {
      e.preventDefault();
      setHighlightedIndex((prev) => (prev + 1) % suggestions.length);
    } else if (e.key === 'ArrowUp') {
      e.preventDefault();
      setHighlightedIndex((prev) => (prev - 1 + suggestions.length) % suggestions.length);
    } else if (e.key === 'Enter' && highlightedIndex >= 0 && highlightedIndex < suggestions.length) {
      e.preventDefault();
      handleSelect(suggestions[highlightedIndex]);
    } else if (e.key === 'Escape') {
      setShowDropdown(false);
    }
  };

  const renderIcon = (type: string) => {
    switch (type) {
      case 'BRAND':
        return <Tag className="h-3.5 w-3.5 text-amber-400" />;
      case 'CATEGORY':
        return <Layers className="h-3.5 w-3.5 text-blue-400" />;
      default:
        return <Package className="h-3.5 w-3.5 text-emerald-400" />;
    }
  };

  return (
    <div className="relative w-full" ref={dropdownRef}>
      <div className="relative flex items-center p-1.5 rounded-xl bg-zinc-950/70 border border-zinc-900 focus-within:border-zinc-700 transition-all shadow-lg backdrop-blur-md">
        <Search className="h-5 w-5 text-zinc-500 ml-3.5 flex-shrink-0" aria-hidden="true" />
        <input
          type="text"
          placeholder={placeholder}
          value={localInput}
          onChange={(e) => {
            setLocalInput(e.target.value);
            setHighlightedIndex(-1);
          }}
          onFocus={() => {
            if (suggestions.length > 0) setShowDropdown(true);
          }}
          onKeyDown={handleKeyDown}
          aria-label="Search products, brands, or categories"
          aria-autocomplete="list"
          aria-expanded={showDropdown}
          className="w-full px-3.5 py-2 bg-transparent text-zinc-100 placeholder-zinc-500 focus:outline-none text-sm font-medium"
        />
        {loadingSuggestions && (
          <Loader2 className="h-4 w-4 text-zinc-500 animate-spin mr-2" aria-hidden="true" />
        )}
        {localInput && (
          <button
            type="button"
            onClick={handleClear}
            aria-label="Clear search input"
            className="p-1.5 hover:bg-zinc-900 rounded-lg text-zinc-500 hover:text-white mr-1 transition-colors cursor-pointer"
          >
            <X className="h-4 w-4" />
          </button>
        )}
      </div>

      {/* Autocomplete Suggestions Dropdown */}
      {showDropdown && suggestions.length > 0 && (
        <div 
          role="listbox" 
          aria-label="Search suggestions"
          className="absolute left-0 right-0 top-full mt-2 rounded-xl bg-zinc-950/95 border border-zinc-800 shadow-2xl backdrop-blur-xl z-50 overflow-hidden py-1.5 animate-in fade-in slide-in-from-top-2 duration-150"
        >
          <div className="px-3 py-1 text-[10px] uppercase font-bold tracking-wider text-zinc-500 border-b border-zinc-900">
            Suggestions
          </div>
          {suggestions.map((s, idx) => (
            <div
              key={`${s.type}-${s.text}-${idx}`}
              role="option"
              aria-selected={highlightedIndex === idx}
              onClick={() => handleSelect(s)}
              onMouseEnter={() => setHighlightedIndex(idx)}
              className={`flex items-center justify-between px-3.5 py-2.5 cursor-pointer text-xs transition-colors ${
                highlightedIndex === idx ? 'bg-zinc-900 text-white' : 'text-zinc-300 hover:bg-zinc-900/60'
              }`}
            >
              <div className="flex items-center gap-2.5 min-w-0">
                {renderIcon(s.type)}
                <span className="font-medium truncate">{s.text}</span>
              </div>
              <span className="text-[10px] uppercase font-semibold text-zinc-500 ml-2 flex-shrink-0">
                {s.type.toLowerCase()}
              </span>
            </div>
          ))}
        </div>
      )}
    </div>
  );
};
