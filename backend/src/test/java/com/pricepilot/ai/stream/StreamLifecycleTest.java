package com.pricepilot.ai.stream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.Closeable;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

public class StreamLifecycleTest {

    @Test
    @DisplayName("Lifecycle: Successful completion transitions ACTIVE -> COMPLETED")
    void testSuccessfulCompletion() {
        StreamLifecycle lifecycle = new StreamLifecycle();
        assertThat(lifecycle.getState()).isEqualTo(StreamLifecycle.State.ACTIVE);
        assertThat(lifecycle.isActive()).isTrue();
        assertThat(lifecycle.isTerminal()).isFalse();

        boolean completed = lifecycle.markCompleted();
        assertThat(completed).isTrue();
        assertThat(lifecycle.getState()).isEqualTo(StreamLifecycle.State.COMPLETED);
        assertThat(lifecycle.isCompleted()).isTrue();
        assertThat(lifecycle.isTerminal()).isTrue();

        // Repeated completion should return false
        assertThat(lifecycle.markCompleted()).isFalse();
        // Cancellation after completion should return false
        assertThat(lifecycle.cancel("LATE_CANCEL")).isFalse();
        assertThat(lifecycle.getState()).isEqualTo(StreamLifecycle.State.COMPLETED);
    }

    @Test
    @DisplayName("Lifecycle: Disconnect transitions ACTIVE -> CANCELLED and runs hooks and closes resources")
    void testDisconnect() throws IOException {
        StreamLifecycle lifecycle = new StreamLifecycle();
        AtomicBoolean hookRan = new AtomicBoolean(false);
        AtomicBoolean resourceClosed = new AtomicBoolean(false);
        CompletableFuture<String> future = new CompletableFuture<>();

        lifecycle.onCancel(() -> hookRan.set(true));
        lifecycle.registerResource(() -> resourceClosed.set(true));
        lifecycle.registerFuture(future);

        boolean cancelled = lifecycle.cancel("CLIENT_DISCONNECT");
        assertThat(cancelled).isTrue();
        assertThat(lifecycle.getState()).isEqualTo(StreamLifecycle.State.CANCELLED);
        assertThat(lifecycle.isCancelled()).isTrue();
        assertThat(lifecycle.isTerminal()).isTrue();

        assertThat(hookRan.get()).isTrue();
        assertThat(resourceClosed.get()).isTrue();
        assertThat(future.isCancelled()).isTrue();

        // Late completion attempt must fail
        assertThat(lifecycle.markCompleted()).isFalse();
        assertThat(lifecycle.getState()).isEqualTo(StreamLifecycle.State.CANCELLED);
    }

    @Test
    @DisplayName("Lifecycle: Timeout transitions ACTIVE -> CANCELLED and prevents subsequent completion")
    void testTimeout() {
        StreamLifecycle lifecycle = new StreamLifecycle();
        boolean cancelled = lifecycle.cancel("TIMEOUT");
        assertThat(cancelled).isTrue();
        assertThat(lifecycle.getState()).isEqualTo(StreamLifecycle.State.CANCELLED);

        // Later worker completion must NOT overwrite terminal state
        boolean marked = lifecycle.markCompleted();
        assertThat(marked).isFalse();
        assertThat(lifecycle.getState()).isEqualTo(StreamLifecycle.State.CANCELLED);
    }

    @Test
    @DisplayName("Lifecycle: Transport error transitions ACTIVE -> CANCELLED")
    void testTransportError() {
        StreamLifecycle lifecycle = new StreamLifecycle();
        boolean cancelled = lifecycle.cancel("TRANSPORT_ERROR");
        assertThat(cancelled).isTrue();
        assertThat(lifecycle.getState()).isEqualTo(StreamLifecycle.State.CANCELLED);

        // Duplicate cancellation returns false
        assertThat(lifecycle.cancel("ANOTHER_ERROR")).isFalse();
        assertThat(lifecycle.getState()).isEqualTo(StreamLifecycle.State.CANCELLED);
    }

