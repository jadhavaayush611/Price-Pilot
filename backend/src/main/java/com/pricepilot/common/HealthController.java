package com.pricepilot.common;

import com.pricepilot.ai.AiClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/health")
public class HealthController {

    private static final Logger log = LoggerFactory.getLogger(HealthController.class);

    private final JdbcTemplate jdbcTemplate;
    private final AiClient aiClient;

    public HealthController(
            JdbcTemplate jdbcTemplate,
            @Autowired(required = false) AiClient aiClient) {
        this.jdbcTemplate = jdbcTemplate;
        this.aiClient = aiClient;
    }

    @GetMapping
    public ResponseEntity<Map<String, Object>> getHealth() {
        Map<String, Object> health = new HashMap<>();
        boolean isDatabaseHealthy = checkDatabase();
        boolean isAiHealthy = checkAi();

        health.put("status", isDatabaseHealthy ? "UP" : "DOWN");
        health.put("database", isDatabaseHealthy ? "UP" : "DOWN");
        health.put("cache", "UP_IN_MEMORY");
        health.put("ai_service", isAiHealthy ? "UP" : "STANDBY_DETERMINISTIC");

        if (isDatabaseHealthy) {
            return ResponseEntity.ok(health);
        } else {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(health);
        }
    }

    private boolean checkDatabase() {
        try {
            if (jdbcTemplate != null) {
                jdbcTemplate.execute("SELECT 1");
                return true;
            }
            return false;
        } catch (Exception e) {
            log.error("Database health check failed", e);
            return false;
        }
    }

    private boolean checkAi() {
        try {
            return aiClient != null && aiClient.isAvailable();
        } catch (Exception e) {
            log.debug("AI service health check note: {}", e.getMessage());
            return false;
        }
    }
}
