import React from 'react';
import { ChevronDown, ChevronUp, ChevronsUpDown, ChevronLeft, ChevronRight } from 'lucide-react';

export interface Column<T> {
  header: string;
  accessor: keyof T | ((item: T) => React.ReactNode);
  sortable?: boolean;
  sortKey?: string;
  className?: string;
}

interface TableProps<T> {
  data: T[];
  columns: Column<T>[];
  isLoading?: boolean;
  onSort?: (key: string) => void;
  sortKey?: string;
  sortDirection?: 'asc' | 'desc';
  pagination?: {
    currentPage: number; // 0-indexed for backend APIs
    totalPages: number;
    totalElements: number;
    pageSize: number;
    onPageChange: (page: number) => void;
  };
}

export function Table<T>({
  data,
  columns,
  isLoading = false,
  onSort,
  sortKey,
  sortDirection,
  pagination,
}: TableProps<T>) {
  const handleSort = (column: Column<T>) => {
    if (!onSort || !column.sortable) return;
    const key = column.sortKey || (typeof column.accessor === 'string' ? (column.accessor as string) : '');
    if (key) {
      onSort(key);
    }
  };

  const renderSortIcon = (column: Column<T>) => {
    if (!column.sortable || !onSort) return null;
    const key = column.sortKey || (typeof column.accessor === 'string' ? (column.accessor as string) : '');
    
    if (sortKey !== key) {
      return <ChevronsUpDown className="ml-1.5 h-3.5 w-3.5 text-zinc-600 group-hover:text-zinc-400 transition-colors" />;
    }
    
    return sortDirection === 'asc' ? (
      <ChevronUp className="ml-1.5 h-3.5 w-3.5 text-zinc-100" />
    ) : (
      <ChevronDown className="ml-1.5 h-3.5 w-3.5 text-zinc-100" />
    );
  };

  return (
    <div className="w-full flex flex-col">
      {/* Table Container */}
      <div className="overflow-x-auto rounded-xl border border-zinc-900 bg-zinc-950/40 backdrop-blur-md">
        <table className="min-w-full divide-y divide-zinc-900 text-left text-sm text-zinc-400">
          <thead className="bg-zinc-950/80 text-xs font-semibold uppercase tracking-wider text-zinc-500">
            <tr>
              {columns.map((column, index) => (
                <th
                  key={index}
                  scope="col"
                  onClick={() => handleSort(column)}
                  className={`px-6 py-4 ${column.sortable && onSort ? 'cursor-pointer select-none hover:bg-zinc-900/40 hover:text-zinc-200 group' : ''} ${column.className || ''}`}
                >
                  <div className="flex items-center">
                    <span>{column.header}</span>
                    {renderSortIcon(column)}
                  </div>
                </th>
              ))}
            </tr>
          </thead>
          <tbody className="divide-y divide-zinc-900 bg-transparent">
            {isLoading ? (
              // Skeleton loading rows
              Array.from({ length: 5 }).map((_, rIndex) => (
                <tr key={rIndex} className="animate-pulse">
                  {columns.map((_, cIndex) => (
                    <td key={cIndex} className="px-6 py-4.5 whitespace-nowrap">
                      <div className="h-4 bg-zinc-900 rounded w-2/3" />
                    </td>
                  ))}
                </tr>
              ))
            ) : data.length === 0 ? (
              // Empty state
              <tr>
                <td colSpan={columns.length} className="px-6 py-12 text-center text-zinc-600">
                  No records found.
                </td>
              </tr>
            ) : (
              // Data rows
              data.map((item, rIndex) => (
                <tr
                  key={rIndex}
                  className="hover:bg-zinc-900/20 transition-colors"
                >
                  {columns.map((column, cIndex) => {
                    const value =
                      typeof column.accessor === 'function'
                        ? column.accessor(item)
                        : (item[column.accessor] as React.ReactNode);

                    return (
                      <td
                        key={cIndex}
                        className={`px-6 py-4.5 text-zinc-300 font-medium ${column.className || ''}`}
                      >
                        {value}
                      </td>
                    );
                  })}
                </tr>
              ))
            )}
          </tbody>
        </table>
      </div>

      {/* Pagination Controls */}
      {pagination && pagination.totalPages > 1 && (
        <div className="flex items-center justify-between mt-5 px-2">
          {/* Info */}
          <div className="text-xs text-zinc-500">
            Showing <span className="font-semibold text-zinc-400">
              {pagination.currentPage * pagination.pageSize + 1}
            </span> to <span className="font-semibold text-zinc-400">
              {Math.min((pagination.currentPage + 1) * pagination.pageSize, pagination.totalElements)}
            </span> of <span className="font-semibold text-zinc-400">{pagination.totalElements}</span> entries
          </div>

          {/* Navigation Buttons */}
          <div className="flex items-center gap-1.5">
            <button
              onClick={() => pagination.onPageChange(pagination.currentPage - 1)}
              disabled={pagination.currentPage === 0}
              className="flex items-center gap-1 px-3 py-1.5 rounded-lg border border-zinc-900 bg-zinc-950/60 text-xs font-semibold text-zinc-400 hover:text-zinc-100 hover:bg-zinc-900 disabled:opacity-50 disabled:cursor-not-allowed transition-all"
            >
              <ChevronLeft className="h-3.5 w-3.5" />
              <span>Prev</span>
            </button>

            {/* Page indicators */}
            <div className="hidden sm:flex items-center gap-1">
              {Array.from({ length: pagination.totalPages }).map((_, idx) => {
                // Show first, last, current, and pages adjacent to current
                if (
                  idx === 0 ||
                  idx === pagination.totalPages - 1 ||
                  Math.abs(idx - pagination.currentPage) <= 1
                ) {
                  return (
                    <button
                      key={idx}
                      onClick={() => pagination.onPageChange(idx)}
                      className={`h-7 w-7 rounded-lg text-xs font-semibold transition-all ${
                        pagination.currentPage === idx
                          ? 'bg-zinc-100 text-zinc-950 font-bold'
                          : 'border border-zinc-900 bg-zinc-950/40 text-zinc-500 hover:text-zinc-200 hover:bg-zinc-900/60'
                      }`}
                    >
                      {idx + 1}
                    </button>
                  );
                } else if (
                  (idx === 1 && pagination.currentPage > 2) ||
                  (idx === pagination.totalPages - 2 && pagination.currentPage < pagination.totalPages - 3)
                ) {
                  return (
                    <span key={idx} className="text-zinc-700 px-1 text-xs select-none">
                      ...
                    </span>
                  );
                }
                return null;
              })}
            </div>

            <button
              onClick={() => pagination.onPageChange(pagination.currentPage + 1)}
              disabled={pagination.currentPage === pagination.totalPages - 1}
              className="flex items-center gap-1 px-3 py-1.5 rounded-lg border border-zinc-900 bg-zinc-950/60 text-xs font-semibold text-zinc-400 hover:text-zinc-100 hover:bg-zinc-900 disabled:opacity-50 disabled:cursor-not-allowed transition-all"
            >
              <span>Next</span>
              <ChevronRight className="h-3.5 w-3.5" />
            </button>
          </div>
        </div>
      )}
    </div>
  );
}
