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
            if (aiClient.isAvailable()) {
                return Health.up()
                        .withDetail("service", "FastAPI AI service is reachable and responsive")
                        .build();
            } else {
                return Health.down()
                        .withDetail("service", "FastAPI AI service is unreachable or not healthy")
                        .build();
            }
        } catch (Exception e) {
            return Health.down(e)
                    .withDetail("service", "FastAPI AI service check failed with exception")
                    .build();
        }
    }
}
