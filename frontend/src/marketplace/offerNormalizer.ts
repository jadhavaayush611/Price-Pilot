import type { CurrencyCode } from '../currency';
import { formatPrice, getDisplayPrice } from '../currency';
import type { Product, ProductPrice, ProductWithPrices } from '../types';
import type {
  ConvertedOfferPrices,
  NormalizedOffer,
  NormalizedOfferDTO,
  NormalizerOptions,
  OfferAvailability,
  OfferAvailabilityStatus,
  OfferPricing,
  OfferProvenance,
  OfferProvenanceType,
  OfferSource,
  OfferVerificationStatus,
} from './types';

export const DEFAULT_STALE_HOURS = 168; // 7 days default staleness threshold

/**
 * Validates that a raw base price is a legitimate finite non-negative number.
 * Explicitly rejects missing, null, undefined, NaN, Infinite, and negative values.
 */
export function isValidBasePrice(priceVal: unknown): priceVal is number {
  return typeof priceVal === 'number' && Number.isFinite(priceVal) && !isNaN(priceVal) && priceVal >= 0;
}

/**
 * Normalizes a single raw ProductPrice into a NormalizedOffer.
 * Strictly avoids fabricating missing provenance, timestamps, stock states, or fees.
 * Returns null if the price is invalid, missing, non-finite, or negative.
 */
