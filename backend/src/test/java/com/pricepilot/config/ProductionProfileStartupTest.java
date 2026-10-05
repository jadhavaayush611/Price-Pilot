package com.pricepilot.config;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.cache.CacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Production Profile Startup & Configuration Tests")
class ProductionProfileStartupTest {

    @Configuration
    static class TestMetricsConfig {
        @Bean
        public MeterRegistry meterRegistry() {
            return new SimpleMeterRegistry();
        }
    }

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(TestMetricsConfig.class, CacheConfig.class, StartupDiagnostics.class)
            .withPropertyValues(
                    "spring.cache.type=simple",
                    "spring.profiles.active=prod",
                    "spring.datasource.hikari.maximum-pool-size=5",
                    "spring.datasource.hikari.minimum-idle=1",
                    "server.tomcat.threads.max=20",
                    "server.tomcat.threads.min-spare=2"
            );

    @Test
    @DisplayName("Context loads in-memory CacheManager without Redis dependencies")
    void testProductionCacheConfigurationLoads() {
        contextRunner.run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).hasSingleBean(CacheManager.class);
            assertThat(context).doesNotHaveBean("redisTemplate");
            assertThat(context).doesNotHaveBean("stringRedisTemplate");
            assertThat(context).doesNotHaveBean("redisConnectionFactory");
        });
    }
}
