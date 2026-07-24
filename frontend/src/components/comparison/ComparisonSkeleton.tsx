import React from 'react';

export const ComparisonSkeleton: React.FC = () => {
  return (
    <div className="w-full space-y-6 animate-pulse">
      {/* Header Skeleton */}
      <div className="h-8 bg-zinc-900 rounded-md w-1/3" />
      <div className="h-4 bg-zinc-900 rounded-md w-2/3" />

      {/* Grid Cards Skeleton */}
      <div className="grid grid-cols-1 md:grid-cols-3 gap-6 pt-4">
        {[1, 2, 3].map((i) => (
          <div key={i} className="bg-zinc-950 border border-zinc-900 rounded-xl p-6 space-y-4">
            <div className="h-40 bg-zinc-900 rounded-lg w-full" />
            <div className="h-6 bg-zinc-900 rounded w-3/4" />
            <div className="h-4 bg-zinc-900 rounded w-1/2" />
            <div className="h-10 bg-zinc-900 rounded-lg w-full" />
          </div>
        ))}
      </div>

      {/* Table Skeleton */}
      <div className="bg-zinc-950 border border-zinc-900 rounded-xl p-6 space-y-4">
        {[1, 2, 3, 4, 5].map((row) => (
          <div key={row} className="flex gap-4 items-center">
            <div className="h-4 bg-zinc-900 rounded w-1/4" />
            <div className="h-4 bg-zinc-900 rounded flex-1" />
            <div className="h-4 bg-zinc-900 rounded flex-1" />
          </div>
        ))}
      </div>
    </div>
  );
};
