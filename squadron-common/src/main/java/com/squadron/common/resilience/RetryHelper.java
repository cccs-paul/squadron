package com.squadron.common.resilience;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.function.Supplier;

/**
 * Simple retry helper for inter-service calls.
 * Supports configurable retries with exponential backoff.
 */
public class RetryHelper {

    private static final Logger log = LoggerFactory.getLogger(RetryHelper.class);

    private final int maxRetries;
    private final Duration initialDelay;
    private final double backoffMultiplier;

    public RetryHelper(int maxRetries, Duration initialDelay, double backoffMultiplier) {
        this.maxRetries = maxRetries;
        this.initialDelay = initialDelay;
        this.backoffMultiplier = backoffMultiplier;
    }

    /**
     * Creates a default retry helper with 3 retries, 500ms initial delay, 2x backoff.
     */
    public static RetryHelper withDefaults() {
        return new RetryHelper(3, Duration.ofMillis(500), 2.0);
    }

    /**
     * Creates a retry helper with custom configuration.
     */
    public static RetryHelper of(int maxRetries, Duration initialDelay, double backoffMultiplier) {
        return new RetryHelper(maxRetries, initialDelay, backoffMultiplier);
    }

    /**
     * Execute the given supplier with retry logic.
     * Retries on any exception up to maxRetries times with exponential backoff.
     */
    public <T> T execute(String operationName, Supplier<T> operation) {
        Exception lastException = null;
        long delayMs = initialDelay.toMillis();

        for (int attempt = 1; attempt <= maxRetries + 1; attempt++) {
            try {
                return operation.get();
            } catch (Exception e) {
                lastException = e;
                if (attempt <= maxRetries) {
                    log.warn("{} failed (attempt {}/{}), retrying in {}ms: {}",
                            operationName, attempt, maxRetries + 1, delayMs, e.getMessage());
                    try {
                        Thread.sleep(delayMs);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        throw new RuntimeException("Retry interrupted for " + operationName, ie);
                    }
                    delayMs = (long) (delayMs * backoffMultiplier);
                }
            }
        }

        log.error("{} failed after {} attempts", operationName, maxRetries + 1);
        throw new RuntimeException("Operation failed after retries: " + operationName, lastException);
    }

    /**
     * Execute a void operation with retry logic.
     */
    public void executeVoid(String operationName, Runnable operation) {
        execute(operationName, () -> {
            operation.run();
            return null;
        });
    }
}
