import React, { useState, useEffect } from 'react';
import { Package } from 'lucide-react';

export interface ProductImageProps {
  src?: string | null;
  alt: string;
  className?: string;
  containerClassName?: string;
  fallbackClassName?: string;
  loading?: 'lazy' | 'eager';
  decoding?: 'async' | 'auto' | 'sync';
  showFallbackText?: boolean;
  fallbackText?: string;
  aspectRatio?: 'square' | 'video' | '4/3' | 'auto';
  onClick?: (e: React.MouseEvent<HTMLElement>) => void;
}

export const ProductImage: React.FC<ProductImageProps> = ({
  src,
  alt,
  className = 'w-full h-full object-contain',
  containerClassName = '',
  fallbackClassName = '',
  loading = 'lazy',
  decoding = 'async',
  showFallbackText = false,
  fallbackText,
  aspectRatio,
  onClick,
}) => {
  const [hasError, setHasError] = useState<boolean>(false);
  const [isLoading, setIsLoading] = useState<boolean>(Boolean(src));

  // Reset error & loading state whenever source URL changes
  useEffect(() => {
    setHasError(false);
    setIsLoading(Boolean(src));
  }, [src]);

  const getAspectClass = () => {
    switch (aspectRatio) {
      case 'square':
        return 'aspect-square';
      case 'video':
        return 'aspect-video';
      case '4/3':
        return 'aspect-[4/3]';
      default:
        return '';
    }
  };

  const isInvalidSrc = !src || typeof src !== 'string' || src.trim() === '' || src === 'null' || src === 'undefined';

  if (isInvalidSrc || hasError) {
    return (
      <div
        className={`flex flex-col items-center justify-center bg-zinc-900/50 border border-zinc-800/60 rounded-xl select-none overflow-hidden ${getAspectClass()} ${containerClassName} ${fallbackClassName}`}
        aria-label={alt || 'Product placeholder'}
        role="img"
        onClick={onClick}
      >
        <div className="flex flex-col items-center justify-center p-3 text-center space-y-1.5">
          <div className="p-2 rounded-lg bg-zinc-800/60 text-zinc-500 border border-zinc-700/40 shadow-inner">
            <Package className="w-5 h-5" aria-hidden="true" />
          </div>
          {showFallbackText && (
            <span className="text-[10px] text-zinc-500 font-mono tracking-tight max-w-[120px] truncate">
              {fallbackText || alt || 'PricePilot'}
            </span>
          )}
        </div>
      </div>
    );
  }

  return (
    <div
      className={`relative overflow-hidden flex items-center justify-center ${getAspectClass()} ${containerClassName}`}
      onClick={onClick}
    >
      {isLoading && (
        <div
          className="absolute inset-0 bg-zinc-900/40 animate-pulse flex items-center justify-center z-10"
          aria-hidden="true"
        >
          <Package className="w-4 h-4 text-zinc-700 animate-pulse" />
        </div>
      )}
      <img
        src={src}
        alt={alt}
        loading={loading}
        decoding={decoding}
        className={`${className} ${isLoading ? 'opacity-0' : 'opacity-100'} transition-opacity duration-300`}
        onLoad={() => setIsLoading(false)}
        onError={() => {
          setIsLoading(false);
          setHasError(true);
        }}
      />
    </div>
  );
};

export default ProductImage;
