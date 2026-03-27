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
class ResilientGitServiceClientTest {

    @Mock
    private GitServiceClient delegate;

    private ResilientGitServiceClient resilientClient;
    private CircuitBreaker circuitBreaker;

    @BeforeEach
    void setUp() {
        circuitBreaker = CircuitBreaker.of("git-service-test", 2, Duration.ofSeconds(30));
        RetryHelper retryHelper = RetryHelper.of(2, Duration.ofMillis(1), 2.0);
        ResilientClient client = ResilientClient.of(circuitBreaker, retryHelper);
        resilientClient = new ResilientGitServiceClient(delegate, client);
    }

    @Test
    void should_delegateResolveStrategy_successfully() {
        Map<String, Object> expected = Map.of("strategy", "feature-branch");
        when(delegate.resolveStrategy("tenant-1", "project-1")).thenReturn(expected);

        Map<String, Object> result = resilientClient.resolveStrategy("tenant-1", "project-1");

        assertEquals(expected, result);
        verify(delegate).resolveStrategy("tenant-1", "project-1");
    }

    @Test
    void should_delegateGenerateBranchName_successfully() {
        Map<String, Object> expected = Map.of("branchName", "feature/TASK-123-add-login");
        when(delegate.generateBranchName("t1", "task-1", "Add login", "proj-1"))
                .thenReturn(expected);

        Map<String, Object> result = resilientClient.generateBranchName("t1", "task-1", "Add login", "proj-1");

        assertEquals(expected, result);
        verify(delegate).generateBranchName("t1", "task-1", "Add login", "proj-1");
    }

    @Test
    void should_delegateCreatePullRequest_successfully() {
        Map<String, Object> request = Map.of("title", "PR title");
        Map<String, Object> expected = Map.of("id", "pr-1");
        when(delegate.createPullRequest(request)).thenReturn(expected);

        Map<String, Object> result = resilientClient.createPullRequest(request);

        assertEquals(expected, result);
        verify(delegate).createPullRequest(request);
    }

    @Test
    void should_delegateGetPullRequestByTaskId_successfully() {
        Map<String, Object> expected = Map.of("id", "pr-1", "status", "open");
        when(delegate.getPullRequestByTaskId("task-1")).thenReturn(expected);

        Map<String, Object> result = resilientClient.getPullRequestByTaskId("task-1");

        assertEquals(expected, result);
        verify(delegate).getPullRequestByTaskId("task-1");
    }

    @Test
    void should_delegateCheckMergeability_successfully() {
        Map<String, Object> expected = Map.of("mergeable", true);
        when(delegate.checkMergeability("pr-1")).thenReturn(expected);

        Map<String, Object> result = resilientClient.checkMergeability("pr-1");

        assertEquals(expected, result);
        verify(delegate).checkMergeability("pr-1");
    }

    @Test
    void should_delegateMergePullRequest_successfully() {
        Map<String, Object> expected = Map.of("merged", true);
        when(delegate.mergePullRequest("pr-1")).thenReturn(expected);

        Map<String, Object> result = resilientClient.mergePullRequest("pr-1");

        assertEquals(expected, result);
        verify(delegate).mergePullRequest("pr-1");
    }

    @Test
    void should_retryOnTransientFailure() {
        Map<String, Object> expected = Map.of("strategy", "trunk-based");
        when(delegate.resolveStrategy("tenant-1", "project-1"))
                .thenThrow(new RuntimeException("Connection refused"))
                .thenReturn(expected);

        Map<String, Object> result = resilientClient.resolveStrategy("tenant-1", "project-1");

        assertEquals(expected, result);
        verify(delegate, times(2)).resolveStrategy("tenant-1", "project-1");
    }

    @Test
    void should_openCircuitBreakerAfterThresholdFailures() {
        when(delegate.resolveStrategy(anyString(), anyString()))
                .thenThrow(new RuntimeException("Service unavailable"));

        for (int i = 0; i < 2; i++) {
            assertThrows(RuntimeException.class,
                    () -> resilientClient.resolveStrategy("tenant-1", "project-1"));
        }

        assertEquals(CircuitBreaker.State.OPEN, circuitBreaker.getState());
    }

    @Test
    void should_throwCircuitBreakerOpenException_whenCircuitIsOpen() {
        when(delegate.resolveStrategy(anyString(), anyString()))
                .thenThrow(new RuntimeException("Service unavailable"));

        // Open the circuit
        for (int i = 0; i < 2; i++) {
            try {
                resilientClient.resolveStrategy("tenant-1", "project-1");
            } catch (RuntimeException ignored) {
            }
        }

        assertEquals(CircuitBreaker.State.OPEN, circuitBreaker.getState());

        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> resilientClient.resolveStrategy("tenant-1", "project-1"));

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
