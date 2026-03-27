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
class ResilientOrchestratorClientTest {

    @Mock
    private OrchestratorClient delegate;

    private ResilientOrchestratorClient resilientClient;
    private CircuitBreaker circuitBreaker;

    @BeforeEach
    void setUp() {
        circuitBreaker = CircuitBreaker.of("orchestrator-test", 2, Duration.ofSeconds(30));
        RetryHelper retryHelper = RetryHelper.of(2, Duration.ofMillis(1), 2.0);
        ResilientClient client = ResilientClient.of(circuitBreaker, retryHelper);
        resilientClient = new ResilientOrchestratorClient(delegate, client);
    }

    @Test
    void should_delegateTransitionTask_successfully() {
        Map<String, Object> request = Map.of("action", "start");
        Map<String, Object> expected = Map.of("status", "IN_PROGRESS");
        when(delegate.transitionTask("task-1", request)).thenReturn(expected);

        Map<String, Object> result = resilientClient.transitionTask("task-1", request);

        assertEquals(expected, result);
        verify(delegate).transitionTask("task-1", request);
    }

    @Test
    void should_retryOnTransientFailure() {
        Map<String, Object> request = Map.of("action", "start");
        Map<String, Object> expected = Map.of("status", "IN_PROGRESS");
        when(delegate.transitionTask("task-1", request))
                .thenThrow(new RuntimeException("Connection refused"))
                .thenReturn(expected);

        Map<String, Object> result = resilientClient.transitionTask("task-1", request);

        assertEquals(expected, result);
        verify(delegate, times(2)).transitionTask("task-1", request);
    }

    @Test
    void should_openCircuitBreakerAfterThresholdFailures() {
        Map<String, Object> request = Map.of("action", "start");
        when(delegate.transitionTask(anyString(), any()))
                .thenThrow(new RuntimeException("Service unavailable"));

        // Exhaust retries to open the circuit breaker (threshold = 2 failures)
        for (int i = 0; i < 2; i++) {
            assertThrows(RuntimeException.class,
                    () -> resilientClient.transitionTask("task-1", request));
        }

        assertEquals(CircuitBreaker.State.OPEN, circuitBreaker.getState());
    }

    @Test
    void should_throwCircuitBreakerOpenException_whenCircuitIsOpen() {
        Map<String, Object> request = Map.of("action", "start");
        when(delegate.transitionTask(anyString(), any()))
                .thenThrow(new RuntimeException("Service unavailable"));

        // Open the circuit
        for (int i = 0; i < 2; i++) {
            try {
                resilientClient.transitionTask("task-1", request);
            } catch (RuntimeException ignored) {
            }
        }

        assertEquals(CircuitBreaker.State.OPEN, circuitBreaker.getState());

        // Next call should fail with CircuitBreakerOpenException in the cause chain
        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> resilientClient.transitionTask("task-1", request));

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
