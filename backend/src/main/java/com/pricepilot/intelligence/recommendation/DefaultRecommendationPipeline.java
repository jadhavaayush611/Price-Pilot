package com.pricepilot.intelligence.recommendation;

import com.pricepilot.ai.v2.ExplanationGenerator;
import com.pricepilot.ai.v2.RecommendationExplanation;
import com.pricepilot.ai.v2.RecommendationPipeline;
import com.pricepilot.exception.ResourceNotFoundException;
import com.pricepilot.intelligence.comparison.scoring.ComparisonScoringStrategy;
import com.pricepilot.intelligence.recommendation.confidence.ConfidenceCalculator;
import com.pricepilot.intelligence.recommendation.dto.EvidenceItem;
import com.pricepilot.intelligence.recommendation.dto.ProductScore;
import com.pricepilot.intelligence.recommendation.dto.RecommendationResponse;
import com.pricepilot.intelligence.recommendation.dto.RecommendationType;
import com.pricepilot.intelligence.recommendation.entity.RecommendationHistoryEventEntity;
import com.pricepilot.intelligence.recommendation.evidence.EvidenceExtractor;
import com.pricepilot.intelligence.recommendation.repository.RecommendationHistoryEventRepository;
import com.pricepilot.product.ProductService;
import com.pricepilot.product.dto.ProductResponseDTO;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import com.pricepilot.intelligence.personalization.preference.UserShoppingPreferenceEntity;
import com.pricepilot.intelligence.personalization.scoring.DefaultPersonalizationScorer;
import com.pricepilot.intelligence.personalization.scoring.PersonalizationResult;
import com.pricepilot.intelligence.personalization.scoring.PersonalizationScorer;
import com.pricepilot.intelligence.personalization.signals.UserShoppingSignals;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Default implementation of RecommendationPipeline for Shopping Intelligence v2.
 * Adheres strictly to the architectural execution flow:
 * Product Candidates -> Comparison / Evidence Collection -> Deterministic Scoring ->
 * Recommendation Ranking -> Evidence Extraction -> Explanation Generation -> RecommendationResponse.
 */
@Service("defaultRecommendationPipeline")
public class DefaultRecommendationPipeline implements RecommendationPipeline {

    private static final Logger log = LoggerFactory.getLogger(DefaultRecommendationPipeline.class);

    private final ProductService productService;
    private final ComparisonScoringStrategy scoringStrategy;
    private final EvidenceExtractor evidenceExtractor;
    private final ConfidenceCalculator confidenceCalculator;
    private final ExplanationGenerator explanationGenerator;
    private final RecommendationHistoryEventRepository historyRepository;
    private final MeterRegistry meterRegistry;
    private final PersonalizationScorer personalizationScorer;

    @Autowired(required = false)
    private com.pricepilot.interaction.UserInteractionEventService eventService;

    @Autowired
    public DefaultRecommendationPipeline(
            ProductService productService,
            ComparisonScoringStrategy scoringStrategy,
            EvidenceExtractor evidenceExtractor,
            ConfidenceCalculator confidenceCalculator,
            @Qualifier("hybridAiExplanationGenerator") ExplanationGenerator explanationGenerator,
            RecommendationHistoryEventRepository historyRepository,
            MeterRegistry meterRegistry,
            PersonalizationScorer personalizationScorer) {
        this.productService = productService;
        this.scoringStrategy = scoringStrategy;
        this.evidenceExtractor = evidenceExtractor;
        this.confidenceCalculator = confidenceCalculator;
        this.explanationGenerator = explanationGenerator;
        this.historyRepository = historyRepository;
        this.meterRegistry = meterRegistry;
        this.personalizationScorer = personalizationScorer;
    }

    public DefaultRecommendationPipeline(
            ProductService productService,
            ComparisonScoringStrategy scoringStrategy,
            EvidenceExtractor evidenceExtractor,
            ConfidenceCalculator confidenceCalculator,
            ExplanationGenerator explanationGenerator,
            RecommendationHistoryEventRepository historyRepository,
            MeterRegistry meterRegistry) {
        this(productService, scoringStrategy, evidenceExtractor, confidenceCalculator,
             explanationGenerator, historyRepository, meterRegistry,
             new DefaultPersonalizationScorer());
    }

