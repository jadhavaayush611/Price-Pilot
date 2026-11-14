package com.pricepilot.ai.stream;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.ThreadPoolExecutor;

/**
 * Dedicated bounded executor configuration for AI assistant streaming workloads.
 * Uses AbortPolicy to cleanly reject excess concurrent streams without blocking HTTP threads.
 */
@Configuration
@EnableConfigurationProperties(AssistantStreamProperties.class)
public class AssistantStreamConfiguration {

    @Bean(name = "assistantStreamExecutor")
    public ThreadPoolTaskExecutor assistantStreamExecutor(AssistantStreamProperties properties) {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(properties.getExecutor().getCorePoolSize());
        executor.setMaxPoolSize(properties.getExecutor().getMaxPoolSize());
        executor.setQueueCapacity(properties.getExecutor().getQueueCapacity());
        executor.setKeepAliveSeconds(properties.getExecutor().getKeepAliveSeconds());
        executor.setThreadNamePrefix("assistant-stream-");
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.AbortPolicy());
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds((int) (properties.getStreamDeadlineMs() / 1000));
        executor.initialize();
        return executor;
    }

    @Bean
    @ConditionalOnMissingBean
    public ObjectMapper objectMapper() {
        return new ObjectMapper().findAndRegisterModules();
    }
}
