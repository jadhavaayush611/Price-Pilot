import React, { useEffect, useState } from 'react';
import { Link, NavLink } from 'react-router-dom';
import { apiService } from '../services/api';
import { useAuth } from '../context/AuthContext';

interface LayoutProps {
  children: React.ReactNode;
}

export const Layout: React.FC<LayoutProps> = ({ children }) => {
  const { user, isAuthenticated, logout, isAdmin } = useAuth();
  const [healthStatus, setHealthStatus] = useState<'LOADING' | 'UP' | 'DOWN'>('LOADING');

  useEffect(() => {
    apiService.checkHealth()
      .then((res) => {
        if (res.status === 'UP') {
          setHealthStatus('UP');
        } else {
          setHealthStatus('DOWN');
        }
      })
      .catch(() => {
        setHealthStatus('DOWN');
      });
  }, []);

  return (
    <div className="min-h-screen bg-[#030303] text-zinc-100 flex flex-col antialiased">
      {/* Header */}
      <header className="sticky top-0 z-50 backdrop-blur-md bg-[#030303]/80 border-b border-zinc-900">
        <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 h-16 flex items-center justify-between">
          <div className="flex items-center gap-6">
            <Link to="/" className="flex items-center gap-2">
              <span className="bg-gradient-to-r from-white to-zinc-400 bg-clip-text text-transparent text-xl font-bold tracking-tight">
                PricePilot
              </span>
            </Link>
            <nav className="hidden md:flex items-center gap-6 text-sm">
              <NavLink to="/" className={({ isActive }) => `transition-colors ${isActive ? 'text-zinc-100 font-semibold border-b border-zinc-100 pb-0.5' : 'text-zinc-400 hover:text-zinc-100'}`}>Discover</NavLink>
              <NavLink to="/search" className={({ isActive }) => `transition-colors ${isActive ? 'text-zinc-100 font-semibold border-b border-zinc-100 pb-0.5' : 'text-zinc-400 hover:text-zinc-100'}`}>Compare</NavLink>
              {isAuthenticated && (
                <NavLink to="/saved-products" className={({ isActive }) => `transition-colors ${isActive ? 'text-zinc-100 font-semibold border-b border-zinc-100 pb-0.5' : 'text-zinc-400 hover:text-zinc-100'}`}>Saved Products</NavLink>
              )}
              {isAuthenticated && (
                <NavLink to="/watchlist" className={({ isActive }) => `transition-colors ${isActive ? 'text-zinc-100 font-semibold border-b border-zinc-100 pb-0.5' : 'text-zinc-400 hover:text-zinc-100'}`}>Watchlist</NavLink>
              )}
              {isAuthenticated && isAdmin() && (
                <>
                  <NavLink to="/admin/products" className={({ isActive }) => `transition-colors ${isActive ? 'text-zinc-100 font-semibold border-b border-zinc-100 pb-0.5' : 'text-zinc-400 hover:text-zinc-100'}`}>Manage Products</NavLink>
                  <NavLink to="/admin/sellers" className={({ isActive }) => `transition-colors ${isActive ? 'text-zinc-100 font-semibold border-b border-zinc-100 pb-0.5' : 'text-zinc-400 hover:text-zinc-100'}`}>Manage Sellers</NavLink>
                  <NavLink to="/admin/prices" className={({ isActive }) => `transition-colors ${isActive ? 'text-zinc-100 font-semibold border-b border-zinc-100 pb-0.5' : 'text-zinc-400 hover:text-zinc-100'}`}>Manage Prices</NavLink>
                </>
              )}
            </nav>
          </div>

          <div className="flex items-center gap-4">
            <div className="flex items-center gap-2 px-3 py-1.5 rounded-full bg-zinc-950 border border-zinc-900 text-xs">
              <span className={`h-1.5 w-1.5 rounded-full ${
                healthStatus === 'UP' ? 'bg-emerald-500 shadow-[0_0_8px_rgba(16,185,129,0.5)]' :
                healthStatus === 'DOWN' ? 'bg-rose-500 shadow-[0_0_8px_rgba(244,63,94,0.5)]' : 'bg-amber-500'
              }`} />
              <span className="text-zinc-400 font-medium">
                {healthStatus === 'LOADING' && 'Connecting...'}
                {healthStatus === 'UP' && 'Engine Connected'}
                {healthStatus === 'DOWN' && 'Local Offline'}
              </span>
            </div>

            {isAuthenticated && user ? (
              <div className="flex items-center gap-3">
                <span className="text-xs text-zinc-400 hidden sm:inline">
                  Welcome, <span className="text-white font-semibold">{user.firstName}</span> <span className="text-[10px] uppercase tracking-wider bg-zinc-900 border border-zinc-800 px-1.5 py-0.5 rounded text-zinc-500 font-mono font-bold ml-1">{user.role}</span>
                </span>
                <button
                  onClick={logout}
                  className="px-3 py-1.5 text-xs font-semibold text-zinc-300 hover:text-white bg-zinc-900 border border-zinc-800 rounded-lg hover:border-zinc-700 active:scale-[0.98] transition-all cursor-pointer"
                >
                  Logout
                </button>
              </div>
            ) : (
              <div className="flex items-center gap-2">
                <Link
                  to="/login"
                  className="px-3 py-1.5 text-xs font-semibold text-zinc-300 hover:text-white bg-zinc-900 border border-zinc-800 rounded-lg hover:border-zinc-700 active:scale-[0.98] transition-all"
                >
                  Login
                </Link>
                <Link
                  to="/register"
                  className="px-3 py-1.5 text-xs font-semibold text-black bg-white rounded-lg hover:bg-zinc-200 active:scale-[0.98] transition-all animate-pulse-slow"
                >
                  Register
                </Link>
              </div>
            )}
          </div>
        </div>
      </header>

      {/* Main Content */}
      <main className="flex-grow max-w-7xl w-full mx-auto px-4 sm:px-6 lg:px-8 py-8">
        {children}
      </main>

      {/* Footer */}
      <footer className="border-t border-zinc-900 bg-[#030303] py-8 text-zinc-600">
        <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 flex flex-col md:flex-row items-center justify-between gap-4">
          <div className="flex items-center gap-2">
            <span className="text-zinc-400 font-semibold tracking-tight">PricePilot</span>
            <span className="text-xs text-zinc-700">|</span>
            <p className="text-xs">Your personal shopping search engine.</p>
          </div>
          <p className="text-xs text-zinc-500">
            &copy; {new Date().getFullYear()} PricePilot. Developed with precision.
          </p>
        </div>
      </footer>
    </div>
  );
};
