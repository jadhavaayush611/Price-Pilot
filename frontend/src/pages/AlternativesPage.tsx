import React, { useEffect, useState, useRef, useCallback } from 'react';
import { useParams, useNavigate, Link } from 'react-router-dom';
import { apiService } from '../services/api';
import { useAuth } from '../context/AuthContext';
import type { AlternativeResponse, AlternativeType } from '../types';
import { AlternativeList } from '../components/alternative/AlternativeList';
import { ArrowLeft, Sparkles, Layers, ShieldAlert, LogIn } from 'lucide-react';
import { motion } from 'framer-motion';

export const AlternativesPage: React.FC = () => {
  const { id } = useParams<{ id: string }>();
  const navigate = useNavigate();
  const { isAuthenticated, user } = useAuth();

  const [selectedType, setSelectedType] = useState<AlternativeType>('SIMILAR');
  const [isPersonalized, setIsPersonalized] = useState(false);
  const [response, setResponse] = useState<AlternativeResponse | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  // Race safety ref to prevent stale response overwrites
  const activeRequestRef = useRef<string>('');
  const activeUserIdRef = useRef<string | null>(user?.id || null);

  const fetchAlternatives = useCallback(async () => {
    if (!id) return;
    setLoading(true);
    setError(null);

    const requestId = `${id}-${selectedType}-${isPersonalized ? 'p' : 'g'}-${Date.now()}`;
    activeRequestRef.current = requestId;

    try {
      let data: AlternativeResponse;
      if (isPersonalized && isAuthenticated) {
        data = await apiService.getPersonalizedAlternatives(id, { type: selectedType, limit: 12 });
      } else {
        data = await apiService.getAlternatives(id, { type: selectedType, limit: 12 });
      }

      // Check race condition
      if (activeRequestRef.current === requestId) {
        setResponse(data);
      }
    } catch (err: unknown) {
      if (activeRequestRef.current === requestId) {
        const errorObj = err as { response?: { status?: number; data?: { message?: string } }; message?: string };
        if (errorObj.response?.status === 401 || errorObj.response?.status === 403) {
          setError('Authentication is required to view personalized alternatives.');
        } else {
          setError(errorObj.response?.data?.message || errorObj.message || 'Failed to load product alternatives.');
        }
      }
    } finally {
      if (activeRequestRef.current === requestId) {
        setLoading(false);
      }
    }
  }, [id, selectedType, isPersonalized, isAuthenticated]);

  // Handle user login / logout changes
  useEffect(() => {
    const currentUserId = user?.id || null;
    if (activeUserIdRef.current !== currentUserId) {
      activeUserIdRef.current = currentUserId;
      if (!isAuthenticated) {
        setIsPersonalized(false);
      }
    }
  }, [user, isAuthenticated]);

  useEffect(() => {
    fetchAlternatives();
  }, [fetchAlternatives]);

  const handleTogglePersonalized = (enabled: boolean) => {
    if (enabled && !isAuthenticated) {
      navigate('/login', { state: { from: { pathname: `/product/${id}/alternatives` } } });
      return;
    }
    setIsPersonalized(enabled);
  };

  return (
    <motion.div
      initial={{ opacity: 0, y: 10 }}
      animate={{ opacity: 1, y: 0 }}
      transition={{ duration: 0.3 }}
      className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 py-8 space-y-8"
    >
      {/* Top Header & Navigation */}
      <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-4 border-b border-zinc-900 pb-6">
        <div className="space-y-1">
          <Link
            to={`/product/${id}`}
            className="inline-flex items-center gap-2 text-xs font-bold text-zinc-500 hover:text-zinc-300 transition-colors mb-2 group"
          >
            <ArrowLeft className="h-4 w-4 transition-transform group-hover:-translate-x-0.5" />
            Back to Product
          </Link>
          <div className="flex items-center gap-3">
            <div className="h-10 w-10 rounded-xl bg-zinc-900 border border-zinc-800 flex items-center justify-center text-emerald-400">
              <Layers className="h-5 w-5" />
            </div>
            <div>
              <h1 className="text-2xl font-extrabold text-white tracking-tight">
                Alternative Product Finder
              </h1>
              <p className="text-xs text-zinc-400">
                Discover qualified alternatives powered by deterministic scoring & semantic intelligence
              </p>
            </div>
          </div>
        </div>

        {/* Generic vs Personalized Toggle */}
        <div className="flex items-center gap-2 bg-zinc-950 p-1.5 rounded-2xl border border-zinc-900 self-start sm:self-auto">
          <button
            type="button"
            onClick={() => handleTogglePersonalized(false)}
            className={`px-3 py-1.5 rounded-xl text-xs font-semibold transition-all cursor-pointer ${
              !isPersonalized
                ? 'bg-zinc-850 text-white border border-zinc-700 shadow-inner'
                : 'text-zinc-500 hover:text-zinc-300'
            }`}
          >
            All Alternatives
          </button>
          <button
            type="button"
            onClick={() => handleTogglePersonalized(true)}
            className={`px-3 py-1.5 rounded-xl text-xs font-semibold flex items-center gap-1.5 transition-all cursor-pointer ${
              isPersonalized
                ? 'bg-indigo-950 text-indigo-200 border border-indigo-700/60 shadow-inner'
                : 'text-zinc-500 hover:text-zinc-300'
            }`}
          >
            <Sparkles className="w-3.5 h-3.5 text-indigo-400" />
            <span>Personalized for You</span>
          </button>
        </div>
      </div>

      {/* Unauthenticated Personalization Banner */}
      {!isAuthenticated && isPersonalized && (
        <div className="bg-gradient-to-r from-indigo-950/40 via-zinc-950 to-indigo-950/20 border border-indigo-900/50 rounded-2xl p-4 flex items-center justify-between gap-4">
          <div className="flex items-center gap-3">
            <ShieldAlert className="w-5 h-5 text-indigo-400 shrink-0" />
            <div className="text-xs">
              <p className="font-semibold text-indigo-200">Sign in to activate personalized alternatives</p>
              <p className="text-zinc-400">Rank alternatives tailored to your preferred brands, budget, and shopping signals.</p>
            </div>
          </div>
          <Link
            to="/login"
            state={{ from: { pathname: `/product/${id}/alternatives` } }}
            className="inline-flex items-center gap-1.5 px-3.5 py-1.5 text-xs font-bold text-black bg-white hover:bg-zinc-200 rounded-xl transition-all shrink-0"
          >
            <LogIn className="w-3.5 h-3.5" />
            <span>Sign In</span>
          </Link>
        </div>
      )}

      {/* Alternative List */}
      <AlternativeList
        response={response}
        loading={loading}
        error={error}
        isPersonalized={isPersonalized}
        onRetry={fetchAlternatives}
        selectedType={selectedType}
        onSelectType={(type) => setSelectedType(type)}
        sourceProductId={id}
      />
    </motion.div>
  );
};

export default AlternativesPage;