    @Override
    public RecommendationResponse executePipeline(UUID targetProductId, UUID userId, int limit, Map<String, Object> context) {
        Timer.Sample sample = Timer.start(meterRegistry);
        String recTypeStr = context != null && context.containsKey("recommendationType")
                ? String.valueOf(context.get("recommendationType")) : "BEST_OVERALL";
        RecommendationType recType = RecommendationType.fromString(recTypeStr);

        try {
            // 1. Retrieve target product and candidate products
            ProductResponseDTO targetProduct = productService.getProductById(targetProductId);
            if (targetProduct == null) {
                throw new ResourceNotFoundException("Target product not found: " + targetProductId);
            }

            int fetchLimit = Math.max(limit, 5);
            List<ProductResponseDTO> candidates = new ArrayList<>();
            candidates.add(targetProduct);

            List<ProductResponseDTO> trending = productService.getTrendingProducts(fetchLimit + 1);
            for (ProductResponseDTO p : trending) {
                if (!p.getId().equals(targetProductId) && candidates.size() < fetchLimit) {
                    candidates.add(p);
                }
            }

            return executeComparisonPipeline(candidates, recType, userId, context, targetProductId);

        } finally {
            sample.stop(meterRegistry.timer("pricepilot.recommendation.duration", "type", recType.name()));
            meterRegistry.counter("pricepilot.recommendation.requests.total", "type", recType.name()).increment();
        }
    }

    @Override
    public RecommendationResponse executeComparisonPipeline(
            List<ProductResponseDTO> candidates,
            RecommendationType recommendationType,
            UUID userId,
            Map<String, Object> context) {
        return executeComparisonPipeline(candidates, recommendationType, userId, context, null);
    }

