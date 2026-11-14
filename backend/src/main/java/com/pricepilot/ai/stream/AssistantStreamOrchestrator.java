package com.pricepilot.ai.stream;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pricepilot.ai.stream.event.AssistantStreamEvent;
import com.pricepilot.ai.stream.event.EvidenceEvent;
import com.pricepilot.ai.stream.event.MessageCompleteEvent;
import com.pricepilot.ai.stream.event.MessageInterruptedEvent;
import com.pricepilot.ai.stream.event.MessageStartEvent;
import com.pricepilot.ai.stream.event.ErrorEvent;
import com.pricepilot.intelligence.assistant.dto.AssistantEvidenceBundle;
import com.pricepilot.intelligence.assistant.dto.SendMessageRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.ArrayList;
import java.util.UUID;
import java.util.concurrent.RejectedExecutionException;

/**
 * Dedicated orchestration component governing the asynchronous streaming lifecycle.
 * Coordinates authenticated request validation, bounded execution, lifecycle transitions,
 * persistence boundaries, and SSE event dispatching.
 */
@Component
public class AssistantStreamOrchestrator {

    private static final Logger log = LoggerFactory.getLogger(AssistantStreamOrchestrator.class);

    private final AssistantStreamProperties properties;
    private final ThreadPoolTaskExecutor assistantStreamExecutor;
    private final AssistantStreamPersistenceService persistenceService;
    private final ObjectMapper objectMapper;

    public AssistantStreamOrchestrator(
            AssistantStreamProperties properties,
            @Qualifier("assistantStreamExecutor") ThreadPoolTaskExecutor assistantStreamExecutor,
            AssistantStreamPersistenceService persistenceService,
            @org.springframework.beans.factory.annotation.Autowired(required = false) ObjectMapper objectMapper) {
        this.properties = properties;
        this.assistantStreamExecutor = assistantStreamExecutor;
        this.persistenceService = persistenceService;
        this.objectMapper = objectMapper != null ? objectMapper : new ObjectMapper().findAndRegisterModules();
    }

    /**
     * Initializes the SSE stream for a validated, authenticated conversation.
     * All security and ownership validations run synchronously on the caller thread
     * before executor admission and before any database records are written.
     */
    public SseEmitter streamMessage(UUID conversationId, UUID userId, SendMessageRequest request) {
        // 1. Synchronously validate ownership before starting any async work or admitting to executor
        persistenceService.validateOwnership(conversationId, userId);

        // 2. Instantiate SseEmitter with end-to-end deadline
        SseEmitter emitter = new SseEmitter(properties.getStreamDeadlineMs());
        StreamLifecycle lifecycle = new StreamLifecycle();

        // 3. Register lifecycle transport hooks on emitter
        emitter.onTimeout(() -> {
            log.warn("SseEmitter container timed out for conversation {}", conversationId);
            lifecycle.cancel("CONTAINER_TIMEOUT");
            try {
                emitter.complete();
            } catch (Exception ignored) {}
        });

        emitter.onError(ex -> {
            log.debug("SseEmitter transport error for conversation {}: {}", conversationId, ex.getMessage());
            lifecycle.cancel("TRANSPORT_ERROR");
        });

        emitter.onCompletion(() -> {
            // If the transport completes while lifecycle is still ACTIVE, it was an unexpected client drop
            if (lifecycle.getState() == StreamLifecycle.State.ACTIVE) {
                log.info("SseEmitter completed while lifecycle was ACTIVE; treating as client disconnect for conv {}", conversationId);
                lifecycle.cancel("UNEXPECTED_CLIENT_DISCONNECT");
            }
        });

        // 4. Submit to bounded executor; cleanly reject if queue is saturated.
        // No database records have been inserted yet, preventing orphan records upon saturation/rejection.
        try {
            assistantStreamExecutor.execute(() -> executeStreamWorker(emitter, lifecycle, conversationId, userId, request));
        } catch (RejectedExecutionException rex) {
            log.warn("Assistant stream executor saturated; rejecting stream for conversation {}", conversationId);
            lifecycle.cancel("EXECUTOR_SATURATED");
            throw new StreamCapacityExceededException("Assistant stream capacity reached. Please retry in a few seconds.", rex);
        }

        return emitter;
    }

