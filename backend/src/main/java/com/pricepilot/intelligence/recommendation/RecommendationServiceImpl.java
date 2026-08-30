package com.pricepilot.intelligence.recommendation;

import com.pricepilot.ai.v2.RecommendationPipeline;
import com.pricepilot.exception.ResourceNotFoundException;
import com.pricepilot.intelligence.recommendation.dto.RecommendationCompareRequest;
import com.pricepilot.intelligence.recommendation.dto.RecommendationResponse;
import com.pricepilot.intelligence.recommendation.dto.RecommendationType;
import com.pricepilot.intelligence.recommendation.repository.RecommendationMetadataRepository;
import com.pricepilot.product.ProductService;
import com.pricepilot.product.dto.ProductResponseDTO;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

/**
 * Service implementation for PricePilot Shopping Intelligence Recommendation Engine v2.
 * Delegates orchestration, evidence extraction, confidence scoring, and explanation generation
 * to the RecommendationPipeline.
 */
@Service("intelligenceRecommendationService")
public class RecommendationServiceImpl implements RecommendationService {

    private final ProductService productService;
    private final RecommendationPipeline pipeline;
    private final RecommendationMetadataRepository recommendationMetadataRepository;

    public RecommendationServiceImpl(
            ProductService productService,
            @Qualifier("defaultRecommendationPipeline") RecommendationPipeline pipeline,
            RecommendationMetadataRepository recommendationMetadataRepository) {
        this.productService = productService;
        this.pipeline = pipeline;
        this.recommendationMetadataRepository = recommendationMetadataRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public RecommendationResponse getRecommendationsForProduct(UUID productId, int limit, RecommendationType type) {
        if (productId == null) {
            throw new IllegalArgumentException("Product ID cannot be null");
        }

        try {
            productService.getProductById(productId);
        } catch (Exception e) {
            throw new ResourceNotFoundException("Target product not found for recommendation with ID: " + productId);
        }

        RecommendationType selectedType = type != null ? type : RecommendationType.BEST_OVERALL;
        Map<String, Object> context = Map.of("recommendationType", selectedType.name());

        return pipeline.executePipeline(productId, null, limit, context);
    }

    @Override
    @Transactional(readOnly = true)
    public RecommendationResponse compareAndRecommend(RecommendationCompareRequest request, UUID userId) {
        if (request == null || request.getProductIds() == null) {
            throw new IllegalArgumentException("Comparison request and product IDs cannot be null");
        }

        List<UUID> productIds = request.getProductIds();
        if (productIds.size() < 2 || productIds.size() > 5) {
            throw new IllegalArgumentException("Comparison recommendations require between 2 and 5 product IDs");
        }

        List<ProductResponseDTO> candidates = productService.getProductsBatch(productIds);
        if (candidates.size() < 2) {
            throw new ResourceNotFoundException("At least 2 valid product candidates must be found for comparison recommendations");
        }

        RecommendationType type = RecommendationType.fromString(request.getRecommendationType());
        return pipeline.executeComparisonPipeline(candidates, type, userId, Map.of());
    }

    @Override
    @Transactional(readOnly = true)
    public RecommendationResponse getPersonalizedRecommendations(UUID userId, int limit) {
        if (userId == null) {
            throw new AccessDeniedException("Authentication required for personalized recommendations");
        }

        List<ProductResponseDTO> trending = productService.getTrendingProducts(Math.max(limit, 5));
        if (trending.isEmpty()) {
            throw new ResourceNotFoundException("No candidate products available for personalized recommendations");
        }

        return pipeline.executeComparisonPipeline(trending, RecommendationType.BEST_OVERALL, userId, Map.of("personalized", true));
    }
}
