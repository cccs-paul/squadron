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
 * All state transitions use CAS for thread safety.
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

    public static CircuitBreaker withDefaults(String name) {
        return new CircuitBreaker(name, 5, Duration.ofSeconds(30));
    }

    public static CircuitBreaker of(String name, int failureThreshold, Duration resetTimeout) {
        return new CircuitBreaker(name, failureThreshold, resetTimeout);
    }

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

    public void executeVoid(Runnable operation) {
        execute(() -> {
            operation.run();
            return null;
        });
    }

    private void onSuccess() {
        if (state.compareAndSet(State.HALF_OPEN, State.CLOSED)) {
            log.info("Circuit breaker '{}' transitioning from HALF_OPEN to CLOSED", name);
            failureCount.set(0);
        }
        successCount.incrementAndGet();
    }

    private void onFailure() {
        lastFailureTime = Instant.now();
        int failures = failureCount.incrementAndGet();
        if (failures >= failureThreshold) {
            if (state.compareAndSet(State.CLOSED, State.OPEN)) {
                log.warn("Circuit breaker '{}' transitioning to OPEN after {} failures", name, failures);
            } else if (state.compareAndSet(State.HALF_OPEN, State.OPEN)) {
                log.warn("Circuit breaker '{}' transitioning from HALF_OPEN to OPEN after failure", name);
            }
        }
    }

    State getEffectiveState() {
        if (state.get() == State.OPEN) {
            if (Instant.now().isAfter(lastFailureTime.plus(resetTimeout))) {
                if (state.compareAndSet(State.OPEN, State.HALF_OPEN)) {
                    log.info("Circuit breaker '{}' transitioning from OPEN to HALF_OPEN", name);
                }
                return state.get();
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

    public static class CircuitBreakerOpenException extends RuntimeException {
        public CircuitBreakerOpenException(String message) {
            super(message);
        }
    }
}
