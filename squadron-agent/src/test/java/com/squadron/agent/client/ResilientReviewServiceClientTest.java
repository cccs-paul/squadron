package com.squadron.agent.client;

import com.squadron.common.resilience.CircuitBreaker;
import com.squadron.common.resilience.ResilientClient;
import com.squadron.common.resilience.RetryHelper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ResilientReviewServiceClientTest {

    @Mock
    private ReviewServiceClient delegate;

    private ResilientReviewServiceClient resilientClient;
    private CircuitBreaker circuitBreaker;

    @BeforeEach
    void setUp() {
        circuitBreaker = CircuitBreaker.of("review-service-test", 2, Duration.ofSeconds(30));
        RetryHelper retryHelper = RetryHelper.of(2, Duration.ofMillis(1), 2.0);
        ResilientClient client = ResilientClient.of(circuitBreaker, retryHelper);
        resilientClient = new ResilientReviewServiceClient(delegate, client);
    }

    @Test
    void should_delegateCreateReview_successfully() {
        Map<String, Object> request = Map.of("taskId", "task-1");
        Map<String, Object> expected = Map.of("reviewId", "rev-1");
        when(delegate.createReview(request)).thenReturn(expected);

        Map<String, Object> result = resilientClient.createReview(request);

        assertEquals(expected, result);
        verify(delegate).createReview(request);
    }

    @Test
    void should_delegateSubmitReview_successfully() {
        Map<String, Object> request = Map.of("reviewId", "rev-1", "approved", true);
        Map<String, Object> expected = Map.of("status", "APPROVED");
        when(delegate.submitReview(request)).thenReturn(expected);

        Map<String, Object> result = resilientClient.submitReview(request);

        assertEquals(expected, result);
        verify(delegate).submitReview(request);
    }

    @Test
    void should_delegateCheckReviewGate_successfully() {
        Map<String, Object> expected = Map.of("passed", true);
        when(delegate.checkReviewGate("task-1", "tenant-1", "team-1")).thenReturn(expected);

        Map<String, Object> result = resilientClient.checkReviewGate("task-1", "tenant-1", "team-1");

        assertEquals(expected, result);
        verify(delegate).checkReviewGate("task-1", "tenant-1", "team-1");
    }

    @Test
    void should_retryOnTransientFailure() {
        Map<String, Object> request = Map.of("taskId", "task-1");
        Map<String, Object> expected = Map.of("reviewId", "rev-1");
        when(delegate.createReview(request))
                .thenThrow(new RuntimeException("Connection refused"))
                .thenReturn(expected);

        Map<String, Object> result = resilientClient.createReview(request);

        assertEquals(expected, result);
        verify(delegate, times(2)).createReview(request);
    }

    @Test
    void should_openCircuitBreakerAfterThresholdFailures() {
        Map<String, Object> request = Map.of("taskId", "task-1");
        when(delegate.createReview(any()))
                .thenThrow(new RuntimeException("Service unavailable"));

        for (int i = 0; i < 2; i++) {
            assertThrows(RuntimeException.class,
                    () -> resilientClient.createReview(request));
        }

        assertEquals(CircuitBreaker.State.OPEN, circuitBreaker.getState());
    }

    @Test
    void should_throwCircuitBreakerOpenException_whenCircuitIsOpen() {
        Map<String, Object> request = Map.of("taskId", "task-1");
        when(delegate.createReview(any()))
                .thenThrow(new RuntimeException("Service unavailable"));

        // Open the circuit
        for (int i = 0; i < 2; i++) {
            try {
                resilientClient.createReview(request);
            } catch (RuntimeException ignored) {
            }
        }

        assertEquals(CircuitBreaker.State.OPEN, circuitBreaker.getState());

        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> resilientClient.createReview(request));

        assertCircuitBreakerOpenExceptionInChain(ex);
    }

    @Test
    void should_exposeResilientClient() {
        assertNotNull(resilientClient.getResilientClient());
    }

    private void assertCircuitBreakerOpenExceptionInChain(Throwable ex) {
        Throwable cause = ex;
        while (cause != null) {
            if (cause instanceof CircuitBreaker.CircuitBreakerOpenException) {
                return;
            }
            cause = cause.getCause();
        }
        fail("Expected CircuitBreakerOpenException in cause chain, but got: " + ex);
    }
}
