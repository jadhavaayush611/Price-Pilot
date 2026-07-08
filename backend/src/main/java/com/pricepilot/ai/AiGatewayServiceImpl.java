package com.pricepilot.ai;

import com.pricepilot.ai.dto.*;
import com.pricepilot.interaction.UserInteractionEventEntity;
import com.pricepilot.interaction.UserInteractionEventRepository;
import com.pricepilot.product.ProductEntity;
import com.pricepilot.recommendation.dto.RecommendationProfile;
import com.pricepilot.recommendation.dto.ScoredProduct;
import com.pricepilot.recommendation.engine.RuleBasedRecommendationEngine;
import com.pricepilot.savedproduct.SavedProductEntity;
import com.pricepilot.watchlist.PriceWatchlistEntity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

@Service
public class AiGatewayServiceImpl implements AiGatewayService {

    private static final Logger log = LoggerFactory.getLogger(AiGatewayServiceImpl.class);

    private final AiClient aiClient;
    private final RuleBasedRecommendationEngine ruleBasedEngine;
    private final UserInteractionEventRepository eventRepository;

    @Value("${pricepilot.ai.enabled:true}")
    private boolean aiEnabled;

    public AiGatewayServiceImpl(
            AiClient aiClient,
            RuleBasedRecommendationEngine ruleBasedEngine,
            UserInteractionEventRepository eventRepository) {
        this.aiClient = aiClient;
        this.ruleBasedEngine = ruleBasedEngine;
        this.eventRepository = eventRepository;
    }