    private void executeStreamWorker(
            SseEmitter emitter,
            StreamLifecycle lifecycle,
            UUID conversationId,
            UUID userId,
            SendMessageRequest request) {

        if (!lifecycle.isActive()) {
            return;
        }

        StreamSessionContext sessionContext;
        try {
            sessionContext = persistenceService.initializeSession(conversationId, userId, request);
        } catch (Exception ex) {
            log.error("Failed to initialize session for conversation {}: {}", conversationId, ex.getMessage(), ex);
            if (lifecycle.isActive()) {
                AssistantStreamEmitter tempEmitter = new AssistantStreamEmitter(
                        emitter, lifecycle, objectMapper, conversationId, UUID.randomUUID()
                );
                ErrorEvent errorEvent = new ErrorEvent();
                errorEvent.setErrorCode("STREAM_INITIALIZATION_ERROR");
                errorEvent.setMessage("Failed to initialize stream session.");
                errorEvent.setFatal(true);
                tempEmitter.send(errorEvent);
                lifecycle.cancel("INITIALIZATION_FAILED: " + ex.getMessage());
                try {
                    emitter.complete();
                } catch (Exception ignored) {}
            }
            return;
        }

        AssistantStreamEmitter streamEmitter = new AssistantStreamEmitter(
                emitter,
                lifecycle,
                objectMapper,
                sessionContext.getConversationId(),
                sessionContext.getAssistantMessageId()
        );

        try {
            if (!lifecycle.isActive()) {
                return;
            }

            // Boundary 2: Grounded evidence bundle preparation (Short read transaction)
            AssistantEvidenceBundle bundle = persistenceService.prepareEvidenceBundle(sessionContext);

            if (!lifecycle.isActive()) {
                return;
            }

            // SSE Event 1: message_start
            MessageStartEvent startEvent = new MessageStartEvent();
            startEvent.setRole("assistant");
            startEvent.setIntent(sessionContext.getIntent());
            streamEmitter.send(startEvent);

            if (!lifecycle.isActive()) {
                return;
            }

            // SSE Event 2: evidence
            EvidenceEvent evidenceEvent = new EvidenceEvent();
            evidenceEvent.setEvidenceBundle(bundle);
            streamEmitter.send(evidenceEvent);

            if (!lifecycle.isActive()) {
                return;
            }

            // Boundary 3: Generation Phase (FastAPI / Gemini streaming)
            // Work Unit 1 Temporary Explicit Behavior:
            // Upstream token streaming from FastAPI / Gemini is not yet integrated in Work Unit 1 (deferred to Work Unit 2).
            // To preserve the approved event contract without fabricating tokens or emitting a misleading
            // successful completion, and without persisting an empty assistant placeholder in conversation history,
            // we explicitly emit MessageInterruptedEvent with reason "UPSTREAM_GENERATION_UNAVAILABLE".
            // In Work Unit 2, this will be replaced with real-time upstream SSE token delta forwarding,
            // message finalization in persistence, and MessageCompleteEvent.
            MessageInterruptedEvent interruptedEvent = new MessageInterruptedEvent();
            interruptedEvent.setReason("UPSTREAM_GENERATION_UNAVAILABLE");
            interruptedEvent.setTokensEmitted(0);
            streamEmitter.send(interruptedEvent);

            try {
                emitter.complete();
            } catch (Exception ex) {
                log.debug("Emitter completion ignored: {}", ex.getMessage());
            }

        } catch (Exception ex) {
            log.error("Error during assistant stream execution for conversation {}: {}",
                    sessionContext.getConversationId(), ex.getMessage(), ex);

            if (lifecycle.isActive()) {
                ErrorEvent errorEvent = new ErrorEvent();
                errorEvent.setErrorCode("STREAM_PROCESSING_ERROR");
                errorEvent.setMessage("An unexpected error occurred during stream generation.");
                errorEvent.setFatal(true);
                streamEmitter.send(errorEvent);

                lifecycle.cancel("PROCESSING_ERROR: " + ex.getMessage());
                try {
                    emitter.complete();
                } catch (Exception ignored) {}
            }
        }
    }
}
