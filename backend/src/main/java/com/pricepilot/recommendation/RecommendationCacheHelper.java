package com.pricepilot.recommendation;

import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class RecommendationCacheHelper {

    private final CacheManager cacheManager;

    public RecommendationCacheHelper(CacheManager cacheManager) {
        this.cacheManager = cacheManager;
    }

    /**
     * Evicts cached dashboard and recommendation profiles for a specific user.
     */
    public void evictUserCaches(UUID userId) {
        if (userId == null) {
            return;
        }

        // Evict specific user dashboard cache entry (key is userId)
        Cache dashboardCache = cacheManager.getCache("dashboard");
        if (dashboardCache != null) {
            dashboardCache.evict(userId);
        }

        // Recommendations cache has dynamic compound keys based on limits/filters.
        // We clear the recommendations cache to ensure fresh suggestions.
        Cache recommendationsCache = cacheManager.getCache("recommendations");
        if (recommendationsCache != null) {
            recommendationsCache.clear();
        }
    }

    /**
     * Clears all dashboard and recommendation caches globally (e.g. on price changes).
     */
    public void evictAllCaches() {
        Cache dashboardCache = cacheManager.getCache("dashboard");
        if (dashboardCache != null) {
            dashboardCache.clear();
        }

        Cache recommendationsCache = cacheManager.getCache("recommendations");
        if (recommendationsCache != null) {
            recommendationsCache.clear();
        }
    }
}
