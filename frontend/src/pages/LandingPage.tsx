import React, { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { motion } from 'framer-motion';
import { Search, ArrowRight, DollarSign, ShieldCheck, Zap } from 'lucide-react';

export const LandingPage: React.FC = () => {
  const [searchQuery, setSearchQuery] = useState('');
  const navigate = useNavigate();

  const handleSearch = (e: React.FormEvent) => {
    e.preventDefault();
    if (searchQuery.trim()) {
      navigate(`/search?q=${encodeURIComponent(searchQuery)}`);
    } else {
      navigate('/search');
    }
  };

  const handleCategoryClick = (category: string) => {
    navigate(`/search?q=${encodeURIComponent(category)}`);
  };

  return (
    <div className="flex flex-col gap-20 py-12 md:py-20 overflow-hidden">
      {/* Hero Section */}
      <section className="relative flex flex-col items-center text-center max-w-4xl mx-auto px-4">
        {/* Subtle background glow */}
        <div className="absolute top-[-20%] left-[50%] translate-x-[-50%] w-[350px] h-[350px] bg-white/5 rounded-full blur-[120px] pointer-events-none" />

        <motion.div
          initial={{ opacity: 0, y: 20 }}
          animate={{ opacity: 1, y: 0 }}
          transition={{ duration: 0.6 }}
          className="flex flex-col gap-6"
        >
          <div className="inline-flex self-center items-center gap-2 px-3 py-1 rounded-full bg-zinc-950 border border-zinc-800 text-xs text-zinc-400">
            <span className="h-1.5 w-1.5 rounded-full bg-zinc-200 animate-pulse" />
            Phase 1 Foundation Live
          </div>

          <h1 className="text-4xl sm:text-6xl font-extrabold tracking-tight leading-none bg-gradient-to-b from-white to-zinc-400 bg-clip-text text-transparent">
            Compare prices. <br />
            Find the best deals.
          </h1>

          <p className="text-lg text-zinc-400 max-w-2xl mx-auto">
            PricePilot scans the top stores to bring you real-time prices, deals, and discounts. Save smart, search instantly.
          </p>
        </motion.div>

        {/* Search Bar */}
        <motion.form
          initial={{ opacity: 0, y: 20 }}
          animate={{ opacity: 1, y: 0 }}
          transition={{ duration: 0.6, delay: 0.15 }}
          onSubmit={handleSearch}
          className="w-full max-w-2xl mt-10"
        >
          <div className="relative flex items-center p-2 rounded-xl bg-zinc-950/70 border border-zinc-900 focus-within:border-zinc-700 transition-colors shadow-2xl backdrop-blur-xl">
            <Search className="h-5 w-5 text-zinc-500 ml-3 flex-shrink-0" />
            <input
              type="text"
              placeholder="Search products, brands, or categories..."
              value={searchQuery}
              onChange={(e) => setSearchQuery(e.target.value)}
              className="w-full px-4 py-3 bg-transparent text-zinc-100 placeholder-zinc-500 focus:outline-none text-base"
            />
            <button
              type="submit"
              className="flex items-center gap-1.5 px-6 py-2.5 bg-white text-black text-sm font-semibold rounded-lg hover:bg-zinc-200 transition-colors cursor-pointer"
            >
              Search
              <ArrowRight className="h-4 w-4" />
            </button>
          </div>
        </motion.form>
      </section>

      {/* Quick Categories */}
      <section className="flex flex-col gap-6 items-center">
        <h2 className="text-xs font-semibold uppercase tracking-wider text-zinc-500">Popular Categories</h2>
        <div className="flex flex-wrap justify-center gap-3 max-w-3xl">
          {['Electronics', 'Headphones', 'Laptops', 'Gaming Consoles', 'Smartphones'].map((cat) => (
            <button
              key={cat}
              onClick={() => handleCategoryClick(cat)}
              className="px-4 py-2 rounded-lg bg-zinc-950 border border-zinc-900 hover:border-zinc-800 hover:bg-zinc-900/50 text-sm font-medium text-zinc-300 transition-all cursor-pointer"
            >
              {cat}
            </button>
          ))}
        </div>
      </section>

      {/* Features Grid */}
      <section className="grid grid-cols-1 md:grid-cols-3 gap-8 max-w-5xl mx-auto px-4 mt-10">
        <motion.div
          initial={{ opacity: 0, y: 20 }}
          animate={{ opacity: 1, y: 0 }}
          transition={{ duration: 0.6, delay: 0.3 }}
          className="flex flex-col gap-3 p-6 rounded-2xl bg-zinc-950/40 border border-zinc-900 hover:border-zinc-800/80 transition-all hover:translate-y-[-2px]"
        >
          <div className="h-10 w-10 flex items-center justify-center rounded-xl bg-zinc-900 text-white border border-zinc-800">
            <Zap className="h-5 w-5" />
          </div>
          <h3 className="font-semibold text-zinc-100 mt-2">Instant Search</h3>
          <p className="text-sm text-zinc-400">
            Engineered for speed. Get comparative product prices across multiple major sellers in milliseconds.
          </p>
        </motion.div>

        <motion.div
          initial={{ opacity: 0, y: 20 }}
          animate={{ opacity: 1, y: 0 }}
          transition={{ duration: 0.6, delay: 0.4 }}
          className="flex flex-col gap-3 p-6 rounded-2xl bg-zinc-950/40 border border-zinc-900 hover:border-zinc-800/80 transition-all hover:translate-y-[-2px]"
        >
          <div className="h-10 w-10 flex items-center justify-center rounded-xl bg-zinc-900 text-white border border-zinc-800">
            <DollarSign className="h-5 w-5" />
          </div>
          <h3 className="font-semibold text-zinc-100 mt-2">Best Deal Highlight</h3>
          <p className="text-sm text-zinc-400">
            Our comparison algorithm automatically highlights the lowest price and calculates potential savings for you.
          </p>
        </motion.div>

        <motion.div
          initial={{ opacity: 0, y: 20 }}
          animate={{ opacity: 1, y: 0 }}
          transition={{ duration: 0.6, delay: 0.5 }}
          className="flex flex-col gap-3 p-6 rounded-2xl bg-zinc-950/40 border border-zinc-900 hover:border-zinc-800/80 transition-all hover:translate-y-[-2px]"
        >
          <div className="h-10 w-10 flex items-center justify-center rounded-xl bg-zinc-900 text-white border border-zinc-800">
            <ShieldCheck className="h-5 w-5" />
          </div>
          <h3 className="font-semibold text-zinc-100 mt-2">Verified Sellers</h3>
          <p className="text-sm text-zinc-400">
            We only aggregate prices from trusted, secure online stores. Direct links mean zero markups and absolute transparency.
          </p>
        </motion.div>
      </section>
    </div>
  );
};
