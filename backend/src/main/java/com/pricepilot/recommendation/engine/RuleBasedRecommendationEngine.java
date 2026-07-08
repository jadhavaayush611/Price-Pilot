package com.pricepilot.recommendation.engine;

import com.pricepilot.analytics.ProductAnalyticsEntity;
import com.pricepilot.product.ProductEntity;
import com.pricepilot.productprice.ProductPriceEntity;
import com.pricepilot.recommendation.dto.RecommendationProfile;
import com.pricepilot.recommendation.dto.ScoredProduct;
import com.pricepilot.recommendation.explainability.RecommendationExplainer;
import com.pricepilot.savedproduct.SavedProductEntity;
import com.pricepilot.watchlist.PriceWatchlistEntity;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service("RuleBasedRecommendationEngine")
public class RuleBasedRecommendationEngine implements RecommendationEngine {

    private final RecommendationExplainer explainer;

    public RuleBasedRecommendationEngine(RecommendationExplainer explainer) {
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
                    double score = calculateScore(product, profile, saved, watchlists);
                    List<String> reasons = explainer.explain(product, profile, saved, watchlists, getAlgorithmName());
                    return new ScoredProduct(product, score, reasons);
                })
                .sorted(Comparator.comparingDouble(ScoredProduct::getScore).reversed())
                .limit(limit)
                .collect(Collectors.toList());
    }

    @Override
    public String getAlgorithmName() {
        return "RuleBased";
    }

    private double calculateScore(
            ProductEntity product,
            RecommendationProfile profile,
            List<SavedProductEntity> saved,
            List<PriceWatchlistEntity> watchlists) {

        double score = 0.0;

        if (profile == null) {
            return score;
        }

        // A. Category Match
        Double catWeight = profile.getPreferredCategories().get(product.getCategory());
        if (catWeight != null) {
            score += catWeight * 10.0;
        }

        // B. Brand Match
        if (product.getBrand() != null) {
            Double brandWeight = profile.getPreferredBrands().get(product.getBrand());
            if (brandWeight != null) {
                score += brandWeight * 8.0;
            }
        }

        // C. Seller Match
        BigDecimal bestPrice = getBestPrice(product);
        if (product.getProductPrices() != null) {
            for (ProductPriceEntity pp : product.getProductPrices()) {
                if (pp.getSeller() != null) {
                    Double sellerWeight = profile.getPreferredSellers().get(pp.getSeller().getName());
                    if (sellerWeight != null) {
                        score += sellerWeight * 5.0;
                    }
                }
            }
        }

        // D. Price Range Match
        if (profile.getMinPrice() != null && profile.getMaxPrice() != null && bestPrice != null) {
            if (bestPrice.compareTo(profile.getMinPrice()) >= 0 && bestPrice.compareTo(profile.getMaxPrice()) <= 0) {
                score += 15.0;
            } else {
                BigDecimal margin = profile.getMaxPrice().subtract(profile.getMinPrice()).multiply(BigDecimal.valueOf(0.2));
                BigDecimal lowerBound = profile.getMinPrice().subtract(margin);
                BigDecimal upperBound = profile.getMaxPrice().add(margin);
                if (bestPrice.compareTo(lowerBound) >= 0 && bestPrice.compareTo(upperBound) <= 0) {
                    score += 5.0;
                }
            }
        }

        // E. Anchor Similarity
        if (saved != null) {
            for (SavedProductEntity sp : saved) {
                if (sp.getProduct() != null && sp.getProduct().getCategory().equals(product.getCategory())) {
                    score += 5.0;
                }
            }
        }
        if (watchlists != null) {
            for (PriceWatchlistEntity pw : watchlists) {
                if (pw.getProduct() != null && pw.getProduct().getCategory().equals(product.getCategory())) {
                    score += 10.0;
                }
            }
        }

        // F. Trending Score
        ProductAnalyticsEntity analytics = product.getAnalytics();
        if (analytics != null) {
            double trendingScore = analytics.getViewCount() * 1.0 +
                    analytics.getSaveCount() * 5.0 +
                    analytics.getWatchlistCount() * 10.0 +
                    analytics.getPriceChangeCount() * 2.0;
            score += trendingScore * 0.1;
            score += analytics.getViewCount() * 0.05;
        }

        // G. Price Drop Activity
        BigDecimal maxDiscount = BigDecimal.ZERO;
        if (product.getProductPrices() != null) {
            for (ProductPriceEntity pp : product.getProductPrices()) {
                if (pp.getDiscountPercentage() != null && pp.getDiscountPercentage().compareTo(maxDiscount) > 0) {
                    maxDiscount = pp.getDiscountPercentage();
                }
            }
        }
        score += maxDiscount.doubleValue() * 0.5;

        return score;
    }

    private BigDecimal getBestPrice(ProductEntity product) {
        if (product.getProductPrices() == null || product.getProductPrices().isEmpty()) {
            return null;
        }
        return product.getProductPrices().stream()
                .map(ProductPriceEntity::getCurrentPrice)
                .min(BigDecimal::compareTo)
                .orElse(null);
    }
}