    @Test
    @DisplayName("Lifecycle: Repeated cancellation is idempotent and executes cleanup exactly once")
    void testRepeatedCancellation() {
        StreamLifecycle lifecycle = new StreamLifecycle();
        AtomicInteger hookCount = new AtomicInteger(0);
        AtomicInteger closeCount = new AtomicInteger(0);

        lifecycle.onCancel(hookCount::incrementAndGet);
        lifecycle.registerResource(() -> closeCount.incrementAndGet());

        assertThat(lifecycle.cancel("FIRST_CANCEL")).isTrue();
        assertThat(lifecycle.cancel("SECOND_CANCEL")).isFalse();
        assertThat(lifecycle.cancel("THIRD_CANCEL")).isFalse();

        assertThat(hookCount.get()).isEqualTo(1);
        assertThat(closeCount.get()).isEqualTo(1);
    }

    @Test
    @DisplayName("Lifecycle: Attaching resources to already CANCELLED lifecycle triggers immediate cleanup")
    void testAttachToAlreadyCancelledLifecycle() {
        StreamLifecycle lifecycle = new StreamLifecycle();
        lifecycle.cancel("EARLY_CANCEL");

        AtomicBoolean hookRan = new AtomicBoolean(false);
        AtomicBoolean resourceClosed = new AtomicBoolean(false);
        CompletableFuture<String> future = new CompletableFuture<>();

        lifecycle.onCancel(() -> hookRan.set(true));
        lifecycle.registerResource(() -> resourceClosed.set(true));
        lifecycle.registerFuture(future);

        assertThat(hookRan.get()).isTrue();
        assertThat(resourceClosed.get()).isTrue();
        assertThat(future.isCancelled()).isTrue();
    }

