package com.pricepilot.intelligence.recommendation.explanation;

import com.pricepilot.ai.v2.ExplanationGenerator;
import com.pricepilot.ai.v2.RecommendationExplanation;
import com.pricepilot.intelligence.recommendation.dto.EvidenceItem;
import com.pricepilot.intelligence.recommendation.dto.RecommendationType;
import com.pricepilot.product.dto.ProductResponseDTO;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Deterministic, rule-based implementation of ExplanationGenerator.
 * Generates transparent, human-readable explanations and trade-offs strictly from supplied evidence.
 */
@Component("deterministicExplanationGenerator")
public class DeterministicExplanationGenerator implements ExplanationGenerator {

    @Override
    public RecommendationExplanation generateExplanation(
            ProductResponseDTO recommendedProduct,
            List<ProductResponseDTO> allCandidates,
            List<EvidenceItem> positiveEvidence,
            List<EvidenceItem> tradeOffEvidence,
            RecommendationType recommendationType,
            double score,
            double confidence) {

        if (recommendedProduct == null) {
            return new RecommendationExplanation(
                    null,
                    "No product candidate available to explain.",
                    List.of(),
                    List.of(),
                    0.0,
                    "PricePilot-Deterministic-Rules",
                    "DETERMINISTIC_RULE_BASED"
            );
        }

        List<String> supportingFactors = positiveEvidence != null ? positiveEvidence.stream()
                .map(EvidenceItem::getDescription)
                .collect(Collectors.toList()) : new ArrayList<>();

        List<String> tradeOffs = tradeOffEvidence != null ? tradeOffEvidence.stream()
                .map(EvidenceItem::getDescription)
                .collect(Collectors.toList()) : new ArrayList<>();

        String summary = buildSummary(recommendedProduct.getName(), recommendationType, positiveEvidence, score);

        return new RecommendationExplanation(
                recommendedProduct.getId(),
                summary,
                supportingFactors,
                tradeOffs,
                confidence,
                "PricePilot-Deterministic-Rules",
                "DETERMINISTIC_RULE_BASED"
        );
    }

    private String buildSummary(String productName, RecommendationType type, List<EvidenceItem> evidence, double score) {
        if (evidence == null || evidence.isEmpty()) {
            return String.format("%s is recommended based on overall multi-factor performance (Score: %.1f/100).", productName, score);
        }

        List<String> highlights = new ArrayList<>();
        for (EvidenceItem item : evidence) {
            switch (item.getType()) {
                case LOWEST_PRICE -> highlights.add("the lowest current price");
                case PRICE_BELOW_AVERAGE -> highlights.add("below-average pricing");
                case HIGHEST_RATING -> highlights.add("the highest customer rating");
                case HIGHEST_DISCOUNT -> highlights.add("the steepest discount");
                case HIGH_SELLER_AVAILABILITY -> highlights.add("strong seller availability");
                case BETTER_SPECIFICATION -> highlights.add("superior specifications");
                default -> highlights.add("strong competitive metrics");
            }
        }

        String joinedHighlights;
        if (highlights.size() == 1) {
            joinedHighlights = highlights.get(0);
        } else if (highlights.size() == 2) {
            joinedHighlights = highlights.get(0) + " and " + highlights.get(1);
        } else {
            joinedHighlights = String.join(", ", highlights.subList(0, highlights.size() - 1))
                    + ", and " + highlights.get(highlights.size() - 1);
        }

        String goalPhrase = switch (type) {
            case BEST_VALUE -> "the best price-to-value choice";
            case HIGHEST_RATED -> "the top-rated selection";
            case BEST_DISCOUNT -> "the strongest discount deal";
            default -> "the strongest overall choice";
        };

        return String.format("%s is %s because it has %s.", productName, goalPhrase, joinedHighlights);
    }
}
