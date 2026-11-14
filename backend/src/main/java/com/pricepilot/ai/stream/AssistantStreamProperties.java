package com.pricepilot.ai.stream;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Spring-side timeout hierarchy and executor configuration properties for AI-03 streaming.
 */
@Data
@ConfigurationProperties(prefix = "pricepilot.assistant.stream")
public class AssistantStreamProperties {

    /**
     * FastAPI upstream first-token deadline (3.5 seconds).
     */
    private long fastapiFirstTokenDeadlineMs = 3500L;

    /**
     * Spring Boot total time-to-first-token deadline (4.5 seconds = 1.0s preparation + 3.5s provider).
     */
    private long firstTokenDeadlineMs = 4500L;

    /**
     * Maximum allowed idle time between consecutive SSE chunk arrivals (2.0 seconds).
     */
    private long interChunkTimeoutMs = 2000L;

    /**
     * End-to-end deadline for the entire stream lifecycle and SseEmitter (30.0 seconds).
     */
    private long streamDeadlineMs = 30000L;

    /**
     * Maximum generation duration deadline before cutoff and entering persistence reserve (28.0 seconds).
     */
    private long generationDeadlineMs = 28000L;

    /**
     * Reserved latency budget for database commit and terminal finalization (2.0 seconds).
     */
    private long persistenceReserveMs = 2000L;

    /**
     * Maximum eligible retries for transient stream errors (1).
     */
    private int retryMaxAttempts = 1;

    /**
     * Backoff duration between retries (500 ms).
     */
    private long retryBackoffMs = 500L;

    private ExecutorProperties executor = new ExecutorProperties();

    @Data
    public static class ExecutorProperties {
        private int corePoolSize = 10;
        private int maxPoolSize = 50;
        private int queueCapacity = 100;
        private int keepAliveSeconds = 60;
    }
}