export function normalizeOffer(
  price: ProductPrice,
  product?: Product | ProductWithPrices,
  options?: NormalizerOptions
): NormalizedOffer | null {
  if (!price) {
    return null;
  }

  // Price Safety Gate: Never convert an invalid, missing, non-finite, or negative price into a valid zero-price offer.
  // Legitimate zero prices (price === 0) are accepted only when explicitly a finite number.
  if (!isValidBasePrice(price.currentPrice)) {
    return null;
  }

  const staleThresholdHours = options?.staleThresholdHours ?? DEFAULT_STALE_HOURS;

  // 1. Source identity extraction
  const sellerId = price.seller?.id || price.sellerId || 'unknown-seller';
  const sellerName = price.seller?.name?.trim() || 'Market Merchant';
  const websiteUrl = price.seller?.websiteUrl || undefined;
  const logoUrl = price.seller?.logoUrl || undefined;
  const offerUrl = price.productUrl || websiteUrl || '#';

  const source: OfferSource = {
    sellerId,
    sellerName,
    websiteUrl,
    logoUrl,
    offerUrl,
  };

  // 2. Pricing normalization: strictly separate listed base price from unknown checkout fees
  const basePrice = price.currentPrice;

  const originalPrice = typeof price.originalPrice === 'number' && Number.isFinite(price.originalPrice) && price.originalPrice > 0
    ? price.originalPrice
    : undefined;

  let discountPercentage = 0;
  if (typeof price.discountPercentage === 'number' && Number.isFinite(price.discountPercentage) && price.discountPercentage > 0) {
    discountPercentage = price.discountPercentage;
  } else if (originalPrice && originalPrice > basePrice) {
    discountPercentage = ((originalPrice - basePrice) / originalPrice) * 100;
  }

  // Fees: Never infer missing shipping or checkout fees to be zero
  const shippingPrice: number | undefined = undefined;
  const taxesAndFees: number | undefined = undefined;
  // Total checkout price can ONLY be calculated when all fees are verified/known
  const totalPrice: number | undefined = undefined;

  const pricing: OfferPricing = {
    basePrice,
    originalPrice,
    discountPercentage,
    currency: 'USD', // Database canonical base currency
    shippingPrice,
    taxesAndFees,
    totalPrice,
    isBasePriceOnly: true,
    pricingDisclaimer: 'Listed base price. Additional shipping, taxes, and merchant fees may apply at checkout.',
  };

  // 3. Provenance and observation timestamp handling
  let observedAt: string | undefined = undefined;
  let rawLastUpdated: string | undefined = undefined;
  let isStale = false;
  let isUnknownFreshness = true;

  if (price.lastUpdated && typeof price.lastUpdated === 'string') {
    const raw = price.lastUpdated.trim();
    // Guard against missing, empty, whitespace, or common junk values
    const isJunk = !raw || /^(null|undefined|invalid(\s+date)?|n\/a|none|unknown)$/i.test(raw);

    if (!isJunk) {
      // Check if the timestamp is an ISO or standard parseable date
      const parsedTime = Date.parse(raw);
      // Ensure it has structured date components (YYYY-MM-DD or standard ISO)
      const hasIsoStructure = /^\d{4}-\d{2}-\d{2}/.test(raw);

      if (!isNaN(parsedTime) && hasIsoStructure) {
        const diffMs = Date.now() - parsedTime;
        // If timestamp is more than 5 minutes (300,000 ms) in the future, reject as untrustworthy
        if (diffMs < -300 * 1000) {
          // Untrustworthy future observation timestamp: do not treat as valid observedAt
          isUnknownFreshness = true;
        } else {
          observedAt = new Date(parsedTime).toISOString();
          isUnknownFreshness = false;
          // Age must be strictly greater than threshold hours (168h * 3600 * 1000); at exactly 168h, it is not stale
          if (diffMs > staleThresholdHours * 3600 * 1000) {
            isStale = true;
          }
        }
      } else {
        // Supported relative timestamp check:
        // Clear relative expressions such as "10 mins ago", "2 hours ago", "yesterday", "just now", "3 days ago"
        const isSupportedRelative = /^(just now|today|yesterday|\d+\s+(sec(ond)?|min(ute)?|hour|day|week|month|year)s?\s+ago)$/i.test(raw);
        if (isSupportedRelative) {
          rawLastUpdated = raw;
        }
      }
    }
  }

  // Base classification
  const baseSourceType: OfferProvenanceType = options?.defaultSourceType || 'demo';
  let verificationStatus: OfferVerificationStatus = options?.defaultVerificationStatus || 'unverified';
  let sourceType: OfferProvenanceType = baseSourceType;

  // Precise Staleness Rule:
  // Stale status does NOT imply a verified source.
  // If the observation is stale:
  // - If it was previously verified (or live provider), verificationStatus degrades to 'stale'.
  // - If it was unverified (e.g. demo seed or user submitted), it strictly remains 'unverified'!
  if (isStale) {
    if (verificationStatus === 'verified') {
      verificationStatus = 'stale';
    }
    if (sourceType === 'live' || sourceType === 'cached') {
      sourceType = 'stale';
    }
  }

  // An offer is live verified ONLY if live, verified, and not stale
  const isLiveVerified = sourceType === 'live' && verificationStatus === 'verified' && !isStale;

  let trustLabel = 'Catalog Offer';
  let trustDescription = 'Offer listed in catalog database. Live merchant checkout pricing may differ.';

  if (sourceType === 'demo') {
    if (isStale) {
      trustLabel = 'Catalog Offer (Stale)';
      trustDescription = 'Catalog seed offer is older than the freshness window. Not a live verified retailer feed.';
    } else if (isUnknownFreshness) {
      trustLabel = 'Catalog Offer (Unverified Freshness)';
      trustDescription = 'Catalog seed offer with unverified observation timestamp. Not a live verified retailer feed.';
    } else {
      trustLabel = 'Catalog Offer';
      trustDescription = 'Demonstration catalog seed offer. Not a live verified retailer feed.';
    }
  } else if (sourceType === 'live') {
    if (isStale) {
      trustLabel = 'Live Verified (Stale)';
      trustDescription = 'Live merchant feed that has exceeded the freshness window.';
    } else if (isUnknownFreshness) {
      trustLabel = 'Live Feed (Unverified Freshness)';
      trustDescription = 'Live merchant feed with unverified observation timestamp.';
    } else {
      trustLabel = 'Live Verified';
      trustDescription = 'Real-time verified offer from integrated merchant endpoint.';
    }
  } else if (sourceType === 'cached') {
    trustLabel = isStale ? 'Cached Offer (Stale)' : 'Cached Offer';
    trustDescription = 'Offer cached from recent observation.';
  } else if (sourceType === 'stale') {
    trustLabel = 'Stale Observation';
    trustDescription = 'Offer timestamp exceeds freshness window and has not been re-verified.';
  } else if (sourceType === 'user_submitted') {
    if (isStale) {
      trustLabel = 'User Submitted (Stale)';
      trustDescription = 'Community-contributed price observation that has exceeded the freshness window.';
    } else if (isUnknownFreshness) {
      trustLabel = 'User Submitted (Unverified Freshness)';
      trustDescription = 'Community-contributed price observation with unverified timestamp.';
    } else {
      trustLabel = 'User Submitted';
      trustDescription = 'Price observation contributed by community member (unverified).';
    }
  } else if (sourceType === 'unknown') {
    trustLabel = 'Unverified Offer';
    trustDescription = 'Offer origin and verification status are unknown.';
  }

  const provenance: OfferProvenance = {
    sourceType,
    verificationStatus,
    isLiveVerified,
    observedAt,
    rawLastUpdated,
    isStale,
    trustLabel,
    trustDescription,
  };

  // 4. Availability / Inventory handling: Never fabricate stock state
  let status: OfferAvailabilityStatus = 'unknown';
  let isAvailable: boolean | undefined = undefined;

  // Check if stock state was explicitly provided on the price entity
  const adhocStock = (price as { inStock?: boolean }).inStock;
  if (typeof adhocStock === 'boolean') {
    status = adhocStock ? 'in_stock' : 'out_of_stock';
    isAvailable = adhocStock;
  }

  const availability: OfferAvailability = {
    status,
    isAvailable,
  };

  // 5. Product association
  const productId = price.productId || product?.id || 'unknown-product';
  const productTitle = product?.name || undefined;

  return {
    id: price.id || `offer-${Math.random().toString(36).slice(2, 9)}`,
    productId,
    productTitle,
    source,
    pricing,
    provenance,
    availability,
    rawPrice: price,
  };
}

