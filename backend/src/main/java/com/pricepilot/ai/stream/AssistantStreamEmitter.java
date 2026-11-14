package com.pricepilot.ai.stream;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pricepilot.ai.stream.event.AssistantStreamEvent;
import com.pricepilot.ai.stream.event.MessageCompleteEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Thread-safe wrapper around SseEmitter enforcing:
 * 1. Monotonically increasing sequence numbers starting at 1.
 * 2. Exactly one terminal event per stream.
 * 3. Prevention of writes after a terminal event or stream cancellation.
 * 4. Serialized payload delivery using the application's Jackson ObjectMapper.
 * 5. Coordinated lifecycle terminal-state ownership with terminal event delivery.
 */
public class AssistantStreamEmitter {

    private static final Logger log = LoggerFactory.getLogger(AssistantStreamEmitter.class);

    private final SseEmitter emitter;
    private final StreamLifecycle lifecycle;
    private final ObjectMapper objectMapper;
    private final UUID conversationId;
    private final UUID messageId;

    private final AtomicInteger sequenceNumber = new AtomicInteger(0);
    private final AtomicBoolean terminalEmitted = new AtomicBoolean(false);
    private final Object writeLock = new Object();

    public AssistantStreamEmitter(
            SseEmitter emitter,
            StreamLifecycle lifecycle,
            ObjectMapper objectMapper,
            UUID conversationId,
            UUID messageId) {
        this.emitter = emitter;
        this.lifecycle = lifecycle;
        this.objectMapper = objectMapper;
        this.conversationId = conversationId;
        this.messageId = messageId;
    }

    /**
     * Sends an SSE event to the client if the stream is active and not terminal.
     * Returns true if sent successfully, false otherwise.
     */
    public boolean send(AssistantStreamEvent event) {
        if (event == null) {
            return false;
        }

        boolean triggerCleanup = false;
        String cleanupReason = null;
        boolean sent = false;

        synchronized (writeLock) {
            if (terminalEmitted.get()) {
                log.debug("Suppressing SSE event '{}' because terminal event was already emitted", event.getEventName());
                return false;
            }

            if (event.isTerminal()) {
                if (event instanceof MessageCompleteEvent) {
                    // Successful terminal completion: coordinate transition ACTIVE -> COMPLETED
                    if (lifecycle.getState() == StreamLifecycle.State.ACTIVE) {
                        if (!lifecycle.markCompleted()) {
                            log.debug("Suppressing message_complete: failed to transition ACTIVE -> COMPLETED (state: {})",
                                    lifecycle.getState());
                            return false;
                        }
                    } else if (lifecycle.getState() != StreamLifecycle.State.COMPLETED) {
                        log.debug("Suppressing message_complete: stream is in cancelled state (state: {})",
                                lifecycle.getState());
                        return false;
                    }
                } else {
                    // Non-success terminal events (error, message_interrupted):
                    // Transition ACTIVE -> CANCELLED atomically without executing blocking cleanup inside writeLock
                    if (lifecycle.getState() == StreamLifecycle.State.ACTIVE) {
                        if (lifecycle.markCancelled()) {
                            triggerCleanup = true;
                            cleanupReason = "TERMINAL_" + event.getEventName().toUpperCase();
                        }
                    } else if (lifecycle.getState() == StreamLifecycle.State.COMPLETED) {
                        log.debug("Suppressing {} because stream is already COMPLETED", event.getEventName());
                        return false;
                    }
                }

                if (!terminalEmitted.compareAndSet(false, true)) {
                    log.warn("Attempted duplicate terminal event delivery: '{}'", event.getEventName());
                    return false;
                }
            } else {
                // Non-terminal event: suppress if lifecycle is terminal
                if (lifecycle.isTerminal()) {
                    log.debug("Suppressing SSE event '{}' because stream is already terminal (state: {})",
                            event.getEventName(), lifecycle.getState());
                    return false;
                }
            }

            int seq = sequenceNumber.incrementAndGet();
            event.setSequenceNumber(seq);
            event.setConversationId(conversationId);
            event.setMessageId(messageId);

            try {
                String payload = objectMapper.writeValueAsString(event);
                emitter.send(SseEmitter.event()
                        .name(event.getEventName())
                        .data(payload, MediaType.APPLICATION_JSON));
                sent = true;
            } catch (IOException | IllegalStateException ex) {
                log.info("Client disconnected or SSE transport failed during send of '{}' for conv {}: {}",
                        event.getEventName(), conversationId, ex.getMessage());
                if (!event.isTerminal()) {
                    triggerCleanup = lifecycle.markCancelled();
                    cleanupReason = "TRANSPORT_SEND_FAILED";
                }
                sent = false;
            } catch (Exception ex) {
                log.error("Failed to serialize or send SSE event '{}': {}", event.getEventName(), ex.getMessage());
                if (!event.isTerminal()) {
                    triggerCleanup = lifecycle.markCancelled();
                    cleanupReason = "SERIALIZATION_ERROR";
                }
                sent = false;
            }
        } // writeLock is released here!

        // Execute external cleanup callbacks strictly OUTSIDE writeLock to prevent deadlocks and delays
        if (triggerCleanup) {
            lifecycle.executeCancellation(cleanupReason);
        }

        return sent;
    }

    public int getCurrentSequenceNumber() {
        return sequenceNumber.get();
    }

    public boolean hasTerminalEmitted() {
        return terminalEmitted.get();
    }

    public UUID getConversationId() {
        return conversationId;
    }

    public UUID getMessageId() {
        return messageId;
    }
}
