package com.pricepilot.intelligence.marketplace.dto;

import com.pricepilot.intelligence.marketplace.model.ProvenanceType;
import com.pricepilot.intelligence.marketplace.model.VerificationStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Provenance and trust metadata for an offer.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OfferProvenanceDTO {

    private ProvenanceType sourceType;
    private VerificationStatus verificationStatus;
    private boolean liveVerified;
    private LocalDateTime observedAt;
    private String rawLastUpdated;
    private boolean stale;
    private String trustLabel;
    private String trustDescription;
}
