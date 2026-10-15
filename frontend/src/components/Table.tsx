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

function TableImpl<T>({
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
      return <ChevronsUpDown className="ml-1.5 h-3.5 w-3.5 text-[var(--color-muted-foreground)] group-hover:text-[var(--color-foreground)] transition-colors" />;
    }
    
    return sortDirection === 'asc' ? (
      <ChevronUp className="ml-1.5 h-3.5 w-3.5 text-[var(--color-foreground)]" />
    ) : (
      <ChevronDown className="ml-1.5 h-3.5 w-3.5 text-[var(--color-foreground)]" />
    );
  };

  return (
    <div className="w-full flex flex-col">
      {/* Table Container */}
      <div className="overflow-x-auto rounded-xl border border-[var(--color-border)] bg-[var(--color-surface)] backdrop-blur-md">
        <table className="min-w-full divide-y divide-[var(--color-border)] text-left text-sm text-[var(--color-muted-foreground)]">
          <thead className="bg-[var(--color-surface-accent)] text-xs font-semibold uppercase tracking-wider text-[var(--color-muted-foreground)]">
            <tr>
              {columns.map((column, index) => (
                <th
                  key={index}
                  scope="col"
                  onClick={() => handleSort(column)}
                  className={`px-6 py-4 ${column.sortable && onSort ? 'cursor-pointer select-none hover:bg-[var(--color-surface)]/60 hover:text-[var(--color-foreground)] group' : ''} ${column.className || ''}`}
                >
                  <div className="flex items-center">
                    <span>{column.header}</span>
                    {renderSortIcon(column)}
                  </div>
                </th>
              ))}
            </tr>
          </thead>
          <tbody className="divide-y divide-[var(--color-border)] bg-transparent">
            {isLoading ? (
              // Skeleton loading rows
              Array.from({ length: 5 }).map((_, rIndex) => (
                <tr key={rIndex} className="animate-pulse">
                  {columns.map((_, cIndex) => (
                    <td key={cIndex} className="px-6 py-4.5 whitespace-nowrap">
                      <div className="h-4 bg-[var(--color-surface-accent)] rounded w-2/3" />
                    </td>
                  ))}
                </tr>
              ))
            ) : data.length === 0 ? (
              // Empty state
              <tr>
                <td colSpan={columns.length} className="px-6 py-12 text-center text-[var(--color-muted-foreground)]">
                  No records found.
                </td>
              </tr>
            ) : (
              // Data rows
              data.map((item, rIndex) => (
                <tr
                  key={rIndex}
                  className="hover:bg-[var(--color-surface-accent)]/50 transition-colors"
                >
                  {columns.map((column, cIndex) => {
                    const value =
                      typeof column.accessor === 'function'
                        ? column.accessor(item)
                        : (item[column.accessor] as React.ReactNode);

                    return (
                      <td
                        key={cIndex}
                        className={`px-6 py-4.5 text-[var(--color-foreground)] font-medium ${column.className || ''}`}
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
          <div className="text-xs text-[var(--color-muted-foreground)]">
            Showing <span className="font-semibold text-[var(--color-foreground)]">
              {pagination.currentPage * pagination.pageSize + 1}
            </span> to <span className="font-semibold text-[var(--color-foreground)]">
              {Math.min((pagination.currentPage + 1) * pagination.pageSize, pagination.totalElements)}
            </span> of <span className="font-semibold text-[var(--color-foreground)]">{pagination.totalElements}</span> entries
          </div>

          {/* Navigation Buttons */}
          <div className="flex items-center gap-1.5">
            <button
              onClick={() => pagination.onPageChange(pagination.currentPage - 1)}
              disabled={pagination.currentPage === 0}
              className="flex items-center gap-1 px-3 py-1.5 rounded-lg border border-[var(--color-border)] bg-[var(--color-surface)] text-xs font-semibold text-[var(--color-muted-foreground)] hover:text-[var(--color-foreground)] hover:bg-[var(--color-surface-accent)] disabled:opacity-50 disabled:cursor-not-allowed transition-all"
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
                          ? 'bg-[var(--color-primary)] text-[var(--color-primary-foreground)] font-bold shadow-sm'
                          : 'border border-[var(--color-border)] bg-[var(--color-surface)] text-[var(--color-muted-foreground)] hover:text-[var(--color-foreground)] hover:bg-[var(--color-surface-accent)]'
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
                    <span key={idx} className="text-[var(--color-muted-foreground)] px-1 text-xs select-none">
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
              className="flex items-center gap-1 px-3 py-1.5 rounded-lg border border-[var(--color-border)] bg-[var(--color-surface)] text-xs font-semibold text-[var(--color-muted-foreground)] hover:text-[var(--color-foreground)] hover:bg-[var(--color-surface-accent)] disabled:opacity-50 disabled:cursor-not-allowed transition-all"
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

export const Table = React.memo(TableImpl) as <T>(props: TableProps<T>) => React.ReactElement;
