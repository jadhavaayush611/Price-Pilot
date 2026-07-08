package com.pricepilot.recommendation.engine;

import com.pricepilot.product.ProductEntity;
import com.pricepilot.recommendation.dto.RecommendationProfile;
import com.pricepilot.recommendation.dto.ScoredProduct;
import com.pricepilot.savedproduct.SavedProductEntity;
import com.pricepilot.watchlist.PriceWatchlistEntity;

import java.util.List;
import java.util.UUID;

public interface RecommendationEngine {
    
    List<ScoredProduct> recommend(
            UUID userId,
            List<ProductEntity> candidates,
            RecommendationProfile profile,
            List<SavedProductEntity> saved,
            List<PriceWatchlistEntity> watchlists,
            int limit
    );
    
    String getAlgorithmName();
}
