package com.pricepilot.recommendation.engine;

import com.pricepilot.analytics.ProductAnalyticsEntity;
import com.pricepilot.product.ProductEntity;
import com.pricepilot.recommendation.dto.RecommendationProfile;
import com.pricepilot.recommendation.dto.ScoredProduct;
import com.pricepilot.recommendation.explainability.RecommendationExplainer;
import com.pricepilot.savedproduct.SavedProductEntity;
import com.pricepilot.watchlist.PriceWatchlistEntity;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service("PopularityRecommendationEngine")
public class PopularityRecommendationEngine implements RecommendationEngine {

    private final RecommendationExplainer explainer;

    public PopularityRecommendationEngine(RecommendationExplainer explainer) {
        this.explainer = explainer;
    }

    @Override
    public List<ScoredProduct> recommend(
            UUID userId,
            List<ProductEntity> candidates,
            RecommendationProfile profile,
            List<SavedProductEntity> saved,
            List<PriceWatchlistEntity> watchlists,
            int limit) {

        return candidates.stream()
                .map(product -> {
                    double score = calculatePopularityScore(product);
                    List<String> reasons = explainer.explain(product, profile, saved, watchlists, getAlgorithmName());
                    return new ScoredProduct(product, score, reasons);
                })
                .sorted(Comparator.comparingDouble(ScoredProduct::getScore).reversed())
                .limit(limit)
                .collect(Collectors.toList());
    }

    @Override
    public String getAlgorithmName() {
        return "Popularity";
    }

    private double calculatePopularityScore(ProductEntity product) {
        ProductAnalyticsEntity analytics = product.getAnalytics();
        if (analytics == null) {
            return 0.0;
        }

        double trendingScore = analytics.getViewCount() * 1.0 +
                analytics.getSaveCount() * 5.0 +
                analytics.getWatchlistCount() * 10.0 +
                analytics.getPriceChangeCount() * 2.0;

        return analytics.getViewCount() * 1.0 +
                analytics.getSaveCount() * 5.0 +
                analytics.getWatchlistCount() * 10.0 +
                trendingScore * 2.0;
    }
}
