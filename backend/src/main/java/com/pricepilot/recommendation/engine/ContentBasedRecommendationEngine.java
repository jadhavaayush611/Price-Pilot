package com.pricepilot.recommendation.engine;

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

@Service("ContentBasedRecommendationEngine")
public class ContentBasedRecommendationEngine implements RecommendationEngine {

    private final RecommendationExplainer explainer;

    public ContentBasedRecommendationEngine(RecommendationExplainer explainer) {
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
                    double score = calculateContentScore(product, profile);
                    List<String> reasons = explainer.explain(product, profile, saved, watchlists, getAlgorithmName());
                    return new ScoredProduct(product, score, reasons);
                })
                .sorted(Comparator.comparingDouble(ScoredProduct::getScore).reversed())
                .limit(limit)
                .collect(Collectors.toList());
    }

    @Override
    public String getAlgorithmName() {
        return "Content";
    }

    private double calculateContentScore(ProductEntity product, RecommendationProfile profile) {
        double score = 0.0;
        if (profile == null) {
            return score;
        }

        // A. Category matching (Cosine weight representation)
        Double catWeight = profile.getPreferredCategories().get(product.getCategory());
        if (catWeight != null) {
            score += catWeight * 20.0;
        }

        // B. Brand matching
        if (product.getBrand() != null) {
            Double brandWeight = profile.getPreferredBrands().get(product.getBrand());
            if (brandWeight != null) {
                score += brandWeight * 15.0;
            }
        }

        // C. Price Range Match
        BigDecimal bestPrice = getBestPrice(product);
        if (profile.getMinPrice() != null && profile.getMaxPrice() != null && bestPrice != null) {
            if (bestPrice.compareTo(profile.getMinPrice()) >= 0 && bestPrice.compareTo(profile.getMaxPrice()) <= 0) {
                score += 15.0;
            }
        }

        // D. Discount Percentage & Rating
        BigDecimal maxDiscount = BigDecimal.ZERO;
        if (product.getProductPrices() != null) {
            for (ProductPriceEntity pp : product.getProductPrices()) {
                if (pp.getDiscountPercentage() != null && pp.getDiscountPercentage().compareTo(maxDiscount) > 0) {
                    maxDiscount = pp.getDiscountPercentage();
                }
            }
        }
        score += maxDiscount.doubleValue() * 0.4;
        
        // E. Seller Rating fallback
        double avgRating = 4.0;
        score += avgRating * 2.0;

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