/**
 * Normalizes all offers for a product and computes competitive ranking
 * (lowest base price, savings, and price differences) with deterministic tie-breaking.
 */
export function normalizeProductOffers(
  product: ProductWithPrices,
  options?: NormalizerOptions
): NormalizedOffer[] {
  if (!product.prices || product.prices.length === 0) {
    return [];
  }

  const offers: NormalizedOffer[] = [];
  for (const p of product.prices) {
    const offer = normalizeOffer(p, product, options);
    if (offer !== null) {
      offers.push(offer);
    }
  }

  if (offers.length === 0) {
    return [];
  }

  // Sort canonically by base price ascending, tie-breaking by higher discount, then seller name
  offers.sort((a, b) => {
    if (a.pricing.basePrice !== b.pricing.basePrice) {
      return a.pricing.basePrice - b.pricing.basePrice;
    }
    // Tie-break by highest discount percentage descending
    if (b.pricing.discountPercentage !== a.pricing.discountPercentage) {
      return b.pricing.discountPercentage - a.pricing.discountPercentage;
    }
    // Tie-break alphabetically by seller name ascending
    return a.source.sellerName.localeCompare(b.source.sellerName);
  });

  const lowestBasePrice = offers[0].pricing.basePrice;

  // Annotate ranking metrics
  offers.forEach((offer, index) => {
    offer.isBestDeal = index === 0;
    offer.savings = offer.pricing.originalPrice && offer.pricing.originalPrice > offer.pricing.basePrice
      ? offer.pricing.originalPrice - offer.pricing.basePrice
      : 0;
    offer.priceDifferenceFromLowest = offer.pricing.basePrice - lowestBasePrice;
  });

  return offers;
}

/**
 * Converts a normalized offer's canonical pricing into a target display currency.
 * Exactly preserves canonical values and prevents double-conversion bugs.
 */
