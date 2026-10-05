package com.pricepilot.ai;

import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.stereotype.Component;

@Component
public class AiServiceHealthIndicator implements HealthIndicator {

    private final AiClient aiClient;

    public AiServiceHealthIndicator(AiClient aiClient) {
        this.aiClient = aiClient;
    }

    @Override
    public Health health() {
        try {
            if (aiClient != null && aiClient.isAvailable()) {
                return Health.up()
                        .withDetail("service", "FastAPI AI service is reachable and responsive")
                        .build();
            } else {
                return Health.up()
                        .withDetail("service", "FastAPI AI service is not configured or offline (local deterministic fallback active)")
                        .build();
            }
        } catch (Exception e) {
            return Health.up()
                    .withDetail("service", "FastAPI AI service check failed (local deterministic fallback active)")
                    .build();
        }
    }
}