    @Test
    @DisplayName("Lifecycle Race: Completion racing with disconnect is mutually exclusive and deterministic")
    void testCompletionRacingWithDisconnect() throws Exception {
        int trials = 100;
        ExecutorService executor = Executors.newFixedThreadPool(4);

        try {
            for (int i = 0; i < trials; i++) {
                StreamLifecycle lifecycle = new StreamLifecycle();
                CyclicBarrier barrier = new CyclicBarrier(2);
                AtomicBoolean completeWon = new AtomicBoolean(false);
                AtomicBoolean cancelWon = new AtomicBoolean(false);

                Future<?> f1 = executor.submit(() -> {
                    try {
                        barrier.await();
                        if (lifecycle.markCompleted()) {
                            completeWon.set(true);
                        }
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                });

                Future<?> f2 = executor.submit(() -> {
                    try {
                        barrier.await();
                        if (lifecycle.cancel("RACE_DISCONNECT")) {
                            cancelWon.set(true);
                        }
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                });

                f1.get(5, TimeUnit.SECONDS);
                f2.get(5, TimeUnit.SECONDS);

                // Exactly one transition must have succeeded
                assertThat(completeWon.get() ^ cancelWon.get()).isTrue();

                if (completeWon.get()) {
                    assertThat(lifecycle.getState()).isEqualTo(StreamLifecycle.State.COMPLETED);
                } else {
                    assertThat(lifecycle.getState()).isEqualTo(StreamLifecycle.State.CANCELLED);
                }
            }
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    @DisplayName("Lifecycle Race: Completion racing with timeout is mutually exclusive")
    void testCompletionRacingWithTimeout() throws Exception {
        StreamLifecycle lifecycle = new StreamLifecycle();
        CyclicBarrier barrier = new CyclicBarrier(2);
        AtomicBoolean completeWon = new AtomicBoolean(false);
        AtomicBoolean timeoutWon = new AtomicBoolean(false);

        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<?> f1 = executor.submit(() -> {
                try {
                    barrier.await();
                    if (lifecycle.markCompleted()) {
                        completeWon.set(true);
                    }
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            });

            Future<?> f2 = executor.submit(() -> {
                try {
                    barrier.await();
                    if (lifecycle.cancel("CONTAINER_TIMEOUT")) {
                        timeoutWon.set(true);
                    }
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            });

            f1.get(5, TimeUnit.SECONDS);
            f2.get(5, TimeUnit.SECONDS);

            assertThat(completeWon.get() ^ timeoutWon.get()).isTrue();
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    @DisplayName("Lifecycle Race: Duplicate terminal callbacks across multiple threads ensure single winner")
    void testDuplicateTerminalCallbacks() throws Exception {
        StreamLifecycle lifecycle = new StreamLifecycle();
        int threadCount = 8;
        CyclicBarrier barrier = new CyclicBarrier(threadCount);
        AtomicInteger winners = new AtomicInteger(0);

        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        try {
            List<Future<?>> futures = new ArrayList<>();
            for (int i = 0; i < threadCount; i++) {
                final int index = i;
                futures.add(executor.submit(() -> {
                    try {
                        barrier.await();
                        boolean won;
                        if (index % 2 == 0) {
                            won = lifecycle.markCompleted();
                        } else {
                            won = lifecycle.cancel("CANCEL_THREAD_" + index);
                        }
                        if (won) {
                            winners.incrementAndGet();
                        }
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                }));
            }

            for (Future<?> f : futures) {
                f.get(5, TimeUnit.SECONDS);
            }

            assertThat(winners.get()).isEqualTo(1);
            assertThat(lifecycle.isTerminal()).isTrue();
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    @DisplayName("Lifecycle Concurrency: Hooks registered concurrently with cancellation run exactly once")
    void testConcurrentCancellationHookRegistration_runsAtMostOnce() throws Exception {
        int threadCount = 20;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount + 1);
        try {
            for (int trial = 0; trial < 10; trial++) {
                StreamLifecycle lifecycle = new StreamLifecycle();
                CyclicBarrier barrier = new CyclicBarrier(threadCount + 1);
                AtomicInteger[] hookCounts = new AtomicInteger[threadCount];
                for (int i = 0; i < threadCount; i++) {
                    hookCounts[i] = new AtomicInteger(0);
                }

                List<Future<?>> futures = new ArrayList<>();
                // Register threads
                for (int i = 0; i < threadCount; i++) {
                    final int idx = i;
                    futures.add(executor.submit(() -> {
                        try {
                            barrier.await();
                            lifecycle.onCancel(hookCounts[idx]::incrementAndGet);
                        } catch (Exception e) {
                            throw new RuntimeException(e);
                        }
                    }));
                }

                // Cancelling thread
                futures.add(executor.submit(() -> {
                    try {
                        barrier.await();
                        lifecycle.cancel("CONCURRENT_CANCEL_TEST");
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                }));

                for (Future<?> f : futures) {
                    f.get(5, TimeUnit.SECONDS);
                }

                assertThat(lifecycle.getState()).isEqualTo(StreamLifecycle.State.CANCELLED);
                for (int i = 0; i < threadCount; i++) {
                    assertThat(hookCounts[i].get())
                            .as("Hook %d should execute exactly once", i)
                            .isEqualTo(1);
                }
            }
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    @DisplayName("Lifecycle Concurrency: Resources registered concurrently with cancellation are closed exactly once")
    void testConcurrentResourceRegistrationAndCleanup_closedAtMostOnce() throws Exception {
        int threadCount = 20;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount + 1);
        try {
            for (int trial = 0; trial < 10; trial++) {
                StreamLifecycle lifecycle = new StreamLifecycle();
                CyclicBarrier barrier = new CyclicBarrier(threadCount + 1);
                AtomicInteger[] closeCounts = new AtomicInteger[threadCount];
                for (int i = 0; i < threadCount; i++) {
                    closeCounts[i] = new AtomicInteger(0);
                }

                List<Future<?>> futures = new ArrayList<>();
                for (int i = 0; i < threadCount; i++) {
                    final int idx = i;
                    futures.add(executor.submit(() -> {
                        try {
                            barrier.await();
                            lifecycle.registerResource(() -> closeCounts[idx].incrementAndGet());
                        } catch (Exception e) {
                            throw new RuntimeException(e);
                        }
                    }));
                }

                futures.add(executor.submit(() -> {
                    try {
                        barrier.await();
                        lifecycle.cancel("CONCURRENT_RESOURCE_CANCEL");
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                }));

                for (Future<?> f : futures) {
                    f.get(5, TimeUnit.SECONDS);
                }

                assertThat(lifecycle.getState()).isEqualTo(StreamLifecycle.State.CANCELLED);
                for (int i = 0; i < threadCount; i++) {
                    assertThat(closeCounts[i].get())
                            .as("Resource %d should be closed exactly once", i)
                            .isEqualTo(1);
                }
            }
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    @DisplayName("Lifecycle Concurrency: Futures registered concurrently with cancellation are cancelled exactly once")
    void testConcurrentFutureRegistrationAndCancellation_cancelledAtMostOnce() throws Exception {
        int threadCount = 20;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount + 1);
        try {
            for (int trial = 0; trial < 10; trial++) {
                StreamLifecycle lifecycle = new StreamLifecycle();
                CyclicBarrier barrier = new CyclicBarrier(threadCount + 1);
                List<CompletableFuture<String>> registeredFutures = new ArrayList<>();
                for (int i = 0; i < threadCount; i++) {
                    registeredFutures.add(new CompletableFuture<>());
                }

                List<Future<?>> tasks = new ArrayList<>();
                for (int i = 0; i < threadCount; i++) {
                    final int idx = i;
                    tasks.add(executor.submit(() -> {
                        try {
                            barrier.await();
                            lifecycle.registerFuture(registeredFutures.get(idx));
                        } catch (Exception e) {
                            throw new RuntimeException(e);
                        }
                    }));
                }

                tasks.add(executor.submit(() -> {
                    try {
                        barrier.await();
                        lifecycle.cancel("CONCURRENT_FUTURE_CANCEL");
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                }));

                for (Future<?> f : tasks) {
                    f.get(5, TimeUnit.SECONDS);
                }

                assertThat(lifecycle.getState()).isEqualTo(StreamLifecycle.State.CANCELLED);
                for (int i = 0; i < threadCount; i++) {
                    assertThat(registeredFutures.get(i).isCancelled())
                            .as("Future %d should be cancelled", i)
                            .isTrue();
                }
            }
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    @DisplayName("Lifecycle: Exception in future isDone or cancel does not prevent remaining cleanup")
    void testFutureCancellationExceptionSafety() {
        StreamLifecycle lifecycle = new StreamLifecycle();

        Future<?> faultyFuture1 = mock(Future.class);
        when(faultyFuture1.isDone()).thenThrow(new RuntimeException("Simulated isDone failure"));

        Future<?> faultyFuture2 = mock(Future.class);
        when(faultyFuture2.isDone()).thenReturn(false);
        when(faultyFuture2.cancel(true)).thenThrow(new RuntimeException("Simulated cancel failure"));

        CompletableFuture<String> validFuture = new CompletableFuture<>();
        AtomicBoolean resourceClosed = new AtomicBoolean(false);
        AtomicBoolean hookRan = new AtomicBoolean(false);

        lifecycle.registerFuture(faultyFuture1);
        lifecycle.registerFuture(faultyFuture2);
        lifecycle.registerFuture(validFuture);
        lifecycle.registerResource(() -> resourceClosed.set(true));
        lifecycle.onCancel(() -> hookRan.set(true));

        boolean cancelled = lifecycle.cancel("TEST_EXCEPTION_SAFETY");

        assertThat(cancelled).isTrue();
        assertThat(lifecycle.getState()).isEqualTo(StreamLifecycle.State.CANCELLED);
        assertThat(validFuture.isCancelled()).isTrue();
        assertThat(resourceClosed.get()).isTrue();
        assertThat(hookRan.get()).isTrue();
    }

    @Test
    @DisplayName("Lifecycle: Exception in late registered future does not escape")
    void testLateRegisteredFutureExceptionSafety() {
        StreamLifecycle lifecycle = new StreamLifecycle();
        lifecycle.cancel("EARLY_CANCEL");

        Future<?> faultyFuture = mock(Future.class);
        when(faultyFuture.isDone()).thenThrow(new RuntimeException("Simulated late isDone failure"));

        assertDoesNotThrow(() -> lifecycle.registerFuture(faultyFuture));
    }

    @Test
    @DisplayName("Lifecycle Invariant: executeCancellation while ACTIVE does not execute cleanup")
    void testExecuteCancellationWhileActive_doesNotExecuteCleanup() {
        StreamLifecycle lifecycle = new StreamLifecycle();
        assertThat(lifecycle.getState()).isEqualTo(StreamLifecycle.State.ACTIVE);

        AtomicBoolean hookRan = new AtomicBoolean(false);
        AtomicBoolean resourceClosed = new AtomicBoolean(false);
        CompletableFuture<String> future = new CompletableFuture<>();

        lifecycle.onCancel(() -> hookRan.set(true));
        lifecycle.registerResource(() -> resourceClosed.set(true));
        lifecycle.registerFuture(future);

        // Attempt direct invocation while ACTIVE
        lifecycle.executeCancellation("PREMATURE_CLEANUP");

        assertThat(lifecycle.getState()).isEqualTo(StreamLifecycle.State.ACTIVE);
        assertThat(hookRan.get()).isFalse();
        assertThat(resourceClosed.get()).isFalse();
        assertThat(future.isCancelled()).isFalse();

        // Calling cancel later should still work normally
        boolean cancelled = lifecycle.cancel("VALID_CANCEL");
        assertThat(cancelled).isTrue();
        assertThat(lifecycle.getState()).isEqualTo(StreamLifecycle.State.CANCELLED);
        assertThat(hookRan.get()).isTrue();
        assertThat(resourceClosed.get()).isTrue();
        assertThat(future.isCancelled()).isTrue();
    }

    @Test
    @DisplayName("Lifecycle Invariant: executeCancellation while COMPLETED does not execute cleanup")
    void testExecuteCancellationWhileCompleted_doesNotExecuteCleanup() {
        StreamLifecycle lifecycle = new StreamLifecycle();
        boolean completed = lifecycle.markCompleted();
        assertThat(completed).isTrue();
        assertThat(lifecycle.getState()).isEqualTo(StreamLifecycle.State.COMPLETED);

        AtomicBoolean hookRan = new AtomicBoolean(false);
        AtomicBoolean resourceClosed = new AtomicBoolean(false);
        CompletableFuture<String> future = new CompletableFuture<>();

        lifecycle.onCancel(() -> hookRan.set(true));
        lifecycle.registerResource(() -> resourceClosed.set(true));
        lifecycle.registerFuture(future);

        // Attempt direct invocation while COMPLETED
        lifecycle.executeCancellation("INVALID_CLEANUP");

        assertThat(lifecycle.getState()).isEqualTo(StreamLifecycle.State.COMPLETED);
        assertThat(hookRan.get()).isFalse();
        assertThat(resourceClosed.get()).isFalse();
        assertThat(future.isCancelled()).isFalse();
    }

    @Test
    @DisplayName("Lifecycle Invariant: executeCancellation after ACTIVE -> CANCELLED runs cleanup exactly once and ignores repeated calls")
    void testExecuteCancellationAfterCancelled_runsExactlyOnce() {
        StreamLifecycle lifecycle = new StreamLifecycle();
        AtomicInteger hookCount = new AtomicInteger(0);
        AtomicInteger resourceCount = new AtomicInteger(0);
        CompletableFuture<String> future = new CompletableFuture<>();

        lifecycle.onCancel(hookCount::incrementAndGet);
        lifecycle.registerResource(() -> resourceCount.incrementAndGet());
        lifecycle.registerFuture(future);

        boolean cancelled = lifecycle.markCancelled();
        assertThat(cancelled).isTrue();
        assertThat(lifecycle.getState()).isEqualTo(StreamLifecycle.State.CANCELLED);

        // First execution runs cleanup
        lifecycle.executeCancellation("FIRST_CLEANUP");
        assertThat(hookCount.get()).isEqualTo(1);
        assertThat(resourceCount.get()).isEqualTo(1);
        assertThat(future.isCancelled()).isTrue();

        // Repeated executions must NOT run cleanup again
        lifecycle.executeCancellation("SECOND_CLEANUP");
        lifecycle.executeCancellation("THIRD_CLEANUP");
        assertThat(hookCount.get()).isEqualTo(1);
        assertThat(resourceCount.get()).isEqualTo(1);
    }
}