export function getOfferDisplayPrices(
  offer: NormalizedOffer,
  currency: CurrencyCode
): ConvertedOfferPrices {
  const displayBasePrice = getDisplayPrice(offer.pricing.basePrice, currency);
  const displayOriginalPrice = offer.pricing.originalPrice !== undefined
    ? getDisplayPrice(offer.pricing.originalPrice, currency)
    : undefined;

  const displaySavings = offer.savings !== undefined && offer.savings > 0
    ? getDisplayPrice(offer.savings, currency)
    : 0;

  const displayPriceDiffFromLowest = offer.priceDifferenceFromLowest !== undefined && offer.priceDifferenceFromLowest > 0
    ? getDisplayPrice(offer.priceDifferenceFromLowest, currency)
    : 0;

  return {
    currency,
    displayBasePrice,
    displayOriginalPrice,
    displaySavings,
    displayPriceDiffFromLowest,
    formattedBasePrice: formatPrice(displayBasePrice, currency),
    formattedOriginalPrice: displayOriginalPrice !== undefined
      ? formatPrice(displayOriginalPrice, currency)
      : undefined,
    formattedSavings: formatPrice(displaySavings, currency),
    formattedPriceDiff: formatPrice(displayPriceDiffFromLowest, currency),
  };
}

/**
 * Formats observation time safely:
 * - If observedAt is present, formats it as a clean localized date string (e.g., "Oct 15, 2026").
 * - If only rawLastUpdated is present (e.g., "10 mins ago"), preserves it directly.
 * - Otherwise falls back to "Update time unavailable".
 */
export function formatOfferObservationTime(observedAt?: string, rawLastUpdated?: string): string {
  if (observedAt) {
    try {
      const d = new Date(observedAt);
      if (!isNaN(d.getTime())) {
        return d.toLocaleDateString('en-US', {
          month: 'short',
          day: 'numeric',
          year: 'numeric',
        });
      }
    } catch {
      // Fall through to raw or fallback
    }
  }
  if (rawLastUpdated) {
    return rawLastUpdated;
  }
  return 'Update time unavailable';
}

/**
 * Maps provenance to UI badge styling.
 * Guaranteed: Simulated offers are never given Live Verified status or emerald tone.
 */
export function getProvenanceBadge(provenance: OfferProvenance): {
  label: string;
  tone: 'emerald' | 'amber' | 'blue' | 'zinc' | 'purple';
  description: string;
} {
  const normSourceType = (provenance.sourceType || '').toLowerCase();
  const isSimulated = normSourceType === 'simulated' || provenance.trustLabel?.toLowerCase().includes('simulat');

  // Simulated offers MUST NEVER be live verified or emerald-badged
  if (isSimulated) {
    return {
      label: provenance.isStale ? 'Simulated Offer (Stale)' : 'Simulated Offer',
      tone: 'amber',
      description: provenance.trustDescription || 'Offline simulated offer for demonstration and testing. Not a verified retail price.',
    };
  }

  if (provenance.isLiveVerified) {
    return {
      label: 'Live Verified',
      tone: 'emerald',
      description: provenance.trustDescription,
    };
  }

  if (provenance.isStale) {
    return {
      label: provenance.trustLabel,
      tone: 'amber',
      description: provenance.trustDescription,
    };
  }

  switch (normSourceType) {
    case 'cached':
      return {
        label: 'Cached',
        tone: 'blue',
        description: provenance.trustDescription,
      };
    case 'user_submitted':
      return {
        label: 'User Sourced',
        tone: 'purple',
        description: provenance.trustDescription,
      };
    case 'demo':
    default:
      return {
        label: provenance.trustLabel || 'Catalog Offer',
        tone: 'zinc',
        description: provenance.trustDescription,
      };
  }
}

/**
 * Safely maps a backend NormalizedOfferDTO into a frontend NormalizedOffer.
 * Preserves canonical USD pricing, ensures truthful provenance, and avoids fabricating stock status or fees.
 */
