package com.pricepilot.ai.stream;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pricepilot.ai.stream.event.*;
import com.pricepilot.intelligence.assistant.dto.AssistantEvidenceBundle;
import com.pricepilot.intelligence.assistant.dto.AssistantIntent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.ArrayList;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

public class AssistantStreamEmitterTest {

    private SseEmitter sseEmitter;
    private StreamLifecycle lifecycle;
    private ObjectMapper objectMapper;
    private UUID conversationId;
    private UUID messageId;
    private AssistantStreamEmitter streamEmitter;

    @BeforeEach
    void setUp() {
        sseEmitter = mock(SseEmitter.class);
        lifecycle = new StreamLifecycle();
        objectMapper = new ObjectMapper().findAndRegisterModules();
        conversationId = UUID.randomUUID();
        messageId = UUID.randomUUID();

        streamEmitter = new AssistantStreamEmitter(
                sseEmitter,
                lifecycle,
                objectMapper,
                conversationId,
                messageId
        );
    }

    @Test
    @DisplayName("SSE: Monotonically increasing sequence numbers and event ordering")
    void testEventOrderingAndSequenceNumbers() throws Exception {
        MessageStartEvent start = new MessageStartEvent();
        start.setIntent(AssistantIntent.DISCOVERY);

        EvidenceEvent evidence = new EvidenceEvent();
        evidence.setEvidenceBundle(AssistantEvidenceBundle.builder().intent(AssistantIntent.DISCOVERY).build());

        TextDeltaEvent delta1 = new TextDeltaEvent();
        delta1.setDelta("Hello ");

        TextDeltaEvent delta2 = new TextDeltaEvent();
        delta2.setDelta("world!");

        MessageCompleteEvent complete = new MessageCompleteEvent();
        complete.setTotalTokens(2);
        complete.setDurationMs(150L);

        assertThat(streamEmitter.send(start)).isTrue();
        assertThat(start.getSequenceNumber()).isEqualTo(1);
        assertThat(start.getConversationId()).isEqualTo(conversationId);
        assertThat(start.getMessageId()).isEqualTo(messageId);

        assertThat(streamEmitter.send(evidence)).isTrue();
        assertThat(evidence.getSequenceNumber()).isEqualTo(2);

        assertThat(streamEmitter.send(delta1)).isTrue();
        assertThat(delta1.getSequenceNumber()).isEqualTo(3);

        assertThat(streamEmitter.send(delta2)).isTrue();
        assertThat(delta2.getSequenceNumber()).isEqualTo(4);

        assertThat(streamEmitter.send(complete)).isTrue();
        assertThat(complete.getSequenceNumber()).isEqualTo(5);

        assertThat(streamEmitter.getCurrentSequenceNumber()).isEqualTo(5);
        verify(sseEmitter, times(5)).send(any(SseEmitter.SseEventBuilder.class));
    }

    @Test
    @DisplayName("SSE: Exactly one terminal event may be emitted; subsequent terminal events are rejected")
    void testTerminalEventExclusivity() {
        MessageCompleteEvent complete = new MessageCompleteEvent();
        MessageInterruptedEvent interrupted = new MessageInterruptedEvent();
        ErrorEvent error = new ErrorEvent();

        assertThat(streamEmitter.send(complete)).isTrue();
        assertThat(streamEmitter.hasTerminalEmitted()).isTrue();

        // Any subsequent terminal event MUST be rejected
        assertThat(streamEmitter.send(interrupted)).isFalse();
        assertThat(streamEmitter.send(error)).isFalse();

        // Sequence number should only have incremented for the successful terminal event
        assertThat(streamEmitter.getCurrentSequenceNumber()).isEqualTo(1);
    }

