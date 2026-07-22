import React, { useState, useEffect } from 'react';
import { Search, X } from 'lucide-react';

interface SearchBarProps {
  value: string;
  onChange: (value: string) => void;
  placeholder?: string;
}

export const SearchBar: React.FC<SearchBarProps> = ({
  value,
  onChange,
  placeholder = "Search products, brands, or categories..."
}) => {
  const [localInput, setLocalInput] = useState(value);
  const [prevValue, setPrevValue] = useState(value);

  // Sync state if parent value changes (e.g. from URL parameters)
  if (value !== prevValue) {
    setPrevValue(value);
    setLocalInput(value);
  }

  // Debounced notification to parent
  useEffect(() => {
    const handler = setTimeout(() => {
      // Avoid double-updating if values are identical
      if (localInput !== value) {
        onChange(localInput);
      }
    }, 400); // 400ms debounce delay

    return () => {
      clearTimeout(handler);
    };
  }, [localInput, onChange, value]);

  const handleClear = () => {
    setLocalInput('');
    onChange('');
  };

  return (
    <div className="relative flex items-center p-1.5 rounded-xl bg-zinc-950/60 border border-zinc-900 focus-within:border-zinc-800 transition-all shadow-lg backdrop-blur-md">
      <Search className="h-5 w-5 text-zinc-500 ml-3.5 flex-shrink-0" />
      <input
        type="text"
        placeholder={placeholder}
        value={localInput}
        onChange={(e) => setLocalInput(e.target.value)}
        className="w-full px-3.5 py-2 bg-transparent text-zinc-100 placeholder-zinc-500 focus:outline-none text-sm font-medium"
      />
      {localInput && (
        <button
          type="button"
          onClick={handleClear}
          className="p-1.5 hover:bg-zinc-900 rounded-lg text-zinc-500 hover:text-white mr-1 transition-colors cursor-pointer"
        >
          <X className="h-4 w-4" />
        </button>
      )}
    </div>
  );
};
