package com.pricepilot.intelligence.marketplace.dto;

import com.pricepilot.intelligence.marketplace.model.AvailabilityStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Inventory availability metadata for an offer.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OfferAvailabilityDTO {

    @Builder.Default
    private AvailabilityStatus status = AvailabilityStatus.UNKNOWN;
    private Boolean available;
    private Integer stockQuantity;
}
