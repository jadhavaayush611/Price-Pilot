package com.pricepilot.intelligence.marketplace.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

/**
 * Merchant / source attribution for an offer.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OfferSourceDTO {

    private UUID sellerId;
    private String sellerName;
    private String websiteUrl;
    private String logoUrl;
    private String offerUrl;
    private String providerName;
}
