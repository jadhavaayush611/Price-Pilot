package com.pricepilot.intelligence.marketplace.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * Breakdown of offer pricing.
 * Strictly distinguishes listed base price from verified final checkout price.
 * Missing fees are NEVER inferred as zero.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OfferPricingDTO {

    /** Canonical listed base price in USD */
    private BigDecimal basePrice;

    /** Original MSRP / list price in USD */
    private BigDecimal originalPrice;

    /** Discount percentage computed or reported */
    private BigDecimal discountPercentage;

    /** Canonical currency of stored base price (USD) */
    @Builder.Default
    private String currency = "USD";

    /** Shipping fee if explicitly known; null when unknown (NEVER zero) */
    private BigDecimal shippingPrice;

    /** Taxes and checkout fees if explicitly known; null when unknown */
    private BigDecimal taxesAndFees;

    /**
     * Total comparable checkout price.
     * Defined ONLY when basePrice, shippingPrice, and all mandatory checkout fees are explicitly known.
     * If shipping or fees are unknown, this MUST remain null.
     */
    private BigDecimal totalPrice;

    /** Flag indicating that this offer represents only the base price */
    @Builder.Default
    private boolean basePriceOnly = true;

    /** Disclaimer clarifying checkout fee variability */
    @Builder.Default
    private String pricingDisclaimer = "Listed base price. Additional shipping, taxes, and merchant fees may apply at checkout.";
}
