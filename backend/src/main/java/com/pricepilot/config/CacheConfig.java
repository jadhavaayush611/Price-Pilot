package com.pricepilot.config;

import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.CachingConfigurer;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;
import org.springframework.cache.interceptor.CacheErrorHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Collection;

@Configuration
@EnableCaching
public class CacheConfig implements CachingConfigurer {

    @Bean
    public CacheManager cacheManager(MeterRegistry meterRegistry) {
        // High-efficiency, low-footprint in-memory ConcurrentMap cache manager
        ConcurrentMapCacheManager concurrentMapCacheManager = new ConcurrentMapCacheManager(
                "product-details", "product-searches", "popular-products",
                "trending-products", "most-watched-products", "most-saved-products", "biggest-drops",
                "recommendations", "price-analytics", "dashboard", "dashboard-v2",
                "user-preferences", "user-recommendations", "user-behavioral-signals"
        );

        return new CacheManager() {
            @Override
            public Cache getCache(String name) {
                Cache cache = concurrentMapCacheManager.getCache(name);
                return cache == null ? null : new InstrumentedCache(cache, meterRegistry);
            }

            @Override
            public Collection<String> getCacheNames() {
                return concurrentMapCacheManager.getCacheNames();
            }
        };
    }

    @Override
    public CacheErrorHandler errorHandler() {
        return new CacheErrorHandler() {
            private static final Logger log = LoggerFactory.getLogger("CacheErrorHandler");

            @Override
            public void handleCacheGetError(RuntimeException exception, Cache cache, Object key) {
                log.warn("Cache GET failed for key '{}' in cache '{}': {}. Falling back to database.", key, cache.getName(), exception.getMessage());
            }

            @Override
            public void handleCachePutError(RuntimeException exception, Cache cache, Object key, Object value) {
                log.warn("Cache PUT failed for key '{}' in cache '{}': {}", key, cache.getName(), exception.getMessage());
            }

            @Override
            public void handleCacheEvictError(RuntimeException exception, Cache cache, Object key) {
                log.warn("Cache EVICT failed for key '{}' in cache '{}': {}", key, cache.getName(), exception.getMessage());
            }

            @Override
            public void handleCacheClearError(RuntimeException exception, Cache cache) {
                log.warn("Cache CLEAR failed for cache '{}': {}", cache.getName(), exception.getMessage());
            }
        };
    }
}
