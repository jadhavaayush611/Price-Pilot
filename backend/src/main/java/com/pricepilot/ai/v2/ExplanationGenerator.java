package com.pricepilot.ai.v2;

import com.pricepilot.intelligence.recommendation.dto.EvidenceItem;
import com.pricepilot.intelligence.recommendation.dto.RecommendationType;
import com.pricepilot.product.ProductEntity;
import com.pricepilot.product.dto.ProductResponseDTO;

import java.util.List;

/**
 * Interface for generating natural language explanations for product recommendations.
 * Keeps the explanation layer decoupled from persistence entities.
 */
public interface ExplanationGenerator {

    /**
     * Generates an explanation for a recommendation based strictly on structured evidence.
     *
     * @param recommendedProduct The selected product.
     * @param allCandidates All evaluated products.
     * @param positiveEvidence Strongest positive factors.
     * @param tradeOffEvidence Trade-offs and negative factors.
     * @param recommendationType Strategy goal (e.g. Best Overall, Best Value).
     * @param score Calculated score.
     * @param confidence Deterministic confidence.
     * @return RecommendationExplanation result.
     */
    RecommendationExplanation generateExplanation(
            ProductResponseDTO recommendedProduct,
            List<ProductResponseDTO> allCandidates,
            List<EvidenceItem> positiveEvidence,
            List<EvidenceItem> tradeOffEvidence,
            RecommendationType recommendationType,
            double score,
            double confidence
    );

    /**
     * Legacy adapter method for entity-based callers.
     */
    default RecommendationExplanation generateExplanation(ProductEntity targetProduct, ProductEntity recommendedProduct, RecommendationScore score) {
        ProductResponseDTO recDto = recommendedProduct != null ? ProductResponseDTO.fromEntity(recommendedProduct) : null;
        List<ProductResponseDTO> candidates = recDto != null ? List.of(recDto) : List.of();
        return generateExplanation(recDto, candidates, List.of(), List.of(), RecommendationType.BEST_OVERALL, score != null ? score.totalScore() : 85.0, 0.85);
    }
}