    @Test
    @DisplayName("SSE: Fallback is non-terminal and can be followed by non-terminal events and exactly one terminal event")
    void testFallbackFollowedByNonTerminalEventsAndExactlyOneTerminalEvent() {
        MessageStartEvent start = new MessageStartEvent();
        start.setIntent(AssistantIntent.DISCOVERY);

        FallbackEvent fallback = new FallbackEvent();
        fallback.setReason("CIRCUIT_BREAKER_OPEN");
        fallback.setResponse("Default catalog advice");

        TextDeltaEvent delta = new TextDeltaEvent();
        delta.setDelta("Fallback guidance text");

        MessageCompleteEvent complete = new MessageCompleteEvent();
        complete.setTotalTokens(3);

        // 1. Fallback is NOT terminal
        assertThat(fallback.isTerminal()).isFalse();

        // 2. Send start event
        assertThat(streamEmitter.send(start)).isTrue();
        assertThat(streamEmitter.hasTerminalEmitted()).isFalse();

        // 3. Send fallback event
        assertThat(streamEmitter.send(fallback)).isTrue();
        assertThat(streamEmitter.hasTerminalEmitted()).isFalse();
        assertThat(lifecycle.isActive()).isTrue();

        // 4. Send non-terminal event after fallback
        assertThat(streamEmitter.send(delta)).isTrue();
        assertThat(streamEmitter.hasTerminalEmitted()).isFalse();

        // 5. Send terminal event
        assertThat(streamEmitter.send(complete)).isTrue();
        assertThat(streamEmitter.hasTerminalEmitted()).isTrue();
        assertThat(lifecycle.isCompleted()).isTrue();

        // 6. Any event after terminal event must be rejected
        TextDeltaEvent lateDelta = new TextDeltaEvent();
        lateDelta.setDelta("late");
        assertThat(streamEmitter.send(lateDelta)).isFalse();

        ErrorEvent lateError = new ErrorEvent();
        assertThat(streamEmitter.send(lateError)).isFalse();

        assertThat(streamEmitter.getCurrentSequenceNumber()).isEqualTo(4);
    }

