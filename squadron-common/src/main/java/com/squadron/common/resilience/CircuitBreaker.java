package com.squadron.common.resilience;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

/**
 * Simple circuit breaker implementation for inter-service calls.
 * States: CLOSED (normal), OPEN (failing), HALF_OPEN (testing).
 */
public class CircuitBreaker {

    private static final Logger log = LoggerFactory.getLogger(CircuitBreaker.class);

    public enum State { CLOSED, OPEN, HALF_OPEN }

    private final String name;
    private final int failureThreshold;
    private final Duration resetTimeout;
    private final AtomicInteger failureCount = new AtomicInteger(0);
    private final AtomicInteger successCount = new AtomicInteger(0);
    private final AtomicReference<State> state = new AtomicReference<>(State.CLOSED);
    private volatile Instant lastFailureTime = Instant.MIN;

    public CircuitBreaker(String name, int failureThreshold, Duration resetTimeout) {
        this.name = name;
        this.failureThreshold = failureThreshold;
        this.resetTimeout = resetTimeout;
    }

    /**
     * Create a circuit breaker with default settings: 5 failures, 30s reset timeout.
     */
    public static CircuitBreaker withDefaults(String name) {
        return new CircuitBreaker(name, 5, Duration.ofSeconds(30));
    }

    /**
     * Create a circuit breaker with custom settings.
     */
    public static CircuitBreaker of(String name, int failureThreshold, Duration resetTimeout) {
        return new CircuitBreaker(name, failureThreshold, resetTimeout);
    }

    /**
     * Execute the operation through the circuit breaker.
     */
    public <T> T execute(Supplier<T> operation) {
        State currentState = getEffectiveState();

        if (currentState == State.OPEN) {
            throw new CircuitBreakerOpenException(
                    "Circuit breaker '" + name + "' is OPEN, request rejected");
        }

        try {
            T result = operation.get();
            onSuccess();
            return result;
        } catch (Exception e) {
            onFailure();
            throw e;
        }
    }

    /**
     * Execute a void operation through the circuit breaker.
     */
    public void executeVoid(Runnable operation) {
        execute(() -> {
            operation.run();
            return null;
        });
    }

    private void onSuccess() {
        if (state.get() == State.HALF_OPEN) {
            log.info("Circuit breaker '{}' transitioning from HALF_OPEN to CLOSED", name);
            state.set(State.CLOSED);
            failureCount.set(0);
        }
        successCount.incrementAndGet();
    }

    private void onFailure() {
        lastFailureTime = Instant.now();
        int failures = failureCount.incrementAndGet();
        if (failures >= failureThreshold && state.get() == State.CLOSED) {
            log.warn("Circuit breaker '{}' transitioning to OPEN after {} failures", name, failures);
            state.set(State.OPEN);
        }
    }

    /**
     * Determine the effective state, considering timeout-based transition to HALF_OPEN.
     */
    State getEffectiveState() {
        if (state.get() == State.OPEN) {
            if (Instant.now().isAfter(lastFailureTime.plus(resetTimeout))) {
                log.info("Circuit breaker '{}' transitioning from OPEN to HALF_OPEN", name);
                state.set(State.HALF_OPEN);
                return State.HALF_OPEN;
            }
            return State.OPEN;
        }
        return state.get();
    }

    public State getState() {
        return getEffectiveState();
    }

    public String getName() {
        return name;
    }

    public int getFailureCount() {
        return failureCount.get();
    }

    public int getSuccessCount() {
        return successCount.get();
    }

    public void reset() {
        state.set(State.CLOSED);
        failureCount.set(0);
        successCount.set(0);
        lastFailureTime = Instant.MIN;
    }

    /**
     * Exception thrown when the circuit breaker is open.
     */
    public static class CircuitBreakerOpenException extends RuntimeException {
        public CircuitBreakerOpenException(String message) {
            super(message);
        }
    }
}