export function mapDtoToNormalizedOffer(dto: NormalizedOfferDTO): NormalizedOffer {
  const rawStatus = (dto.availability?.status || 'UNKNOWN').toLowerCase();
  let status: OfferAvailabilityStatus = 'unknown';
  if (rawStatus === 'in_stock') status = 'in_stock';
  else if (rawStatus === 'out_of_stock') status = 'out_of_stock';
  else if (rawStatus === 'preorder') status = 'preorder';
  else if (rawStatus === 'backorder') status = 'backorder';

  const isAvailable = dto.availability?.available ?? (status === 'in_stock' ? true : status === 'out_of_stock' ? false : undefined);

  const rawSourceType = (dto.provenance?.sourceType || 'demo').toLowerCase();
  const isSimulated = rawSourceType === 'simulated'
    || dto.provenance?.trustLabel?.toLowerCase().includes('simulat')
    || dto.source?.providerName === 'mock-marketplace'
    || dto.source?.providerName === 'mock';

  const sourceType: OfferProvenanceType = isSimulated ? 'simulated' : (rawSourceType as OfferProvenanceType);

  return {
    id: dto.id,
    productId: dto.productId,
    productTitle: dto.productTitle,
    source: {
      sellerId: dto.source?.sellerId || 'unknown-seller',
      sellerName: dto.source?.sellerName?.trim() || 'Market Merchant',
      websiteUrl: dto.source?.websiteUrl,
      logoUrl: dto.source?.logoUrl,
      offerUrl: dto.source?.offerUrl || '#',
      providerName: dto.source?.providerName,
    },
    pricing: {
      basePrice: Number(dto.pricing.basePrice),
      originalPrice: dto.pricing.originalPrice != null ? Number(dto.pricing.originalPrice) : undefined,
      discountPercentage: Number(dto.pricing.discountPercentage || 0),
      currency: (dto.pricing.currency || 'USD') as CurrencyCode,
      shippingPrice: dto.pricing.shippingPrice != null ? Number(dto.pricing.shippingPrice) : undefined,
      taxesAndFees: dto.pricing.taxesAndFees != null ? Number(dto.pricing.taxesAndFees) : undefined,
      totalPrice: dto.pricing.totalPrice != null ? Number(dto.pricing.totalPrice) : undefined,
      isBasePriceOnly: dto.pricing.basePriceOnly ?? true,
      pricingDisclaimer: dto.pricing.pricingDisclaimer || 'Listed base price. Additional shipping, taxes, and merchant fees may apply at checkout.',
    },
    provenance: {
      sourceType,
      verificationStatus: (dto.provenance?.verificationStatus || 'unverified').toLowerCase() as any,
      isLiveVerified: isSimulated ? false : Boolean(dto.provenance?.liveVerified),
      observedAt: dto.provenance?.observedAt,
      rawLastUpdated: dto.provenance?.rawLastUpdated,
      isStale: Boolean(dto.provenance?.stale),
      trustLabel: isSimulated
        ? (Boolean(dto.provenance?.stale) ? 'Simulated Offer (Stale)' : 'Simulated Offer')
        : (dto.provenance?.trustLabel || 'Catalog Offer'),
      trustDescription: dto.provenance?.trustDescription || (isSimulated ? 'Simulated offline offer for demonstration and testing.' : 'Catalog demonstration data.'),
    },
    availability: {
      status,
      isAvailable,
      stockQuantity: dto.availability?.stockQuantity != null ? Number(dto.availability.stockQuantity) : undefined,
    },
    isBestDeal: Boolean(dto.isBestDeal),
    savings: dto.savings != null ? Number(dto.savings) : undefined,
    priceDifferenceFromLowest: dto.priceDifferenceFromLowest != null ? Number(dto.priceDifferenceFromLowest) : undefined,
  };
}

/**
 * Helper to convert a NormalizedOffer back to a legacy ProductPrice shape if needed.
 */
export function offerToLegacyPrice(offer: NormalizedOffer): ProductPrice {
  if (offer.rawPrice) {
    return offer.rawPrice;
  }
  return {
    id: offer.id,
    productId: offer.productId,
    sellerId: offer.source.sellerId,
    currentPrice: offer.pricing.basePrice,
    originalPrice: offer.pricing.originalPrice ?? offer.pricing.basePrice,
    discountPercentage: offer.pricing.discountPercentage,
    productUrl: offer.source.offerUrl,
    lastUpdated: offer.provenance.rawLastUpdated || offer.provenance.observedAt || new Date().toISOString(),
    seller: {
      id: offer.source.sellerId,
      name: offer.source.sellerName,
      websiteUrl: offer.source.websiteUrl || '',
      logoUrl: offer.source.logoUrl || '',
    },
  };
}
