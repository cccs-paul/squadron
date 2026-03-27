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
class ResilientWorkspaceServiceClientTest {

    @Mock
    private WorkspaceServiceClient delegate;

    private ResilientWorkspaceServiceClient resilientClient;
    private CircuitBreaker circuitBreaker;

    @BeforeEach
    void setUp() {
        circuitBreaker = CircuitBreaker.of("workspace-test", 2, Duration.ofSeconds(30));
        RetryHelper retryHelper = RetryHelper.of(2, Duration.ofMillis(1), 2.0);
        ResilientClient client = ResilientClient.of(circuitBreaker, retryHelper);
        resilientClient = new ResilientWorkspaceServiceClient(delegate, client);
    }

    @Test
    void should_delegateExec_successfully() {
        Map<String, Object> request = Map.of("command", "mvn test");
        Map<String, Object> expected = Map.of("exitCode", 0, "output", "BUILD SUCCESS");
        when(delegate.exec("ws-1", request)).thenReturn(expected);

        Map<String, Object> result = resilientClient.exec("ws-1", request);

        assertEquals(expected, result);
        verify(delegate).exec("ws-1", request);
    }

    @Test
    void should_delegateWriteFile_successfully() {
        byte[] content = "file content".getBytes();
        doNothing().when(delegate).writeFile("ws-1", "/src/Main.java", content);

        resilientClient.writeFile("ws-1", "/src/Main.java", content);

        verify(delegate).writeFile("ws-1", "/src/Main.java", content);
    }

    @Test
    void should_delegateReadFile_successfully() {
        byte[] expected = "file content".getBytes();
        when(delegate.readFile("ws-1", "/src/Main.java")).thenReturn(expected);

        byte[] result = resilientClient.readFile("ws-1", "/src/Main.java");

        assertArrayEquals(expected, result);
        verify(delegate).readFile("ws-1", "/src/Main.java");
    }

    @Test
    void should_retryExecOnTransientFailure() {
        Map<String, Object> request = Map.of("command", "mvn test");
        Map<String, Object> expected = Map.of("exitCode", 0);
        when(delegate.exec("ws-1", request))
                .thenThrow(new RuntimeException("Connection refused"))
                .thenReturn(expected);

        Map<String, Object> result = resilientClient.exec("ws-1", request);

        assertEquals(expected, result);
        verify(delegate, times(2)).exec("ws-1", request);
    }

    @Test
    void should_retryWriteFileOnTransientFailure() {
        byte[] content = "content".getBytes();
        doThrow(new RuntimeException("Connection refused"))
                .doNothing()
                .when(delegate).writeFile("ws-1", "/path", content);

        resilientClient.writeFile("ws-1", "/path", content);

        verify(delegate, times(2)).writeFile("ws-1", "/path", content);
    }

    @Test
    void should_retryReadFileOnTransientFailure() {
        byte[] expected = "content".getBytes();
        when(delegate.readFile("ws-1", "/path"))
                .thenThrow(new RuntimeException("Timeout"))
                .thenReturn(expected);

        byte[] result = resilientClient.readFile("ws-1", "/path");

        assertArrayEquals(expected, result);
        verify(delegate, times(2)).readFile("ws-1", "/path");
    }

    @Test
    void should_openCircuitBreakerAfterThresholdFailures() {
        Map<String, Object> request = Map.of("command", "ls");
        when(delegate.exec(anyString(), any()))
                .thenThrow(new RuntimeException("Service unavailable"));

        for (int i = 0; i < 2; i++) {
            assertThrows(RuntimeException.class,
                    () -> resilientClient.exec("ws-1", request));
        }

        assertEquals(CircuitBreaker.State.OPEN, circuitBreaker.getState());
    }

    @Test
    void should_throwCircuitBreakerOpenException_whenCircuitIsOpen() {
        Map<String, Object> request = Map.of("command", "ls");
        when(delegate.exec(anyString(), any()))
                .thenThrow(new RuntimeException("Service unavailable"));

        // Open the circuit
        for (int i = 0; i < 2; i++) {
            try {
                resilientClient.exec("ws-1", request);
            } catch (RuntimeException ignored) {
            }
        }

        assertEquals(CircuitBreaker.State.OPEN, circuitBreaker.getState());

        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> resilientClient.exec("ws-1", request));

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
