import React, { useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { apiService } from '../services/api';
import type { SavedProduct } from '../types';
import { Heart, Inbox, ArrowLeft, Trash2, ShoppingBag } from 'lucide-react';
import { motion, AnimatePresence } from 'framer-motion';
import { formatPrice } from '../lib/utils';
import { useAuth } from '../context/AuthContext';

export const SavedProductsPage: React.FC = () => {
  const navigate = useNavigate();
  const { isAuthenticated } = useAuth();
  const [savedProducts, setSavedProducts] = useState<SavedProduct[]>([]);
  const [loading, setLoading] = useState(true);
  const [removingId, setRemovingId] = useState<string | null>(null);

  useEffect(() => {
    if (!isAuthenticated) {
      navigate('/login?from=/saved-products');
      return;
    }

    setLoading(true);
    apiService.getSavedProducts()
      .then((data) => {
        setSavedProducts(data);
      })
      .catch((err) => {
        console.error('Error fetching saved products:', err);
      })
      .finally(() => {
        setLoading(false);
      });
  }, [isAuthenticated, navigate]);

  const handleRemove = async (productId: string, e: React.MouseEvent) => {
    e.stopPropagation();
    setRemovingId(productId);
    try {
      await apiService.removeProduct(productId);
      setSavedProducts(prev => prev.filter(sp => sp.productId !== productId));
    } catch (err) {
      console.error('Failed to remove saved product:', err);
    } finally {
      setRemovingId(null);
    }
  };

  // framer-motion variants
  const containerVariants = {
    hidden: { opacity: 0 },
    show: {
      opacity: 1,
      transition: { staggerChildren: 0.05 }
    }
  };

  const itemVariants = {
    hidden: { opacity: 0, y: 15 },
    show: { 
      opacity: 1, 
      y: 0,
      transition: { type: 'spring' as const, stiffness: 100, damping: 15 }
    },
    exit: { opacity: 0, scale: 0.95, transition: { duration: 0.2 } }
  };

  if (loading) {
    return (
      <div className="flex flex-col gap-6 max-w-5xl mx-auto w-full py-6">
        <div className="h-4 w-28 bg-zinc-900 rounded-lg animate-pulse" />
        <div className="h-9 w-48 bg-zinc-900 rounded-xl mt-2 animate-pulse" />
        
        <div className="grid grid-cols-1 md:grid-cols-2 gap-6 mt-6">
          {[1, 2, 3, 4].map((n) => (
            <div
              key={n}
              className="h-[140px] rounded-2xl bg-zinc-950/20 border border-zinc-900/60 flex p-4 relative overflow-hidden animate-pulse"
            >
              <div className="h-full aspect-[4/3] rounded-xl bg-zinc-900/40 border border-zinc-900/60 mr-4" />
              <div className="flex-grow flex flex-col justify-between py-1">
                <div>
                  <div className="h-3 w-16 bg-zinc-900/60 rounded mb-2" />
                  <div className="h-5 w-3/4 bg-zinc-900/60 rounded" />
                </div>
                <div className="h-6 w-24 bg-zinc-900/60 rounded" />
              </div>
            </div>
          ))}
        </div>
      </div>
    );
  }

  return (
    <div className="flex flex-col gap-6 max-w-5xl mx-auto w-full py-6">
      {/* Header */}
      <div>
        <button
          onClick={() => navigate('/search')}
          className="inline-flex items-center gap-2 text-xs font-bold text-zinc-500 hover:text-zinc-300 transition-colors cursor-pointer group animate-fade-in"
        >
          <ArrowLeft className="h-4 w-4 transition-transform group-hover:-translate-x-0.5" /> 
          Back to Search
        </button>
        <div className="flex items-center justify-between mt-4">
          <div>
            <h1 className="text-3xl font-extrabold tracking-tight text-white flex items-center gap-3">
              <Heart className="h-8 w-8 text-rose-500 fill-current" />
              Saved Products
            </h1>
            <p className="text-xs text-zinc-500 mt-1">
              Track prices and manage your interest in catalog items
            </p>
          </div>
          <span className="px-3 py-1.5 rounded-full bg-zinc-950 border border-zinc-900 text-xs font-mono text-zinc-400 font-bold">
            {savedProducts.length} Saved
          </span>
        </div>
      </div>

      {/* Grid of Saved Products */}
      <AnimatePresence mode="popLayout">
        {savedProducts.length === 0 ? (
          <motion.div
            initial={{ opacity: 0, scale: 0.98 }}
            animate={{ opacity: 1, scale: 1 }}
            exit={{ opacity: 0, scale: 0.98 }}
            className="flex flex-col items-center justify-center py-24 px-4 border border-zinc-900/60 border-dashed rounded-2xl bg-zinc-955/10 backdrop-blur-sm text-center mt-6"
          >
            <div className="h-14 w-14 rounded-2xl bg-zinc-950 border border-zinc-900 flex items-center justify-center mb-5 text-zinc-500">
              <Inbox className="h-7 w-7" />
            </div>
            <h3 className="text-zinc-200 font-bold text-lg mb-1.5">No saved products yet</h3>
            <p className="text-xs text-zinc-500 max-w-xs leading-relaxed mb-6">
              Start browsing the catalog and click the save button on product cards to monitor their pricing updates.
            </p>
            <button
              onClick={() => navigate('/search')}
              className="inline-flex items-center gap-2 px-5 py-2.5 bg-white hover:bg-zinc-200 text-black text-xs font-bold rounded-xl shadow-md transition-all active:scale-95 cursor-pointer"
            >
              <ShoppingBag className="h-4 w-4" />
              <span>Browse Catalog</span>
            </button>
          </motion.div>
        ) : (
          <motion.div
            variants={containerVariants}
            initial="hidden"
            animate="show"
            className="grid grid-cols-1 md:grid-cols-2 gap-6 mt-4"
          >
            {savedProducts.map((product) => (
              <motion.div
                key={product.productId}
                variants={itemVariants}
                exit="exit"
                layout
                onClick={() => navigate(`/product/${product.productId}`)}
                className="flex rounded-2xl bg-zinc-950/30 border border-zinc-900/85 hover:border-zinc-800 p-4 hover:shadow-[0_8px_30px_rgb(0,0,0,0.3)] transition-all duration-300 group cursor-pointer items-stretch justify-between relative overflow-hidden"
              >
                <div className="flex items-center flex-grow">
                  {/* Product Image */}
                  <div className="h-20 w-24 rounded-xl overflow-hidden bg-zinc-950 border border-zinc-900 mr-4 shrink-0 relative">
                    <img
                      src={product.imageUrl || 'https://images.unsplash.com/photo-1531403009284-440f080d1e12?auto=format&fit=crop&q=80&w=600'}
                      alt={product.name}
                      className="w-full h-full object-cover group-hover:scale-102 transition-transform duration-500"
                      onError={(e) => {
                        (e.target as HTMLImageElement).src = 'https://images.unsplash.com/photo-1531403009284-440f080d1e12?auto=format&fit=crop&q=80&w=600';
                      }}
                    />
                  </div>

                  {/* Product details */}
                  <div className="flex flex-col justify-between py-0.5 flex-grow">
                    <div>
                      <span className="text-[9px] font-bold tracking-widest uppercase text-zinc-500">
                        {product.brand}
                      </span>
                      <h3 className="font-bold text-zinc-200 text-sm leading-snug group-hover:text-white transition-colors line-clamp-1 mt-0.5">
                        {product.name}
                      </h3>
                      <span className="text-[10px] text-zinc-500">
                        Category: {product.category}
                      </span>
                    </div>

                    <div className="mt-2">
                      {product.bestPrice ? (
                        <div className="flex items-baseline gap-1">
                          <span className="text-[9px] text-zinc-500 uppercase font-bold tracking-wider mr-1">Best Price:</span>
                          <span className="text-sm font-extrabold text-emerald-400">
                            {formatPrice(product.bestPrice)}
                          </span>
                        </div>
                      ) : (
                        <span className="text-[10px] text-zinc-500 italic">No pricing active</span>
                      )}
                    </div>
                  </div>
                </div>

                {/* Actions */}
                <div className="flex flex-col items-end justify-between shrink-0 ml-4 pl-4 border-l border-zinc-900/60">
                  <button
                    type="button"
                    onClick={(e) => handleRemove(product.productId, e)}
                    disabled={removingId === product.productId}
                    className="p-2 rounded-xl text-zinc-500 hover:text-rose-455 hover:bg-rose-500/10 transition-colors cursor-pointer active:scale-95"
                    title="Remove from saved products"
                  >
                    <Trash2 className={`h-4.5 w-4.5 ${removingId === product.productId ? 'animate-pulse' : ''}`} />
                  </button>
                  <span className="text-[9px] text-zinc-600 font-medium">
                    {new Date(product.savedAt).toLocaleDateString()}
                  </span>
                </div>
              </motion.div>
            ))}
          </motion.div>
        )}
      </AnimatePresence>
    </div>
  );
};

export default SavedProductsPage;
