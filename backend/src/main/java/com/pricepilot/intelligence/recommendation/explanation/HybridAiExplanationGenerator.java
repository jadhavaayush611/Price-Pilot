package com.pricepilot.intelligence.recommendation.explanation;

import com.pricepilot.ai.AiClient;
import com.pricepilot.ai.dto.AiEvidenceItem;
import com.pricepilot.ai.dto.AiExplainRequest;
import com.pricepilot.ai.dto.AiExplainResponse;
import com.pricepilot.ai.v2.ExplanationGenerator;
import com.pricepilot.ai.v2.RecommendationExplanation;
import com.pricepilot.intelligence.recommendation.dto.EvidenceItem;
import com.pricepilot.intelligence.recommendation.dto.RecommendationType;
import com.pricepilot.product.dto.ProductResponseDTO;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Hybrid AI explanation generator integrating with the Python FastAPI AI service.
 * Treats AI responses as untrusted input, validates against strict schemas,
 * sanitizes against markup injection, and falls back to DeterministicExplanationGenerator on any failure.
 */
@Component("hybridAiExplanationGenerator")
@Primary
public class HybridAiExplanationGenerator implements ExplanationGenerator {

    private static final Logger log = LoggerFactory.getLogger(HybridAiExplanationGenerator.class);
    private static final Pattern HTML_TAG_PATTERN = Pattern.compile("<[^>]*>");

    private final AiClient aiClient;
    private final DeterministicExplanationGenerator deterministicGenerator;
    private final MeterRegistry meterRegistry;

    @Value("${pricepilot.ai.enabled:true}")
    private boolean aiEnabled;

    public HybridAiExplanationGenerator(
            AiClient aiClient,
            @Qualifier("deterministicExplanationGenerator") DeterministicExplanationGenerator deterministicGenerator,
            MeterRegistry meterRegistry) {
        this.aiClient = aiClient;
        this.deterministicGenerator = deterministicGenerator;
        this.meterRegistry = meterRegistry;
    }

    @Override
    public RecommendationExplanation generateExplanation(
            ProductResponseDTO recommendedProduct,
            List<ProductResponseDTO> allCandidates,
            List<EvidenceItem> positiveEvidence,
            List<EvidenceItem> tradeOffEvidence,
            RecommendationType recommendationType,
            double score,
            double confidence) {

        if (!aiEnabled) {
            log.debug("AI explanation integration disabled by config. Using deterministic generator.");
            return deterministicGenerator.generateExplanation(
                    recommendedProduct, allCandidates, positiveEvidence, tradeOffEvidence, recommendationType, score, confidence
            );
        }

        if (meterRegistry != null) {
            meterRegistry.counter("pricepilot.ai.explanation.requests.total").increment();
        }

        try {
            List<AiEvidenceItem> aiEvidence = positiveEvidence != null ? positiveEvidence.stream()
                    .map(this::toAiEvidenceItem)
                    .collect(Collectors.toList()) : Collections.emptyList();

            List<AiEvidenceItem> aiTradeOffs = tradeOffEvidence != null ? tradeOffEvidence.stream()
                    .map(this::toAiEvidenceItem)
                    .collect(Collectors.toList()) : Collections.emptyList();

            AiExplainRequest request = new AiExplainRequest(
                    recommendedProduct != null ? recommendedProduct.getId().toString() : "",
                    recommendedProduct != null ? recommendedProduct.getName() : "",
                    recommendationType != null ? recommendationType.name() : "BEST_OVERALL",
                    score,
                    confidence,
                    aiEvidence,
                    aiTradeOffs
            );

            AiExplainResponse response = aiClient.explain(request);

            if (response == null || response.getExplanation() == null || response.getExplanation().isBlank()) {
                throw new IllegalStateException("AI service returned empty explanation payload");
            }

            // Sanitize untrusted AI output against script/HTML markup
            String cleanExplanation = sanitize(response.getExplanation());
            List<String> cleanFactors = response.getSupportingFactors() != null ? response.getSupportingFactors().stream()
                    .map(this::sanitize)
                    .filter(s -> !s.isBlank())
                    .collect(Collectors.toList()) : Collections.emptyList();

            List<String> cleanTradeOffs = response.getTradeOffs() != null ? response.getTradeOffs().stream()
                    .map(this::sanitize)
                    .filter(s -> !s.isBlank())
                    .collect(Collectors.toList()) : Collections.emptyList();

            String modelName = response.getModel() != null ? sanitize(response.getModel()) : "PricePilot-AI-Hybrid";

            return new RecommendationExplanation(
                    recommendedProduct != null ? recommendedProduct.getId() : null,
                    cleanExplanation,
                    cleanFactors,
                    cleanTradeOffs,
                    confidence,
                    modelName,
                    "AI_GATEWAY_HYBRID"
            );

        } catch (Exception e) {
            log.warn("AI explanation generation failed or timed out ({}). Falling back to deterministic generator.", e.getMessage());
            if (meterRegistry != null) {
                meterRegistry.counter("pricepilot.ai.explanation.failures.total").increment();
                meterRegistry.counter("pricepilot.ai.explanation.fallback.total").increment();
            }
            return deterministicGenerator.generateExplanation(
                    recommendedProduct, allCandidates, positiveEvidence, tradeOffEvidence, recommendationType, score, confidence
            );
        }
    }

    private AiEvidenceItem toAiEvidenceItem(EvidenceItem item) {
        return new AiEvidenceItem(
                item.getProductId() != null ? item.getProductId().toString() : "",
                item.getProductName(),
                item.getType() != null ? item.getType().name() : "",
                item.getDescription(),
                item.getMetricName(),
                item.getMetricValue(),
                item.getComparisonValue(),
                item.isPositive(),
                item.getImportance()
        );
    }

    private String sanitize(String input) {
        if (input == null) return "";
        return HTML_TAG_PATTERN.matcher(input).replaceAll("").trim();
    }
}
