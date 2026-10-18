import type { CurrencyCode } from '../currency';
import type { ProductPrice } from '../types';

/**
 * Origin classification of offer data.
 * Used to distinguish live verified feeds from cached, stale, demo/seed, or user-submitted data.
 */
export type OfferProvenanceType =
  | 'live'            // Active, directly verified feed from authorized provider
  | 'cached'          // Cached provider/catalog feed within freshness TTL
  | 'stale'           // Observation exceeds freshness TTL
  | 'demo'            // Synthetic seed or demonstration catalog data
  | 'user_submitted'  // Sourced from crowd or user submission
  | 'simulated'       // Simulated offline marketplace test data
  | 'unknown';        // Unverified or missing provenance metadata

/**
 * Verification status of the offer.
 */
export type OfferVerificationStatus =
  | 'verified'    // Confirmed via provider API or verification key
  | 'unverified'  // Not verified (default for catalog seed, demo, or untrusted source)
  | 'stale';      // Was previously verified but verification window expired

/**
 * Offer-level inventory / stock availability status.
 */
export type OfferAvailabilityStatus =
  | 'in_stock'
  | 'out_of_stock'
  | 'preorder'
  | 'backorder'
  | 'unknown';    // MUST be unknown when source data provides no inventory claim

/**
 * Merchant / source attribution for an offer.
 */
export interface OfferSource {
  sellerId: string;
  sellerName: string;
  websiteUrl?: string;
  logoUrl?: string;
  offerUrl: string;
  providerName?: string;
}

/**
 * Pricing breakdown for an offer.
 * Strictly separates listed base price from verified final checkout price.
 * Missing fees are NEVER silently treated as zero.
 */
export interface OfferPricing {
  /** Canonical listed base price in USD */
  basePrice: number;
  /** Original MSRP or list price in USD if available */
  originalPrice?: number;
  /** Discount percentage computed or reported */
  discountPercentage: number;
  /** Canonical currency of the stored base price (standardized to USD) */
  currency: CurrencyCode;
  /** Merchant shipping cost if explicitly known. Undefined if unverified/unknown. */
  shippingPrice?: number;
  /** Taxes and checkout fees if explicitly known. Undefined if unverified/unknown. */
  taxesAndFees?: number;
  /**
   * Total comparable checkout price.
   * Defined ONLY when basePrice, shippingPrice, and all mandatory checkout fees are explicitly known.
   * If shipping or fees are unknown, this MUST remain undefined.
   */
  totalPrice?: number;
  /** Flag indicating that this offer represents only the base price */
  isBasePriceOnly: boolean;
  /** Disclaimer clarifying checkout fee variability */
  pricingDisclaimer: string;
}

/**
 * Provenance and trust metadata.
 */
export interface OfferProvenance {
  sourceType: OfferProvenanceType;
  verificationStatus: OfferVerificationStatus;
  isLiveVerified: boolean;
  /** ISO 8601 timestamp string when the offer was observed or checked */
  observedAt?: string;
  /** Preserved raw string from source data (e.g. relative "10 mins ago" or unparsed format) */
  rawLastUpdated?: string;
  /** Flag indicating whether the observation is considered stale */
  isStale: boolean;
  /** Human-readable short trust badge label */
  trustLabel: string;
  /** Detailed human-readable explanation of the data origin */
  trustDescription: string;
}

/**
 * Inventory availability metadata.
 */
export interface OfferAvailability {
  status: OfferAvailabilityStatus;
  isAvailable?: boolean;
  stockQuantity?: number;
}

/**
 * Normalized Offer representation.
 * Decouples marketplace offer observations from the canonical catalog product,
 * preserving provenance, canonical pricing, display-currency conversion readiness,
 * and seller identity.
 */
export interface NormalizedOffer {
  id: string;
  productId: string;
  productTitle?: string;
  source: OfferSource;
  pricing: OfferPricing;
  provenance: OfferProvenance;
  availability: OfferAvailability;
  /** Whether this offer is currently the lowest listed base price for the associated product */
  isBestDeal?: boolean;
  /** Canonical USD savings compared to originalPrice */
  savings?: number;
  /** Difference in canonical USD compared to the product's lowest base price */
  priceDifferenceFromLowest?: number;
  /** Original ProductPrice entity preserved for backward compatibility */
  rawPrice?: ProductPrice;
}

/**
 * Currency-converted presentation prices for an offer.
 */
export interface ConvertedOfferPrices {
  currency: CurrencyCode;
  displayBasePrice: number;
  displayOriginalPrice?: number;
  displaySavings: number;
  displayPriceDiffFromLowest: number;
  formattedBasePrice: string;
  formattedOriginalPrice?: string;
  formattedSavings: string;
  formattedPriceDiff: string;
}

/**
 * Normalization options.
 */
export interface NormalizerOptions {
  defaultSourceType?: OfferProvenanceType;
  defaultVerificationStatus?: OfferVerificationStatus;
  staleThresholdHours?: number;
}

/**
 * Backend DTO contract for GET /api/v1/products/{id}/offers.
 * Matches com.pricepilot.intelligence.marketplace.dto.NormalizedOfferDTO.
 */
export interface NormalizedOfferDTO {
  id: string;
  productId: string;
  productTitle?: string;
  source: {
    sellerId?: string;
    sellerName: string;
    websiteUrl?: string;
    logoUrl?: string;
    offerUrl: string;
    providerName?: string;
  };
  pricing: {
    basePrice: number;
    originalPrice?: number;
    discountPercentage?: number;
    currency: string;
    shippingPrice?: number | null;
    taxesAndFees?: number | null;
    totalPrice?: number | null;
    basePriceOnly?: boolean;
    pricingDisclaimer?: string;
  };
  provenance: {
    sourceType: string;
    verificationStatus: string;
    liveVerified: boolean;
    observedAt?: string;
    rawLastUpdated?: string;
    stale: boolean;
    trustLabel: string;
    trustDescription: string;
  };
  availability?: {
    status: string;
    available?: boolean | null;
    stockQuantity?: number | null;
  };
  isBestDeal?: boolean;
  savings?: number;
  priceDifferenceFromLowest?: number;
}
