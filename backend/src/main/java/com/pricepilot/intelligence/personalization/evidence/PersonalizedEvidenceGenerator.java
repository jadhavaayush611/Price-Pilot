package com.pricepilot.intelligence.personalization.evidence;

import com.pricepilot.intelligence.personalization.context.PersonalizationContext;
import com.pricepilot.intelligence.personalization.scoring.PersonalizedScore;
import com.pricepilot.product.dto.ProductResponseDTO;

/**
 * Interface for generating deterministic, grounded personalized evidence explaining
 * a product candidate's {@link PersonalizedScore}.
 * <p>
 * Core Invariant:
 * Evidence explains the score; evidence never creates, modifies, or recalculates the score.
 */
public interface PersonalizedEvidenceGenerator {

    /**
     * Generates grounded evidence items and trade-offs for a candidate based strictly on the scored contributions.
     *
     * @param product the candidate product
     * @param score the computed personalized score output from Phase 6.4
     * @param context the user personalization context
     * @return an immutable PersonalizedEvidence containing grounded positive evidence and trade-offs
     */
    PersonalizedEvidence generate(
            ProductResponseDTO product,
            PersonalizedScore score,
            PersonalizationContext context
    );
}
