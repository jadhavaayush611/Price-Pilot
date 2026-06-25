import React, { useState, useEffect } from 'react';
import { useNavigate } from 'react-router-dom';
import { motion } from 'framer-motion';
import { 
  Search, 
  ArrowRight, 
  Zap, 
  TrendingDown, 
  Sparkles,
  Layers,
  ChevronRight,
  CheckCircle2,
  LineChart
} from 'lucide-react';
import { apiService } from '../services/api';
import type { ProductWithPrices } from '../types';

export const LandingPage: React.FC = () => {
  const [searchQuery, setSearchQuery] = useState('');
  const navigate = useNavigate();

  const [trendingProducts, setTrendingProducts] = useState<ProductWithPrices[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(false);

  useEffect(() => {
    setLoading(true);
    apiService.getTrendingProducts(3)
      .then((data) => {
        setTrendingProducts(data);
      })
      .catch((err) => {
        console.error('Error fetching trending products:', err);
        setError(true);
      })
      .finally(() => {
        setLoading(false);
      });
  }, []);

  const handleSearch = (e: React.FormEvent) => {
    e.preventDefault();
    if (searchQuery.trim()) {
      navigate(`/search?q=${encodeURIComponent(searchQuery)}`);
    } else {
      navigate('/search');
    }
  };

  const handleCategoryClick = (category: string) => {
    navigate(`/search?category=${encodeURIComponent(category)}`);
  };

  // Framer Motion Animation Variants
  const containerVariants = {
    hidden: { opacity: 0 },
    visible: {
      opacity: 1,
      transition: {
        staggerChildren: 0.1,
        delayChildren: 0.1
      }
    }
  };

  const itemVariants = {
    hidden: { opacity: 0, y: 20 },
    visible: {
      opacity: 1,
      y: 0,
      transition: {
        type: 'spring' as const,
        stiffness: 100,
        damping: 15
      }
    }
  };

  return (
    <div className="relative flex flex-col gap-24 md:gap-32 py-12 md:py-20 overflow-hidden">
      {/* Background Orbs */}
      <div className="absolute top-[-10%] left-[50%] translate-x-[-50%] w-[600px] h-[300px] bg-gradient-to-r from-zinc-800/10 to-zinc-700/10 rounded-full blur-[100px] pointer-events-none" />
      <div className="absolute top-[20%] right-[-10%] w-[300px] h-[300px] bg-zinc-800/5 rounded-full blur-[120px] pointer-events-none" />
      <div className="absolute bottom-[20%] left-[-10%] w-[400px] h-[400px] bg-zinc-900/10 rounded-full blur-[150px] pointer-events-none" />

      {/* Hero Section */}
      <section className="relative flex flex-col items-center text-center max-w-5xl mx-auto px-4 z-10">
        <motion.div
          initial={{ opacity: 0, y: -20 }}
          animate={{ opacity: 1, y: 0 }}
          transition={{ duration: 0.6, type: 'spring' }}
          className="inline-flex items-center gap-2 px-3.5 py-1.5 rounded-full bg-zinc-900/60 border border-zinc-800/80 text-xs text-zinc-400 hover:border-zinc-700 transition-colors cursor-pointer mb-8 shadow-inner"
        >
          <span className="flex h-2 w-2 relative">
            <span className="animate-ping absolute inline-flex h-full w-full rounded-full bg-emerald-400 opacity-75"></span>
            <span className="relative inline-flex rounded-full h-2 w-2 bg-emerald-500"></span>
          </span>
          <span className="font-medium tracking-wide">Dynamic Price Engine 2.0 Live</span>
          <ChevronRight className="h-3.5 w-3.5 text-zinc-600" />
        </motion.div>

        <motion.div
          initial={{ opacity: 0, y: 30 }}
          animate={{ opacity: 1, y: 0 }}
          transition={{ duration: 0.8, ease: [0.16, 1, 0.3, 1] }}
          className="flex flex-col gap-6"
        >
          <h1 className="text-5xl sm:text-7xl font-extrabold tracking-tight leading-none bg-gradient-to-b from-white via-zinc-100 to-zinc-500 bg-clip-text text-transparent">
            Find the Best <br className="hidden sm:inline" />
            Price Instantly
          </h1>

          <p className="text-base sm:text-lg text-zinc-400 max-w-2xl mx-auto font-normal leading-relaxed">
            The intelligent search engine that aggregates prices from top retailers. 
            Compare deals, track updates, and purchase directly without markups.
          </p>
        </motion.div>

        {/* Search Bar */}
        <motion.form
          initial={{ opacity: 0, y: 30 }}
          animate={{ opacity: 1, y: 0 }}
          transition={{ duration: 0.8, delay: 0.1, ease: [0.16, 1, 0.3, 1] }}
          onSubmit={handleSearch}
          className="w-full max-w-2xl mt-12 px-2"
        >
          <div className="relative flex items-center p-1.5 rounded-2xl bg-zinc-950/80 border border-zinc-900 focus-within:border-zinc-700/70 focus-within:ring-1 focus-within:ring-zinc-800 focus-within:shadow-[0_0_30px_rgba(255,255,255,0.02)] transition-all duration-300 shadow-2xl backdrop-blur-xl group">
            <div className="absolute inset-x-0 -bottom-px h-px bg-gradient-to-r from-transparent via-zinc-700/35 to-transparent opacity-0 group-focus-within:opacity-100 transition-opacity" />
            
            <Search className="h-5 w-5 text-zinc-500 ml-4 flex-shrink-0 transition-colors group-focus-within:text-zinc-300" />
            <input
              type="text"
              placeholder="Search products, brands, or categories (e.g. iPhone, Sony, OLED)..."
              value={searchQuery}
              onChange={(e) => setSearchQuery(e.target.value)}
              className="w-full px-4 py-3.5 bg-transparent text-zinc-100 placeholder-zinc-600 focus:outline-none text-base antialiased"
            />
            <button
              type="submit"
              className="flex items-center gap-1.5 px-6 py-3 bg-zinc-100 text-black text-sm font-semibold rounded-xl hover:bg-white active:scale-98 transition-all cursor-pointer shadow-md flex-shrink-0"
            >
              Search
              <ArrowRight className="h-4 w-4 transition-transform group-hover:translate-x-0.5" />
            </button>
          </div>
        </motion.form>

        {/* Quick Categories */}
        <motion.div 
          initial={{ opacity: 0 }}
          animate={{ opacity: 1 }}
          transition={{ delay: 0.3, duration: 0.8 }}
          className="flex flex-wrap justify-center items-center gap-2.5 mt-8 max-w-3xl"
        >
          <span className="text-xs text-zinc-500 mr-1">Trending:</span>
          {['Electronics', 'Headphones', 'Laptops', 'Gaming Consoles', 'Smartphones'].map((cat) => (
            <button
              key={cat}
              onClick={() => handleCategoryClick(cat)}
              className="px-3.5 py-1.5 rounded-xl bg-zinc-950 border border-zinc-900/80 hover:border-zinc-700 hover:bg-zinc-900/30 text-xs font-medium text-zinc-400 hover:text-zinc-200 transition-all cursor-pointer active:scale-95"
            >
              {cat}
            </button>
          ))}
        </motion.div>
      </section>

      {/* Features Section */}
      <section className="relative max-w-6xl mx-auto px-6 z-10 w-full">
        <div className="flex flex-col items-center text-center gap-4 mb-16">
          <h2 className="text-xs font-bold tracking-widest text-zinc-500 uppercase">Engine Architecture</h2>
          <p className="text-2xl sm:text-3xl font-extrabold text-white tracking-tight">
            Designed for Speed and Accuracy.
          </p>
        </div>

        <motion.div 
          variants={containerVariants}
          initial="hidden"
          whileInView="visible"
          viewport={{ once: true, margin: "-100px" }}
          className="grid grid-cols-1 md:grid-cols-3 gap-6"
        >
          {/* Feature 1 */}
          <motion.div
            variants={itemVariants}
            whileHover={{ y: -5, borderColor: 'var(--color-zinc-700)' }}
            className="flex flex-col gap-4 p-6 rounded-2xl bg-zinc-950/40 border border-zinc-900/80 backdrop-blur-md transition-all duration-300 group"
          >
            <div className="h-10 w-10 flex items-center justify-center rounded-xl bg-zinc-900 text-zinc-300 border border-zinc-800 shadow-inner group-hover:text-white transition-colors">
              <Zap className="h-5 w-5" />
            </div>
            <h3 className="font-semibold text-zinc-100 text-lg">Instant Aggregator</h3>
            <p className="text-sm text-zinc-400 leading-relaxed">
              Query multiple suppliers concurrently. Our optimized pipeline aggregates prices in real-time to eliminate latency.
            </p>
          </motion.div>

          {/* Feature 2 */}
          <motion.div
            variants={itemVariants}
            whileHover={{ y: -5, borderColor: 'var(--color-zinc-700)' }}
            className="flex flex-col gap-4 p-6 rounded-2xl bg-zinc-950/40 border border-zinc-900/80 backdrop-blur-md transition-all duration-300 group"
          >
            <div className="h-10 w-10 flex items-center justify-center rounded-xl bg-zinc-900 text-zinc-300 border border-zinc-800 shadow-inner group-hover:text-white transition-colors">
              <TrendingDown className="h-5 w-5" />
            </div>
            <h3 className="font-semibold text-zinc-100 text-lg">Smart Deal Discovery</h3>
            <p className="text-sm text-zinc-400 leading-relaxed">
              Instantly spots and labels the lowest prices, calculates discounts, and guides you to the highest saving opportunities.
            </p>
          </motion.div>

          {/* Feature 3 */}
          <motion.div
            variants={itemVariants}
            whileHover={{ y: -5, borderColor: 'var(--color-zinc-700)' }}
            className="flex flex-col gap-4 p-6 rounded-2xl bg-zinc-950/40 border border-zinc-900/80 backdrop-blur-md transition-all duration-300 group"
          >
            <div className="h-10 w-10 flex items-center justify-center rounded-xl bg-zinc-900 text-zinc-300 border border-zinc-800 shadow-inner group-hover:text-white transition-colors">
              <Layers className="h-5 w-5" />
            </div>
            <h3 className="font-semibold text-zinc-100 text-lg">Clean Interface</h3>
            <p className="text-sm text-zinc-400 leading-relaxed">
              No pop-ups, no spam, no tracking. Just clean, developer-grade UI that allows you to compare and find what you need.
            </p>
          </motion.div>
        </motion.div>
      </section>

      {/* Trending Products Section */}
      <section className="relative max-w-6xl mx-auto px-6 z-10 w-full">
        <div className="flex flex-col sm:flex-row sm:items-end justify-between gap-4 mb-10">
          <div className="flex flex-col gap-2">
            <span className="text-xs font-bold tracking-widest text-zinc-500 uppercase flex items-center gap-1.5">
              <Sparkles className="h-3.5 w-3.5 text-zinc-400" />
              Live Rankings
            </span>
            <h2 className="text-2xl sm:text-3xl font-extrabold text-white tracking-tight">
              Trending on PricePilot.
            </h2>
          </div>
          <button
            onClick={() => navigate('/trending')}
            className="inline-flex items-center gap-1 text-sm font-semibold text-zinc-400 hover:text-zinc-200 transition-colors cursor-pointer group"
          >
            Explore all lists
            <ArrowRight className="h-4 w-4 transition-transform group-hover:translate-x-0.5" />
          </button>
        </div>

        {loading ? (
          <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-6">
            {[1, 2, 3].map((n) => (
              <div key={n} className="h-[280px] rounded-2xl bg-zinc-950/40 border border-zinc-900 animate-pulse" />
            ))}
          </div>
        ) : error || trendingProducts.length === 0 ? (
          <div className="flex flex-col items-center justify-center py-12 px-4 border border-zinc-900/60 border-dashed rounded-2xl bg-zinc-950/20 text-center">
            <p className="text-sm text-zinc-500">Failed to load trending products or no data available.</p>
          </div>
        ) : (
          <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-6">
            {trendingProducts.map((product) => {
              const bestPrice = product.prices && product.prices.length > 0 
                ? Math.min(...product.prices.map(p => p.currentPrice))
                : null;
              const originalPrice = product.prices && product.prices.length > 0
                ? product.prices.find(p => p.currentPrice === bestPrice)?.originalPrice
                : null;
              const discount = bestPrice && originalPrice && originalPrice > bestPrice
                ? Math.round(((originalPrice - bestPrice) / originalPrice) * 100)
                : 0;

              return (
                <motion.div
                  key={product.id}
                  whileHover={{ y: -4, borderColor: 'rgba(255,255,255,0.1)' }}
                  onClick={() => navigate(`/product/${product.id}`)}
                  className="flex flex-col h-full rounded-2xl bg-zinc-950/45 border border-zinc-900 hover:shadow-[0_0_30px_rgba(255,255,255,0.01)] transition-all duration-300 cursor-pointer overflow-hidden group"
                >
                  <div className="h-44 w-full bg-zinc-900/40 border-b border-zinc-900 relative flex items-center justify-center overflow-hidden">
                    {product.imageUrl ? (
                      <img
                        src={product.imageUrl}
                        alt={product.name}
                        className="h-full w-full object-cover group-hover:scale-105 transition-transform duration-500"
                      />
                    ) : (
                      <span className="text-zinc-700 text-xs font-medium uppercase font-mono">No image available</span>
                    )}
                    {discount > 0 && (
                      <span className="absolute top-3.5 right-3.5 bg-emerald-500/10 border border-emerald-500/30 text-emerald-400 text-[10px] font-bold uppercase tracking-wider px-2 py-0.5 rounded-full font-mono">
                        -{discount}% OFF
                      </span>
                    )}
                  </div>

                  <div className="flex flex-col flex-grow p-5 justify-between gap-4">
                    <div className="flex flex-col gap-1.5">
                      <span className="text-[10px] font-bold text-zinc-500 uppercase tracking-widest">{product.brand}</span>
                      <h3 className="font-bold text-zinc-100 text-sm group-hover:text-white line-clamp-1 transition-colors">
                        {product.name}
                      </h3>
                      <p className="text-xs text-zinc-500 line-clamp-2 leading-relaxed">
                        {product.description || 'No description provided.'}
                      </p>
                    </div>

                    <div className="flex items-center justify-between mt-1 pt-4 border-t border-zinc-900/80">
                      <div className="flex flex-col">
                        <span className="text-[10px] text-zinc-500 uppercase tracking-wider">Best price</span>
                        <div className="flex items-baseline gap-1.5">
                          <span className="text-base font-extrabold text-white font-mono">
                            {bestPrice ? `$${bestPrice.toLocaleString()}` : 'N/A'}
                          </span>
                          {originalPrice && originalPrice > bestPrice! && (
                            <span className="text-xs text-zinc-600 line-through font-mono">
                              ${originalPrice.toLocaleString()}
                            </span>
                          )}
                        </div>
                      </div>
                      <span className="h-7 w-7 flex items-center justify-center rounded-lg bg-zinc-900 border border-zinc-800 text-zinc-400 group-hover:text-white group-hover:bg-zinc-800 transition-all">
                        <ChevronRight className="h-4 w-4" />
                      </span>
                    </div>
                  </div>
                </motion.div>
              );
            })}
          </div>
        )}
      </section>

      {/* Benefits Section */}
      <section className="relative max-w-6xl mx-auto px-6 z-10 w-full bg-zinc-950/20 border border-zinc-900/50 rounded-3xl py-12 md:py-16 backdrop-blur-sm">
        <div className="grid grid-cols-1 lg:grid-cols-12 gap-12 items-center">
          <div className="lg:col-span-5 flex flex-col gap-4">
            <span className="text-xs font-bold tracking-widest text-zinc-500 uppercase">Why PricePilot?</span>
            <h2 className="text-3xl font-extrabold text-white tracking-tight">
              Smarter Shopping, Engineered.
            </h2>
            <p className="text-sm text-zinc-400 leading-relaxed">
              PricePilot empowers consumers by exposing retail pricing data transparently. We bypass affiliate filters to show you true listing costs.
            </p>
            <div className="mt-4 hidden lg:block">
              <div className="h-40 w-full rounded-2xl bg-gradient-to-br from-zinc-900 to-zinc-950 border border-zinc-900 relative overflow-hidden flex items-center justify-center">
                <LineChart className="h-16 w-16 text-zinc-800" />
                <div className="absolute inset-0 bg-gradient-to-t from-zinc-950 via-transparent to-transparent" />
              </div>
            </div>
          </div>

          <div className="lg:col-span-7 grid grid-cols-1 sm:grid-cols-2 gap-8">
            {/* Benefit 1 */}
            <div className="flex gap-4">
              <div className="mt-1 flex-shrink-0 text-emerald-400">
                <CheckCircle2 className="h-5 w-5" />
              </div>
              <div className="flex flex-col gap-1.5">
                <h4 className="font-semibold text-zinc-200 text-sm">Save Hours of Research</h4>
                <p className="text-xs text-zinc-400 leading-relaxed">
                  Stop manually opening tabs for Amazon, Best Buy, and Walmart. We monitor them all under a single dashboard.
                </p>
              </div>
            </div>

            {/* Benefit 2 */}
            <div className="flex gap-4">
              <div className="mt-1 flex-shrink-0 text-emerald-400">
                <CheckCircle2 className="h-5 w-5" />
              </div>
              <div className="flex flex-col gap-1.5">
                <h4 className="font-semibold text-zinc-200 text-sm">Direct Store Checkout</h4>
                <p className="text-xs text-zinc-400 leading-relaxed">
                  We supply original store hyperlinks directly. Zero middle-man markups, tracking cookies, or custom fees.
                </p>
              </div>
            </div>

            {/* Benefit 3 */}
            <div className="flex gap-4">
              <div className="mt-1 flex-shrink-0 text-emerald-400">
                <CheckCircle2 className="h-5 w-5" />
              </div>
              <div className="flex flex-col gap-1.5">
                <h4 className="font-semibold text-zinc-200 text-sm">Accurate Price Analytics</h4>
                <p className="text-xs text-zinc-400 leading-relaxed">
                  Compare historical lowest prices vs. current listings to identify if the current discount is truly a deal.
                </p>
              </div>
            </div>

            {/* Benefit 4 */}
            <div className="flex gap-4">
              <div className="mt-1 flex-shrink-0 text-emerald-400">
                <CheckCircle2 className="h-5 w-5" />
              </div>
              <div className="flex flex-col gap-1.5">
                <h4 className="font-semibold text-zinc-200 text-sm">Zero Manipulation</h4>
                <p className="text-xs text-zinc-400 leading-relaxed">
                  We rank listings strictly by cost, guaranteeing that sponsor bias won't color your comparison flow.
                </p>
              </div>
            </div>
          </div>
        </div>
      </section>

      {/* Call to Action Section */}
      <section className="relative max-w-5xl mx-auto px-6 z-10 w-full text-center">
        <div className="relative overflow-hidden rounded-3xl bg-gradient-to-b from-zinc-900/60 to-zinc-950 border border-zinc-800/80 py-16 px-6 sm:px-12 shadow-2xl">
          <div className="absolute inset-0 bg-[radial-gradient(ellipse_at_center,_var(--tw-gradient-stops))] from-zinc-800/10 via-transparent to-transparent pointer-events-none" />
          
          <div className="relative z-10 flex flex-col items-center gap-6 max-w-xl mx-auto">
            <Sparkles className="h-8 w-8 text-zinc-400 animate-pulse-slow" />
            
            <h2 className="text-3xl sm:text-4xl font-extrabold text-white tracking-tight leading-tight">
              Ready to find your next purchase?
            </h2>
            
            <p className="text-sm text-zinc-400 leading-relaxed">
              Explore the database or run a quick comparison search. Get instant insights and make your budget go further.
            </p>
            
            <div className="flex flex-col sm:flex-row items-center gap-4 mt-4 w-full justify-center">
              <button
                onClick={() => navigate('/search')}
                className="w-full sm:w-auto px-6 py-3 bg-zinc-100 text-black hover:bg-white text-sm font-semibold rounded-xl transition-all cursor-pointer flex items-center justify-center gap-2 group shadow-lg"
              >
                Launch Search Engine
                <ArrowRight className="h-4 w-4 transition-transform group-hover:translate-x-0.5" />
              </button>
              
              <button
                onClick={() => navigate('/admin/products')}
                className="w-full sm:w-auto px-6 py-3 bg-zinc-900 border border-zinc-800 hover:border-zinc-700 text-zinc-300 hover:text-white text-sm font-semibold rounded-xl transition-all cursor-pointer"
              >
                Manage Inventory
              </button>
            </div>
          </div>
        </div>
      </section>
    </div>
  );
};

export default LandingPage;
