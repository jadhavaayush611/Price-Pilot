import React, { useState } from 'react';
import { Link, NavLink } from 'react-router-dom';
import { useAuth } from '../context/AuthContext';
import { 
  Menu, 
  X, 
  Search, 
  Layers, 
  Sparkles, 
  TrendingUp, 
  Activity, 
  Bot, 
  LayoutDashboard, 
  Bookmark, 
  Bell, 
  Sliders, 
  Shield 
} from 'lucide-react';
import { NotificationCenter } from './notifications/NotificationCenter';

interface LayoutProps {
  children: React.ReactNode;
}

export const Layout: React.FC<LayoutProps> = ({ children }) => {
  const { user, isAuthenticated, logout, isAdmin } = useAuth();
  const [mobileMenuOpen, setMobileMenuOpen] = useState(false);

  const navLinkClass = ({ isActive }: { isActive: boolean }) =>
    `transition-colors text-xs font-medium ${
      isActive 
        ? 'text-white font-semibold' 
        : 'text-zinc-400 hover:text-zinc-100'
    }`;

  const mobileNavLinkClass = ({ isActive }: { isActive: boolean }) =>
    `flex items-center gap-2.5 px-3 py-2 rounded-xl text-sm font-medium transition-colors ${
      isActive 
        ? 'bg-zinc-900 text-white font-semibold' 
        : 'text-zinc-400 hover:text-zinc-100 hover:bg-zinc-900/50'
    }`;

  return (
    <div className="min-h-screen bg-transparent text-zinc-100 flex flex-col antialiased relative z-10">
      {/* Header */}
      <header className="sticky top-0 z-50 backdrop-blur-md bg-[#030303]/85 border-b border-zinc-900/80">
        <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 h-16 flex items-center justify-between gap-4">
          <div className="flex items-center gap-8">
            <Link to="/" className="flex items-center gap-2 group shrink-0">
              <span className="bg-gradient-to-r from-white via-zinc-200 to-zinc-400 bg-clip-text text-transparent text-xl font-extrabold tracking-tight group-hover:from-white group-hover:to-white transition-all">
                PricePilot
              </span>
            </Link>

            {/* Desktop Navigation */}
            <nav className="hidden lg:flex items-center gap-6">
              {/* Primary Shopping */}
              <div className="flex items-center gap-4">
                <NavLink to="/" className={navLinkClass}>Discover</NavLink>
                <NavLink to="/compare" className={navLinkClass}>Compare</NavLink>
                <NavLink to="/recommendations" className={navLinkClass}>Recommendations</NavLink>
                <NavLink to="/trending" className={navLinkClass}>Trending</NavLink>
              </div>

              {/* Tools & Personal Navigation */}
              {isAuthenticated ? (
                <>
                  <div className="h-3.5 w-px bg-zinc-800" />
                  <div className="flex items-center gap-4">
                    <NavLink to="/assistant" className={navLinkClass}>AI Assistant</NavLink>
                    <NavLink to="/analytics" className={navLinkClass}>Analytics</NavLink>
                    <NavLink to="/watchlist" className={navLinkClass}>Watchlist</NavLink>
                    <NavLink to="/saved-products" className={navLinkClass}>Saved</NavLink>
                  </div>
                </>
              ) : (
                <>
                  <div className="h-3.5 w-px bg-zinc-800" />
                  <div className="flex items-center gap-4">
                    <NavLink to="/analytics" className={navLinkClass}>Analytics</NavLink>
                  </div>
                </>
              )}

              {/* Admin Section */}
              {isAuthenticated && isAdmin() && (
                <>
                  <div className="h-3.5 w-px bg-zinc-800" />
                  <div className="flex items-center gap-3">
                    <NavLink to="/admin/products" className={({ isActive }) => `text-[11px] font-mono px-2 py-0.5 rounded border ${isActive ? 'bg-amber-950/60 border-amber-700 text-amber-300 font-bold' : 'border-zinc-800 text-zinc-400 hover:text-amber-300'}`}>Admin</NavLink>
                  </div>
                </>
              )}
            </nav>
          </div>

          <div className="flex items-center gap-3.5">
            {isAuthenticated && user ? (
              <div className="flex items-center gap-3">
                <NotificationCenter />
                <NavLink to="/dashboard/v2" className={navLinkClass} title="Shopping Dashboard">
                  Dashboard
                </NavLink>
                <NavLink to="/settings/preferences" className={navLinkClass} title="Shopping Preferences">
                  Preferences
                </NavLink>
                <button
                  onClick={logout}
                  className="px-3 py-1.5 text-xs font-semibold text-zinc-300 hover:text-white bg-zinc-900 border border-zinc-800 rounded-xl hover:border-zinc-700 active:scale-[0.98] transition-all cursor-pointer"
                >
                  Logout
                </button>
              </div>
            ) : (
              <div className="flex items-center gap-2">
                <Link
                  to="/login"
                  className="px-3 py-1.5 text-xs font-semibold text-zinc-300 hover:text-white bg-zinc-900 border border-zinc-800 rounded-xl hover:border-zinc-700 active:scale-[0.98] transition-all"
                >
                  Login
                </Link>
                <Link
                  to="/register"
                  className="px-3.5 py-1.5 text-xs font-semibold text-black bg-white rounded-xl hover:bg-zinc-200 active:scale-[0.98] transition-all shadow-sm"
                >
                  Register
                </Link>
              </div>
            )}

            {/* Mobile Navigation Toggle */}
            <button
              onClick={() => setMobileMenuOpen(!mobileMenuOpen)}
              className="lg:hidden p-2 rounded-xl bg-zinc-900 border border-zinc-800 text-zinc-400 hover:text-white transition-colors"
              aria-label="Toggle Navigation Menu"
            >
              {mobileMenuOpen ? <X size={18} /> : <Menu size={18} />}
            </button>
          </div>
        </div>

        {/* Mobile Navigation Drawer */}
        {mobileMenuOpen && (
          <div className="lg:hidden border-t border-zinc-900 bg-zinc-950/95 backdrop-blur-xl px-4 py-4 space-y-4">
            <div className="space-y-1">
              <span className="text-[10px] font-bold uppercase tracking-wider text-zinc-500 px-3 block">Shopping</span>
              <NavLink to="/" onClick={() => setMobileMenuOpen(false)} className={mobileNavLinkClass}>
                <Search size={16} /> Discover
              </NavLink>
              <NavLink to="/compare" onClick={() => setMobileMenuOpen(false)} className={mobileNavLinkClass}>
                <Layers size={16} /> Compare
              </NavLink>
              <NavLink to="/recommendations" onClick={() => setMobileMenuOpen(false)} className={mobileNavLinkClass}>
                <Sparkles size={16} /> Recommendations
              </NavLink>
              <NavLink to="/trending" onClick={() => setMobileMenuOpen(false)} className={mobileNavLinkClass}>
                <TrendingUp size={16} /> Trending
              </NavLink>
            </div>

            <div className="space-y-1 pt-2 border-t border-zinc-900">
              <span className="text-[10px] font-bold uppercase tracking-wider text-zinc-500 px-3 block">Tools & Intelligence</span>
              <NavLink to="/analytics" onClick={() => setMobileMenuOpen(false)} className={mobileNavLinkClass}>
                <Activity size={16} /> Analytics
              </NavLink>
              {isAuthenticated && (
                <NavLink to="/assistant" onClick={() => setMobileMenuOpen(false)} className={mobileNavLinkClass}>
                  <Bot size={16} /> AI Assistant
                </NavLink>
              )}
            </div>

            {isAuthenticated && (
              <div className="space-y-1 pt-2 border-t border-zinc-900">
                <span className="text-[10px] font-bold uppercase tracking-wider text-zinc-500 px-3 block">Personal</span>
                <NavLink to="/watchlist" onClick={() => setMobileMenuOpen(false)} className={mobileNavLinkClass}>
                  <Bell size={16} /> Watchlist
                </NavLink>
                <NavLink to="/saved-products" onClick={() => setMobileMenuOpen(false)} className={mobileNavLinkClass}>
                  <Bookmark size={16} /> Saved Products
                </NavLink>
                <NavLink to="/dashboard/v2" onClick={() => setMobileMenuOpen(false)} className={mobileNavLinkClass}>
                  <LayoutDashboard size={16} /> Dashboard
                </NavLink>
                <NavLink to="/settings/preferences" onClick={() => setMobileMenuOpen(false)} className={mobileNavLinkClass}>
                  <Sliders size={16} /> Preferences
                </NavLink>
              </div>
            )}

            {isAuthenticated && isAdmin() && (
              <div className="space-y-1 pt-2 border-t border-zinc-900">
                <span className="text-[10px] font-bold uppercase tracking-wider text-amber-500 px-3 block">Admin</span>
                <NavLink to="/admin/products" onClick={() => setMobileMenuOpen(false)} className={mobileNavLinkClass}>
                  <Shield size={16} /> Manage Products
                </NavLink>
                <NavLink to="/admin/sellers" onClick={() => setMobileMenuOpen(false)} className={mobileNavLinkClass}>
                  <Shield size={16} /> Manage Sellers
                </NavLink>
                <NavLink to="/admin/prices" onClick={() => setMobileMenuOpen(false)} className={mobileNavLinkClass}>
                  <Shield size={16} /> Manage Prices
                </NavLink>
              </div>
            )}
          </div>
        )}
      </header>

      {/* Main Content */}
      <main className="flex-grow max-w-7xl w-full mx-auto px-4 sm:px-6 lg:px-8 py-8">
        {children}
      </main>

      {/* Footer */}
      <footer className="border-t border-zinc-900/80 bg-[#030303] py-8 text-zinc-600">
        <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 flex flex-col md:flex-row items-center justify-between gap-4">
          <div className="flex items-center gap-2">
            <span className="text-zinc-400 font-semibold tracking-tight">PricePilot</span>
            <span className="text-xs text-zinc-700">|</span>
            <p className="text-xs">Your personal shopping intelligence and price comparison engine.</p>
          </div>
          <p className="text-xs text-zinc-500">
            &copy; {new Date().getFullYear()} PricePilot. Built for smart shoppers.
          </p>
        </div>
      </footer>
    </div>
  );
};
