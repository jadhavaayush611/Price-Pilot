package com.pricepilot.intelligence.marketplace.adapter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Raw data model produced by external or simulated marketplace providers.
 * Strictly decoupled from NormalizedOfferDTO so that raw provider inputs cannot bypass
 * validation, canonical USD ranking, provenance rules, or invalid-price safety gates.
 */
public record RawMarketplaceOffer(
        String rawOfferId,
        String sellerName,
        String sellerUrl,
        String offerUrl,
        BigDecimal price,
        BigDecimal originalPrice,
        String currency,
        Boolean inStock,
        Integer stockQuantity,
        LocalDateTime observationTime
) {}
