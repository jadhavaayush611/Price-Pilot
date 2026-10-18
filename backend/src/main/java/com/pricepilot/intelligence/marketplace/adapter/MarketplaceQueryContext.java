package com.pricepilot.intelligence.marketplace.adapter;

import java.util.UUID;

/**
 * Context passed to marketplace adapters describing the product query.
 */
public record MarketplaceQueryContext(
        UUID productId,
        String productName,
        String brand,
        String category,
        String sku,
        int maxOffers
) {
    public MarketplaceQueryContext {
        if (maxOffers <= 0) {
            maxOffers = 10;
        }
    }

    public static MarketplaceQueryContext of(UUID productId, String productName) {
        return new MarketplaceQueryContext(productId, productName, null, null, null, 10);
    }
}
