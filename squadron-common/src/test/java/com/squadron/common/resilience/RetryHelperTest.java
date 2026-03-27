package com.squadron.common.resilience;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class RetryHelperTest {

    @Test
    void should_succeedOnFirstAttempt() {
        RetryHelper helper = RetryHelper.of(3, Duration.ofMillis(1), 2.0);
        AtomicInteger attempts = new AtomicInteger(0);

        String result = helper.execute("test-op", () -> {
            attempts.incrementAndGet();
            return "success";
        });

        assertEquals("success", result);
        assertEquals(1, attempts.get());
    }

    @Test
    void should_retryAndSucceedOnSecondAttempt() {
        RetryHelper helper = RetryHelper.of(3, Duration.ofMillis(1), 2.0);
        AtomicInteger attempts = new AtomicInteger(0);

        String result = helper.execute("test-op", () -> {
            if (attempts.incrementAndGet() < 2) {
                throw new RuntimeException("transient failure");
            }
            return "success";
        });

        assertEquals("success", result);
        assertEquals(2, attempts.get());
    }

    @Test
    void should_retryAndSucceedOnThirdAttempt() {
        RetryHelper helper = RetryHelper.of(3, Duration.ofMillis(1), 2.0);
        AtomicInteger attempts = new AtomicInteger(0);

        String result = helper.execute("test-op", () -> {
            if (attempts.incrementAndGet() < 3) {
                throw new RuntimeException("transient failure");
            }
            return "success";
        });

        assertEquals("success", result);
        assertEquals(3, attempts.get());
    }

    @Test
    void should_failAfterMaxRetries() {
        RetryHelper helper = RetryHelper.of(2, Duration.ofMillis(1), 2.0);
        AtomicInteger attempts = new AtomicInteger(0);

        RuntimeException ex = assertThrows(RuntimeException.class, () ->
                helper.execute("failing-op", () -> {
                    attempts.incrementAndGet();
                    throw new RuntimeException("persistent failure");
                })
        );

        // maxRetries=2 means 3 total attempts (1 initial + 2 retries)
        assertEquals(3, attempts.get());
        assertTrue(ex.getMessage().contains("Operation failed after retries: failing-op"));
        assertNotNull(ex.getCause());
        assertEquals("persistent failure", ex.getCause().getMessage());
    }

    @Test
    void should_useExponentialBackoff() {
        // Use 10ms initial delay with 2x multiplier so we can measure timing
        RetryHelper helper = RetryHelper.of(2, Duration.ofMillis(10), 2.0);
        AtomicInteger attempts = new AtomicInteger(0);

        long start = System.currentTimeMillis();

        assertThrows(RuntimeException.class, () ->
                helper.execute("backoff-op", () -> {
                    attempts.incrementAndGet();
                    throw new RuntimeException("fail");
                })
        );

        long elapsed = System.currentTimeMillis() - start;

        // Should have waited at least 10ms + 20ms = 30ms total for 2 retries
        // Use a generous lower bound to avoid flaky tests
        assertTrue(elapsed >= 25, "Expected at least 25ms elapsed, got " + elapsed + "ms");
        assertEquals(3, attempts.get());
    }

    @Test
    void should_executeVoid_successfully() {
        RetryHelper helper = RetryHelper.of(3, Duration.ofMillis(1), 2.0);
        AtomicInteger counter = new AtomicInteger(0);

        helper.executeVoid("void-op", counter::incrementAndGet);

        assertEquals(1, counter.get());
    }

    @Test
    void should_executeVoid_withRetries() {
        RetryHelper helper = RetryHelper.of(3, Duration.ofMillis(1), 2.0);
        AtomicInteger attempts = new AtomicInteger(0);

        helper.executeVoid("void-retry-op", () -> {
            if (attempts.incrementAndGet() < 2) {
                throw new RuntimeException("transient failure");
            }
        });

        assertEquals(2, attempts.get());
    }

    @Test
    void should_handleInterruption() {
        RetryHelper helper = RetryHelper.of(3, Duration.ofMillis(100), 2.0);

        Thread.currentThread().interrupt();

        RuntimeException ex = assertThrows(RuntimeException.class, () ->
                helper.execute("interrupt-op", () -> {
                    throw new RuntimeException("fail");
                })
        );

        assertTrue(ex.getMessage().contains("Retry interrupted"));
        assertTrue(Thread.interrupted()); // clears the flag
    }

    @Test
    void should_createWithDefaults() {
        RetryHelper helper = RetryHelper.withDefaults();
        assertNotNull(helper);

        // Verify defaults by executing — should allow at least 1 attempt
        String result = helper.execute("default-op", () -> "ok");
        assertEquals("ok", result);
    }

    @Test
    void should_createWithCustomConfig() {
        RetryHelper helper = RetryHelper.of(5, Duration.ofMillis(100), 1.5);
        assertNotNull(helper);

        AtomicInteger attempts = new AtomicInteger(0);

        // Should allow up to 6 attempts (1 initial + 5 retries)
        String result = helper.execute("custom-op", () -> {
            if (attempts.incrementAndGet() < 6) {
                throw new RuntimeException("fail");
            }
            return "finally";
        });

        assertEquals("finally", result);
        assertEquals(6, attempts.get());
    }
}
