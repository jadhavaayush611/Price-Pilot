import React from 'react';

export const AlternativeSkeleton: React.FC = () => {
  return (
    <div className="space-y-6" aria-label="Loading alternatives">
      {/* Banner Skeleton */}
      <div className="h-16 bg-zinc-950 border border-zinc-900 rounded-2xl relative overflow-hidden">
        <div className="absolute inset-0 bg-gradient-to-r from-transparent via-zinc-900/20 to-transparent -translate-x-full animate-shimmer" />
      </div>

      {/* Grid Skeleton */}
      <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-6">
        {[1, 2, 3, 4, 5, 6].map((idx) => (
          <div
            key={idx}
            className="bg-zinc-950 border border-zinc-900 rounded-2xl p-5 space-y-4 relative overflow-hidden"
          >
            <div className="absolute inset-0 bg-gradient-to-r from-transparent via-zinc-900/20 to-transparent -translate-x-full animate-shimmer" />
            <div className="flex justify-between items-center">
              <div className="h-5 w-24 bg-zinc-900 rounded-full" />
              <div className="h-5 w-16 bg-zinc-900 rounded-lg" />
            </div>
            <div className="h-44 w-full bg-zinc-900/50 rounded-xl" />
            <div className="space-y-2">
              <div className="h-4 w-3/4 bg-zinc-900 rounded" />
              <div className="h-3 w-1/2 bg-zinc-900 rounded" />
            </div>
            <div className="h-12 w-full bg-zinc-900/40 rounded-xl" />
            <div className="pt-4 border-t border-zinc-900 flex justify-between items-center">
              <div className="h-6 w-20 bg-zinc-900 rounded" />
              <div className="h-8 w-24 bg-zinc-900 rounded-xl" />
            </div>
          </div>
        ))}
      </div>
    </div>
  );
};