    @Override
    public List<ScoredProduct> recommend(
            UUID userId,
            List<ProductEntity> candidates,
            RecommendationProfile profile,
            List<SavedProductEntity> saved,
            List<PriceWatchlistEntity> watchlists,
            String algorithm,
            int limit) {

        if (!aiEnabled) {
            log.info("AI Service integration is disabled. Falling back to local Rule-Based engine.");
            List<ScoredProduct> fallbacks = ruleBasedEngine.recommend(userId, candidates, profile, saved, watchlists, limit);
            for (ScoredProduct sp : fallbacks) {
                sp.setAlgorithm("Rule-Based");
            }
            return fallbacks;
        }

        try {
            // 1. Map candidate products
            List<ProductFeature> mappedCandidates = candidates.stream().map(c -> {
                double minPrice = c.getProductPrices() != null ? c.getProductPrices().stream()
                        .map(p -> p.getCurrentPrice() != null ? p.getCurrentPrice().doubleValue() : 0.0)
                        .min(Double::compareTo)
                        .orElse(0.0) : 0.0;
                
                double originalPrice = c.getProductPrices() != null ? c.getProductPrices().stream()
                        .map(p -> p.getOriginalPrice() != null ? p.getOriginalPrice().doubleValue() : (p.getCurrentPrice() != null ? p.getCurrentPrice().doubleValue() : 0.0))
                        .min(Double::compareTo)
                        .orElse(0.0) : 0.0;

                double maxDiscount = c.getProductPrices() != null ? c.getProductPrices().stream()
                        .map(p -> p.getDiscountPercentage() != null ? p.getDiscountPercentage().doubleValue() : 0.0)
                        .max(Double::compareTo)
                        .orElse(0.0) : 0.0;

                long viewCount = 0L;
                long saveCount = 0L;
                long watchlistCount = 0L;
                long priceChangeCount = 0L;

                if (c.getAnalytics() != null) {
                    viewCount = c.getAnalytics().getViewCount();
                    saveCount = c.getAnalytics().getSaveCount();
                    watchlistCount = c.getAnalytics().getWatchlistCount();
                    priceChangeCount = c.getAnalytics().getPriceChangeCount();
                }

                double trendingScore = viewCount * 1.0 + saveCount * 5.0 + watchlistCount * 10.0 + priceChangeCount * 2.0;

                return ProductFeature.builder()
                        .productId(c.getId().toString())
                        .category(c.getCategory())
                        .brand(c.getBrand() != null ? c.getBrand() : "Unknown")
                        .currentMinPrice(minPrice)
                        .originalMinPrice(originalPrice)
                        .averageSellerRating(4.0) // default seller rating
                        .viewCount((double) viewCount)
                        .saveCount((double) saveCount)
                        .watchlistCount((double) watchlistCount)
                        .trendingScore(trendingScore)
                        .discountPercentage(maxDiscount)
                        .build();
            }).collect(Collectors.toList());

            // 2. Map User profile
            UserProfile userProfile = UserProfile.builder()
                    .preferredCategories(profile.getPreferredCategories() != null ? profile.getPreferredCategories() : Map.of())
                    .preferredBrands(profile.getPreferredBrands() != null ? profile.getPreferredBrands() : Map.of())
                    .preferredSellers(profile.getPreferredSellers() != null ? profile.getPreferredSellers() : Map.of())
                    .minPrice(profile.getMinPrice() != null ? profile.getMinPrice().doubleValue() : null)
                    .maxPrice(profile.getMaxPrice() != null ? profile.getMaxPrice().doubleValue() : null)
                    .build();

            // 3. Map user interactions
            List<UserInteraction> mappedInteractions = new ArrayList<>();
            if (saved != null) {
                saved.forEach(s -> {
                    if (s.getProduct() != null) {
                        mappedInteractions.add(UserInteraction.builder()
                                .productId(s.getProduct().getId().toString())
                                .interactionType("PRODUCT_SAVE")
                                .createdAt(s.getCreatedAt() != null ? s.getCreatedAt().toString() : null)
                                .build());
                    }
                });
            }
            if (watchlists != null) {
                watchlists.forEach(w -> {
                    if (w.getProduct() != null) {
                        mappedInteractions.add(UserInteraction.builder()
                                .productId(w.getProduct().getId().toString())
                                .interactionType("WATCHLIST_ADD")
                                .createdAt(w.getCreatedAt() != null ? w.getCreatedAt().toString() : null)
                                .build());
                    }
                });
            }

            // Enrich with recent database interaction logs if available
            try {
                List<UserInteractionEventEntity> events = eventRepository.findByUserIdWithRelations(userId, PageRequest.of(0, 50));
                if (events != null) {
                    events.forEach(e -> {
                        if (e.getProduct() != null) {
                            mappedInteractions.add(UserInteraction.builder()
                                    .productId(e.getProduct().getId().toString())
                                    .interactionType(e.getInteractionType().name())
                                    .createdAt(e.getCreatedAt() != null ? e.getCreatedAt().toString() : null)
                                    .build());
                        }
                    });
                }
            } catch (Exception e) {
                log.warn("Failed to load user interaction history from DB, proceeding with saved/watchlisted history. Error: {}", e.getMessage());
            }

            // 4. Send request
            AiPredictRequest request = AiPredictRequest.builder()
                    .userId(userId.toString())
                    .algorithm(algorithm)
                    .limit(limit)
                    .candidates(mappedCandidates)
                    .userProfile(userProfile)
                    .interactions(mappedInteractions)
                    .build();

            AiPredictResponse response = aiClient.predict(request);

            if (response == null || response.getRecommendations() == null) {
                log.warn("FastAPI returned empty response. Falling back to local Rule-Based engine.");
                return ruleBasedEngine.recommend(userId, candidates, profile, saved, watchlists, limit);
            }

            // 5. Re-map candidate product entities based on returned scores
            Map<String, ProductEntity> candidateMap = candidates.stream()
                    .collect(Collectors.toMap(c -> c.getId().toString(), c -> c));

            List<ScoredProduct> scoredProducts = new ArrayList<>();
            for (ScoredRecommendation rec : response.getRecommendations()) {
                ProductEntity product = candidateMap.get(rec.getProductId());
                if (product != null) {
                    scoredProducts.add(new ScoredProduct(product, rec.getScore(), rec.getReasons(), response.getAlgorithm()));
                }
            }

            // Sort descending by score
            scoredProducts.sort(Comparator.comparingDouble(ScoredProduct::getScore).reversed());
            return scoredProducts;

        } catch (Exception e) {
            log.warn("FastAPI prediction failed or unavailable. Falling back to local Rule-Based engine. Exception details: {}", e.getMessage());
            List<ScoredProduct> fallbacks = ruleBasedEngine.recommend(userId, candidates, profile, saved, watchlists, limit);
            for (ScoredProduct sp : fallbacks) {
                sp.setAlgorithm("Rule-Based");
            }
            return fallbacks;
        }
    }
}