    @Test
    @DisplayName("SSE Race: Completion racing against disconnect during terminal event delivery preserves COMPLETED state")
    void testCompletionRacingWithDisconnectDuringTerminalDelivery() throws Exception {
        CountDownLatch insideSendLatch = new CountDownLatch(1);
        CountDownLatch continueSendLatch = new CountDownLatch(1);

        // When emitter.send() is called, pause execution to simulate delivery in flight
        doAnswer(invocation -> {
            insideSendLatch.countDown();
            continueSendLatch.await(5, TimeUnit.SECONDS);
            return null;
        }).when(sseEmitter).send(any(SseEmitter.SseEventBuilder.class));

        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            AtomicBoolean completeSent = new AtomicBoolean(false);
            AtomicBoolean disconnectSucceeded = new AtomicBoolean(false);

            // Thread 1: Worker sends MessageCompleteEvent
            Future<?> workerFuture = executor.submit(() -> {
                MessageCompleteEvent complete = new MessageCompleteEvent();
                completeSent.set(streamEmitter.send(complete));
            });

            // Thread 2: Disconnect attempt arrives during terminal delivery
            Future<?> disconnectFuture = executor.submit(() -> {
                try {
                    insideSendLatch.await(5, TimeUnit.SECONDS);
                    // Disconnect attempts to cancel the stream
                    disconnectSucceeded.set(lifecycle.cancel("CLIENT_DISCONNECT"));
                    continueSendLatch.countDown();
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            });

            workerFuture.get(5, TimeUnit.SECONDS);
            disconnectFuture.get(5, TimeUnit.SECONDS);

            // Completion must win because terminal ownership was secured before/during delivery
            assertThat(completeSent.get()).isTrue();
            assertThat(disconnectSucceeded.get()).isFalse();
            assertThat(lifecycle.getState()).isEqualTo(StreamLifecycle.State.COMPLETED);
            assertThat(lifecycle.isCompleted()).isTrue();
            assertThat(lifecycle.isCancelled()).isFalse();
            assertThat(streamEmitter.hasTerminalEmitted()).isTrue();
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    @DisplayName("SSE Race: Completion racing against timeout during terminal event delivery preserves COMPLETED state")
    void testCompletionRacingWithTimeoutDuringTerminalDelivery() throws Exception {
        CountDownLatch insideSendLatch = new CountDownLatch(1);
        CountDownLatch continueSendLatch = new CountDownLatch(1);

        doAnswer(invocation -> {
            insideSendLatch.countDown();
            continueSendLatch.await(5, TimeUnit.SECONDS);
            return null;
        }).when(sseEmitter).send(any(SseEmitter.SseEventBuilder.class));

        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            AtomicBoolean completeSent = new AtomicBoolean(false);
            AtomicBoolean timeoutSucceeded = new AtomicBoolean(false);

            // Thread 1: Worker sends MessageCompleteEvent
            Future<?> workerFuture = executor.submit(() -> {
                MessageCompleteEvent complete = new MessageCompleteEvent();
                completeSent.set(streamEmitter.send(complete));
            });

            // Thread 2: Container timeout arrives during terminal delivery
            Future<?> timeoutFuture = executor.submit(() -> {
                try {
                    insideSendLatch.await(5, TimeUnit.SECONDS);
                    timeoutSucceeded.set(lifecycle.cancel("CONTAINER_TIMEOUT"));
                    continueSendLatch.countDown();
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            });

            workerFuture.get(5, TimeUnit.SECONDS);
            timeoutFuture.get(5, TimeUnit.SECONDS);

            assertThat(completeSent.get()).isTrue();
            assertThat(timeoutSucceeded.get()).isFalse();
            assertThat(lifecycle.getState()).isEqualTo(StreamLifecycle.State.COMPLETED);
            assertThat(lifecycle.isCompleted()).isTrue();
            assertThat(lifecycle.isCancelled()).isFalse();
            assertThat(streamEmitter.hasTerminalEmitted()).isTrue();
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    @DisplayName("SSE: Terminal send failure records terminal emission and prevents duplicate writes")
    void testTerminalSendFailureAndResultingLifecycleState() throws Exception {
        doThrow(new IOException("Broken pipe")).when(sseEmitter).send(any(SseEmitter.SseEventBuilder.class));

        MessageCompleteEvent complete = new MessageCompleteEvent();
        boolean sent = streamEmitter.send(complete);

        // Send failed due to transport
        assertThat(sent).isFalse();
        // State remains COMPLETED because terminal transition succeeded before wire write failed
        assertThat(lifecycle.getState()).isEqualTo(StreamLifecycle.State.COMPLETED);
        // Terminal event has been marked as emitted to prevent further writes
        assertThat(streamEmitter.hasTerminalEmitted()).isTrue();

        // Any subsequent event must be suppressed
        TextDeltaEvent lateDelta = new TextDeltaEvent();
        assertThat(streamEmitter.send(lateDelta)).isFalse();
        ErrorEvent lateError = new ErrorEvent();
        assertThat(streamEmitter.send(lateError)).isFalse();
    }

    @Test
    @DisplayName("SSE: Terminal error send failure sets CANCELLED state and prevents duplicate writes")
    void testTerminalErrorSendFailureSetsCancelledState() throws Exception {
        doThrow(new IOException("Broken pipe")).when(sseEmitter).send(any(SseEmitter.SseEventBuilder.class));

        ErrorEvent error = new ErrorEvent();
        error.setErrorCode("INTERNAL_ERROR");
        boolean sent = streamEmitter.send(error);

        assertThat(sent).isFalse();
        assertThat(lifecycle.getState()).isEqualTo(StreamLifecycle.State.CANCELLED);
        assertThat(streamEmitter.hasTerminalEmitted()).isTrue();

        TextDeltaEvent lateDelta = new TextDeltaEvent();
        assertThat(streamEmitter.send(lateDelta)).isFalse();
    }

    @Test
    @DisplayName("SSE: No writes permitted after terminal event has been emitted")
    void testNoWritesAfterTerminalTransition() {
        MessageCompleteEvent complete = new MessageCompleteEvent();
        assertThat(streamEmitter.send(complete)).isTrue();

        TextDeltaEvent lateDelta = new TextDeltaEvent();
        lateDelta.setDelta("This should never be sent");

        assertThat(streamEmitter.send(lateDelta)).isFalse();
        assertThat(streamEmitter.getCurrentSequenceNumber()).isEqualTo(1);
    }

    @Test
    @DisplayName("SSE: No writes permitted when stream lifecycle is cancelled")
    void testNoWritesWhenCancelled() {
        lifecycle.cancel("CLIENT_ABORT");

        MessageStartEvent start = new MessageStartEvent();
        assertThat(streamEmitter.send(start)).isFalse();
        assertThat(streamEmitter.getCurrentSequenceNumber()).isEqualTo(0);
    }

    @Test
    @DisplayName("SSE: Transport error triggers cancellation and ceases writes")
    void testTransportErrorTriggersCancellation() throws Exception {
        doThrow(new IOException("Broken pipe")).when(sseEmitter).send(any(SseEmitter.SseEventBuilder.class));

        MessageStartEvent start = new MessageStartEvent();
        boolean sent = streamEmitter.send(start);

        assertThat(sent).isFalse();
        assertThat(lifecycle.isCancelled()).isTrue();
        assertThat(lifecycle.getState()).isEqualTo(StreamLifecycle.State.CANCELLED);

        // Next write attempt must immediately fail without touching emitter
        TextDeltaEvent delta = new TextDeltaEvent();
        assertThat(streamEmitter.send(delta)).isFalse();
    }

    @Test
    @DisplayName("SSE: Cancellation hook attempting to emit another event is safely suppressed without re-entrancy bypass")
    void testCancellationHookAttemptingToEmitAnotherEvent_isSuppressed() {
        AtomicBoolean hookRan = new AtomicBoolean(false);
        AtomicBoolean hookSendResult = new AtomicBoolean(true);

        lifecycle.onCancel(() -> {
            hookRan.set(true);
            // Attempt to emit another event from inside the cancellation hook
            ErrorEvent reentrantError = new ErrorEvent();
            reentrantError.setErrorCode("HOOK_ERROR");
            boolean sent = streamEmitter.send(reentrantError);
            hookSendResult.set(sent);
        });

        MessageInterruptedEvent interrupted = new MessageInterruptedEvent();
        interrupted.setReason("UPSTREAM_GENERATION_UNAVAILABLE");
        interrupted.setTokensEmitted(0);

        boolean sent = streamEmitter.send(interrupted);

        assertThat(sent).isTrue();
        assertThat(hookRan.get()).isTrue();
        // The event emitted from inside the cancellation hook MUST be suppressed
        assertThat(hookSendResult.get()).isFalse();
        // Only the single terminal interrupted event was ever sent
        assertThat(streamEmitter.getCurrentSequenceNumber()).isEqualTo(1);
        assertThat(streamEmitter.hasTerminalEmitted()).isTrue();
    }

    @Test
    @DisplayName("SSE: Slow cancellation hook does not block terminal wire delivery")
    void testSlowCancellationHookDoesNotBlockTerminalDelivery() throws Exception {
        CountDownLatch hookStartedLatch = new CountDownLatch(1);
        CountDownLatch hookProceedLatch = new CountDownLatch(1);

        lifecycle.onCancel(() -> {
            hookStartedLatch.countDown();
            try {
                hookProceedLatch.await(5, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });

        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            MessageInterruptedEvent interrupted = new MessageInterruptedEvent();
            interrupted.setReason("CLIENT_DISCONNECT");

            Future<Boolean> sendFuture = executor.submit(() -> streamEmitter.send(interrupted));

            // Wait for hook to be invoked
            boolean hookStarted = hookStartedLatch.await(5, TimeUnit.SECONDS);
            assertThat(hookStarted).isTrue();

            // Terminal event has already been delivered to wire before hook started/finished
            assertThat(streamEmitter.hasTerminalEmitted()).isTrue();

            // Unblock the hook
            hookProceedLatch.countDown();
            boolean sent = sendFuture.get(5, TimeUnit.SECONDS);
            assertThat(sent).isTrue();
            assertThat(lifecycle.getState()).isEqualTo(StreamLifecycle.State.CANCELLED);
        } finally {
            executor.shutdownNow();
        }
    }
}
