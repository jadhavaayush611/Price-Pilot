package com.pricepilot.config;

import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.jsontype.impl.LaissezFaireSubTypeValidator;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.StringRedisSerializer;

import org.springframework.cache.annotation.CachingConfigurer;
import org.springframework.cache.interceptor.CacheErrorHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.Collection;
import java.util.Map;

@Configuration
@EnableCaching
public class CacheConfig implements CachingConfigurer {

    @Bean
    public RedisCacheConfiguration cacheConfiguration() {
        return createCacheConfig(Duration.ofMinutes(10)); // Default 10 min TTL
    }

    @Bean
    public CacheManager cacheManager(
            @Value("${spring.cache.type:simple}") String cacheType,
            ObjectProvider<RedisConnectionFactory> connectionFactoryProvider,
            MeterRegistry meterRegistry) {

        RedisConnectionFactory connectionFactory = connectionFactoryProvider.getIfAvailable();

        if ("redis".equalsIgnoreCase(cacheType) && connectionFactory != null) {
            RedisCacheManager redisCacheManager = RedisCacheManager.builder(connectionFactory)
                    .cacheDefaults(cacheConfiguration())
                    .withInitialCacheConfigurations(Map.of(
                            "product-details", createCacheConfig(Duration.ofMinutes(30)),
                            "product-searches", createCacheConfig(Duration.ofMinutes(5)),
                            "popular-products", createCacheConfig(Duration.ofMinutes(60)),
                            "recommendations", createCacheConfig(Duration.ofMinutes(10)),
                            "dashboard", createCacheConfig(Duration.ofMinutes(5))
                    ))
                    .build();

            return new CacheManager() {
                @Override
                public Cache getCache(String name) {
                    Cache cache = redisCacheManager.getCache(name);
                    return cache == null ? null : new InstrumentedCache(cache, meterRegistry);
                }

                @Override
                public Collection<String> getCacheNames() {
                    return redisCacheManager.getCacheNames();
                }
            };
        } else {
            // Fallback to simple in-memory ConcurrentMap cache
            ConcurrentMapCacheManager concurrentMapCacheManager = new ConcurrentMapCacheManager(
                    "product-details", "product-searches", "popular-products", "recommendations", "dashboard"
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
    }

    private RedisCacheConfiguration createCacheConfig(Duration ttl) {
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
        objectMapper.activateDefaultTyping(
                LaissezFaireSubTypeValidator.instance,
                ObjectMapper.DefaultTyping.NON_FINAL,
                JsonTypeInfo.As.PROPERTY
        );

        return RedisCacheConfiguration.defaultCacheConfig()
                .entryTtl(ttl)
                .disableCachingNullValues()
                .serializeKeysWith(RedisSerializationContext.SerializationPair.fromSerializer(new StringRedisSerializer()))
                .serializeValuesWith(RedisSerializationContext.SerializationPair.fromSerializer(new GenericJackson2JsonRedisSerializer(objectMapper)));
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