    private RecommendationResponse executeComparisonPipeline(
            List<ProductResponseDTO> candidates,
            RecommendationType recommendationType,
            UUID userId,
            Map<String, Object> context,
            UUID targetProductId) {

        if (candidates == null || candidates.isEmpty()) {
            throw new IllegalArgumentException("Candidate products list cannot be empty for recommendation pipeline");
        }

        RecommendationType type = recommendationType != null ? recommendationType : RecommendationType.BEST_OVERALL;

        boolean isPersonalized = context != null && Boolean.TRUE.equals(context.get("personalized"));
        UserShoppingPreferenceEntity preferences = null;
        UserShoppingSignals signals = null;
        if (isPersonalized) {
            if (context.get("preferences") instanceof UserShoppingPreferenceEntity p) {
                preferences = p;
            }
            if (context.get("signals") instanceof UserShoppingSignals s) {
                signals = s;
            }
        }

        // 2. Deterministic Base Scoring via existing ComparisonScoringStrategy
        Map<UUID, ProductScore> rawScores = scoringStrategy.calculateScores(candidates);

        // 2b. Optional Personalization Layer
        Map<UUID, PersonalizationResult> personalizationResults = new HashMap<>();
        if (isPersonalized && personalizationScorer != null) {
            for (ProductResponseDTO candidate : candidates) {
                ProductScore baseScore = rawScores.get(candidate.getId());
                PersonalizationResult pRes = personalizationScorer.scorePersonalization(
                        candidate, baseScore, preferences, signals
                );
                personalizationResults.put(candidate.getId(), pRes);

                if (baseScore != null) {
                    baseScore.setBaseScore(pRes.getBaseScore());
                    baseScore.setPersonalizationContribution(pRes.getPersonalizationContribution());
                    baseScore.setOverallScore(pRes.getFinalScore());
                    baseScore.setPersonalizationBreakdown(pRes.getPersonalizationBreakdown());
                }
            }
        }

        // 3. Rank candidates according to RecommendationType with deterministic tie-breaking
        List<ProductResponseDTO> rankedCandidates;
        if (isPersonalized && personalizationScorer != null) {
            rankedCandidates = new ArrayList<>(candidates);
            rankedCandidates.sort(personalizationScorer.getDeterministicPersonalizedComparator(personalizationResults));
        } else {
            rankedCandidates = rankCandidates(candidates, rawScores, type);
        }

        List<ProductScore> rankedScores = rankedCandidates.stream()
                .map(p -> rawScores.get(p.getId()))
                .filter(Objects::nonNull)
                .collect(Collectors.toList());

        // 4. Determine recommended product
        ProductResponseDTO recommendedProduct = rankedCandidates.get(0);
        ProductScore topScore = rawScores.get(recommendedProduct.getId());

        // Set recommendation badge according to strategy
        if (topScore != null) {
            topScore.setRecommendationBadge(type.getDisplayName().toUpperCase());
        }

        // 5. Extract strongest positive and negative factors
        EvidenceExtractor.ExtractedEvidence extracted = evidenceExtractor.extractEvidence(
                recommendedProduct, rankedCandidates, rawScores
        );
        List<EvidenceItem> positiveEvidence = new ArrayList<>(extracted.positiveEvidence());
        List<EvidenceItem> tradeOffEvidence = new ArrayList<>(extracted.negativeTradeOffs());

        List<EvidenceItem> personalizationEvidenceList = new ArrayList<>();
        PersonalizationResult topPersonalization = personalizationResults.get(recommendedProduct.getId());
        if (isPersonalized && topPersonalization != null) {
            personalizationEvidenceList.addAll(topPersonalization.getPersonalizationEvidence());
            positiveEvidence.addAll(topPersonalization.getPersonalizationEvidence());
            tradeOffEvidence.addAll(topPersonalization.getPersonalizationTradeOffs());
        }

        // 6. Calculate deterministic confidence score
        double confidence = confidenceCalculator.calculateConfidence(
                rankedCandidates, rankedScores, positiveEvidence, tradeOffEvidence
        );

        if (meterRegistry != null) {
            meterRegistry.summary("pricepilot.recommendation.confidence.distribution").record(confidence);
        }

        // 7. Generate explanation using only supplied evidence
        double scoreValue = topScore != null ? topScore.getOverallScore() : 85.0;
        RecommendationExplanation explanationResult = explanationGenerator.generateExplanation(
                recommendedProduct, rankedCandidates, positiveEvidence, tradeOffEvidence, type, scoreValue, confidence
        );

        List<String> decisionDrivers = new ArrayList<>(explanationResult.keyDecisionDrivers());
        if (isPersonalized && !personalizationEvidenceList.isEmpty()) {
            for (EvidenceItem pe : personalizationEvidenceList) {
                if (pe.getDescription() != null && !decisionDrivers.contains(pe.getDescription())) {
                    decisionDrivers.add(0, pe.getDescription());
                }
            }
        }

        // 8. Return structured RecommendationResponse
        RecommendationResponse response = new RecommendationResponse(
                targetProductId,
                userId,
                rankedCandidates,
                recommendedProduct,
                type.name(),
                scoreValue,
                confidence,
                explanationResult.summaryExplanation(),
                decisionDrivers,
                explanationResult.tradeOffs(),
                positiveEvidence,
                rankedScores,
                "DEFAULT_COMPARISON_SCORER",
                explanationResult.explanationStrategy(),
                LocalDateTime.now()
        );

        if (isPersonalized && topPersonalization != null) {
            response.setBaseScore(topPersonalization.getBaseScore());
            response.setPersonalizationContribution(topPersonalization.getPersonalizationContribution());
            response.setPersonalizationEvidence(personalizationEvidenceList);
        }

        // 9. Asynchronously/safely persist to recommendation history
        saveHistoryEvent(response, rankedCandidates, userId, targetProductId, type);

        return response;
    }

