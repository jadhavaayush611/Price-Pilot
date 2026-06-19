import React, { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { apiService } from '../services/api';

interface LayoutProps {
  children: React.ReactNode;
}

export const Layout: React.FC<LayoutProps> = ({ children }) => {
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
            <nav className="hidden md:flex items-center gap-6 text-sm text-zinc-400">
              <Link to="/" className="hover:text-zinc-100 transition-colors">Discover</Link>
              <Link to="/search" className="hover:text-zinc-100 transition-colors">Compare</Link>
              <Link to="/admin/products" className="hover:text-zinc-100 transition-colors">Manage Products</Link>
              <Link to="/admin/sellers" className="hover:text-zinc-100 transition-colors">Manage Sellers</Link>
              <Link to="/admin/prices" className="hover:text-zinc-100 transition-colors">Manage Prices</Link>
            </nav>
          </div>

          <div className="flex items-center gap-3">
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
