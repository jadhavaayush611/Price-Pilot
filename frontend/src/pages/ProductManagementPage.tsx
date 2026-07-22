import React, { useState } from 'react';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { apiService } from '../services/api';
import { Table } from '../components/Table';
import type { Column } from '../components/Table';
import type { Product } from '../types';
import { 
  Plus, 
  Search, 
  Edit2, 
  Trash2, 
  X, 
  Loader2, 
  Sparkles, 
  Package, 
  AlertTriangle,
  Image as ImageIcon
} from 'lucide-react';
import { motion, AnimatePresence } from 'framer-motion';

interface FormState {
  name: string;
  brand: string;
  category: string;
  description: string;
  imageUrl: string;
}

const initialFormState: FormState = {
  name: '',
  brand: '',
  category: '',
  description: '',
  imageUrl: '',
};

export const ProductManagementPage: React.FC = () => {
  const queryClient = useQueryClient();

  // Search & Pagination States
  const [searchTerm, setSearchTerm] = useState('');
  const [debouncedSearch, setDebouncedSearch] = useState('');
  const [page, setPage] = useState(0);
  const [pageSize] = useState(10);
  const [sortKey, setSortKey] = useState<string>('createdAt');
  const [sortDirection, setSortDirection] = useState<'asc' | 'desc'>('desc');

  // UI States
  const [isModalOpen, setIsModalOpen] = useState(false);
  const [isDeleteConfirmOpen, setIsDeleteConfirmOpen] = useState(false);
  const [selectedProduct, setSelectedProduct] = useState<Product | null>(null);
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
    queryKey: ['products', page, pageSize, sortKey, sortDirection, debouncedSearch],
    queryFn: () => apiService.getProducts(page, pageSize, sortKey, sortDirection, debouncedSearch),
  });

  // Mutations
  const createMutation = useMutation({
    mutationFn: (newProduct: FormState) => apiService.createProduct(newProduct),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['products'] });
      handleCloseModal();
    },
    onError: (error: unknown) => {
      const err = error as { response?: { data?: { message?: string } } };
      const msg = err?.response?.data?.message || 'Failed to create product. Please check validation requirements.';
      setApiError(msg);
    }
  });

  const updateMutation = useMutation({
    mutationFn: ({ id, product }: { id: string; product: FormState }) => 
      apiService.updateProduct(id, product),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['products'] });
      handleCloseModal();
    },
    onError: (error: unknown) => {
      const err = error as { response?: { data?: { message?: string } } };
      const msg = err?.response?.data?.message || 'Failed to update product.';
      setApiError(msg);
    }
  });

  const deleteMutation = useMutation({
    mutationFn: (id: string) => apiService.deleteProduct(id),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['products'] });
      setIsDeleteConfirmOpen(false);
      setSelectedProduct(null);
    },
    onError: (error: unknown) => {
      const err = error as { response?: { data?: { message?: string } } };
      alert(err?.response?.data?.message || 'Failed to delete product.');
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
    setSelectedProduct(null);
    setFormData(initialFormState);
    setFormErrors({});
    setApiError(null);
    setIsModalOpen(true);
  };

  const handleOpenEditModal = (product: Product) => {
    setSelectedProduct(product);
    setFormData({
      name: product.name,
      brand: product.brand || '',
      category: product.category,
      description: product.description || '',
      imageUrl: product.imageUrl || '',
    });
    setFormErrors({});
    setApiError(null);
    setIsModalOpen(true);
  };

  const handleCloseModal = () => {
    setIsModalOpen(false);
    setSelectedProduct(null);
    setFormData(initialFormState);
    setFormErrors({});
    setApiError(null);
  };

  const handleOpenDeleteConfirm = (product: Product) => {
    setSelectedProduct(product);
    setIsDeleteConfirmOpen(true);
  };

  const validateForm = (): boolean => {
    const errors: Partial<FormState> = {};
    if (!formData.name.trim()) errors.name = 'Product name is required';
    if (!formData.category.trim()) errors.category = 'Category is required';
    
    setFormErrors(errors);
    return Object.keys(errors).length === 0;
  };

  const handleSubmit = (e: React.FormEvent) => {
    e.preventDefault();
    setApiError(null);

    if (!validateForm()) return;

    if (selectedProduct) {
      updateMutation.mutate({ id: selectedProduct.id, product: formData });
    } else {
      createMutation.mutate(formData);
    }
  };

  const handleDeleteConfirm = () => {
    if (selectedProduct) {
      deleteMutation.mutate(selectedProduct.id);
    }
  };

  // Reusable Form Input CSS Classes
  const inputClass = (error?: string) => `w-full px-4 py-2.5 rounded-lg border ${
    error ? 'border-rose-500/60 focus:border-rose-500' : 'border-zinc-800 focus:border-zinc-600'
  } bg-zinc-950 text-zinc-100 placeholder-zinc-600 text-sm focus:outline-none transition-all duration-200`;

  // Columns definition for Table component
  const columns: Column<Product>[] = [
    {
      header: 'Product',
      accessor: (product: Product) => (
        <div className="flex items-center gap-3.5">
          <div className="h-10 w-10 rounded-lg overflow-hidden border border-zinc-900 bg-zinc-950 flex items-center justify-center text-zinc-700 shrink-0">
            {product.imageUrl ? (
              <img 
                src={product.imageUrl} 
                alt={product.name} 
                className="h-full w-full object-cover"
                onError={(e) => {
                  (e.target as HTMLImageElement).src = ''; // Clear source to trigger fallback icon
                }}
              />
            ) : (
              <Package className="h-5 w-5" />
            )}
          </div>
          <div className="flex flex-col min-w-0">
            <span className="text-zinc-200 font-semibold truncate max-w-[200px] sm:max-w-[300px]">
              {product.name}
            </span>
            <span className="text-xs text-zinc-500">
              ID: {product.id.substring(0, 8)}...
            </span>
          </div>
        </div>
      ),
      sortable: true,
      sortKey: 'name',
    },
    {
      header: 'Brand',
      accessor: (product: Product) => (
        <span className="px-2.5 py-1 rounded-md text-xs font-semibold tracking-wide bg-zinc-900 border border-zinc-800 text-zinc-300">
          {product.brand || 'N/A'}
        </span>
      ),
      sortable: true,
      sortKey: 'brand',
    },
    {
      header: 'Category',
      accessor: (product: Product) => (
        <span className="px-2.5 py-1 rounded-md text-xs font-semibold tracking-wide bg-zinc-900/60 border border-zinc-800/80 text-zinc-400">
          {product.category}
        </span>
      ),
      sortable: true,
      sortKey: 'category',
    },
    {
      header: 'Description',
      accessor: (product: Product) => (
        <span className="text-zinc-500 font-normal line-clamp-1 max-w-[240px]">
          {product.description || 'No description provided'}
        </span>
      ),
    },
    {
      header: 'Actions',
      accessor: (product: Product) => (
        <div className="flex items-center gap-2">
          <button
            onClick={() => handleOpenEditModal(product)}
            className="p-1.5 rounded-md border border-zinc-900 hover:border-zinc-700 hover:text-zinc-100 transition-all cursor-pointer"
            title="Edit Product"
          >
            <Edit2 className="h-4 w-4" />
          </button>
          <button
            onClick={() => handleOpenDeleteConfirm(product)}
            className="p-1.5 rounded-md border border-zinc-900 hover:border-rose-900/60 hover:text-rose-400 transition-all cursor-pointer"
            title="Delete Product"
          >
            <Trash2 className="h-4 w-4" />
          </button>
        </div>
      ),
      className: 'w-[100px] text-right',
    }
  ];

  return (
    <div className="space-y-6">
      {/* Header section */}
      <div className="flex flex-col sm:flex-row sm:items-center sm:justify-between gap-4">
        <div>
          <div className="flex items-center gap-2">
            <span className="p-1.5 bg-zinc-900/80 border border-zinc-800 rounded-lg text-zinc-300">
              <Package className="h-5 w-5" />
            </span>
            <h1 className="text-2xl font-bold tracking-tight text-white sm:text-3xl">
              Product Catalogue
            </h1>
          </div>
          <p className="mt-1 text-sm text-zinc-400">
            Create, edit, view, and delete products in the database.
          </p>
        </div>

        <button
          onClick={handleOpenCreateModal}
          className="flex items-center justify-center gap-1.5 px-4.5 py-2.5 rounded-lg bg-zinc-100 hover:bg-white text-zinc-950 font-semibold text-sm transition-all shadow-[0_0_12px_rgba(255,255,255,0.1)] hover:scale-[1.01] active:scale-[0.99] cursor-pointer"
        >
          <Plus className="h-4 w-4" />
          <span>Add Product</span>
        </button>
      </div>

      {/* Filter and Table Card */}
      <div className="rounded-2xl border border-zinc-900 bg-zinc-950/20 p-5 space-y-4">
        {/* Search filter */}
        <div className="flex items-center max-w-sm rounded-lg border border-zinc-800 bg-zinc-950/60 px-3 py-2">
          <Search className="h-4 w-4 text-zinc-500 mr-2 shrink-0" />
          <input
            type="text"
            placeholder="Search catalog by name, brand, category..."
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
                    {selectedProduct ? 'Edit Product details' : 'Add new Product'}
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

                {/* Name */}
                <div className="space-y-1.5">
                  <label className="text-xs font-bold text-zinc-400 uppercase tracking-wide">
                    Product Name <span className="text-rose-500">*</span>
                  </label>
                  <input
                    type="text"
                    value={formData.name}
                    onChange={(e) => setFormData(prev => ({ ...prev, name: e.target.value }))}
                    placeholder="e.g. Sony WH-1000XM5 Noise Canceling Headphones"
                    className={inputClass(formErrors.name)}
                  />
                  {formErrors.name && (
                    <p className="text-xs text-rose-500 mt-1">{formErrors.name}</p>
                  )}
                </div>

                {/* Grid for Brand & Category */}
                <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
                  {/* Brand */}
                  <div className="space-y-1.5">
                    <label className="text-xs font-bold text-zinc-400 uppercase tracking-wide">
                      Brand
                    </label>
                    <input
                      type="text"
                      value={formData.brand}
                      onChange={(e) => setFormData(prev => ({ ...prev, brand: e.target.value }))}
                      placeholder="e.g. Sony, Apple"
                      className={inputClass()}
                    />
                  </div>

                  {/* Category */}
                  <div className="space-y-1.5">
                    <label className="text-xs font-bold text-zinc-400 uppercase tracking-wide">
                      Category <span className="text-rose-500">*</span>
                    </label>
                    <input
                      type="text"
                      value={formData.category}
                      onChange={(e) => setFormData(prev => ({ ...prev, category: e.target.value }))}
                      placeholder="e.g. Electronics, Home"
                      className={inputClass(formErrors.category)}
                    />
                    {formErrors.category && (
                      <p className="text-xs text-rose-500 mt-1">{formErrors.category}</p>
                    )}
                  </div>
                </div>

                {/* Image URL */}
                <div className="space-y-1.5">
                  <label className="text-xs font-bold text-zinc-400 uppercase tracking-wide flex items-center gap-1.5">
                    <ImageIcon className="h-3.5 w-3.5 text-zinc-500" />
                    <span>Image URL</span>
                  </label>
                  <input
                    type="url"
                    value={formData.imageUrl}
                    onChange={(e) => setFormData(prev => ({ ...prev, imageUrl: e.target.value }))}
                    placeholder="https://images.unsplash.com/photo-..."
                    className={inputClass()}
                  />
                </div>

                {/* Description */}
                <div className="space-y-1.5">
                  <label className="text-xs font-bold text-zinc-400 uppercase tracking-wide">
                    Description
                  </label>
                  <textarea
                    value={formData.description}
                    onChange={(e) => setFormData(prev => ({ ...prev, description: e.target.value }))}
                    placeholder="Provide a comprehensive product specification or summary..."
                    rows={3}
                    className="w-full px-4 py-2.5 rounded-lg border border-zinc-800 focus:border-zinc-600 bg-zinc-950 text-zinc-100 placeholder-zinc-600 text-sm focus:outline-none transition-all duration-200 resize-none"
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
                    <span>{selectedProduct ? 'Update Product' : 'Save Product'}</span>
                  </button>
                </div>
              </form>
            </motion.div>
          </div>
        )}
      </AnimatePresence>

      {/* DELETE CONFIRMATION MODAL */}
      <AnimatePresence>
        {isDeleteConfirmOpen && selectedProduct && (
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
                    Delete Product?
                  </h3>
                  <p className="text-sm text-zinc-400 mt-2">
                    Are you sure you want to delete <span className="font-semibold text-zinc-200">"{selectedProduct.name}"</span>? 
                    This action is permanent and cannot be undone. All corresponding references will be removed.
                  </p>
                </div>
              </div>

              {/* Actions */}
              <div className="flex items-center justify-end gap-3 mt-6 pt-4 border-t border-zinc-900">
                <button
                  type="button"
                  onClick={() => { setIsDeleteConfirmOpen(false); setSelectedProduct(null); }}
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
