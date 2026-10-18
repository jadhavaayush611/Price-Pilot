package com.pricepilot.intelligence.marketplace.adapter;

import java.util.List;

/**
 * Service Provider Interface for marketplace offer data providers.
 * Providers return raw provider-specific offers which must subsequently pass through
 * OfferNormalizer before being ranked, compared, or presented to callers.
 */
public interface MarketplaceOfferAdapter {

    /**
     * Unique identifier for the provider adapter (e.g. "catalog", "mock", "bestbuy").
     */
    String getProviderId();

    /**
     * Human-readable display name for the provider.
     */
    String getDisplayName();

    /**
     * Returns true if this adapter is enabled and properly configured.
     * Guaranteed to return false for live external providers when no credentials exist.
     */
    boolean isAvailable();

    /**
     * Fetches raw marketplace offers for the given query context.
     * Implementations must handle errors internally or let expected exceptions propagate
     * safely to the orchestrator.
     *
     * @param context Query parameters such as product ID, name, brand, etc.
     * @return List of raw marketplace offers (never null).
     */
    List<RawMarketplaceOffer> fetchRawOffers(MarketplaceQueryContext context);
}
