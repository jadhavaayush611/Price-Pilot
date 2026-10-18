package com.pricepilot.intelligence.marketplace.service;

import com.pricepilot.intelligence.marketplace.dto.NormalizedOfferDTO;

import java.util.List;
import java.util.UUID;

/**
 * Service interface for retrieving normalized product offers across configured marketplace data sources.
 */
public interface ProductOfferService {

    /**
     * Retrieves aggregated, normalized, and canonically ranked offers for a product.
     * Enforces provenance integrity, invalid-price filtering, and deterministic deduplication.
     *
     * @param productId Unique identifier of the product.
     * @return Ranked list of valid normalized offers, or empty list if none exist or product not found.
     */
    List<NormalizedOfferDTO> getProductOffers(UUID productId);
}
