package com.pricepilot.ai;

import com.pricepilot.product.ProductEntity;
import com.pricepilot.recommendation.dto.RecommendationProfile;
import com.pricepilot.recommendation.dto.ScoredProduct;
import com.pricepilot.savedproduct.SavedProductEntity;
import com.pricepilot.watchlist.PriceWatchlistEntity;

import java.util.List;
import java.util.UUID;

public interface AiGatewayService {

    /**
     * Obtains scored recommendations from the FastAPI AI service.
     *
     * @param userId The ID of the user.
     * @param candidates The candidate product entities to score.
     * @param profile The user preference profile.
     * @param saved The user's saved products history.
     * @param watchlists The user's watchlisted products history.
     * @param algorithm The active algorithm strategy (e.g. Popularity, Content, Collaborative, Hybrid).
     * @param limit The maximum number of recommendations to return.
     * @return A list of scored products with reasons.
     */
    List<ScoredProduct> recommend(
            UUID userId,
            List<ProductEntity> candidates,
            RecommendationProfile profile,
            List<SavedProductEntity> saved,
            List<PriceWatchlistEntity> watchlists,
            String algorithm,
            int limit
    );
}
