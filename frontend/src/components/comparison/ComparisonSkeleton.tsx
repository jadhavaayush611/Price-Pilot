import React from 'react';

export const ComparisonSkeleton: React.FC = () => {
  return (
    <div className="w-full space-y-6 animate-pulse">
      {/* Header Skeleton */}
      <div className="h-8 bg-[var(--color-surface-accent)] rounded-md w-1/3" />
      <div className="h-4 bg-[var(--color-surface-accent)] rounded-md w-2/3" />

      {/* Grid Cards Skeleton */}
      <div className="grid grid-cols-1 md:grid-cols-3 gap-6 pt-4">
        {[1, 2, 3].map((i) => (
          <div key={i} className="bg-[var(--color-surface)] border border-[var(--color-border)] rounded-xl p-6 space-y-4">
            <div className="h-40 bg-[var(--color-surface-accent)] rounded-lg w-full" />
            <div className="h-6 bg-[var(--color-surface-accent)] rounded w-3/4" />
            <div className="h-4 bg-[var(--color-surface-accent)] rounded w-1/2" />
            <div className="h-10 bg-[var(--color-surface-accent)] rounded-lg w-full" />
          </div>
        ))}
      </div>

      {/* Table Skeleton */}
      <div className="bg-[var(--color-surface)] border border-[var(--color-border)] rounded-xl p-6 space-y-4">
        {[1, 2, 3, 4, 5].map((row) => (
          <div key={row} className="flex gap-4 items-center">
            <div className="h-4 bg-[var(--color-surface-accent)] rounded w-1/4" />
            <div className="h-4 bg-[var(--color-surface-accent)] rounded flex-1" />
            <div className="h-4 bg-[var(--color-surface-accent)] rounded flex-1" />
          </div>
        ))}
      </div>
    </div>
  );
};
