package com.pricepilot.intelligence.personalization.scoring;

import com.pricepilot.intelligence.personalization.context.PersonalizationContext;
import com.pricepilot.intelligence.recommendation.dto.ProductScore;
import com.pricepilot.product.dto.ProductResponseDTO;

import java.util.Comparator;
import java.util.Map;
import java.util.UUID;

/**
 * Strategy interface for calculating deterministic, bounded personalized scoring on product candidates.
 * Operates purely in-memory using {@link PersonalizationContext} as the sole personalization input.
 */
public interface PersonalizedScoringStrategy {

    /**
     * Scores a product candidate with an explicit scalar base score and personalization context.
     *
     * @param product the candidate product to score
     * @param baseScore the deterministic base score (e.g. 0.0 - 100.0)
     * @param context the normalized user personalization context
     * @return an immutable PersonalizedScore containing the computed adjustment and final score
     */
    PersonalizedScore score(ProductResponseDTO product, double baseScore, PersonalizationContext context);

    /**
     * Scores a product candidate using a {@link ProductScore} and personalization context.
     *
     * @param product the candidate product to score
     * @param baseScore the candidate's base product score
     * @param context the normalized user personalization context
     * @return an immutable PersonalizedScore containing the computed adjustment and final score
     */
    PersonalizedScore score(ProductResponseDTO product, ProductScore baseScore, PersonalizationContext context);

    /**
     * Returns a deterministic multi-tier comparator for ranking products by personalized score.
     * Tiers:
     * 1. Final score descending
     * 2. Base score descending
     * 3. Personalization adjustment descending
     * 4. Lowest price ascending
     * 5. Lexicographical Product UUID ascending
     *
     * @param scoreMap mapping of product UUID to its computed PersonalizedScore
     * @return deterministic comparator for candidate sorting
     */
    Comparator<ProductResponseDTO> getDeterministicComparator(Map<UUID, PersonalizedScore> scoreMap);
}
