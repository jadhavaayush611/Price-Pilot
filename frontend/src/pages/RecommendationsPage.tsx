import React, { useEffect, useState, useCallback, useRef } from 'react';
import { Link } from 'react-router-dom';
import { Sparkles, Sliders, RefreshCw, LogIn, UserPlus, Info, CheckCircle2, Shield, ArrowRight } from 'lucide-react';
import { apiService } from '../services/api';
import { useAuth } from '../context/AuthContext';
import type { RecommendationResponse } from '../types';
import { RecommendationList } from '../components/recommendation/RecommendationList';
import { RecommendationSkeleton } from '../components/recommendation/RecommendationSkeleton';

export const RecommendationsPage: React.FC = () => {
  const { user, isAuthenticated, isLoading: authLoading } = useAuth();

  const [recommendations, setRecommendations] = useState<RecommendationResponse | null>(null);
  const [loading, setLoading] = useState<boolean>(true);
  const [refreshing, setRefreshing] = useState<boolean>(false);
  const [error, setError] = useState<string | null>(null);

  // Track active user ID to ensure request safety and prevent race conditions on user switch
  const activeUserIdRef = useRef<string | null>(null);

  const fetchPersonalizedRecommendations = useCallback(async (isRefresh = false) => {
    if (!isAuthenticated || !user) {
      setRecommendations(null);
      setLoading(false);
      setRefreshing(false);
      return;
    }

    const currentUserId = user.id;
    activeUserIdRef.current = currentUserId;

    if (isRefresh) {
      setRefreshing(true);
    } else {
      setLoading(true);
    }
    setError(null);

    try {
      const data = await apiService.getPersonalizedRecommendations(12);

      // Verify that the user identity has not changed while the request was in flight
      if (activeUserIdRef.current === currentUserId) {
        setRecommendations(data);
      }
    } catch (err: unknown) {
      if (activeUserIdRef.current === currentUserId) {
        console.error('Failed to load personalized recommendations:', err);
        const errorObj = err as { response?: { status?: number; data?: { message?: string } } };
        if (errorObj.response?.status === 401 || errorObj.response?.status === 403) {
          setError('Authentication required to access personalized recommendations.');
        } else {
          setError('Unable to load personalized recommendations. Please check your connection and try again.');
        }
      }
    } finally {
      if (activeUserIdRef.current === currentUserId) {
        setLoading(false);
        setRefreshing(false);
      }
    }
  }, [isAuthenticated, user]);

  // Refetch recommendations whenever authenticated user changes or logs out
  useEffect(() => {
    if (!authLoading) {
      if (isAuthenticated && user) {
        fetchPersonalizedRecommendations();
      } else {
        // Clear state immediately on logout or unauthenticated state
        setRecommendations(null);
        setLoading(false);
        setError(null);
        activeUserIdRef.current = null;
      }
    }
  }, [isAuthenticated, user, authLoading, fetchPersonalizedRecommendations]);

  // Auth loading state
  if (authLoading) {
    return (
      <main className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 py-8 space-y-6 animate-pulse" aria-busy="true">
        <div className="h-8 bg-[var(--color-surface-accent)] rounded-lg w-1/4" />
        <div className="h-4 bg-[var(--color-surface-accent)]/60 rounded w-1/3" />
        <RecommendationSkeleton />
      </main>
    );
  }

  // State B: Unauthenticated State
  if (!isAuthenticated) {
    return (
      <main className="max-w-4xl mx-auto px-4 sm:px-6 lg:px-8 py-16 text-center space-y-8" aria-label="Personalized Recommendations Sign-in Required">
        <div className="mx-auto w-16 h-16 rounded-2xl bg-indigo-500/15 border border-indigo-500/30 flex items-center justify-center shadow-lg">
          <Sparkles className="w-8 h-8 text-indigo-600 dark:text-indigo-400" />
        </div>

        <div className="space-y-3 max-w-xl mx-auto">
          <h1 className="text-3xl font-extrabold text-[var(--color-foreground)] tracking-tight">
            Personalized Recommendations
          </h1>
          <p className="text-sm text-[var(--color-muted-foreground)] leading-relaxed">
            Sign in to get product recommendations tailored to your preferences, preferred brands, budget bounds, and recent shopping interests.
          </p>
        </div>

        <div className="flex flex-col sm:flex-row items-center justify-center gap-3 pt-2">
          <Link
            to="/login"
            className="w-full sm:w-auto inline-flex items-center justify-center gap-2 px-6 py-3 bg-[var(--color-primary)] text-[var(--color-primary-foreground)] font-bold text-xs rounded-xl hover:bg-[var(--color-primary-hover)] active:scale-[0.98] transition-all shadow-md"
          >
            <LogIn className="w-4 h-4" />
            <span>Sign In to Continue</span>
          </Link>
          <Link
            to="/register"
            className="w-full sm:w-auto inline-flex items-center justify-center gap-2 px-6 py-3 bg-[var(--color-surface-accent)] border border-[var(--color-border)] hover:border-[var(--color-border)]/80 text-[var(--color-foreground)] font-semibold text-xs rounded-xl hover:bg-[var(--color-surface)] active:scale-[0.98] transition-all"
          >
            <UserPlus className="w-4 h-4 text-[var(--color-muted-foreground)]" />
            <span>Create Free Account</span>
          </Link>
        </div>

        <div className="pt-8 border-t border-[var(--color-border)] grid grid-cols-1 sm:grid-cols-3 gap-4 text-left max-w-2xl mx-auto">
          <div className="p-4 rounded-xl bg-[var(--color-surface)] border border-[var(--color-border)] space-y-1.5">
            <h3 className="text-xs font-bold text-[var(--color-foreground)] flex items-center gap-1.5">
              <CheckCircle2 className="w-3.5 h-3.5 text-emerald-600 dark:text-emerald-400" />
              Preference Matching
            </h3>
            <p className="text-[11px] text-[var(--color-muted-foreground)]">
              Prioritize your favorite brands and target budget thresholds.
            </p>
          </div>

          <div className="p-4 rounded-xl bg-[var(--color-surface)] border border-[var(--color-border)] space-y-1.5">
            <h3 className="text-xs font-bold text-[var(--color-foreground)] flex items-center gap-1.5">
              <CheckCircle2 className="w-3.5 h-3.5 text-indigo-600 dark:text-indigo-400" />
              Verified Matching
            </h3>
            <p className="text-[11px] text-[var(--color-muted-foreground)]">
              Clear factual explanations for why each product matches your profile.
            </p>
          </div>

          <div className="p-4 rounded-xl bg-[var(--color-surface)] border border-[var(--color-border)] space-y-1.5">
            <h3 className="text-xs font-bold text-[var(--color-foreground)] flex items-center gap-1.5">
              <Shield className="w-3.5 h-3.5 text-cyan-600 dark:text-cyan-400" />
              Zero Manipulation
            </h3>
            <p className="text-[11px] text-[var(--color-muted-foreground)]">
              Deterministic ranking without sponsored promotions or affiliate bias.
            </p>
          </div>
        </div>
      </main>
    );
  }

  const hasEvidence = (recommendations?.personalizationEvidence && recommendations.personalizationEvidence.length > 0) ||
    (recommendations?.scores && recommendations.scores.some(s => s.personalizationContribution && s.personalizationContribution !== 0));

  return (
    <main className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 py-8 space-y-8" aria-label="Personalized Product Recommendations">
      {/* Page Header */}
      <header className="flex flex-col sm:flex-row sm:items-center justify-between gap-4 border-b border-[var(--color-border)] pb-6">
        <div>
          <h1 className="text-2xl font-bold tracking-tight text-[var(--color-foreground)]">
            Recommended for You
          </h1>
          <p className="text-[var(--color-muted-foreground)] text-xs mt-1">
            Tailored to your saved brands, budget bounds, and shopping activity.
          </p>
        </div>

        <div className="flex items-center gap-3">
          <button
            type="button"
            onClick={() => fetchPersonalizedRecommendations(true)}
            disabled={refreshing || loading}
            className="inline-flex items-center gap-2 px-3.5 py-2 rounded-xl bg-[var(--color-surface-accent)] border border-[var(--color-border)] hover:bg-[var(--color-surface)] text-[var(--color-foreground)] text-xs font-semibold transition-all disabled:opacity-50 cursor-pointer"
            aria-label="Refresh Recommendations"
          >
            <RefreshCw className={`h-3.5 w-3.5 ${refreshing ? 'animate-spin' : ''}`} />
            <span>{refreshing ? 'Refreshing...' : 'Refresh'}</span>
          </button>
          <Link
            to="/settings/preferences"
            className="inline-flex items-center gap-2 px-4 py-2 rounded-xl bg-[var(--color-primary)] hover:bg-[var(--color-primary-hover)] text-[var(--color-primary-foreground)] text-xs font-semibold transition-all shadow-sm active:scale-[0.98]"
          >
            <Sliders className="h-3.5 w-3.5" />
            <span>Adjust Preferences</span>
          </Link>
        </div>
      </header>

      {/* Cold Start / Empty Personalization Banner (when authenticated but no active adjustment signals yet) */}
      {!loading && !error && recommendations && !hasEvidence && (
        <section aria-label="Personalization Onboarding" className="p-4 rounded-2xl bg-[var(--color-surface)] border border-[var(--color-border)] flex flex-col sm:flex-row sm:items-center justify-between gap-4 shadow-sm">
          <div className="flex items-start gap-3">
            <div className="p-2 rounded-xl bg-indigo-500/15 border border-indigo-500/30 text-indigo-600 dark:text-indigo-400 shrink-0 mt-0.5">
              <Info className="w-4 h-4" />
            </div>
            <div className="space-y-0.5">
              <h3 className="text-xs font-bold text-[var(--color-foreground)]">Welcome to PricePilot Personalization</h3>
              <p className="text-xs text-[var(--color-muted-foreground)] leading-relaxed">
                Your recommendations will become more tailored as PricePilot learns what matters to you. Configure your preferred brands and budget bounds to jumpstart your recommendations.
              </p>
            </div>
          </div>
          <Link
            to="/settings/preferences"
            className="inline-flex items-center gap-1.5 px-3.5 py-1.5 rounded-xl bg-[var(--color-surface-accent)] border border-[var(--color-border)] hover:border-[var(--color-border)]/80 text-xs font-semibold text-[var(--color-foreground)] hover:text-[var(--color-foreground)] shrink-0 self-start sm:self-auto transition-colors"
          >
            <span>Set Preferences</span>
            <ArrowRight className="w-3 h-3" />
          </Link>
        </section>
      )}

      {/* Main Recommendations List */}
      <RecommendationList
        recommendations={recommendations}
        loading={loading}
        error={error}
        isPersonalized={true}
        onRetry={() => fetchPersonalizedRecommendations()}
      />

      {/* Why these recommendations? Explainability & Architecture Section */}
      {!loading && !error && recommendations && recommendations.recommendedProducts && recommendations.recommendedProducts.length > 0 && (
        <section aria-labelledby="why-recommendations-heading" className="pt-8 border-t border-[var(--color-border)] space-y-4">
          <div className="flex items-center justify-between">
            <h2 id="why-recommendations-heading" className="text-base font-bold text-[var(--color-foreground)] tracking-tight flex items-center gap-2">
              <Info className="w-4 h-4 text-indigo-600 dark:text-indigo-400" />
              How PricePilot Personalization Works
            </h2>
            <Link to="/settings/preferences" className="text-xs font-semibold text-indigo-600 dark:text-indigo-400 hover:underline">
              Manage Preferences &rarr;
            </Link>
          </div>

          <div className="grid grid-cols-1 md:grid-cols-2 gap-4 text-xs">
            <div className="p-4 rounded-xl bg-[var(--color-surface)] border border-[var(--color-border)] space-y-2">
              <h3 className="font-bold text-[var(--color-foreground)] uppercase tracking-wider text-[11px]">
                1. Authoritative Product Facts
              </h3>
              <p className="text-[var(--color-muted-foreground)] leading-relaxed">
                Market prices, retailer availability, seller ratings, and discount calculations remain 100% objective and verified in real-time. Personalization never alters product facts.
              </p>
            </div>

            <div className="p-4 rounded-xl bg-[var(--color-surface)] border border-[var(--color-border)] space-y-2">
              <h3 className="font-bold text-[var(--color-foreground)] uppercase tracking-wider text-[11px]">
                2. Tailored Deterministic Ranking
              </h3>
              <p className="text-[var(--color-muted-foreground)] leading-relaxed">
                Products rank higher for you when they match your configured target budget, preferred manufacturers, and shopping interests. Evidence badges explain every match.
              </p>
            </div>
          </div>
        </section>
      )}
    </main>
  );
};

export default RecommendationsPage;
