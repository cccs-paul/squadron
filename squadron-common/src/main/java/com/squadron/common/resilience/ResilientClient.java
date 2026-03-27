package com.squadron.common.resilience;

import java.util.function.Supplier;

/**
 * Combines circuit breaker and retry patterns for resilient inter-service calls.
 */
public class ResilientClient {

    private final CircuitBreaker circuitBreaker;
    private final RetryHelper retryHelper;

    public ResilientClient(CircuitBreaker circuitBreaker, RetryHelper retryHelper) {
        this.circuitBreaker = circuitBreaker;
        this.retryHelper = retryHelper;
    }

    public static ResilientClient withDefaults(String name) {
        return new ResilientClient(
                CircuitBreaker.withDefaults(name),
                RetryHelper.withDefaults()
        );
    }

    public static ResilientClient of(CircuitBreaker circuitBreaker, RetryHelper retryHelper) {
        return new ResilientClient(circuitBreaker, retryHelper);
    }

    /**
     * Execute with both circuit breaker and retry.
     * Retry wraps the circuit breaker — retries will respect the circuit breaker state.
     */
    public <T> T execute(String operationName, Supplier<T> operation) {
        return retryHelper.execute(operationName, () -> circuitBreaker.execute(operation));
    }

    public void executeVoid(String operationName, Runnable operation) {
        retryHelper.executeVoid(operationName, () -> circuitBreaker.executeVoid(operation));
    }

    public CircuitBreaker getCircuitBreaker() {
        return circuitBreaker;
    }
}
