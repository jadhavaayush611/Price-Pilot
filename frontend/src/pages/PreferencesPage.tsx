import React, { useState, useEffect } from 'react';
import { apiService } from '../services/api';
import type { UserShoppingPreference, DealSensitivity, PriceSensitivity, AvailabilityPreference } from '../types';
import { 
  Sliders, 
  Tag, 
  DollarSign, 
  Star, 
  Zap, 
  PackageCheck, 
  RotateCcw, 
  Save, 
  CheckCircle, 
  AlertCircle,
  Plus,
  X
} from 'lucide-react';
import { motion, AnimatePresence } from 'framer-motion';

export const PreferencesPage: React.FC = () => {
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);
  const [resetting, setResetting] = useState(false);
  const [statusMessage, setStatusMessage] = useState<{ type: 'success' | 'error'; text: string } | null>(null);

  // Preference State
  const [preferredCategories, setPreferredCategories] = useState<string[]>([]);
  const [newCategoryInput, setNewCategoryInput] = useState('');
  const [preferredBrands, setPreferredBrands] = useState<string[]>([]);
  const [newBrandInput, setNewBrandInput] = useState('');
  const [minBudget, setMinBudget] = useState<string>('');
  const [maxBudget, setMaxBudget] = useState<string>('');
  const [minRating, setMinRating] = useState<number>(0);
  const [dealSensitivity, setDealSensitivity] = useState<DealSensitivity>('MEDIUM');
  const [priceSensitivity, setPriceSensitivity] = useState<PriceSensitivity>('MEDIUM');
  const [availabilityPreference, setAvailabilityPreference] = useState<AvailabilityPreference>('ALL');

  useEffect(() => {
    fetchPreferences();
  }, []);

  const fetchPreferences = async () => {
    setLoading(true);
    try {
      const prefs: UserShoppingPreference = await apiService.getUserPreferences();
      setPreferredCategories(prefs.preferredCategories || []);
      setPreferredBrands(prefs.preferredBrands || []);
      setMinBudget(prefs.minBudget !== undefined && prefs.minBudget !== null ? prefs.minBudget.toString() : '');
      setMaxBudget(prefs.maxBudget !== undefined && prefs.maxBudget !== null ? prefs.maxBudget.toString() : '');
      setMinRating(prefs.minRating !== undefined && prefs.minRating !== null ? prefs.minRating : 0);
      setDealSensitivity(prefs.dealSensitivity || 'MEDIUM');
      setPriceSensitivity(prefs.priceSensitivity || 'MEDIUM');
      setAvailabilityPreference(prefs.availabilityPreference || 'ALL');
    } catch (err: unknown) {
      console.error('Failed to load shopping preferences', err);
      setStatusMessage({ type: 'error', text: 'Could not load preferences. Please try again.' });
    } finally {
      setLoading(false);
    }
  };

  const handleSave = async (e: React.FormEvent) => {
    e.preventDefault();
    setSaving(true);
    setStatusMessage(null);

    const minB = minBudget.trim() !== '' ? parseFloat(minBudget) : undefined;
    const maxB = maxBudget.trim() !== '' ? parseFloat(maxBudget) : undefined;

    if (minB !== undefined && maxB !== undefined && minB > maxB) {
      setStatusMessage({ type: 'error', text: 'Minimum budget cannot exceed maximum budget.' });
      setSaving(false);
      return;
    }

    try {
      await apiService.updateUserPreferences({
        preferredCategories,
        preferredBrands,
        minBudget: minB,
        maxBudget: maxB,
        minRating: minRating > 0 ? minRating : undefined,
        dealSensitivity,
        priceSensitivity,
        availabilityPreference,
      });
      setStatusMessage({ type: 'success', text: 'Shopping preferences saved successfully!' });
    } catch (err: unknown) {
      console.error('Failed to update preferences', err);
      setStatusMessage({ type: 'error', text: 'Failed to save preferences. Please check inputs and retry.' });
    } finally {
      setSaving(false);
    }
  };

  const handleReset = async () => {
    if (!window.confirm('Reset all shopping preferences to defaults? This will clear your custom category and brand preferences.')) {
      return;
    }
    setResetting(true);
    setStatusMessage(null);
    try {
      await apiService.resetUserPreferences();
      setPreferredCategories([]);
      setPreferredBrands([]);
      setMinBudget('');
      setMaxBudget('');
      setMinRating(0);
      setDealSensitivity('MEDIUM');
      setPriceSensitivity('MEDIUM');
      setAvailabilityPreference('ALL');
      setStatusMessage({ type: 'success', text: 'Preferences have been reset to defaults.' });
    } catch (err: unknown) {
      console.error('Failed to reset preferences', err);
      setStatusMessage({ type: 'error', text: 'Failed to reset preferences.' });
    } finally {
      setResetting(false);
    }
  };

  const addCategory = () => {
    const val = newCategoryInput.trim();
    if (val && !preferredCategories.includes(val)) {
      setPreferredCategories([...preferredCategories, val]);
      setNewCategoryInput('');
    }
  };

  const removeCategory = (cat: string) => {
    setPreferredCategories(preferredCategories.filter(c => c !== cat));
  };

  const addBrand = () => {
    const val = newBrandInput.trim();
    if (val && !preferredBrands.includes(val)) {
      setPreferredBrands([...preferredBrands, val]);
      setNewBrandInput('');
    }
  };

  const removeBrand = (br: string) => {
    setPreferredBrands(preferredBrands.filter(b => b !== br));
  };

  if (loading) {
    return (
      <div className="flex items-center justify-center min-h-[400px]">
        <div className="h-8 w-8 rounded-full border-2 border-zinc-800 border-t-zinc-200 animate-spin" />
      </div>
    );
  }

  return (
    <div className="max-w-4xl mx-auto px-4 sm:px-6 lg:px-8 py-8">
      {/* Header */}
      <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-4 pb-6 border-b border-zinc-800/80 mb-8">
        <div>
          <div className="flex items-center gap-2 mb-1">
            <Sliders className="w-5 h-5 text-indigo-400" />
            <h1 className="text-2xl font-bold text-white tracking-tight">Shopping Preferences</h1>
          </div>
          <p className="text-sm text-zinc-400">
            Tailor PricePilot's recommendation engine to your specific shopping tastes and budget bounds.
          </p>
        </div>
        <div className="flex items-center gap-3">
          <button
            type="button"
            onClick={handleReset}
            disabled={resetting || saving}
            className="flex items-center gap-1.5 px-3 py-2 text-xs font-semibold text-zinc-400 hover:text-white bg-zinc-900 border border-zinc-800 hover:border-zinc-700 rounded-lg transition-all disabled:opacity-50 cursor-pointer"
          >
            <RotateCcw className="w-3.5 h-3.5" />
            Reset to Defaults
          </button>
        </div>
      </div>

      <AnimatePresence>
        {statusMessage && (
          <motion.div
            initial={{ opacity: 0, y: -10 }}
            animate={{ opacity: 1, y: 0 }}
            exit={{ opacity: 0, y: -10 }}
            className={`p-4 rounded-xl mb-6 flex items-center gap-3 border ${
              statusMessage.type === 'success'
                ? 'bg-emerald-950/40 border-emerald-800/60 text-emerald-300'
                : 'bg-rose-950/40 border-rose-800/60 text-rose-300'
            }`}
          >
            {statusMessage.type === 'success' ? (
              <CheckCircle className="w-5 h-5 text-emerald-400 shrink-0" />
            ) : (
              <AlertCircle className="w-5 h-5 text-rose-400 shrink-0" />
            )}
            <span className="text-sm font-medium">{statusMessage.text}</span>
          </motion.div>
        )}
      </AnimatePresence>

      <form onSubmit={handleSave} className="space-y-8">
        {/* Preferred Categories */}
        <div className="bg-zinc-950/60 border border-zinc-800/80 rounded-xl p-6">
          <div className="flex items-center gap-2 mb-2">
            <Tag className="w-4 h-4 text-indigo-400" />
            <h2 className="text-base font-semibold text-white">Preferred Categories</h2>
          </div>
          <p className="text-xs text-zinc-400 mb-4">
            Items from these categories receive a deterministic boost in personalized recommendations.
          </p>

          <div className="flex flex-wrap gap-2 mb-3">
            {preferredCategories.map((cat) => (
              <span
                key={cat}
                className="inline-flex items-center gap-1.5 px-3 py-1 bg-zinc-900 text-zinc-200 border border-zinc-800 rounded-full text-xs font-medium"
              >
                {cat}
                <button
                  type="button"
                  onClick={() => removeCategory(cat)}
                  className="hover:text-rose-400 transition-colors"
                >
                  <X className="w-3.5 h-3.5" />
                </button>
              </span>
            ))}
            {preferredCategories.length === 0 && (
              <span className="text-xs text-zinc-500 italic">No specific preferred categories set.</span>
            )}
          </div>

          <div className="flex gap-2 max-w-md">
            <input
              type="text"
              placeholder="e.g. Smartphone, Laptop, Headphones..."
              value={newCategoryInput}
              onChange={(e) => setNewCategoryInput(e.target.value)}
              onKeyDown={(e) => {
                if (e.key === 'Enter') {
                  e.preventDefault();
                  addCategory();
                }
              }}
              className="flex-1 bg-zinc-900 border border-zinc-800 rounded-lg px-3 py-2 text-xs text-white placeholder-zinc-500 focus:outline-none focus:border-zinc-700"
            />
            <button
              type="button"
              onClick={addCategory}
              className="flex items-center gap-1 px-3 py-2 bg-zinc-800 hover:bg-zinc-700 text-white rounded-lg text-xs font-semibold transition-colors"
            >
              <Plus className="w-3.5 h-3.5" />
              Add
            </button>
          </div>
        </div>

        {/* Preferred Brands */}
        <div className="bg-zinc-950/60 border border-zinc-800/80 rounded-xl p-6">
          <div className="flex items-center gap-2 mb-2">
            <Tag className="w-4 h-4 text-emerald-400" />
            <h2 className="text-base font-semibold text-white">Preferred Brands</h2>
          </div>
          <p className="text-xs text-zinc-400 mb-4">
            Products made by these brands are prioritized in personal recommendations.
          </p>

          <div className="flex flex-wrap gap-2 mb-3">
            {preferredBrands.map((brand) => (
              <span
                key={brand}
                className="inline-flex items-center gap-1.5 px-3 py-1 bg-zinc-900 text-zinc-200 border border-zinc-800 rounded-full text-xs font-medium"
              >
                {brand}
                <button
                  type="button"
                  onClick={() => removeBrand(brand)}
                  className="hover:text-rose-400 transition-colors"
                >
                  <X className="w-3.5 h-3.5" />
                </button>
              </span>
            ))}
            {preferredBrands.length === 0 && (
              <span className="text-xs text-zinc-500 italic">No specific preferred brands set.</span>
            )}
          </div>

          <div className="flex gap-2 max-w-md">
            <input
              type="text"
              placeholder="e.g. Apple, Samsung, Sony..."
              value={newBrandInput}
              onChange={(e) => setNewBrandInput(e.target.value)}
              onKeyDown={(e) => {
                if (e.key === 'Enter') {
                  e.preventDefault();
                  addBrand();
                }
              }}
              className="flex-1 bg-zinc-900 border border-zinc-800 rounded-lg px-3 py-2 text-xs text-white placeholder-zinc-500 focus:outline-none focus:border-zinc-700"
            />
            <button
              type="button"
              onClick={addBrand}
              className="flex items-center gap-1 px-3 py-2 bg-zinc-800 hover:bg-zinc-700 text-white rounded-lg text-xs font-semibold transition-colors"
            >
              <Plus className="w-3.5 h-3.5" />
              Add
            </button>
          </div>
        </div>

        {/* Budget Range & Rating */}
        <div className="grid grid-cols-1 sm:grid-cols-2 gap-6">
          <div className="bg-zinc-950/60 border border-zinc-800/80 rounded-xl p-6">
            <div className="flex items-center gap-2 mb-2">
              <DollarSign className="w-4 h-4 text-cyan-400" />
              <h2 className="text-base font-semibold text-white">Target Budget ($)</h2>
            </div>
            <p className="text-xs text-zinc-400 mb-4">
              Items fitting within this price range receive positive scoring; items over max budget are penalized.
            </p>

            <div className="grid grid-cols-2 gap-3">
              <div>
                <label className="block text-[11px] uppercase tracking-wider text-zinc-400 font-bold mb-1">
                  Min Budget
                </label>
                <input
                  type="number"
                  placeholder="Min ($)"
                  min="0"
                  step="0.01"
                  value={minBudget}
                  onChange={(e) => setMinBudget(e.target.value)}
                  className="w-full bg-zinc-900 border border-zinc-800 rounded-lg px-3 py-2 text-xs text-white placeholder-zinc-500 focus:outline-none focus:border-zinc-700"
                />
              </div>
              <div>
                <label className="block text-[11px] uppercase tracking-wider text-zinc-400 font-bold mb-1">
                  Max Budget
                </label>
                <input
                  type="number"
                  placeholder="Max ($)"
                  min="0"
                  step="0.01"
                  value={maxBudget}
                  onChange={(e) => setMaxBudget(e.target.value)}
                  className="w-full bg-zinc-900 border border-zinc-800 rounded-lg px-3 py-2 text-xs text-white placeholder-zinc-500 focus:outline-none focus:border-zinc-700"
                />
              </div>
            </div>
          </div>

          <div className="bg-zinc-950/60 border border-zinc-800/80 rounded-xl p-6">
            <div className="flex items-center gap-2 mb-2">
              <Star className="w-4 h-4 text-amber-400" />
              <h2 className="text-base font-semibold text-white">Minimum Rating</h2>
            </div>
            <p className="text-xs text-zinc-400 mb-4">
              Only recommend products meeting or exceeding your preferred minimum star rating.
            </p>

            <div className="flex items-center gap-3">
              <input
                type="range"
                min="0"
                max="5"
                step="0.5"
                value={minRating}
                onChange={(e) => setMinRating(parseFloat(e.target.value))}
                className="flex-1 accent-indigo-500"
              />
              <span className="text-sm font-semibold text-amber-400 font-mono w-16 text-right">
                {minRating > 0 ? `${minRating.toFixed(1)} ★` : 'Any'}
              </span>
            </div>
          </div>
        </div>

        {/* Sensitivities & Availability */}
        <div className="grid grid-cols-1 sm:grid-cols-3 gap-6">
          <div className="bg-zinc-950/60 border border-zinc-800/80 rounded-xl p-6">
            <div className="flex items-center gap-2 mb-2">
              <Zap className="w-4 h-4 text-amber-400" />
              <h3 className="text-sm font-semibold text-white">Deal Sensitivity</h3>
            </div>
            <p className="text-xs text-zinc-400 mb-3">
              How aggressively to boost discounted items.
            </p>
            <select
              value={dealSensitivity}
              onChange={(e) => setDealSensitivity(e.target.value as DealSensitivity)}
              className="w-full bg-zinc-900 border border-zinc-800 rounded-lg px-3 py-2 text-xs text-white focus:outline-none focus:border-zinc-700 cursor-pointer"
            >
              <option value="LOW">Low (Standard)</option>
              <option value="MEDIUM">Medium (Balanced)</option>
              <option value="HIGH">High (Prioritize Big Deals)</option>
            </select>
          </div>

          <div className="bg-zinc-950/60 border border-zinc-800/80 rounded-xl p-6">
            <div className="flex items-center gap-2 mb-2">
              <DollarSign className="w-4 h-4 text-blue-400" />
              <h3 className="text-sm font-semibold text-white">Price Sensitivity</h3>
            </div>
            <p className="text-xs text-zinc-400 mb-3">
              Emphasis placed on lowest current market price.
            </p>
            <select
              value={priceSensitivity}
              onChange={(e) => setPriceSensitivity(e.target.value as PriceSensitivity)}
              className="w-full bg-zinc-900 border border-zinc-800 rounded-lg px-3 py-2 text-xs text-white focus:outline-none focus:border-zinc-700 cursor-pointer"
            >
              <option value="LOW">Low (Features First)</option>
              <option value="MEDIUM">Medium (Balanced Value)</option>
              <option value="HIGH">High (Lowest Price First)</option>
            </select>
          </div>

          <div className="bg-zinc-950/60 border border-zinc-800/80 rounded-xl p-6">
            <div className="flex items-center gap-2 mb-2">
              <PackageCheck className="w-4 h-4 text-emerald-400" />
              <h3 className="text-sm font-semibold text-white">Availability</h3>
            </div>
            <p className="text-xs text-zinc-400 mb-3">
              Filter or penalize out-of-stock items.
            </p>
            <select
              value={availabilityPreference}
              onChange={(e) => setAvailabilityPreference(e.target.value as AvailabilityPreference)}
              className="w-full bg-zinc-900 border border-zinc-800 rounded-lg px-3 py-2 text-xs text-white focus:outline-none focus:border-zinc-700 cursor-pointer"
            >
              <option value="ALL">All Products (Show All)</option>
              <option value="IN_STOCK_ONLY">In-Stock Only</option>
            </select>
          </div>
        </div>

        {/* Submit */}
        <div className="flex justify-end pt-4">
          <button
            type="submit"
            disabled={saving}
            className="flex items-center gap-2 px-6 py-2.5 bg-indigo-600 hover:bg-indigo-500 active:scale-[0.98] text-white rounded-lg text-xs font-semibold transition-all disabled:opacity-50 cursor-pointer shadow-lg shadow-indigo-600/20"
          >
            {saving ? (
              <div className="h-4 w-4 rounded-full border-2 border-white border-t-transparent animate-spin" />
            ) : (
              <Save className="w-4 h-4" />
            )}
            Save Preferences
          </button>
        </div>
      </form>
    </div>
  );
};

export default PreferencesPage;
