import React, { useState } from 'react';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { apiService } from '../services/api';
import { Table } from '../components/Table';
import type { Column } from '../components/Table';
import type { ProductPrice } from '../types';
import {
  Plus,
  Search,
  Edit2,
  Trash2,
  X,
  Loader2,
  Sparkles,
  DollarSign,
  AlertTriangle,
  ExternalLink,
  Percent,
  Calendar
} from 'lucide-react';
import { motion, AnimatePresence } from 'framer-motion';

interface FormState {
  productId: string;
  sellerId: string;
  currentPrice: string;
  originalPrice: string;
  productUrl: string;
}

const initialFormState: FormState = {
  productId: '',
  sellerId: '',
  currentPrice: '',
  originalPrice: '',
  productUrl: '',
};

export const PriceManagementPage: React.FC = () => {
  const queryClient = useQueryClient();

  // Search & Pagination States
  const [searchTerm, setSearchTerm] = useState('');
  const [debouncedSearch, setDebouncedSearch] = useState('');
  const [page, setPage] = useState(0);
  const [pageSize] = useState(10);
  const [sortKey, setSortKey] = useState<string>('lastUpdated');
  const [sortDirection, setSortDirection] = useState<'asc' | 'desc'>('desc');

  // UI States
  const [isModalOpen, setIsModalOpen] = useState(false);
  const [isDeleteConfirmOpen, setIsDeleteConfirmOpen] = useState(false);
  const [selectedPrice, setSelectedPrice] = useState<ProductPrice | null>(null);
  const [formData, setFormData] = useState<FormState>(initialFormState);
  const [formErrors, setFormErrors] = useState<Partial<FormState>>({});
  const [apiError, setApiError] = useState<string | null>(null);

  // Debounce search input
  const handleSearchChange = (e: React.ChangeEvent<HTMLInputElement>) => {
    const value = e.target.value;
    setSearchTerm(value);

    // Simple debounce
    const handler = setTimeout(() => {
      setDebouncedSearch(value);
      setPage(0); // Reset page on search
    }, 400);

    return () => clearTimeout(handler);
  };

  // Queries
  const { data, isLoading, isError } = useQuery({
    queryKey: ['prices', page, pageSize, sortKey, sortDirection, debouncedSearch],
    queryFn: () => apiService.getProductPrices(page, pageSize, sortKey, sortDirection, debouncedSearch),
  });

  // Fetch products and sellers for select dropdowns (limit 100 for admin simplicity)
  const { data: productsData } = useQuery({
    queryKey: ['products-select'],
    queryFn: () => apiService.getProducts(0, 100, 'name', 'asc'),
  });

  const { data: sellersData } = useQuery({
    queryKey: ['sellers-select'],
    queryFn: () => apiService.getSellers(0, 100, 'name', 'asc'),
  });

  // Mutations
  const createMutation = useMutation({
    mutationFn: (newPrice: { productId: string; sellerId: string; currentPrice: number; originalPrice: number; productUrl?: string }) =>
      apiService.createProductPrice(newPrice),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['prices'] });
      handleCloseModal();
    },
    onError: (error: unknown) => {
      const err = error as { response?: { data?: { message?: string } } };
      const msg = err?.response?.data?.message || 'Failed to register price. Do not store invalid discounts (e.g. current price > original price).';
      setApiError(msg);
    }
  });

  const updateMutation = useMutation({
    mutationFn: ({ id, price }: { id: string; price: { productId: string; sellerId: string; currentPrice: number; originalPrice: number; productUrl?: string } }) =>
      apiService.updateProductPrice(id, price),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['prices'] });
      handleCloseModal();
    },
    onError: (error: unknown) => {
      const err = error as { response?: { data?: { message?: string } } };
      const msg = err?.response?.data?.message || 'Failed to update price. Please verify values.';
      setApiError(msg);
    }
  });

  const deleteMutation = useMutation({
    mutationFn: (id: string) => apiService.deleteProductPrice(id),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['prices'] });
      setIsDeleteConfirmOpen(false);
      setSelectedPrice(null);
    },
    onError: (error: unknown) => {
      const err = error as { response?: { data?: { message?: string } } };
      alert(err?.response?.data?.message || 'Failed to delete price.');
    }
  });

  // Handlers
  const handleSort = (key: string) => {
    if (sortKey === key) {
      setSortDirection(prev => (prev === 'asc' ? 'desc' : 'asc'));
    } else {
      setSortKey(key);
      setSortDirection('asc');
    }
    setPage(0);
  };

  const handleOpenCreateModal = () => {
    setSelectedPrice(null);
    setFormData(initialFormState);
    setFormErrors({});
    setApiError(null);
    setIsModalOpen(true);
  };

  const handleOpenEditModal = (price: ProductPrice) => {
    setSelectedPrice(price);
    setFormData({
      productId: price.product?.id || '',
      sellerId: price.seller?.id || '',
      currentPrice: price.currentPrice.toString(),
      originalPrice: price.originalPrice.toString(),
      productUrl: price.productUrl || '',
    });
    setFormErrors({});
    setApiError(null);
    setIsModalOpen(true);
  };

  const handleCloseModal = () => {
    setIsModalOpen(false);
    setSelectedPrice(null);
    setFormData(initialFormState);
    setFormErrors({});
    setApiError(null);
  };

  const handleOpenDeleteConfirm = (price: ProductPrice) => {
    setSelectedPrice(price);
    setIsDeleteConfirmOpen(true);
  };

  const validateForm = (): boolean => {
    const errors: Partial<FormState> = {};
    if (!formData.productId) errors.productId = 'Product is required';
    if (!formData.sellerId) errors.sellerId = 'Seller is required';
    
    const curr = parseFloat(formData.currentPrice);
    const orig = parseFloat(formData.originalPrice);

    if (isNaN(curr) || curr < 0) {
      errors.currentPrice = 'Current price must be a valid positive number';
    }
    if (isNaN(orig) || orig <= 0) {
      errors.originalPrice = 'Original price must be greater than zero';
    }
    if (!isNaN(curr) && !isNaN(orig) && curr > orig) {
      errors.currentPrice = 'Current price cannot exceed original price (invalid discount)';
    }

    setFormErrors(errors);
    return Object.keys(errors).length === 0;
  };

  const handleSubmit = (e: React.FormEvent) => {
    e.preventDefault();
    setApiError(null);

    if (!validateForm()) return;

    const payload = {
      productId: formData.productId,
      sellerId: formData.sellerId,
      currentPrice: parseFloat(formData.currentPrice),
      originalPrice: parseFloat(formData.originalPrice),
      productUrl: formData.productUrl,
    };

    if (selectedPrice) {
      updateMutation.mutate({ id: selectedPrice.id, price: payload });
    } else {
      createMutation.mutate(payload);
    }
  };

  const handleDeleteConfirm = () => {
    if (selectedPrice) {
      deleteMutation.mutate(selectedPrice.id);
    }
  };

  // Reusable Form Input CSS Classes
  const inputClass = (error?: string) => `w-full px-4 py-2.5 rounded-lg border ${
    error ? 'border-rose-500/60 focus:border-rose-500' : 'border-zinc-800 focus:border-zinc-600'
  } bg-zinc-950 text-zinc-100 placeholder-zinc-600 text-sm focus:outline-none transition-all duration-200`;

  const selectClass = (error?: string) => `w-full px-4 py-2.5 rounded-lg border ${
    error ? 'border-rose-500/60 focus:border-rose-500' : 'border-zinc-800 focus:border-zinc-600'
  } bg-zinc-950 text-zinc-200 text-sm focus:outline-none transition-all duration-200`;

  // Format currency
  const formatCurrency = (val: number) => {
    return new Intl.NumberFormat('en-US', { style: 'currency', currency: 'USD' }).format(val);
  };

  // Format last updated text
  const formatDateTime = (dateTimeStr?: string) => {
    if (!dateTimeStr) return 'N/A';
    try {
      const date = new Date(dateTimeStr);
      return date.toLocaleString('en-US', { 
        month: 'short', 
        day: 'numeric', 
        hour: '2-digit', 
        minute: '2-digit' 
      });
    } catch {
      return dateTimeStr;
    }
  };

  // Columns definition for Table component
  const columns: Column<ProductPrice>[] = [
    {
      header: 'Product',
      accessor: (pp: ProductPrice) => (
        <div className="flex items-center gap-3">
          <div className="h-10 w-10 rounded-lg overflow-hidden border border-zinc-900 bg-zinc-950 flex items-center justify-center text-zinc-700 shrink-0">
            {pp.product?.imageUrl ? (
              <img
                src={pp.product.imageUrl}
                alt={pp.product.name}
                className="h-full w-full object-cover"
                onError={(e) => {
                  (e.target as HTMLImageElement).src = '';
                }}
              />
            ) : (
              <div className="h-5 w-5 bg-zinc-900 rounded" />
            )}
          </div>
          <div className="flex flex-col min-w-0">
            <span className="text-zinc-200 font-semibold truncate max-w-[180px] sm:max-w-[260px]">
              {pp.product?.name || 'Deleted Product'}
            </span>
            <span className="text-xs text-zinc-500">
              Brand: {pp.product?.brand || 'N/A'}
            </span>
          </div>
        </div>
      ),
    },
    {
      header: 'Seller',
      accessor: (pp: ProductPrice) => (
        <div className="flex items-center gap-2">
          <div className="h-6 w-6 rounded overflow-hidden border border-zinc-900 bg-zinc-950 flex items-center justify-center shrink-0">
            {pp.seller?.logoUrl ? (
              <img
                src={pp.seller.logoUrl}
                alt={pp.seller.name}
                className="h-full w-full object-contain p-0.5"
                onError={(e) => {
                  (e.target as HTMLImageElement).src = '';
                }}
              />
            ) : (
              <span className="text-[10px] text-zinc-600">S</span>
            )}
          </div>
          <span className="text-zinc-300 font-medium text-sm">
            {pp.seller?.name || 'Deleted Merchant'}
          </span>
        </div>
      ),
    },
    {
      header: 'Current Price',
      accessor: (pp: ProductPrice) => (
        <div className="flex flex-col">
          <span className="text-zinc-100 font-bold">
            {formatCurrency(pp.currentPrice)}
          </span>
          <span className="text-xs text-zinc-500 line-through">
            {formatCurrency(pp.originalPrice)}
          </span>
        </div>
      ),
      sortable: true,
      sortKey: 'currentPrice',
    },
    {
      header: 'Discount',
      accessor: (pp: ProductPrice) => (
        pp.discountPercentage > 0 ? (
          <span className="inline-flex items-center gap-0.5 px-2 py-1 rounded-md text-xs font-bold text-emerald-400 bg-emerald-500/10 border border-emerald-500/20">
            <Percent className="h-3 w-3" />
            {pp.discountPercentage.toFixed(1)}% OFF
          </span>
        ) : (
          <span className="text-zinc-500 text-xs">No Discount</span>
        )
      ),
      sortable: true,
      sortKey: 'discountPercentage',
    },
    {
      header: 'Product Link',
      accessor: (pp: ProductPrice) => (
        pp.productUrl ? (
          <a
            href={pp.productUrl}
            target="_blank"
            rel="noopener noreferrer"
            className="inline-flex items-center gap-1 text-zinc-400 hover:text-zinc-200 transition-colors text-xs font-semibold hover:underline"
          >
            <span>Visit Store</span>
            <ExternalLink className="h-3.5 w-3.5" />
          </a>
        ) : (
          <span className="text-zinc-600 text-xs">No link</span>
        )
      ),
    },
    {
      header: 'Last Updated',
      accessor: (pp: ProductPrice) => (
        <div className="flex items-center gap-1.5 text-zinc-500 text-xs">
          <Calendar className="h-3.5 w-3.5 shrink-0" />
          <span>{formatDateTime(pp.lastUpdated)}</span>
        </div>
      ),
      sortable: true,
      sortKey: 'lastUpdated',
    },
    {
      header: 'Actions',
      accessor: (pp: ProductPrice) => (
        <div className="flex items-center gap-2">
          <button
            onClick={() => handleOpenEditModal(pp)}
            className="p-1.5 rounded-md border border-zinc-900 hover:border-zinc-700 hover:text-zinc-100 transition-all cursor-pointer"
            title="Edit Price"
          >
            <Edit2 className="h-4 w-4" />
          </button>
          <button
            onClick={() => handleOpenDeleteConfirm(pp)}
            className="p-1.5 rounded-md border border-zinc-900 hover:border-rose-900/60 hover:text-rose-400 transition-all cursor-pointer"
            title="Delete Price"
          >
            <Trash2 className="h-4 w-4" />
          </button>
        </div>
      ),
      className: 'w-[100px] text-right',
    }
  ];

  const productsList = productsData?.content || [];
  const sellersList = sellersData?.content || [];

  return (
    <div className="space-y-6">
      {/* Header section */}
      <div className="flex flex-col sm:flex-row sm:items-center sm:justify-between gap-4">
        <div>
          <div className="flex items-center gap-2">
            <span className="p-1.5 bg-zinc-900/80 border border-zinc-800 rounded-lg text-zinc-300">
              <DollarSign className="h-5 w-5" />
            </span>
            <h1 className="text-2xl font-bold tracking-tight text-white sm:text-3xl">
              Price Registry
            </h1>
          </div>
          <p className="mt-1 text-sm text-zinc-400">
            Map catalog products to seller pricing plans and discount campaigns.
          </p>
        </div>

        <button
          onClick={handleOpenCreateModal}
          className="flex items-center justify-center gap-1.5 px-4.5 py-2.5 rounded-lg bg-zinc-100 hover:bg-white text-zinc-950 font-semibold text-sm transition-all shadow-[0_0_12px_rgba(255,255,255,0.1)] hover:scale-[1.01] active:scale-[0.99] cursor-pointer"
        >
          <Plus className="h-4 w-4" />
          <span>Add Product Price</span>
        </button>
      </div>

      {/* Filter and Table Card */}
      <div className="rounded-2xl border border-zinc-900 bg-zinc-950/20 p-5 space-y-4">
        {/* Search filter */}
        <div className="flex items-center max-w-sm rounded-lg border border-zinc-800 bg-zinc-950/60 px-3 py-2">
          <Search className="h-4 w-4 text-zinc-500 mr-2 shrink-0" />
          <input
            type="text"
            placeholder="Search by product or seller name..."
            value={searchTerm}
            onChange={handleSearchChange}
            className="w-full bg-transparent text-sm text-zinc-200 placeholder-zinc-600 focus:outline-none"
          />
          {searchTerm && (
            <button
              onClick={() => { setSearchTerm(''); setDebouncedSearch(''); }}
              className="text-zinc-600 hover:text-zinc-400 ml-1.5 cursor-pointer"
            >
              <X className="h-3.5 w-3.5" />
            </button>
          )}
        </div>

        {/* Data Table */}
        {isError ? (
          <div className="rounded-xl border border-rose-950 bg-rose-950/10 p-6 text-center text-rose-400">
            <AlertTriangle className="h-8 w-8 mx-auto mb-2 opacity-80" />
            <p className="font-semibold text-sm">Failed to connect to the backend engine.</p>
            <p className="text-xs text-rose-500/80 mt-1">
              Please verify that the Spring Boot server is running and database configuration is healthy.
            </p>
          </div>
        ) : (
          <Table
            data={data?.content || []}
            columns={columns}
            isLoading={isLoading}
            onSort={handleSort}
            sortKey={sortKey}
            sortDirection={sortDirection}
            pagination={
              data
                ? {
                    currentPage: data.number,
                    totalPages: data.totalPages,
                    totalElements: data.totalElements,
                    pageSize: data.size,
                    onPageChange: (newPage) => setPage(newPage),
                  }
                : undefined
            }
          />
        )}
      </div>

      {/* CREATE & EDIT MODAL */}
      <AnimatePresence>
        {isModalOpen && (
          <div className="fixed inset-0 z-50 flex items-center justify-center">
            {/* Backdrop */}
            <motion.div
              initial={{ opacity: 0 }}
              animate={{ opacity: 1 }}
              exit={{ opacity: 0 }}
              onClick={handleCloseModal}
              className="absolute inset-0 bg-[#000000]/70 backdrop-blur-sm"
            />

            {/* Modal Body */}
            <motion.div
              initial={{ opacity: 0, scale: 0.95, y: 10 }}
              animate={{ opacity: 1, scale: 1, y: 0 }}
              exit={{ opacity: 0, scale: 0.95, y: 10 }}
              transition={{ type: 'spring', duration: 0.4 }}
              className="relative w-full max-w-lg rounded-2xl border border-zinc-850 bg-zinc-950 p-6 shadow-2xl z-10 max-h-[90vh] overflow-y-auto"
            >
              {/* Title */}
              <div className="flex items-center justify-between pb-4 border-b border-zinc-900">
                <div className="flex items-center gap-2">
                  <Sparkles className="h-4.5 w-4.5 text-zinc-400 animate-pulse" />
                  <h3 className="text-lg font-bold text-zinc-100">
                    {selectedPrice ? 'Edit Pricing Entry' : 'Add new Pricing Entry'}
                  </h3>
                </div>
                <button
                  onClick={handleCloseModal}
                  className="rounded-md p-1.5 text-zinc-500 hover:text-zinc-200 hover:bg-zinc-900 transition-all cursor-pointer"
                >
                  <X className="h-4.5 w-4.5" />
                </button>
              </div>

              {/* Form */}
              <form onSubmit={handleSubmit} className="mt-5 space-y-4">
                {apiError && (
                  <div className="p-3 rounded-lg border border-rose-950 bg-rose-950/15 text-xs text-rose-400">
                    {apiError}
                  </div>
                )}

                {/* Product Dropdown */}
                <div className="space-y-1.5">
                  <label className="text-xs font-bold text-zinc-400 uppercase tracking-wide">
                    Select Product <span className="text-rose-500">*</span>
                  </label>
                  <select
                    value={formData.productId}
                    onChange={(e) => setFormData(prev => ({ ...prev, productId: e.target.value }))}
                    className={selectClass(formErrors.productId)}
                  >
                    <option value="" disabled className="bg-zinc-950 text-zinc-600">-- Choose Product from Catalog --</option>
                    {productsList.map((p) => (
                      <option key={p.id} value={p.id} className="bg-zinc-950 text-zinc-200">
                        {p.name} ({p.brand})
                      </option>
                    ))}
                  </select>
                  {formErrors.productId && (
                    <p className="text-xs text-rose-500 mt-1">{formErrors.productId}</p>
                  )}
                </div>

                {/* Seller Dropdown */}
                <div className="space-y-1.5">
                  <label className="text-xs font-bold text-zinc-400 uppercase tracking-wide">
                    Select Merchant / Seller <span className="text-rose-500">*</span>
                  </label>
                  <select
                    value={formData.sellerId}
                    onChange={(e) => setFormData(prev => ({ ...prev, sellerId: e.target.value }))}
                    className={selectClass(formErrors.sellerId)}
                  >
                    <option value="" disabled className="bg-zinc-950 text-zinc-600">-- Choose Merchant --</option>
                    {sellersList.map((s) => (
                      <option key={s.id} value={s.id} className="bg-zinc-950 text-zinc-200">
                        {s.name}
                      </option>
                    ))}
                  </select>
                  {formErrors.sellerId && (
                    <p className="text-xs text-rose-500 mt-1">{formErrors.sellerId}</p>
                  )}
                </div>

                {/* Pricing row (Current & Original Price) */}
                <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
                  {/* Current Price */}
                  <div className="space-y-1.5">
                    <label className="text-xs font-bold text-zinc-400 uppercase tracking-wide">
                      Current Price ($) <span className="text-rose-500">*</span>
                    </label>
                    <input
                      type="number"
                      step="0.01"
                      min="0"
                      value={formData.currentPrice}
                      onChange={(e) => setFormData(prev => ({ ...prev, currentPrice: e.target.value }))}
                      placeholder="e.g. 299.99"
                      className={inputClass(formErrors.currentPrice)}
                    />
                    {formErrors.currentPrice && (
                      <p className="text-xs text-rose-500 mt-1">{formErrors.currentPrice}</p>
                    )}
                  </div>

                  {/* Original Price */}
                  <div className="space-y-1.5">
                    <label className="text-xs font-bold text-zinc-400 uppercase tracking-wide">
                      Original Price ($) <span className="text-rose-500">*</span>
                    </label>
                    <input
                      type="number"
                      step="0.01"
                      min="0.01"
                      value={formData.originalPrice}
                      onChange={(e) => setFormData(prev => ({ ...prev, originalPrice: e.target.value }))}
                      placeholder="e.g. 399.99"
                      className={inputClass(formErrors.originalPrice)}
                    />
                    {formErrors.originalPrice && (
                      <p className="text-xs text-rose-500 mt-1">{formErrors.originalPrice}</p>
                    )}
                  </div>
                </div>

                {/* Product URL */}
                <div className="space-y-1.5">
                  <label className="text-xs font-bold text-zinc-400 uppercase tracking-wide flex items-center gap-1.5">
                    <ExternalLink className="h-3.5 w-3.5 text-zinc-500" />
                    <span>Product Merchant Webpage Link</span>
                  </label>
                  <input
                    type="url"
                    value={formData.productUrl}
                    onChange={(e) => setFormData(prev => ({ ...prev, productUrl: e.target.value }))}
                    placeholder="https://amazon.com/dp/B0..."
                    className={inputClass()}
                  />
                </div>

                {/* Footer Buttons */}
                <div className="flex items-center justify-end gap-3 pt-4 border-t border-zinc-900 mt-6">
                  <button
                    type="button"
                    onClick={handleCloseModal}
                    className="px-4 py-2 rounded-lg border border-zinc-850 hover:bg-zinc-900 text-zinc-400 hover:text-zinc-200 text-sm font-semibold transition-all cursor-pointer"
                  >
                    Cancel
                  </button>
                  <button
                    type="submit"
                    disabled={createMutation.isPending || updateMutation.isPending}
                    className="flex items-center gap-1.5 px-4.5 py-2 rounded-lg bg-zinc-100 hover:bg-white text-zinc-950 font-semibold text-sm transition-all disabled:opacity-50 disabled:cursor-not-allowed cursor-pointer"
                  >
                    {(createMutation.isPending || updateMutation.isPending) && (
                      <Loader2 className="h-4 w-4 animate-spin" />
                    )}
                    <span>{selectedPrice ? 'Update Price' : 'Save Price'}</span>
                  </button>
                </div>
              </form>
            </motion.div>
          </div>
        )}
      </AnimatePresence>

      {/* DELETE CONFIRMATION MODAL */}
      <AnimatePresence>
        {isDeleteConfirmOpen && selectedPrice && (
          <div className="fixed inset-0 z-50 flex items-center justify-center">
            {/* Backdrop */}
            <motion.div
              initial={{ opacity: 0 }}
              animate={{ opacity: 1 }}
              exit={{ opacity: 0 }}
              onClick={() => setIsDeleteConfirmOpen(false)}
              className="absolute inset-0 bg-[#000000]/70 backdrop-blur-sm"
            />

            {/* Modal Body */}
            <motion.div
              initial={{ opacity: 0, scale: 0.95, y: 10 }}
              animate={{ opacity: 1, scale: 1, y: 0 }}
              exit={{ opacity: 0, scale: 0.95, y: 10 }}
              transition={{ type: 'spring', duration: 0.4 }}
              className="relative w-full max-w-md rounded-2xl border border-rose-950/40 bg-zinc-950 p-6 shadow-2xl z-10"
            >
              <div className="flex items-start gap-4">
                <div className="h-10 w-10 rounded-full bg-rose-500/10 text-rose-500 flex items-center justify-center shrink-0">
                  <AlertTriangle className="h-5 w-5" />
                </div>
                <div>
                  <h3 className="text-lg font-bold text-zinc-100">
                    Delete Price Entry?
                  </h3>
                  <p className="text-sm text-zinc-400 mt-2">
                    Are you sure you want to delete this price mapping of <span className="font-semibold text-zinc-200">"{selectedPrice.product?.name}"</span> by <span className="font-semibold text-zinc-200">"{selectedPrice.seller?.name}"</span>?
                    This action is permanent and cannot be undone.
                  </p>
                </div>
              </div>

              {/* Actions */}
              <div className="flex items-center justify-end gap-3 mt-6 pt-4 border-t border-zinc-900">
                <button
                  type="button"
                  onClick={() => { setIsDeleteConfirmOpen(false); setSelectedPrice(null); }}
                  className="px-4 py-2 rounded-lg border border-zinc-850 hover:bg-zinc-900 text-zinc-400 hover:text-zinc-200 text-sm font-semibold transition-all cursor-pointer"
                >
                  Cancel
                </button>
                <button
                  type="button"
                  onClick={handleDeleteConfirm}
                  disabled={deleteMutation.isPending}
                  className="flex items-center gap-1.5 px-4.5 py-2 rounded-lg bg-rose-600 hover:bg-rose-500 text-white font-semibold text-sm transition-all disabled:opacity-50 cursor-pointer"
                >
                  {deleteMutation.isPending && (
                    <Loader2 className="h-4 w-4 animate-spin" />
                  )}
                  <span>Delete</span>
                </button>
              </div>
            </motion.div>
          </div>
        )}
      </AnimatePresence>
    </div>
  );
};
