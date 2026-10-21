import React, { useState, useEffect } from 'react';
import {
  Smartphone,
  Laptop,
  Tablet,
  Headphones,
  AudioLines,
  Watch,
  Gamepad2,
  Monitor,
  Keyboard,
  Mouse,
  HardDrive,
  Router,
  Camera,
  Package,
} from 'lucide-react';

export interface ProductImageProps {
  src?: string | null;
  alt: string;
  category?: string | null;
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

export const getCategoryIcon = (category?: string | null): React.FC<{ className?: string; 'aria-hidden'?: boolean | 'true' | 'false' }> => {
  if (!category || typeof category !== 'string') return Package;

  const normalized = category.trim().toLowerCase();
  switch (normalized) {
    case 'smartphone':
    case 'smartphones':
    case 'phone':
    case 'mobile':
      return Smartphone;

    case 'laptop':
    case 'laptops':
    case 'notebook':
      return Laptop;

    case 'tablet':
    case 'tablets':
    case 'ipad':
      return Tablet;

    case 'headphones':
    case 'headphone':
      return Headphones;

    case 'earbuds':
    case 'earbud':
    case 'earphones':
    case 'audio':
      return AudioLines;

    case 'smartwatch':
    case 'smartwatches':
    case 'watch':
    case 'wearable':
    case 'wearables':
      return Watch;

    case 'gaming console':
    case 'gaming':
    case 'console':
    case 'gaming consoles':
      return Gamepad2;

    case 'monitor':
    case 'monitors':
    case 'display':
    case 'screen':
      return Monitor;

    case 'keyboard':
    case 'keyboards':
      return Keyboard;

    case 'mouse':
    case 'mice':
      return Mouse;

    case 'storage':
    case 'ssd':
    case 'hdd':
    case 'hard drive':
      return HardDrive;

    case 'networking':
    case 'router':
    case 'routers':
    case 'wifi':
      return Router;

    case 'camera':
    case 'cameras':
    case 'photography':
      return Camera;

    case 'accessory':
    case 'accessories':
    default:
      return Package;
  }
};

export const ProductImage: React.FC<ProductImageProps> = ({
  src,
  alt,
  category,
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
  const FallbackIcon = getCategoryIcon(category);

  if (isInvalidSrc || hasError) {
    const displayLabel = fallbackText || category || alt || 'PricePilot';
    return (
      <div
        className={`flex flex-col items-center justify-center bg-[var(--color-surface)] border border-[var(--color-border)] rounded-xl select-none overflow-hidden ${getAspectClass()} ${containerClassName} ${fallbackClassName}`}
        aria-label={alt || `${category || 'Product'} placeholder`}
        role="img"
        onClick={onClick}
      >
        <div className="flex flex-col items-center justify-center p-3 text-center space-y-1.5">
          <div className="p-2.5 rounded-xl bg-[var(--color-surface-accent)] text-[var(--color-primary)] border border-[var(--color-border)] shadow-inner">
            <FallbackIcon className="w-6 h-6" aria-hidden="true" />
          </div>
          {showFallbackText && (
            <span className="text-[10px] text-[var(--color-muted-foreground)] font-mono tracking-tight max-w-[120px] truncate">
              {displayLabel}
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
          className="absolute inset-0 bg-[var(--color-surface-accent)]/40 animate-pulse flex items-center justify-center z-10"
          aria-hidden="true"
        >
          <FallbackIcon className="w-5 h-5 text-[var(--color-muted-foreground)] animate-pulse" />
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
