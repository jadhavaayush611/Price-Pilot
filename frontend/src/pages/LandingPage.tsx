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
  CheckCircle2
} from 'lucide-react';
import { apiService } from '../services/api';
import type { ProductWithPrices } from '../types';
import { formatPrice, getDisplayPrice, getSavedCurrency } from '../currency';
import { ProductImage } from '../components/common/ProductImage';
import { FeaturedPricePreview } from '../components/landing/FeaturedPricePreview';

export const LandingPage: React.FC = () => {
  const [searchQuery, setSearchQuery] = useState('');
  const navigate = useNavigate();
  const currency = getSavedCurrency();

  const [trendingProducts, setTrendingProducts] = useState<ProductWithPrices[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(false);

  useEffect(() => {
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
    <div className="relative flex flex-col gap-16 md:gap-24 py-12 md:py-20 overflow-hidden">
      {/* Background Orbs */}
      <div className="absolute top-[-10%] left-[50%] translate-x-[-50%] w-[600px] h-[300px] bg-gradient-to-r from-violet-500/10 to-indigo-500/10 dark:from-zinc-800/10 dark:to-zinc-700/10 rounded-full blur-[100px] pointer-events-none" />
      <div className="absolute top-[20%] right-[-10%] w-[300px] h-[300px] bg-violet-500/5 dark:bg-zinc-800/5 rounded-full blur-[120px] pointer-events-none" />
      <div className="absolute bottom-[20%] left-[-10%] w-[400px] h-[400px] bg-indigo-500/5 dark:bg-zinc-900/10 rounded-full blur-[150px] pointer-events-none" />

      {/* Hero Section */}
      <section className="relative flex flex-col items-center text-center max-w-5xl mx-auto px-4 z-10">
        <motion.div
          initial={{ opacity: 0, y: -20 }}
          animate={{ opacity: 1, y: 0 }}
          transition={{ duration: 0.6, type: 'spring' }}
          className="inline-flex items-center gap-2 px-3.5 py-1.5 rounded-full bg-[var(--color-surface)] border border-[var(--color-border)] text-xs text-[var(--color-muted-foreground)] mb-8 shadow-xs"
        >
          <Sparkles className="h-3.5 w-3.5 text-emerald-500 dark:text-emerald-400" />
          <span className="font-medium tracking-wide">Real-Time Price Intelligence</span>
        </motion.div>

        <motion.div
          initial={{ opacity: 0, y: 30 }}
          animate={{ opacity: 1, y: 0 }}
          transition={{ duration: 0.8, ease: [0.16, 1, 0.3, 1] }}
          className="flex flex-col gap-6"
        >
          <h1 className="text-5xl sm:text-7xl font-extrabold tracking-tight leading-none text-zinc-900 dark:text-transparent dark:bg-clip-text dark:bg-gradient-to-b dark:from-white dark:via-zinc-100 dark:to-zinc-400">
            Find the Best <br className="hidden sm:inline" />
            Price Instantly
          </h1>

          <p className="text-base sm:text-lg text-[var(--color-muted-foreground)] max-w-2xl mx-auto font-normal leading-relaxed">
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
          <div className="relative flex items-center p-1.5 rounded-2xl bg-[var(--color-surface)] border border-[var(--color-border)] focus-within:border-[var(--color-primary)] focus-within:ring-2 focus-within:ring-[var(--color-ring)] transition-all duration-300 shadow-xl backdrop-blur-xl group">
            <div className="absolute inset-x-0 -bottom-px h-px bg-gradient-to-r from-transparent via-[var(--color-primary)]/30 to-transparent opacity-0 group-focus-within:opacity-100 transition-opacity" />
            
            <Search className="h-5 w-5 text-[var(--color-muted-foreground)] ml-4 flex-shrink-0 transition-colors group-focus-within:text-[var(--color-foreground)]" />
            <input
              type="text"
              placeholder="Search products, brands, or categories (e.g. iPhone, Sony, OLED)..."
              value={searchQuery}
              onChange={(e) => setSearchQuery(e.target.value)}
              className="w-full px-4 py-3.5 bg-transparent text-[var(--color-foreground)] placeholder-[var(--color-muted-foreground)] focus:outline-none text-base antialiased"
            />
            <button
              type="submit"
              className="flex items-center gap-1.5 px-6 py-3 bg-[var(--color-primary)] text-[var(--color-primary-foreground)] text-sm font-semibold rounded-xl hover:opacity-90 active:scale-98 transition-all cursor-pointer shadow-md flex-shrink-0"
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
          <span className="text-xs text-[var(--color-muted-foreground)] mr-1">Trending:</span>
          {['Smartphone', 'Laptop', 'Tablet', 'Headphones', 'Gaming Console'].map((cat) => (
            <button
              key={cat}
              onClick={() => handleCategoryClick(cat)}
              className="px-3.5 py-1.5 rounded-xl bg-[var(--color-surface)] border border-[var(--color-border)] hover:border-[var(--color-primary)] hover:bg-[var(--color-surface-accent)] text-xs font-medium text-[var(--color-muted-foreground)] hover:text-[var(--color-foreground)] transition-all cursor-pointer active:scale-95 shadow-xs"
            >
              {cat}
            </button>
          ))}
        </motion.div>
      </section>

      {/* Features Section */}
      <section className="relative max-w-6xl mx-auto px-6 z-10 w-full">
        <div className="flex flex-col items-center text-center gap-4 mb-16">
          <h2 className="text-xs font-bold tracking-widest text-[var(--color-muted-foreground)] uppercase">Shopping Intelligence</h2>
          <p className="text-2xl sm:text-3xl font-extrabold text-[var(--color-foreground)] tracking-tight">
            Designed for Clarity, Speed, and Savings
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
            whileHover={{ y: -5 }}
            className="flex flex-col gap-4 p-6 rounded-2xl bg-[var(--color-surface)] border border-[var(--color-border)] hover:border-[var(--color-primary)] transition-all duration-300 shadow-sm hover:shadow-md group"
          >
            <div className="h-10 w-10 flex items-center justify-center rounded-xl bg-[var(--color-surface-accent)] text-[var(--color-primary)] border border-[var(--color-border)] transition-colors">
              <Zap className="h-5 w-5" />
            </div>
            <h3 className="font-semibold text-[var(--color-foreground)] text-lg">Understand True Pricing</h3>
            <p className="text-sm text-[var(--color-muted-foreground)] leading-relaxed">
              Compare offers across verified retailers concurrently to instantly see real live market prices with zero markup.
            </p>
          </motion.div>

          {/* Feature 2 */}
          <motion.div
            variants={itemVariants}
            whileHover={{ y: -5 }}
            className="flex flex-col gap-4 p-6 rounded-2xl bg-[var(--color-surface)] border border-[var(--color-border)] hover:border-[var(--color-primary)] transition-all duration-300 shadow-sm hover:shadow-md group"
          >
            <div className="h-10 w-10 flex items-center justify-center rounded-xl bg-[var(--color-surface-accent)] text-[var(--color-primary)] border border-[var(--color-border)] transition-colors">
              <Layers className="h-5 w-5" />
            </div>
            <h3 className="font-semibold text-[var(--color-foreground)] text-lg">Compare Without Bias</h3>
            <p className="text-sm text-[var(--color-muted-foreground)] leading-relaxed">
              Side-by-side specs, direct store links, and true seller pricing without sponsored distortion or affiliate ranking bias.
            </p>
          </motion.div>

          {/* Feature 3 */}
          <motion.div
            variants={itemVariants}
            whileHover={{ y: -5 }}
            className="flex flex-col gap-4 p-6 rounded-2xl bg-[var(--color-surface)] border border-[var(--color-border)] hover:border-[var(--color-primary)] transition-all duration-300 shadow-sm hover:shadow-md group"
          >
            <div className="h-10 w-10 flex items-center justify-center rounded-xl bg-[var(--color-surface-accent)] text-[var(--color-primary)] border border-[var(--color-border)] transition-colors">
              <TrendingDown className="h-5 w-5" />
            </div>
            <h3 className="font-semibold text-[var(--color-foreground)] text-lg">Decide When to Buy</h3>
            <p className="text-sm text-[var(--color-muted-foreground)] leading-relaxed">
              Price history trends, discount tracking, and purchase timing signals help you buy with complete confidence.
            </p>
          </motion.div>
        </motion.div>
      </section>

      {/* Trending Products Section */}
      <section className="relative max-w-6xl mx-auto px-6 z-10 w-full">
        <div className="flex flex-col sm:flex-row sm:items-end justify-between gap-4 mb-10">
          <div className="flex flex-col gap-2">
            <span className="text-xs font-bold tracking-widest text-[var(--color-muted-foreground)] uppercase flex items-center gap-1.5">
              <Sparkles className="h-3.5 w-3.5 text-[var(--color-primary)]" />
              Live Rankings
            </span>
            <h2 className="text-2xl sm:text-3xl font-extrabold text-[var(--color-foreground)] tracking-tight">
              Trending on PricePilot.
            </h2>
          </div>
          <button
            onClick={() => navigate('/trending')}
            className="inline-flex items-center gap-1 text-sm font-semibold text-[var(--color-muted-foreground)] hover:text-[var(--color-foreground)] transition-colors cursor-pointer group"
          >
            Explore all lists
            <ArrowRight className="h-4 w-4 transition-transform group-hover:translate-x-0.5" />
          </button>
        </div>

        {loading ? (
          <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-6">
            {[1, 2, 3].map((n) => (
              <div key={n} className="h-[280px] rounded-2xl bg-[var(--color-surface)] border border-[var(--color-border)] animate-pulse" />
            ))}
          </div>
        ) : error || trendingProducts.length === 0 ? (
          <div className="flex flex-col items-center justify-center py-12 px-4 border border-[var(--color-border)] border-dashed rounded-2xl bg-[var(--color-surface)] text-center">
            <p className="text-sm text-[var(--color-muted-foreground)]">Failed to load trending products or no data available.</p>
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
                  whileHover={{ y: -4 }}
                  onClick={() => navigate(`/product/${product.id}`)}
                  className="flex flex-col h-full rounded-2xl bg-[var(--color-surface)] border border-[var(--color-border)] hover:border-[var(--color-primary)] transition-all duration-300 cursor-pointer overflow-hidden group shadow-sm hover:shadow-md"
                >
                  <div className="h-44 w-full bg-[var(--color-surface-accent)]/30 border-b border-[var(--color-border)] relative flex items-center justify-center overflow-hidden p-2">
                    <ProductImage
                      src={product.imageUrl}
                      alt={product.name}
                      className="h-full w-full object-contain group-hover:scale-105 transition-transform duration-500"
                      showFallbackText
                      fallbackText={product.brand}
                    />
                    {discount > 0 && (
                      <span className="absolute top-3.5 right-3.5 bg-emerald-500/10 border border-emerald-500/30 text-emerald-600 dark:text-emerald-400 text-[10px] font-bold uppercase tracking-wider px-2 py-0.5 rounded-full font-mono">
                        -{discount}% OFF
                      </span>
                    )}
                  </div>

                  <div className="flex flex-col flex-grow p-5 justify-between gap-4">
                    <div className="flex flex-col gap-1.5">
                      <span className="text-[10px] font-bold text-[var(--color-muted-foreground)] uppercase tracking-widest">{product.brand}</span>
                      <h3 className="font-bold text-[var(--color-foreground)] text-sm group-hover:text-[var(--color-primary)] line-clamp-1 transition-colors">
                        {product.name}
                      </h3>
                      <p className="text-xs text-[var(--color-muted-foreground)] line-clamp-2 leading-relaxed">
                        {product.description || 'No description provided.'}
                      </p>
                    </div>

                    <div className="flex items-center justify-between mt-1 pt-4 border-t border-[var(--color-border)]">
                      <div className="flex flex-col">
                        <span className="text-[10px] text-[var(--color-muted-foreground)] uppercase tracking-wider">Best price</span>
                        <span className="text-sm font-extrabold text-[var(--color-foreground)] font-mono">
                            {bestPrice ? formatPrice(getDisplayPrice(bestPrice, currency), currency) : 'N/A'}
                        </span>
                        {originalPrice && originalPrice > bestPrice! && (
                          <span className="text-[10px] text-[var(--color-muted-foreground)]/70 line-through font-mono">
                              {formatPrice(getDisplayPrice(originalPrice, currency), currency)}
                          </span>
                        )}
                      </div>
                      <span className="h-7 w-7 flex items-center justify-center rounded-lg bg-[var(--color-surface)] border border-[var(--color-border)] text-[var(--color-muted-foreground)] group-hover:text-[var(--color-foreground)] group-hover:border-[var(--color-primary)] transition-all">
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
      <section className="relative max-w-6xl mx-auto px-6 z-10 w-full bg-[var(--color-surface)]/70 border border-[var(--color-border)] rounded-3xl py-12 md:py-16 backdrop-blur-sm shadow-sm">
        <div className="grid grid-cols-1 lg:grid-cols-12 gap-12 items-center">
          <div className="lg:col-span-5 flex flex-col gap-4">
            <span className="text-xs font-bold tracking-widest text-[var(--color-muted-foreground)] uppercase">Why PricePilot?</span>
            <h2 className="text-3xl font-extrabold text-[var(--color-foreground)] tracking-tight">
              Smarter Shopping, Engineered.
            </h2>
            <p className="text-sm text-[var(--color-muted-foreground)] leading-relaxed">
              PricePilot empowers consumers by exposing retail pricing data transparently. We bypass affiliate filters to show you true listing costs.
            </p>
            <div className="mt-4 w-full">
              <FeaturedPricePreview initialProducts={trendingProducts} />
            </div>
          </div>

          <div className="lg:col-span-7 grid grid-cols-1 sm:grid-cols-2 gap-8">
            {/* Benefit 1 */}
            <div className="flex gap-4">
              <div className="mt-1 flex-shrink-0 text-emerald-500 dark:text-emerald-400">
                <CheckCircle2 className="h-5 w-5" />
              </div>
              <div className="flex flex-col gap-1.5">
                <h4 className="font-semibold text-[var(--color-foreground)] text-sm">Save Hours of Research</h4>
                <p className="text-xs text-[var(--color-muted-foreground)] leading-relaxed">
                  Stop manually opening tabs for Amazon, Best Buy, and Walmart. We monitor them all under a single dashboard.
                </p>
              </div>
            </div>

            {/* Benefit 2 */}
            <div className="flex gap-4">
              <div className="mt-1 flex-shrink-0 text-emerald-500 dark:text-emerald-400">
                <CheckCircle2 className="h-5 w-5" />
              </div>
              <div className="flex flex-col gap-1.5">
                <h4 className="font-semibold text-[var(--color-foreground)] text-sm">Direct Store Checkout</h4>
                <p className="text-xs text-[var(--color-muted-foreground)] leading-relaxed">
                  We supply original store hyperlinks directly. Zero middle-man markups, tracking cookies, or custom fees.
                </p>
              </div>
            </div>

            {/* Benefit 3 */}
            <div className="flex gap-4">
              <div className="mt-1 flex-shrink-0 text-emerald-500 dark:text-emerald-400">
                <CheckCircle2 className="h-5 w-5" />
              </div>
              <div className="flex flex-col gap-1.5">
                <h4 className="font-semibold text-[var(--color-foreground)] text-sm">Accurate Price Analytics</h4>
                <p className="text-xs text-[var(--color-muted-foreground)] leading-relaxed">
                  Compare historical lowest prices vs. current listings to identify if the current discount is truly a deal.
                </p>
              </div>
            </div>

            {/* Benefit 4 */}
            <div className="flex gap-4">
              <div className="mt-1 flex-shrink-0 text-emerald-500 dark:text-emerald-400">
                <CheckCircle2 className="h-5 w-5" />
              </div>
              <div className="flex flex-col gap-1.5">
                <h4 className="font-semibold text-[var(--color-foreground)] text-sm">Zero Manipulation</h4>
                <p className="text-xs text-[var(--color-muted-foreground)] leading-relaxed">
                  We rank listings strictly by cost, guaranteeing that sponsor bias won't color your comparison flow.
                </p>
              </div>
            </div>
          </div>
        </div>
      </section>

      {/* Call to Action Section */}
      <section className="relative max-w-5xl mx-auto px-6 z-10 w-full text-center">
        <div className="relative overflow-hidden rounded-3xl bg-[var(--color-surface)] border border-[var(--color-border)] py-16 px-6 sm:px-12 shadow-xl">
          <div className="absolute inset-0 bg-[radial-gradient(ellipse_at_center,_var(--tw-gradient-stops))] from-violet-500/5 via-transparent to-transparent pointer-events-none" />
          
          <div className="relative z-10 flex flex-col items-center gap-6 max-w-xl mx-auto">
            <Sparkles className="h-8 w-8 text-[var(--color-primary)] animate-pulse-slow" />
            
            <h2 className="text-3xl sm:text-4xl font-extrabold text-[var(--color-foreground)] tracking-tight leading-tight">
              Ready to find your next purchase?
            </h2>
            
            <p className="text-sm text-[var(--color-muted-foreground)] leading-relaxed">
              Explore the database or run a quick comparison search. Get instant insights and make your budget go further.
            </p>
            
            <div className="flex flex-col sm:flex-row items-center gap-4 mt-4 w-full justify-center">
              <button
                onClick={() => navigate('/search')}
                className="w-full sm:w-auto px-6 py-3 bg-[var(--color-primary)] text-[var(--color-primary-foreground)] hover:opacity-90 text-sm font-semibold rounded-xl transition-all cursor-pointer flex items-center justify-center gap-2 group shadow-md active:scale-95"
              >
                Search Catalog
                <ArrowRight className="h-4 w-4 transition-transform group-hover:translate-x-0.5" />
              </button>
              
              <button
                onClick={() => navigate('/compare')}
                className="w-full sm:w-auto px-6 py-3 bg-[var(--color-surface-accent)] border border-[var(--color-border)] hover:border-[var(--color-primary)] text-[var(--color-foreground)] text-sm font-semibold rounded-xl transition-all cursor-pointer active:scale-95"
              >
                Compare Products
              </button>
            </div>
          </div>
        </div>
      </section>
    </div>
  );
};

export default LandingPage;
