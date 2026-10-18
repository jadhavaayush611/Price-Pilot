import React, { useState, useEffect } from 'react';
import { apiService } from '../services/api';
import type { PriceHistory } from '../types';
import { formatPrice, getDisplayPrice, type CurrencyCode } from '../currency';
import { 
  History, 
  TrendingDown, 
  TrendingUp, 
  ChevronLeft, 
  ChevronRight, 
  Calendar, 
  ShoppingBag, 
  ArrowRight,
  Loader2
} from 'lucide-react';
import { motion } from 'framer-motion';

interface PriceHistorySectionProps {
  productId: string;
  currency: CurrencyCode;
}

export const PriceHistorySection: React.FC<PriceHistorySectionProps> = ({ productId, currency }) => {
  const [history, setHistory] = useState<PriceHistory[]>([]);
  const [loading, setLoading] = useState(true);
  const [currentPage, setCurrentPage] = useState(0);
  const [totalPages, setTotalPages] = useState(0);
  const [totalElements, setTotalElements] = useState(0);
  const pageSize = 5;

  useEffect(() => {
    const fetchHistory = async () => {
      setLoading(true);
      try {
        const response = await apiService.getProductPriceHistory(productId, currentPage, pageSize);
        setHistory(response.content);
        setTotalPages(response.totalPages);
        setTotalElements(response.totalElements);
      } catch (err) {
        console.error("Failed to load price history", err);
      } finally {
        setLoading(false);
      }
    };

    fetchHistory();
  }, [productId, currentPage]);

  const formatDate = (dateStr: string) => {
    return new Date(dateStr).toLocaleDateString('en-IN', {
      day: 'numeric',
      month: 'short',
      year: 'numeric',
    });
  };

  return (
    <motion.div 
      initial={{ opacity: 0, y: 20 }}
      animate={{ opacity: 1, y: 0 }}
      transition={{ duration: 0.4 }}
      className="w-full rounded-3xl border border-[var(--color-border)] bg-[var(--color-surface)] p-6 shadow-sm backdrop-blur-xl mt-8"
    >
      <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-4 mb-6 pb-5 border-b border-[var(--color-border)]">
        <div className="flex items-center gap-3">
          <div className="h-10 w-10 rounded-2xl bg-[var(--color-surface-accent)] border border-[var(--color-border)] flex items-center justify-center text-[var(--color-foreground)]">
            <History className="h-5 w-5" />
          </div>
          <div>
            <h3 className="text-lg font-bold text-[var(--color-foreground)] tracking-tight">Price History</h3>
            <p className="text-xs text-[var(--color-muted-foreground)] mt-0.5">Historical tracking of price changes over time</p>
          </div>
        </div>
        
        {!loading && totalElements > 0 && (
          <span className="self-start sm:self-auto px-3 py-1 rounded-full bg-[var(--color-surface-accent)] border border-[var(--color-border)] text-[10px] font-bold text-[var(--color-muted-foreground)] uppercase tracking-wider">
            {totalElements} {totalElements === 1 ? 'record' : 'records'} found
          </span>
        )}
      </div>

      {loading ? (
        <div className="flex flex-col items-center justify-center py-16 text-[var(--color-muted-foreground)]">
          <Loader2 className="h-8 w-8 animate-spin text-[var(--color-foreground)] mb-2" />
          <span className="text-xs font-semibold text-[var(--color-muted-foreground)]">Analyzing price trends...</span>
        </div>
      ) : history.length === 0 ? (
        <div className="flex flex-col items-center justify-center py-12 px-6 border border-[var(--color-border)] border-dashed rounded-2xl bg-[var(--color-surface-accent)]/30 text-center">
          <div className="h-12 w-12 rounded-full bg-[var(--color-surface-accent)] border border-[var(--color-border)] flex items-center justify-center text-[var(--color-muted-foreground)] mb-3.5">
            <History className="h-6 w-6" />
          </div>
          <h4 className="text-sm font-semibold text-[var(--color-foreground)] mb-1">No Price History Yet</h4>
          <p className="text-xs text-[var(--color-muted-foreground)] max-w-xs leading-relaxed">
            We are actively monitoring this product's price. When a price change is detected from any seller, the history will populate here.
          </p>
        </div>
      ) : (
        <div className="flex flex-col">
          <div className="overflow-hidden rounded-2xl border border-[var(--color-border)] bg-[var(--color-surface)]">
            <div className="overflow-x-auto">
              <table className="w-full text-left border-collapse">
                <thead>
                  <tr className="border-b border-[var(--color-border)] bg-[var(--color-surface-accent)] text-[10px] font-bold uppercase tracking-widest text-[var(--color-muted-foreground)]">
                    <th className="px-5 py-4">Date</th>
                    <th className="px-5 py-4">Seller</th>
                    <th className="px-5 py-4">Price Change</th>
                    <th className="px-5 py-4">Difference</th>
                    <th className="px-5 py-4 text-right">Trend</th>
                  </tr>
                </thead>
                <tbody className="divide-y divide-[var(--color-border)] text-sm">
                  {history.map((record) => {
                    const oldPriceLocal = getDisplayPrice(record.oldPrice, currency);
                    const newPriceLocal = getDisplayPrice(record.newPrice, currency);
                    const diffLocal = Math.abs(newPriceLocal - oldPriceLocal);
                    const isDrop = record.priceDifference < 0;
                    return (
                      <tr 
                        key={record.id}
                        className="transition-all hover:bg-[var(--color-surface-accent)]/40"
                      >
                        {/* Date */}
                        <td className="px-5 py-4 font-medium text-[var(--color-foreground)]">
                          <div className="flex items-center gap-2">
                            <Calendar className="h-3.5 w-3.5 text-[var(--color-muted-foreground)]" />
                            <span>{formatDate(record.changedAt)}</span>
                          </div>
                        </td>

                        {/* Seller */}
                        <td className="px-5 py-4">
                          <div className="flex items-center gap-2.5">
                            <div className="h-5.5 px-2 rounded bg-[var(--color-surface-accent)] border border-[var(--color-border)] flex items-center justify-center text-[9px] font-extrabold text-[var(--color-foreground)] uppercase tracking-wider">
                              <ShoppingBag className="h-3 w-3 mr-1 text-[var(--color-muted-foreground)]" />
                              {record.sellerName}
                            </div>
                          </div>
                        </td>

                        {/* Price Change */}
                        <td className="px-5 py-4">
                          <div className="flex items-center gap-2 font-semibold">
                            <span className="text-[var(--color-muted-foreground)] line-through text-xs">
                              {formatPrice(oldPriceLocal, currency)}
                            </span>
                            <ArrowRight className="h-3 w-3 text-[var(--color-muted-foreground)]" />
                            <span className="text-[var(--color-foreground)]">
                              {formatPrice(newPriceLocal, currency)}
                            </span>
                          </div>
                        </td>

                        {/* Difference */}
                        <td className="px-5 py-4">
                          <span className={`font-bold ${isDrop ? 'text-emerald-600 dark:text-emerald-400' : 'text-rose-600 dark:text-rose-400'}`}>
                            {isDrop ? '-' : '+'}{formatPrice(diffLocal, currency)}
                          </span>
                        </td>

                        {/* Trend Indicator */}
                        <td className="px-5 py-4 text-right">
                          <span className={`inline-flex items-center gap-1 px-2.5 py-1 rounded-full text-xs font-bold ${
                            isDrop 
                              ? 'text-emerald-700 dark:text-emerald-300 bg-emerald-500/15 border border-emerald-500/30'
                              : 'text-rose-700 dark:text-rose-300 bg-rose-500/15 border border-rose-500/30'
                          }`}>
                            {isDrop ? (
                              <>
                                <TrendingDown className="h-3 w-3" />
                                <span>{Math.abs(record.changePercentage).toFixed(2)}% drop</span>
                              </>
                            ) : (
                              <>
                                <TrendingUp className="h-3 w-3" />
                                <span>{record.changePercentage.toFixed(2)}% rise</span>
                              </>
                            )}
                          </span>
                        </td>
                      </tr>
                    );
                  })}
                </tbody>
              </table>
            </div>
          </div>

          {/* Pagination Controls */}
          {totalPages > 1 && (
            <div className="flex items-center justify-between gap-4 mt-5 pt-3">
              <span className="text-[11px] text-[var(--color-muted-foreground)] font-medium">
                Page {currentPage + 1} of {totalPages}
              </span>
              <div className="flex items-center gap-2">
                <button
                  onClick={() => setCurrentPage(prev => Math.max(0, prev - 1))}
                  disabled={currentPage === 0}
                  className="p-2 rounded-xl border border-[var(--color-border)] bg-[var(--color-surface)] hover:bg-[var(--color-surface-accent)] disabled:opacity-30 text-[var(--color-muted-foreground)] hover:text-[var(--color-foreground)] transition-all cursor-pointer shadow-sm"
                >
                  <ChevronLeft className="h-4 w-4" />
                </button>
                <button
                  onClick={() => setCurrentPage(prev => Math.min(totalPages - 1, prev + 1))}
                  disabled={currentPage === totalPages - 1}
                  className="p-2 rounded-xl border border-[var(--color-border)] bg-[var(--color-surface)] hover:bg-[var(--color-surface-accent)] disabled:opacity-30 text-[var(--color-muted-foreground)] hover:text-[var(--color-foreground)] transition-all cursor-pointer shadow-sm"
                >
                  <ChevronRight className="h-4 w-4" />
                </button>
              </div>
            </div>
          )}
        </div>
      )}
    </motion.div>
  );
};
