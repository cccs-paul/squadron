package com.squadron.common.resilience;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class ResilientClientTest {

    @Test
    void should_executeSuccessfully() {
        ResilientClient client = ResilientClient.withDefaults("test-service");

        String result = client.execute("get-data", () -> "data");

        assertEquals("data", result);
    }

    @Test
    void should_retryOnFailure() {
        CircuitBreaker cb = CircuitBreaker.of("test", 10, Duration.ofSeconds(30));
        RetryHelper retry = RetryHelper.of(3, Duration.ofMillis(1), 2.0);
        ResilientClient client = ResilientClient.of(cb, retry);

        AtomicInteger attempts = new AtomicInteger(0);

        String result = client.execute("retry-op", () -> {
            if (attempts.incrementAndGet() < 3) {
                throw new RuntimeException("transient");
            }
            return "recovered";
        });

        assertEquals("recovered", result);
        assertEquals(3, attempts.get());
    }

    @Test
    void should_failWhenCircuitOpen() {
        CircuitBreaker cb = CircuitBreaker.of("test", 2, Duration.ofSeconds(30));
        RetryHelper retry = RetryHelper.of(1, Duration.ofMillis(1), 2.0);
        ResilientClient client = ResilientClient.of(cb, retry);

        // Open the circuit by failing through it
        for (int i = 0; i < 2; i++) {
            try {
                client.execute("fail-op", () -> { throw new RuntimeException("fail"); });
            } catch (RuntimeException ignored) {
                // expected
            }
        }

        assertEquals(CircuitBreaker.State.OPEN, cb.getState());

        // Now the circuit is open, the next call should fail with circuit breaker exception
        // wrapped in the retry RuntimeException
        RuntimeException ex = assertThrows(RuntimeException.class, () ->
                client.execute("blocked-op", () -> "should not run")
        );

        // The exception chain should contain the circuit breaker open exception
        Throwable cause = ex;
        boolean foundCircuitBreakerException = false;
        while (cause != null) {
            if (cause instanceof CircuitBreaker.CircuitBreakerOpenException) {
                foundCircuitBreakerException = true;
                break;
            }
            cause = cause.getCause();
        }
        assertTrue(foundCircuitBreakerException,
                "Expected CircuitBreakerOpenException in cause chain");
    }

    @Test
    void should_executeVoid() {
        ResilientClient client = ResilientClient.withDefaults("test-service");
        AtomicInteger counter = new AtomicInteger(0);

        client.executeVoid("void-op", counter::incrementAndGet);

        assertEquals(1, counter.get());
    }

    @Test
    void should_createWithDefaults() {
        ResilientClient client = ResilientClient.withDefaults("my-service");

        assertNotNull(client);
        assertNotNull(client.getCircuitBreaker());
        assertEquals("my-service", client.getCircuitBreaker().getName());
    }

    @Test
    void should_getCircuitBreaker() {
        CircuitBreaker cb = CircuitBreaker.of("custom", 3, Duration.ofSeconds(10));
        RetryHelper retry = RetryHelper.withDefaults();
        ResilientClient client = ResilientClient.of(cb, retry);

        assertSame(cb, client.getCircuitBreaker());
        assertEquals("custom", client.getCircuitBreaker().getName());
    }
}
