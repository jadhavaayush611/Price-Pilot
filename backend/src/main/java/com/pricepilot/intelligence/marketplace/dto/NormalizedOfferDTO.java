package com.pricepilot.intelligence.marketplace.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Normalized Offer DTO.
 * Decouples marketplace offer observations from the canonical catalog product,
 * preserving provenance, canonical pricing, display conversion readiness,
 * and seller identity.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NormalizedOfferDTO {

    private UUID id;
    private UUID productId;
    private String productTitle;
    private OfferSourceDTO source;
    private OfferPricingDTO pricing;
    private OfferProvenanceDTO provenance;
    private OfferAvailabilityDTO availability;
    private Boolean isBestDeal;
    private BigDecimal savings;
    private BigDecimal priceDifferenceFromLowest;
}
