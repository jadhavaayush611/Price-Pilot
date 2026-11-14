package com.pricepilot.ai.stream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Closeable;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Dedicated lifecycle abstraction governing the streaming response.
 * Uses atomic CAS state transitions to guarantee mutually exclusive terminal states.
 * Guarantees exactly-once execution for cancellation hooks, resource closing, and future cancellation.
 */
public class StreamLifecycle {

    private static final Logger log = LoggerFactory.getLogger(StreamLifecycle.class);

    public enum State {
        ACTIVE,
        COMPLETED,
        CANCELLED
    }

    private final AtomicReference<State> state = new AtomicReference<>(State.ACTIVE);
    private final List<Runnable> cancellationHooks = new ArrayList<>();
    private final List<Closeable> resourcesToClose = new ArrayList<>();
    private final List<Future<?>> futuresToCancel = new ArrayList<>();
    private final Object cancellationLock = new Object();
    private final AtomicBoolean cancellationExecuted = new AtomicBoolean(false);

    public State getState() {
        return state.get();
    }

    public boolean isActive() {
        return state.get() == State.ACTIVE;
    }

    public boolean isTerminal() {
        return state.get() != State.ACTIVE;
    }

    public boolean isCompleted() {
        return state.get() == State.COMPLETED;
    }

    public boolean isCancelled() {
        return state.get() == State.CANCELLED;
    }

    /**
     * Mutually exclusive atomic transition from ACTIVE -> COMPLETED.
     * Returns true ONLY if transition succeeded.
     */
    public boolean markCompleted() {
        return state.compareAndSet(State.ACTIVE, State.COMPLETED);
    }

    /**
     * Mutually exclusive atomic transition from ACTIVE -> CANCELLED without executing cleanup.
     * Returns true ONLY if transition succeeded.
     */
    public boolean markCancelled() {
        return state.compareAndSet(State.ACTIVE, State.CANCELLED);
    }

    /**
     * Mutually exclusive atomic transition from ACTIVE -> CANCELLED.
     * Triggers all cancellation hooks and cleans up resources idempotently and exactly once.
     * Returns true ONLY if transition succeeded (meaning this call changed ACTIVE -> CANCELLED).
     */
    public boolean cancel() {
        return cancel(null);
    }

    public boolean cancel(String reason) {
        if (markCancelled()) {
            executeCancellation(reason);
            return true;
        }
        return false;
    }

    void executeCancellation(String reason) {
        if (state.get() != State.CANCELLED
                || !cancellationExecuted.compareAndSet(false, true)) {
            return;
        }
        log.info("StreamLifecycle: Cancelled (reason: {})", reason != null ? reason : "unspecified");

        List<Future<?>> futuresCopy;
        List<Closeable> resourcesCopy;
        List<Runnable> hooksCopy;

        synchronized (cancellationLock) {
            futuresCopy = new ArrayList<>(futuresToCancel);
            futuresToCancel.clear();

            resourcesCopy = new ArrayList<>(resourcesToClose);
            resourcesToClose.clear();

            hooksCopy = new ArrayList<>(cancellationHooks);
            cancellationHooks.clear();
        }

        for (Future<?> future : futuresCopy) {
            if (future != null) {
                try {
                    if (!future.isDone()) {
                        future.cancel(true);
                    }
                } catch (RuntimeException e) {
                    log.warn("Error cancelling registered future during stream cancellation: {}", e.getMessage());
                }
            }
        }

        for (Closeable resource : resourcesCopy) {
            if (resource != null) {
                try {
                    resource.close();
                } catch (IOException | RuntimeException e) {
                    log.debug("Error closing resource during stream cancellation: {}", e.getMessage());
                }
            }
        }

        for (Runnable hook : hooksCopy) {
            if (hook != null) {
                try {
                    hook.run();
                } catch (RuntimeException e) {
                    log.warn("Error running stream cancellation hook: {}", e.getMessage());
                }
            }
        }
    }

    public void onCancel(Runnable hook) {
        if (hook == null) {
            return;
        }
        boolean runImmediately = false;
        synchronized (cancellationLock) {
            if (state.get() == State.CANCELLED) {
                runImmediately = true;
            } else {
                cancellationHooks.add(hook);
            }
        }
        if (runImmediately) {
            try {
                hook.run();
            } catch (RuntimeException e) {
                log.warn("Error running stream cancellation hook: {}", e.getMessage());
            }
        }
    }

    public void registerResource(Closeable resource) {
        if (resource == null) {
            return;
        }
        boolean closeImmediately = false;
        synchronized (cancellationLock) {
            if (state.get() == State.CANCELLED) {
                closeImmediately = true;
            } else {
                resourcesToClose.add(resource);
            }
        }
        if (closeImmediately) {
            try {
                resource.close();
            } catch (IOException | RuntimeException ignored) {}
        }
    }

    public void registerFuture(Future<?> future) {
        if (future == null) {
            return;
        }
        boolean cancelImmediately = false;
        synchronized (cancellationLock) {
            if (state.get() == State.CANCELLED) {
                cancelImmediately = true;
            } else {
                futuresToCancel.add(future);
            }
        }
        if (cancelImmediately) {
            try {
                if (!future.isDone()) {
                    future.cancel(true);
                }
            } catch (RuntimeException e) {
                log.warn("Error cancelling registered future: {}", e.getMessage());
            }
        }
    }
}
