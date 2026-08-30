import React, { useState, useEffect, useRef } from 'react';
import { useNavigate } from 'react-router-dom';
import { apiService } from '../../services/api';
import type { PriceAlert } from '../../types';
import { useAuth } from '../../context/AuthContext';
import {
  Bell,
  Check,
  CheckCheck,
  TrendingDown,
  TrendingUp,
  Target,
  Sparkles,
  PackageCheck,
  AlertCircle,
  Clock,
  ExternalLink,
} from 'lucide-react';

export const NotificationCenter: React.FC = () => {
  const { isAuthenticated } = useAuth();
  const navigate = useNavigate();

  const [isOpen, setIsOpen] = useState(false);
  const [alerts, setAlerts] = useState<PriceAlert[]>([]);
  const [unreadCount, setUnreadCount] = useState<number>(0);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const dropdownRef = useRef<HTMLDivElement>(null);

  const fetchAlerts = async () => {
    if (!isAuthenticated) return;
    try {
      setLoading(true);
      setError(null);
      const [unreadList, count] = await Promise.all([
        apiService.getUnreadAlerts().catch(() => []),
        apiService.getUnreadAlertCount().catch(() => 0),
      ]);
      setAlerts(unreadList);
      setUnreadCount(count);
    } catch (err) {
      console.error('Failed to load alerts:', err);
      setError('Unable to load alerts');
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    if (isAuthenticated) {
      fetchAlerts();
      // Light polling every 60s
      const interval = setInterval(fetchAlerts, 60000);
      return () => clearInterval(interval);
    } else {
      setAlerts([]);
      setUnreadCount(0);
    }
  }, [isAuthenticated]);

  // Close dropdown on click outside
  useEffect(() => {
    const handleClickOutside = (event: MouseEvent) => {
      if (dropdownRef.current && !dropdownRef.current.contains(event.target as Node)) {
        setIsOpen(false);
      }
    };
    document.addEventListener('mousedown', handleClickOutside);
    return () => document.removeEventListener('mousedown', handleClickOutside);
  }, []);

  const handleMarkAsRead = async (alertId: string, e: React.MouseEvent) => {
    e.stopPropagation();
    try {
      await apiService.markAlertRead(alertId);
      setAlerts((prev) => prev.filter((a) => a.id !== alertId));
      setUnreadCount((prev) => Math.max(0, prev - 1));
    } catch (err) {
      console.error('Failed to mark alert as read:', err);
    }
  };

  const handleMarkAllRead = async () => {
    try {
      await apiService.markAllAlertsRead();
      setAlerts([]);
      setUnreadCount(0);
    } catch (err) {
      console.error('Failed to mark all alerts as read:', err);
    }
  };

  const handleAlertClick = (alert: PriceAlert) => {
    setIsOpen(false);
    if (!alert.read) {
      apiService.markAlertRead(alert.id).catch(() => {});
      setAlerts((prev) => prev.filter((a) => a.id !== alert.id));
      setUnreadCount((prev) => Math.max(0, prev - 1));
    }
    navigate(`/product/${alert.productId}`);
  };

  const getAlertIcon = (type: string) => {
    switch (type) {
      case 'PRICE_DROP':
        return <TrendingDown className="w-4 h-4 text-emerald-400" />;
      case 'PRICE_TARGET_REACHED':
        return <Target className="w-4 h-4 text-indigo-400" />;
      case 'HISTORICAL_LOW_REACHED':
        return <Sparkles className="w-4 h-4 text-emerald-400" />;
      case 'GOOD_DEAL_DETECTED':
        return <Check className="w-4 h-4 text-teal-400" />;
      case 'PRICE_INCREASE':
        return <TrendingUp className="w-4 h-4 text-rose-400" />;
      case 'BACK_IN_STOCK':
        return <PackageCheck className="w-4 h-4 text-cyan-400" />;
      default:
        return <Clock className="w-4 h-4 text-zinc-400" />;
    }
  };

  if (!isAuthenticated) {
    return null;
  }

  return (
    <div className="relative" ref={dropdownRef}>
      {/* Bell Trigger Button */}
      <button
        type="button"
        aria-label={`Notifications (${unreadCount} unread)`}
        aria-expanded={isOpen}
        onClick={() => {
          setIsOpen(!isOpen);
          if (!isOpen) fetchAlerts();
        }}
        className="relative p-2 rounded-lg text-zinc-400 hover:text-zinc-100 hover:bg-zinc-800/60 transition-colors focus:outline-none focus:ring-2 focus:ring-emerald-500/50"
      >
        <Bell className="w-5 h-5" />
        {unreadCount > 0 && (
          <span className="absolute top-1 right-1 flex items-center justify-center min-w-[18px] h-[18px] px-1 text-[10px] font-black text-white bg-rose-500 rounded-full animate-in zoom-in duration-200">
            {unreadCount > 99 ? '99+' : unreadCount}
          </span>
        )}
      </button>

      {/* Popover Menu */}
      {isOpen && (
        <div
          role="region"
          aria-label="Notifications panel"
          className="absolute right-0 mt-2 w-80 sm:w-96 rounded-2xl bg-zinc-950 border border-zinc-800 shadow-2xl z-50 overflow-hidden text-left"
        >
          {/* Header */}
          <div className="flex items-center justify-between px-4 py-3 border-b border-zinc-850 bg-zinc-900/50">
            <div className="flex items-center gap-2">
              <span className="text-xs font-bold uppercase tracking-wider text-zinc-200">
                Price Alerts
              </span>
              {unreadCount > 0 && (
                <span className="px-2 py-0.5 rounded-full text-[10px] font-mono font-bold bg-emerald-950 border border-emerald-800/60 text-emerald-400">
                  {unreadCount} new
                </span>
              )}
            </div>
            {unreadCount > 0 && (
              <button
                type="button"
                onClick={handleMarkAllRead}
                className="flex items-center gap-1 text-[11px] text-zinc-400 hover:text-emerald-400 transition-colors font-medium"
              >
                <CheckCheck className="w-3.5 h-3.5" />
                Mark all read
              </button>
            )}
          </div>

          {/* Body */}
          <div className="max-h-[380px] overflow-y-auto divide-y divide-zinc-900">
            {loading && alerts.length === 0 && (
              <div className="p-6 text-center text-xs text-zinc-500">
                Loading alerts...
              </div>
            )}

            {error && (
              <div className="p-6 text-center text-xs text-rose-400 flex items-center justify-center gap-2">
                <AlertCircle className="w-4 h-4" />
                {error}
              </div>
            )}

            {!loading && !error && alerts.length === 0 && (
              <div className="p-8 text-center space-y-2">
                <div className="w-10 h-10 rounded-full bg-zinc-900 flex items-center justify-center mx-auto text-zinc-600">
                  <Bell className="w-5 h-5" />
                </div>
                <p className="text-xs font-medium text-zinc-300">All caught up!</p>
                <p className="text-[11px] text-zinc-500 max-w-[200px] mx-auto">
                  You have no unread price alerts for products in your watchlist.
                </p>
              </div>
            )}

            {alerts.map((alert) => (
              <div
                key={alert.id}
                onClick={() => handleAlertClick(alert)}
                className="p-3.5 hover:bg-zinc-900/60 transition-colors cursor-pointer flex items-start gap-3 group relative"
              >
                <div className="w-8 h-8 rounded-lg bg-zinc-900 border border-zinc-800 flex items-center justify-center shrink-0 mt-0.5">
                  {getAlertIcon(alert.alertType)}
                </div>

                <div className="flex-1 min-w-0 space-y-1">
                  <div className="flex items-center justify-between gap-2">
                    <h4 className="text-xs font-bold text-zinc-100 truncate group-hover:text-emerald-400 transition-colors">
                      {alert.title}
                    </h4>
                    <span className="text-[10px] font-mono text-zinc-500 shrink-0">
                      {new Date(alert.createdAt).toLocaleDateString([], {
                        month: 'short',
                        day: 'numeric',
                      })}
                    </span>
                  </div>

                  <p className="text-[11px] text-zinc-400 line-clamp-2 leading-relaxed">
                    {alert.message}
                  </p>

                  <div className="flex items-center justify-between pt-1">
                    <span className="text-[10px] text-emerald-400 font-mono flex items-center gap-1">
                      View product <ExternalLink className="w-2.5 h-2.5" />
                    </span>

                    <button
                      type="button"
                      aria-label="Mark as read"
                      onClick={(e) => handleMarkAsRead(alert.id, e)}
                      className="text-[10px] text-zinc-500 hover:text-zinc-200 transition-colors p-1"
                    >
                      <Check className="w-3.5 h-3.5" />
                    </button>
                  </div>
                </div>
              </div>
            ))}
          </div>
        </div>
      )}
    </div>
  );
};
