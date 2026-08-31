package com.pricepilot.intelligence.recommendation;

import com.pricepilot.ai.v2.RecommendationPipeline;
import com.pricepilot.exception.ResourceNotFoundException;
import com.pricepilot.intelligence.recommendation.dto.RecommendationCompareRequest;
import com.pricepilot.intelligence.recommendation.dto.RecommendationResponse;
import com.pricepilot.intelligence.recommendation.dto.RecommendationType;
import com.pricepilot.intelligence.recommendation.repository.RecommendationMetadataRepository;
import com.pricepilot.product.ProductService;
import com.pricepilot.product.dto.ProductResponseDTO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.pricepilot.intelligence.personalization.preference.UserShoppingPreferenceEntity;
import com.pricepilot.intelligence.personalization.preference.UserShoppingPreferenceService;
import com.pricepilot.intelligence.personalization.signals.BehavioralSignalService;
import com.pricepilot.intelligence.personalization.signals.UserShoppingSignals;
import com.pricepilot.product.ProductEntity;
import com.pricepilot.product.ProductRepository;
import org.springframework.data.domain.PageRequest;

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
    private final UserShoppingPreferenceService preferenceService;
    private final BehavioralSignalService behavioralSignalService;
    private final ProductRepository productRepository;

    public RecommendationServiceImpl(
            ProductService productService,
            @Qualifier("defaultRecommendationPipeline") RecommendationPipeline pipeline,
            RecommendationMetadataRepository recommendationMetadataRepository,
            UserShoppingPreferenceService preferenceService,
            BehavioralSignalService behavioralSignalService,
            ProductRepository productRepository) {
        this.productService = productService;
        this.pipeline = pipeline;
        this.recommendationMetadataRepository = recommendationMetadataRepository;
        this.preferenceService = preferenceService;
        this.behavioralSignalService = behavioralSignalService;
        this.productRepository = productRepository;
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

    @Autowired(required = false)
    private com.pricepilot.interaction.UserInteractionEventService eventService;

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

        if (userId != null && eventService != null) {
            for (UUID pid : productIds) {
                try {
                    eventService.trackEvent(
                            userId,
                            pid,
                            null,
                            com.pricepilot.interaction.InteractionType.COMPARISON_VIEW,
                            Map.of("type", request.getRecommendationType() != null ? request.getRecommendationType() : "BEST_OVERALL")
                    );
                } catch (Exception ignored) {}
            }
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

        int targetLimit = Math.max(limit, 5);

        // 1. Fetch user explicit preferences
        UserShoppingPreferenceEntity preferences = preferenceService.getPreferenceEntity(userId).orElse(null);

        // 2. Fetch bounded behavioral signals
        UserShoppingSignals signals = behavioralSignalService.extractSignals(userId);

        // 3. Gather bounded personalized candidates
        List<ProductResponseDTO> candidates = gatherPersonalizedCandidates(preferences, signals, targetLimit);
        if (candidates.isEmpty()) {
            throw new ResourceNotFoundException("No candidate products available for personalized recommendations");
        }

        // 4. Construct execution context
        Map<String, Object> context = new HashMap<>();
        context.put("personalized", true);
        if (preferences != null) {
            context.put("preferences", preferences);
        }
        if (signals != null) {
            context.put("signals", signals);
        }

        return pipeline.executeComparisonPipeline(candidates, RecommendationType.BEST_OVERALL, userId, context);
    }

    private List<ProductResponseDTO> gatherPersonalizedCandidates(
            UserShoppingPreferenceEntity preferences,
            UserShoppingSignals signals,
            int limit) {

        Map<UUID, ProductResponseDTO> candidateMap = new LinkedHashMap<>();

        Set<String> categories = new HashSet<>();
        Set<String> brands = new HashSet<>();

        if (preferences != null) {
            if (preferences.getPreferredCategories() != null) {
                categories.addAll(preferences.getPreferredCategories());
            }
            if (preferences.getPreferredBrands() != null) {
                brands.addAll(preferences.getPreferredBrands());
            }
        }

        if (signals != null) {
            signals.getCategoryAffinity().entrySet().stream()
                    .filter(e -> e.getValue() >= 0.3)
                    .map(Map.Entry::getKey)
                    .forEach(categories::add);

            signals.getBrandAffinity().entrySet().stream()
                    .filter(e -> e.getValue() >= 0.3)
                    .map(Map.Entry::getKey)
                    .forEach(brands::add);
        }

        // Fetch preference/signal matched products if any
        if (!categories.isEmpty() || !brands.isEmpty()) {
            try {
                List<ProductEntity> matchedEntities = productRepository.findCandidateProducts(
                        Collections.emptyList(),
                        categories.isEmpty() ? List.of("__NONE__") : categories,
                        brands.isEmpty() ? List.of("__NONE__") : brands,
                        PageRequest.of(0, 15)
                );
                if (matchedEntities != null) {
                    for (ProductEntity pe : matchedEntities) {
                        candidateMap.put(pe.getId(), ProductResponseDTO.fromEntity(pe));
                    }
                }
            } catch (Exception e) {
                // Defensive fallback
            }
        }

        // Add trending products to ensure candidate coverage
        int remaining = Math.max(10, limit * 2) - candidateMap.size();
        if (remaining > 0) {
            try {
                List<ProductResponseDTO> trending = productService.getTrendingProducts(remaining + 5);
                if (trending != null) {
                    for (ProductResponseDTO p : trending) {
                        candidateMap.putIfAbsent(p.getId(), p);
                    }
                }
            } catch (Exception e) {
                // Defensive fallback
            }
        }

        return new ArrayList<>(candidateMap.values());
    }
}