    private List<ProductResponseDTO> rankCandidates(
            List<ProductResponseDTO> candidates,
            Map<UUID, ProductScore> scores,
            RecommendationType type) {

        List<ProductResponseDTO> sorted = new ArrayList<>(candidates);
        sorted.sort((p1, p2) -> {
            ProductScore s1 = scores.get(p1.getId());
            ProductScore s2 = scores.get(p2.getId());

            double v1 = 0.0;
            double v2 = 0.0;

            switch (type) {
                case BEST_VALUE -> {
                    v1 = s1 != null ? s1.getPriceValueScore() : 0.0;
                    v2 = s2 != null ? s2.getPriceValueScore() : 0.0;
                }
                case HIGHEST_RATED -> {
                    v1 = s1 != null ? s1.getFeatureScore() : 0.0;
                    v2 = s2 != null ? s2.getFeatureScore() : 0.0;
                }
                case BEST_DISCOUNT -> {
                    v1 = s1 != null && s1.getBreakdown() != null
                            ? s1.getBreakdown().getOrDefault("DiscountPercentage", 0.0) : 0.0;
                    v2 = s2 != null && s2.getBreakdown() != null
                            ? s2.getBreakdown().getOrDefault("DiscountPercentage", 0.0) : 0.0;
                }
                case BEST_OVERALL -> {
                    v1 = s1 != null ? s1.getOverallScore() : 0.0;
                    v2 = s2 != null ? s2.getOverallScore() : 0.0;
                }
            }

            int cmp = Double.compare(v2, v1);
            if (cmp != 0) {
                return cmp;
            }

            // Secondary tie-breaker: overall score
            double o1 = s1 != null ? s1.getOverallScore() : 0.0;
            double o2 = s2 != null ? s2.getOverallScore() : 0.0;
            int cmpOverall = Double.compare(o2, o1);
            if (cmpOverall != 0) {
                return cmpOverall;
            }

            // Tertiary tie-breaker: lowest price (lower is better)
            BigDecimal pr1 = getMinPrice(p1);
            BigDecimal pr2 = getMinPrice(p2);
            if (pr1 != null && pr2 != null) {
                int cmpPrice = pr1.compareTo(pr2);
                if (cmpPrice != 0) return cmpPrice;
            }

            // Deterministic stable tie-breaker: UUID
            return p1.getId().compareTo(p2.getId());
        });

        return sorted;
    }

    private BigDecimal getMinPrice(ProductResponseDTO p) {
        if (p == null || p.getPrices() == null || p.getPrices().isEmpty()) {
            return null;
        }
        return p.getPrices().stream()
                .map(pr -> pr.getCurrentPrice())
                .filter(Objects::nonNull)
                .min(BigDecimal::compareTo)
                .orElse(null);
    }

    private void saveHistoryEvent(
            RecommendationResponse response,
            List<ProductResponseDTO> rankedCandidates,
            UUID userId,
            UUID targetProductId,
            RecommendationType type) {
        try {
            if (historyRepository == null || response.getRecommendedProduct() == null) {
                return;
            }
            String productIdsStr = rankedCandidates.stream()
                    .map(p -> p.getId().toString())
                    .collect(Collectors.joining(","));

            RecommendationHistoryEventEntity entity = new RecommendationHistoryEventEntity(
                    null,
                    userId,
                    null,
                    targetProductId,
                    response.getRecommendedProduct().getId(),
                    productIdsStr,
                    type.name(),
                    response.getScoringStrategy(),
                    response.getExplanationStrategy(),
                    response.getScore(),
                    response.getConfidence(),
                    LocalDateTime.now()
            );
            historyRepository.save(entity);

            if (userId != null && eventService != null) {
                try {
                    eventService.trackEvent(
                            userId,
                            response.getRecommendedProduct().getId(),
                            null,
                            com.pricepilot.interaction.InteractionType.RECOMMENDATION_INTERACTION,
                            Map.of("type", type.name())
                    );
                } catch (Exception ignored) {}
            }
        } catch (Exception e) {
            log.warn("Failed to persist recommendation history event (non-fatal): {}", e.getMessage());
        }
    }
}
