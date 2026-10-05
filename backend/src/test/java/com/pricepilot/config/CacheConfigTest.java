package com.pricepilot.config;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("CacheConfig Unit & Integration Tests")
class CacheConfigTest {

    @Test
    @DisplayName("CacheConfig creates CacheManager with instrumentation")
    void testCacheManagerCreationAndOperation() {
        MeterRegistry meterRegistry = new SimpleMeterRegistry();
        CacheConfig cacheConfig = new CacheConfig();

        CacheManager cacheManager = cacheConfig.cacheManager(meterRegistry);
        assertThat(cacheManager).isNotNull();

        Cache testCache = cacheManager.getCache("product-details");
        assertThat(testCache).isNotNull();

        // Put and Get
        testCache.put("key1", "value1");
        Cache.ValueWrapper wrapper = testCache.get("key1");
        assertThat(wrapper).isNotNull();
        assertThat(wrapper.get()).isEqualTo("value1");

        // Miss
        Cache.ValueWrapper missWrapper = testCache.get("non-existent-key");
        assertThat(missWrapper).isNull();

        // Evict
        testCache.evict("key1");
        assertThat(testCache.get("key1")).isNull();

        // Verify metrics recorded with pricepilot prefix
        double hits = meterRegistry.get("pricepilot.cache.hits").tag("cache", "product-details").counter().count();
        double misses = meterRegistry.get("pricepilot.cache.misses").tag("cache", "product-details").counter().count();

        assertThat(hits).isEqualTo(1.0);
        assertThat(misses).isEqualTo(2.0); // miss on "non-existent-key" and miss after evict on "key1"
    }
}
