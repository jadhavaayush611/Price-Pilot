import React, { lazy, Suspense } from 'react';
import { BrowserRouter as Router, Routes, Route } from 'react-router-dom';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { Layout } from './components/Layout';
import { AuthProvider } from './context/AuthContext';
import { ProtectedRoute } from './components/ProtectedRoute';

// Lazy-load page components for optimal code splitting & bundle size reduction
const LandingPage = lazy(() => import('./pages/LandingPage'));
const SearchPage = lazy(() => import('./pages/SearchPage'));
const ProductPage = lazy(() => import('./pages/ProductPage'));
const TrendingProductsPage = lazy(() => import('./pages/TrendingProductsPage'));
const ProductManagementPage = lazy(() => import('./pages/ProductManagementPage').then(m => ({ default: m.ProductManagementPage })));
const SellerManagementPage = lazy(() => import('./pages/SellerManagementPage').then(m => ({ default: m.SellerManagementPage })));
const PriceManagementPage = lazy(() => import('./pages/PriceManagementPage').then(m => ({ default: m.PriceManagementPage })));
const LoginPage = lazy(() => import('./pages/LoginPage'));
const RegisterPage = lazy(() => import('./pages/RegisterPage'));
const SavedProductsPage = lazy(() => import('./pages/SavedProductsPage'));
const WatchlistPage = lazy(() => import('./pages/WatchlistPage'));
const DashboardPage = lazy(() => import('./pages/DashboardPage'));
const AiAssistantPage = lazy(() => import('./pages/AiAssistantPage').then(m => ({ default: m.AiAssistantPage })));

// Create TanStack Query Client with optimal caching configuration
const queryClient = new QueryClient({
  defaultOptions: {
    queries: {
      refetchOnWindowFocus: false,
      retry: 1,
      staleTime: 1000 * 60 * 5, // 5 minutes fresh cache
      gcTime: 1000 * 60 * 15,    // 15 minutes garbage collection time
    },
  },
});

const PageLoader: React.FC = () => (
  <div className="flex items-center justify-center min-h-[400px] w-full">
    <div className="h-8 w-8 rounded-full border-2 border-zinc-800 border-t-zinc-200 animate-spin" />
  </div>
);

export const App: React.FC = () => {
  return (
    <QueryClientProvider client={queryClient}>
      <AuthProvider>
        <Router>
          <Layout>
            <Suspense fallback={<PageLoader />}>
              <Routes>
                {/* Public Routes */}
                <Route path="/" element={<LandingPage />} />
                <Route path="/search" element={<SearchPage />} />
                <Route path="/product/:id" element={<ProductPage />} />
                <Route path="/trending" element={<TrendingProductsPage />} />
                <Route path="/login" element={<LoginPage />} />
                <Route path="/register" element={<RegisterPage />} />
                <Route
                  path="/saved-products"
                  element={
                    <ProtectedRoute>
                      <SavedProductsPage />
                    </ProtectedRoute>
                  }
                />
                <Route
                  path="/watchlist"
                  element={
                    <ProtectedRoute>
                      <WatchlistPage />
                    </ProtectedRoute>
                  }
                />
                <Route
                  path="/dashboard"
                  element={
                    <ProtectedRoute>
                      <DashboardPage />
                    </ProtectedRoute>
                  }
                />
                <Route
                  path="/assistant"
                  element={
                    <ProtectedRoute>
                      <AiAssistantPage />
                    </ProtectedRoute>
                  }
                />

                {/* Admin Protected Routes */}
                <Route
                  path="/admin/products"
                  element={
                    <ProtectedRoute adminOnly={true}>
                      <ProductManagementPage />
                    </ProtectedRoute>
                  }
                />
                <Route
                  path="/admin/sellers"
                  element={
                    <ProtectedRoute adminOnly={true}>
                      <SellerManagementPage />
                    </ProtectedRoute>
                  }
                />
                <Route
                  path="/admin/prices"
                  element={
                    <ProtectedRoute adminOnly={true}>
                      <PriceManagementPage />
                    </ProtectedRoute>
                  }
                />
              </Routes>
            </Suspense>
          </Layout>
        </Router>
      </AuthProvider>
    </QueryClientProvider>
  );
};

export default App;
