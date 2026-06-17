import React, { useEffect, useState } from 'react';
import { useParams, useNavigate } from 'react-router-dom';
import { apiService } from '../services/api';
import type { ProductWithPrices } from '../types';
import { ArrowLeft, Clock, ExternalLink, Sparkles, Tag } from 'lucide-react';

export const ProductPage: React.FC = () => {
  const { id } = useParams<{ id: string }>();
  const navigate = useNavigate();
  const [product, setProduct] = useState<ProductWithPrices | null>(null);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    if (id) {
      setLoading(true);
      apiService.getProduct(id)
        .then((data) => {
          setProduct(data);
        })
        .finally(() => {
          setLoading(false);
        });
    }
  }, [id]);

  if (loading) {
    return (
      <div className="flex flex-col gap-8 py-10 animate-pulse">
        <div className="h-6 w-24 bg-zinc-900 rounded" />
        <div className="grid grid-cols-1 md:grid-cols-2 gap-12">
          <div className="aspect-[4/3] rounded-2xl bg-zinc-900" />
          <div className="flex flex-col gap-4">
            <div className="h-10 w-3/4 bg-zinc-900 rounded" />
            <div className="h-20 w-full bg-zinc-900 rounded" />
          </div>
        </div>
      </div>
    );
  }

  if (!product) {
    return (
      <div className="flex flex-col items-center justify-center py-20 gap-4 text-center">
        <h2 className="text-xl font-bold text-white">Product Not Found</h2>
        <p className="text-zinc-500 text-sm">The product details you are looking for are unavailable.</p>
        <button
          onClick={() => navigate('/search')}
          className="flex items-center gap-2 px-4 py-2 bg-zinc-900 hover:bg-zinc-800 text-sm font-semibold rounded-lg border border-zinc-800 text-zinc-300 cursor-pointer"
        >
          <ArrowLeft className="h-4 w-4" /> Back to Search
        </button>
      </div>
    );
  }

  // Sort prices so the lowest price is first, to easily find the lowest seller
  const sortedPrices = [...product.prices].sort((a, b) => a.currentPrice - b.currentPrice);
  const lowestPriceId = sortedPrices[0]?.id;

  return (
    <div className="flex flex-col gap-8 py-6">
      {/* Back Button */}
      <div>
        <button
          onClick={() => navigate(-1)}
          className="inline-flex items-center gap-2 text-xs font-semibold text-zinc-500 hover:text-zinc-300 transition-colors cursor-pointer"
        >
          <ArrowLeft className="h-4.5 w-4.5" /> Back to listings
        </button>
      </div>

      {/* Product Information */}
      <div className="grid grid-cols-1 md:grid-cols-2 gap-12 items-start border-b border-zinc-900 pb-12">
        {/* Left: Product Image */}
        <div className="aspect-[4/3] rounded-2xl overflow-hidden bg-zinc-950 border border-zinc-900 shadow-2xl">
          <img
            src={product.imageUrl}
            alt={product.name}
            className="w-full h-full object-cover"
          />
        </div>

        {/* Right: Details */}
        <div className="flex flex-col gap-6">
          <div>
            <span className="px-2.5 py-1 rounded bg-zinc-950 border border-zinc-900 text-xs text-zinc-400 font-semibold tracking-wider uppercase">
              {product.category}
            </span>
            <h1 className="text-3xl font-extrabold tracking-tight text-white mt-4 mb-2 leading-tight">
              {product.name}
            </h1>
            <p className="text-xs font-semibold tracking-widest text-zinc-500 uppercase">
              Brand: {product.brand}
            </p>
          </div>

          <div className="flex flex-col gap-2">
            <h3 className="text-xs font-semibold text-zinc-500 uppercase">Overview</h3>
            <p className="text-sm text-zinc-400 leading-relaxed">
              {product.description}
            </p>
          </div>

          {/* Quick Stats */}
          <div className="grid grid-cols-2 gap-4 p-4 rounded-xl bg-zinc-950/40 border border-zinc-900">
            <div className="flex flex-col">
              <span className="text-[10px] text-zinc-500 uppercase font-semibold">Best Price</span>
              <span className="text-xl font-bold text-white">${sortedPrices[0]?.currentPrice}</span>
            </div>
            <div className="flex flex-col">
              <span className="text-[10px] text-zinc-500 uppercase font-semibold">Price Range</span>
              <span className="text-xl font-bold text-zinc-400">
                ${sortedPrices[0]?.currentPrice} - ${sortedPrices[sortedPrices.length - 1]?.currentPrice}
              </span>
            </div>
          </div>
        </div>
      </div>

      {/* Comparison Deals */}
      <div className="flex flex-col gap-6">
        <div>
          <h2 className="text-lg font-bold text-white m-0">Compare Seller Prices</h2>
          <p className="text-xs text-zinc-500">Real-time offers updated dynamically</p>
        </div>

        <div className="overflow-x-auto rounded-xl border border-zinc-900 bg-zinc-950/30">
          <table className="w-full text-left border-collapse">
            <thead>
              <tr className="border-b border-zinc-900 bg-zinc-950/60 text-xs font-semibold uppercase text-zinc-500">
                <th className="px-6 py-4">Seller</th>
                <th className="px-6 py-4">Price</th>
                <th className="px-6 py-4">Discount</th>
                <th className="px-6 py-4">Last Updated</th>
                <th className="px-6 py-4 text-right">Action</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-zinc-900 text-sm">
              {sortedPrices.map((price) => {
                const isLowest = price.id === lowestPriceId;
                return (
                  <tr
                    key={price.id}
                    className={`transition-colors hover:bg-zinc-900/10 ${
                      isLowest ? 'bg-emerald-500/[0.02]' : ''
                    }`}
                  >
                    {/* Seller Logo & Name */}
                    <td className="px-6 py-4 flex items-center gap-3 font-medium text-white">
                      {price.seller?.logoUrl ? (
                        <div className="h-6 w-12 flex items-center justify-center bg-zinc-950 border border-zinc-900 rounded p-1">
                          <img
                            src={price.seller.logoUrl}
                            alt={price.seller.name}
                            className="max-h-full max-w-full object-contain"
                          />
                        </div>
                      ) : (
                        <span className="h-6 w-12 flex items-center justify-center bg-zinc-900 border border-zinc-800 rounded text-[10px] text-zinc-400 uppercase">
                          {price.seller?.name.substring(0, 3)}
                        </span>
                      )}
                      <div className="flex flex-col">
                        <span>{price.seller?.name}</span>
                        {isLowest && (
                          <span className="inline-flex items-center gap-1 text-[10px] font-bold text-emerald-400">
                            <Sparkles className="h-3 w-3" /> Best Deal
                          </span>
                        )}
                      </div>
                    </td>

                    {/* Price & Original Price */}
                    <td className="px-6 py-4">
                      <div className="flex items-center gap-2">
                        <span className="font-bold text-white">${price.currentPrice}</span>
                        {price.originalPrice > price.currentPrice && (
                          <span className="text-xs text-zinc-500 line-through">
                            ${price.originalPrice}
                          </span>
                        )}
                      </div>
                    </td>

                    {/* Discount badge */}
                    <td className="px-6 py-4">
                      {price.discountPercentage > 0 ? (
                        <span className="inline-flex items-center gap-1 px-2 py-0.5 rounded bg-rose-500/10 text-rose-400 text-xs font-semibold border border-rose-500/20">
                          <Tag className="h-3 w-3" /> Save {price.discountPercentage.toFixed(0)}%
                        </span>
                      ) : (
                        <span className="text-zinc-600 text-xs">-</span>
                      )}
                    </td>

                    {/* Last Updated */}
                    <td className="px-6 py-4 text-zinc-500 text-xs">
                      <span className="inline-flex items-center gap-1">
                        <Clock className="h-3.5 w-3.5" /> {price.lastUpdated}
                      </span>
                    </td>

                    {/* Redirect button */}
                    <td className="px-6 py-4 text-right">
                      <a
                        href={price.productUrl}
                        target="_blank"
                        rel="noopener noreferrer"
                        className={`inline-flex items-center gap-1.5 px-3 py-1.5 text-xs font-semibold rounded-lg border transition-all ${
                          isLowest
                            ? 'bg-white text-black hover:bg-zinc-200 border-white'
                            : 'bg-zinc-900 text-zinc-300 hover:bg-zinc-800 border-zinc-800 hover:text-white'
                        }`}
                      >
                        Go to Store
                        <ExternalLink className="h-3 w-3" />
                      </a>
                    </td>
                  </tr>
                );
              })}
            </tbody>
          </table>
        </div>
      </div>
    </div>
  );
};
