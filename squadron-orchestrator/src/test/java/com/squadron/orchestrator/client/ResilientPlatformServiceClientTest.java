package com.squadron.orchestrator.client;

import com.squadron.common.resilience.CircuitBreaker;
import com.squadron.common.resilience.ResilientClient;
import com.squadron.common.resilience.RetryHelper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Constructor;
import java.time.Duration;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ResilientPlatformServiceClientTest {

    @Mock
    private PlatformServiceClient delegate;

    private ResilientPlatformServiceClient resilientClient;
    private CircuitBreaker circuitBreaker;

    @BeforeEach
    void setUp() throws Exception {
        circuitBreaker = CircuitBreaker.of("platform-service-test", 2, Duration.ofSeconds(30));
        RetryHelper retryHelper = RetryHelper.of(2, Duration.ofMillis(1), 2.0);
        ResilientClient client = ResilientClient.of(circuitBreaker, retryHelper);
        Constructor<ResilientPlatformServiceClient> ctor = ResilientPlatformServiceClient.class
                .getDeclaredConstructor(PlatformServiceClient.class, ResilientClient.class);
        ctor.setAccessible(true);
        resilientClient = ctor.newInstance(delegate, client);
    }

    @Test
    void should_delegateFetchTasks_successfully() {
        List<Map<String, Object>> expected = List.of(
                Map.of("id", "task-1", "title", "Fix bug"),
                Map.of("id", "task-2", "title", "Add feature")
        );
        when(delegate.fetchTasks("conn-1", "PROJ")).thenReturn(expected);

        List<Map<String, Object>> result = resilientClient.fetchTasks("conn-1", "PROJ");

        assertEquals(expected, result);
        verify(delegate).fetchTasks("conn-1", "PROJ");
    }

    @Test
    void should_retryOnTransientFailure() {
        List<Map<String, Object>> expected = List.of(Map.of("id", "task-1"));
        when(delegate.fetchTasks("conn-1", "PROJ"))
                .thenThrow(new RuntimeException("Connection refused"))
                .thenReturn(expected);

        List<Map<String, Object>> result = resilientClient.fetchTasks("conn-1", "PROJ");

        assertEquals(expected, result);
        verify(delegate, times(2)).fetchTasks("conn-1", "PROJ");
    }

    @Test
    void should_openCircuitBreakerAfterThresholdFailures() {
        when(delegate.fetchTasks(anyString(), anyString()))
                .thenThrow(new RuntimeException("Service unavailable"));

        for (int i = 0; i < 2; i++) {
            assertThrows(RuntimeException.class,
                    () -> resilientClient.fetchTasks("conn-1", "PROJ"));
        }

        assertEquals(CircuitBreaker.State.OPEN, circuitBreaker.getState());
    }

    @Test
    void should_throwCircuitBreakerOpenException_whenCircuitIsOpen() {
        when(delegate.fetchTasks(anyString(), anyString()))
                .thenThrow(new RuntimeException("Service unavailable"));

        // Open the circuit
        for (int i = 0; i < 2; i++) {
            try {
                resilientClient.fetchTasks("conn-1", "PROJ");
            } catch (RuntimeException ignored) {
            }
        }

        assertEquals(CircuitBreaker.State.OPEN, circuitBreaker.getState());

        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> resilientClient.fetchTasks("conn-1", "PROJ"));

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
