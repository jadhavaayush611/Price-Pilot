import React from 'react';

export const RecommendationSkeleton: React.FC = () => {
  return (
    <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-6 animate-pulse">
      {[1, 2, 3].map((i) => (
        <div key={i} className="bg-zinc-950 border border-zinc-900 rounded-xl p-5 space-y-4">
          <div className="h-44 bg-zinc-900 rounded-lg w-full" />
          <div className="h-5 bg-zinc-900 rounded w-3/4" />
          <div className="h-4 bg-zinc-900 rounded w-1/2" />
          <div className="h-8 bg-zinc-900 rounded w-full" />
        </div>
      ))}
    </div>
  );
};
