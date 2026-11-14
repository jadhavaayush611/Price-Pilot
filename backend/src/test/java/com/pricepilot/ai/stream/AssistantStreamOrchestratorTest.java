package com.pricepilot.ai.stream;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pricepilot.intelligence.assistant.dto.AssistantIntent;
import com.pricepilot.intelligence.assistant.dto.SendMessageRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.UUID;
import java.util.concurrent.RejectedExecutionException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

public class AssistantStreamOrchestratorTest {

    private AssistantStreamProperties properties;
    private ThreadPoolTaskExecutor executor;
    private AssistantStreamPersistenceService persistenceService;
    private ObjectMapper objectMapper;
    private AssistantStreamOrchestrator orchestrator;

    @BeforeEach
    void setUp() {
        properties = new AssistantStreamProperties();
        executor = mock(ThreadPoolTaskExecutor.class);
        persistenceService = mock(AssistantStreamPersistenceService.class);
        objectMapper = new ObjectMapper().findAndRegisterModules();

        orchestrator = new AssistantStreamOrchestrator(
                properties,
                executor,
                persistenceService,
                objectMapper
        );
    }

    @Test
    @DisplayName("Orchestrator: Throws StreamCapacityExceededException when bounded executor is saturated without initializing session")
    void testExecutorSaturationThrowsCapacityExceeded() {
        UUID conversationId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        SendMessageRequest request = new SendMessageRequest("Find laptops", null);

        doNothing().when(persistenceService).validateOwnership(conversationId, userId);
        doThrow(new RejectedExecutionException("Executor queue saturated")).when(executor).execute(any(Runnable.class));

        assertThatThrownBy(() -> orchestrator.streamMessage(conversationId, userId, request))
                .isInstanceOf(StreamCapacityExceededException.class)
                .hasMessageContaining("Assistant stream capacity reached");

        // Verify synchronous validation occurred, but DB session initialization was NEVER called
        verify(persistenceService, times(1)).validateOwnership(conversationId, userId);
        verify(persistenceService, never()).initializeSession(any(), any(), any());
    }

    @Test
    @DisplayName("Orchestrator: Throws StreamCapacityExceededException on executor shutdown rejection")
    void testExecutorRejectionDuringShutdown() {
        UUID conversationId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        SendMessageRequest request = new SendMessageRequest("Find laptops", null);

        doNothing().when(persistenceService).validateOwnership(conversationId, userId);
        doThrow(new RejectedExecutionException("Executor is shutting down")).when(executor).execute(any(Runnable.class));

        assertThatThrownBy(() -> orchestrator.streamMessage(conversationId, userId, request))
                .isInstanceOf(StreamCapacityExceededException.class)
                .hasMessageContaining("Assistant stream capacity reached");

        verify(persistenceService, times(1)).validateOwnership(conversationId, userId);
        verify(persistenceService, never()).initializeSession(any(), any(), any());
    }

    @Test
    @DisplayName("Orchestrator: Initializes SseEmitter with configured stream deadline timeout")
    void testInitializesSseEmitterWithConfiguredTimeout() {
        UUID conversationId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        SendMessageRequest request = new SendMessageRequest("Find laptops", null);

        doNothing().when(persistenceService).validateOwnership(conversationId, userId);

        SseEmitter emitter = orchestrator.streamMessage(conversationId, userId, request);

        assertThat(emitter).isNotNull();
        assertThat(emitter.getTimeout()).isEqualTo(properties.getStreamDeadlineMs());
        verify(persistenceService, times(1)).validateOwnership(conversationId, userId);
        verify(executor, times(1)).execute(any(Runnable.class));
    }
}
