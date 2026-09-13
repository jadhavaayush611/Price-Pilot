package com.pricepilot.intelligence.semantic.health;

import com.pricepilot.intelligence.semantic.config.SemanticIntelligenceProperties;
import com.pricepilot.intelligence.semantic.model.ProviderHealth;
import com.pricepilot.intelligence.semantic.provider.EmbeddingProvider;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.stereotype.Component;

/**
 * Spring Boot Actuator HealthIndicator for the Semantic Intelligence Foundation.
 * Exposes model provider status, model name, model version, and vector dimensions.
 */
@Component
public class SemanticIntelligenceHealthIndicator implements HealthIndicator {

    private final EmbeddingProvider embeddingProvider;
    private final SemanticIntelligenceProperties properties;

    public SemanticIntelligenceHealthIndicator(
            EmbeddingProvider embeddingProvider,
            SemanticIntelligenceProperties properties) {
        this.embeddingProvider = embeddingProvider;
        this.properties = properties;
    }

    @Override
    public Health health() {
        if (!properties.isEnabled()) {
            return Health.unknown()
                    .withDetail("status", "DISABLED")
                    .withDetail("message", "Semantic Intelligence is disabled via configuration")
                    .build();
        }

        try {
            ProviderHealth providerHealth = embeddingProvider.checkHealth();
            if (providerHealth.isHealthy()) {
                return Health.up()
                        .withDetail("provider", providerHealth.getProviderName())
                        .withDetail("model", providerHealth.getModelName())
                        .withDetail("version", providerHealth.getModelVersion())
                        .withDetail("dimension", providerHealth.getDimension())
                        .withDetail("message", providerHealth.getMessage())
                        .build();
            } else {
                return Health.down()
                        .withDetail("provider", providerHealth.getProviderName())
                        .withDetail("message", providerHealth.getMessage())
                        .build();
            }
        } catch (Exception e) {
            return Health.down(e)
                    .withDetail("provider", properties.getProvider())
                    .withDetail("message", "Health check failed with exception: " + e.getMessage())
                    .build();
        }
    }
}
