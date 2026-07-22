package com.pricepilot.config;

import com.pricepilot.ai.AiClient;
import org.flywaydb.core.Flyway;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.info.BuildProperties;
import org.springframework.core.env.Environment;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.Arrays;

@Component
public class StartupDiagnostics implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(StartupDiagnostics.class);

    @Autowired
    private Environment environment;

    @Autowired(required = false)
    private JdbcTemplate jdbcTemplate;

    @Autowired(required = false)
    private RedisConnectionFactory redisConnectionFactory;

    @Autowired(required = false)
    private AiClient aiClient;

    @Autowired(required = false)
    private Flyway flyway;

    @Autowired(required = false)
    private BuildProperties buildProperties;

    @Override
    public void run(String... args) {
        log.info("====================================================================");
        log.info("PricePilot Backend Startup Diagnostics");
        log.info("--------------------------------------------------------------------");

        // 1. Active Profiles
        String[] activeProfiles = environment.getActiveProfiles();
        log.info("Active Profile       : {}", activeProfiles.length > 0 ? Arrays.toString(activeProfiles) : "default");

        // 2. Build Version
        String buildVersion = buildProperties != null ? buildProperties.getVersion() : "1.0.1 (Dev)";
        log.info("Build Version        : {}", buildVersion);

        // 3. Database Connectivity
        if (jdbcTemplate != null) {
            String dbStatus = "DISCONNECTED";
            try {
                jdbcTemplate.execute("SELECT 1");
                dbStatus = "CONNECTED";
            } catch (Exception e) {
                log.error("Database connection check failed", e);
            }
            log.info("Database Connected   : {}", dbStatus);
        } else {
            log.info("Database Connected   : NOT CONFIGURED");
        }

        // 4. Flyway Schema Version
        if (flyway != null) {
            String flywayVersion = "N/A";
            try {
                if (flyway.info() != null) {
                    var current = flyway.info().current();
                    if (current != null) {
                        flywayVersion = current.getVersion().toString();
                    } else {
                        flywayVersion = "No migrations executed";
                    }
                }
            } catch (Exception e) {
                log.error("Failed to retrieve Flyway status", e);
            }
            log.info("Flyway Version       : {}", flywayVersion);
        } else {
            log.info("Flyway Version       : NOT ENABLED");
        }

        // 5. Redis Connectivity
        if (redisConnectionFactory != null) {
            String redisStatus = "DISCONNECTED";
            try (var connection = redisConnectionFactory.getConnection()) {
                connection.ping();
                redisStatus = "CONNECTED";
            } catch (Exception e) {
                log.error("Redis connection check failed", e);
            }
            log.info("Redis Connected      : {}", redisStatus);
        } else {
            log.info("Redis Connected      : NOT CONFIGURED");
        }

        // 6. AI Gateway Connectivity
        if (aiClient != null) {
            String aiStatus = "DISCONNECTED";
            try {
                if (aiClient.isAvailable()) {
                    aiStatus = "CONNECTED";
                }
            } catch (Exception e) {
                log.error("AI service connection check failed", e);
            }
            log.info("AI Service Connected : {}", aiStatus);
        } else {
            log.info("AI Service Connected : NOT CONFIGURED");
        }

        log.info("====================================================================");
    }
}
