package com.pricepilot.recommendation.engine;

import com.pricepilot.product.ProductEntity;
import com.pricepilot.recommendation.dto.RecommendationProfile;
import com.pricepilot.recommendation.dto.ScoredProduct;
import com.pricepilot.recommendation.explainability.RecommendationExplainer;
import com.pricepilot.savedproduct.SavedProductEntity;
import com.pricepilot.savedproduct.SavedProductRepository;
import com.pricepilot.watchlist.PriceWatchlistEntity;
import com.pricepilot.watchlist.PriceWatchlistRepository;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

@Service("CollaborativeFilteringEngine")
public class CollaborativeFilteringEngine implements RecommendationEngine {

    private final RecommendationExplainer explainer;
    private final SavedProductRepository savedProductRepository;
    private final PriceWatchlistRepository watchlistRepository;

    public CollaborativeFilteringEngine(
            RecommendationExplainer explainer,
            SavedProductRepository savedProductRepository,
            PriceWatchlistRepository watchlistRepository) {
        this.explainer = explainer;
        this.savedProductRepository = savedProductRepository;
        this.watchlistRepository = watchlistRepository;
    }

    @Override
    public List<ScoredProduct> recommend(
            UUID userId,
            List<ProductEntity> candidates,
            RecommendationProfile profile,
            List<SavedProductEntity> saved,
            List<PriceWatchlistEntity> watchlists,
            int limit) {

        // 1. Get user's own interactions
        Set<UUID> userInteractedProductIds = new HashSet<>();
        if (saved != null) {
            saved.forEach(s -> {
                if (s.getProduct() != null) userInteractedProductIds.add(s.getProduct().getId());
            });
        }
        if (watchlists != null) {
            watchlists.forEach(w -> {
                if (w.getProduct() != null) userInteractedProductIds.add(w.getProduct().getId());
            });
        }

        // 2. Compute user-product interaction overlaps
        // Find other users who have saved/watchlisted the same products as the target user
        Map<UUID, Integer> otherUserOverlaps = new HashMap<>();
        for (UUID prodId : userInteractedProductIds) {
            List<SavedProductEntity> savesForProd = savedProductRepository.findAllByProductId(prodId);
            for (SavedProductEntity s : savesForProd) {
                if (!s.getUser().getId().equals(userId)) {
                    otherUserOverlaps.put(s.getUser().getId(), otherUserOverlaps.getOrDefault(s.getUser().getId(), 0) + 1);
                }
            }
        }

        // 3. Count recommendations from overlapping users
        Map<UUID, Double> recommendedProductScores = new HashMap<>();
        otherUserOverlaps.entrySet().stream()
                .sorted(Map.Entry.<UUID, Integer>comparingByValue().reversed())
                .limit(10) // Top 10 similar users
                .forEach(entry -> {
                    UUID similarUserId = entry.getKey();
                    double similarityWeight = entry.getValue();

                    // Products saved by similar user
                    List<SavedProductEntity> simSaved = savedProductRepository.findAllByUserIdWithProduct(similarUserId);
                    for (SavedProductEntity s : simSaved) {
                        if (s.getProduct() != null && !userInteractedProductIds.contains(s.getProduct().getId())) {
                            recommendedProductScores.put(
                                    s.getProduct().getId(),
                                    recommendedProductScores.getOrDefault(s.getProduct().getId(), 0.0) + similarityWeight
                            );
                        }
                    }
                });

        // 4. Rank candidates
        return candidates.stream()
                .map(product -> {
                    double score = recommendedProductScores.getOrDefault(product.getId(), 0.0);
                    // Fallback minor score based on general popular views to prevent all-zeros
                    if (score == 0.0 && product.getAnalytics() != null) {
                        score = product.getAnalytics().getViewCount() * 0.001;
                    }
                    List<String> reasons = explainer.explain(product, profile, saved, watchlists, getAlgorithmName());
                    return new ScoredProduct(product, score, reasons);
                })
                .sorted(Comparator.comparingDouble(ScoredProduct::getScore).reversed())
                .limit(limit)
                .collect(Collectors.toList());
    }

    @Override
    public String getAlgorithmName() {
        return "Collaborative";
    }
}
