package com.pricepilot.recommendation;

import com.pricepilot.recommendation.dto.RecommendationProfile;
import java.util.UUID;

public interface RecommendationProfileService {
    
    /**
     * Dynamically computes the user recommendation preference profile.
     *
     * @param userId User UUID.
     * @return User preference profile.
     */
    RecommendationProfile getProfile(UUID userId);
}
