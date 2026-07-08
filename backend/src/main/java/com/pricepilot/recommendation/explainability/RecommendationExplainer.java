package com.pricepilot.recommendation.explainability;

import com.pricepilot.product.ProductEntity;
import com.pricepilot.recommendation.dto.RecommendationProfile;
import com.pricepilot.savedproduct.SavedProductEntity;
import com.pricepilot.watchlist.PriceWatchlistEntity;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Component
public class RecommendationExplainer {

    public List<String> explain(
            ProductEntity product,
            RecommendationProfile profile,
            List<SavedProductEntity> saved,
            List<PriceWatchlistEntity> watchlists,
            String algorithmName) {
            
        List<String> reasons = new ArrayList<>();

        // A. Category Match
        if (profile != null && profile.getPreferredCategories() != null && profile.getPreferredCategories().containsKey(product.getCategory())) {
            reasons.add("Matches your preferred category: " + product.getCategory());
        }

        // B. Brand Match
        if (product.getBrand() != null && profile != null && profile.getPreferredBrands() != null && profile.getPreferredBrands().containsKey(product.getBrand())) {
            reasons.add("Matches your preferred brand: " + product.getBrand());
        }

        // C. Price Range Match
        BigDecimal bestPrice = getBestPrice(product);
        if (profile != null && profile.getMinPrice() != null && profile.getMaxPrice() != null && bestPrice != null) {
            if (bestPrice.compareTo(profile.getMinPrice()) >= 0 && bestPrice.compareTo(profile.getMaxPrice()) <= 0) {
                reasons.add("Within your preferred price range");
            }
        }

        // D. Anchor Similarity (saved/watchlisted)
        boolean hasSavedMatch = false;
        if (saved != null) {
            for (SavedProductEntity sp : saved) {
                if (sp.getProduct() != null && sp.getProduct().getCategory().equals(product.getCategory())) {
                    hasSavedMatch = true;
                    break;
                }
            }
        }
        if (hasSavedMatch) {
            reasons.add("Similar to items in your Saved list");
        }

        // E. Discount check
        BigDecimal maxDiscount = BigDecimal.ZERO;
        if (product.getProductPrices() != null) {
            for (var pp : product.getProductPrices()) {
                if (pp.getDiscountPercentage() != null && pp.getDiscountPercentage().compareTo(maxDiscount) > 0) {
                    maxDiscount = pp.getDiscountPercentage();
                }
            }
        }
        if (maxDiscount.compareTo(BigDecimal.valueOf(15)) > 0) {
            reasons.add("Offers a significant discount of " + maxDiscount.intValue() + "%");
        }

        // F. Popularity check
        if (product.getAnalytics() != null && product.getAnalytics().getViewCount() > 100) {
            reasons.add("Highly popular with many views recently");
        }

        // Fallbacks
        if (reasons.isEmpty()) {
            if ("Popularity".equalsIgnoreCase(algorithmName)) {
                reasons.add("Trending product popular among other users");
            } else if ("Collaborative".equalsIgnoreCase(algorithmName)) {
                reasons.add("Recommended based on users with similar interests");
            } else if ("Content".equalsIgnoreCase(algorithmName)) {
                reasons.add("Matches characteristics of items you like");
            } else {
                reasons.add("Recommended product you might like");
            }
        }

        return reasons;
    }

    private BigDecimal getBestPrice(ProductEntity product) {
        if (product.getProductPrices() == null || product.getProductPrices().isEmpty()) {
            return null;
        }
        return product.getProductPrices().stream()
                .map(p -> p.getCurrentPrice())
                .min(BigDecimal::compareTo)
                .orElse(null);
    }
}
