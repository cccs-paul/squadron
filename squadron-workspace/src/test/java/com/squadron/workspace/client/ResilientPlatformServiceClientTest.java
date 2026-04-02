package com.squadron.workspace.client;

import com.squadron.common.dto.ApiResponse;
import com.squadron.common.resilience.CircuitBreaker;
import com.squadron.common.resilience.ResilientClient;
import com.squadron.common.resilience.RetryHelper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ResilientPlatformServiceClientTest {

    @Mock
    private PlatformServiceClient delegate;

    private CircuitBreaker circuitBreaker;
    private ResilientPlatformServiceClient resilientClient;

    @BeforeEach
    void setUp() {
        circuitBreaker = CircuitBreaker.of("platform-service-test", 2, Duration.ofSeconds(30));
        RetryHelper retryHelper = RetryHelper.of(2, Duration.ofMillis(1), 2.0);
        ResilientClient client = ResilientClient.of(circuitBreaker, retryHelper);
        resilientClient = new ResilientPlatformServiceClient(delegate, client);
    }

    @Test
    void should_delegateGetDecryptedPrivateKey_successfully() {
        UUID sshKeyId = UUID.randomUUID();
        String privateKey = "-----BEGIN RSA PRIVATE KEY-----\nMIIEpA...\n-----END RSA PRIVATE KEY-----";
        ApiResponse<String> response = ApiResponse.<String>builder()
                .success(true)
                .data(privateKey)
                .build();

        when(delegate.getDecryptedPrivateKey(sshKeyId)).thenReturn(response);

        String result = resilientClient.getDecryptedPrivateKey(sshKeyId);

        assertEquals(privateKey, result);
        verify(delegate).getDecryptedPrivateKey(sshKeyId);
    }

    @Test
    void should_retryGetDecryptedPrivateKey_onTransientFailure() {
        UUID sshKeyId = UUID.randomUUID();
        String privateKey = "-----BEGIN RSA PRIVATE KEY-----\ntest-key\n-----END RSA PRIVATE KEY-----";
        ApiResponse<String> response = ApiResponse.<String>builder()
                .success(true)
                .data(privateKey)
                .build();

        when(delegate.getDecryptedPrivateKey(sshKeyId))
                .thenThrow(new RuntimeException("Connection refused"))
                .thenReturn(response);

        String result = resilientClient.getDecryptedPrivateKey(sshKeyId);

        assertEquals(privateKey, result);
        verify(delegate, times(2)).getDecryptedPrivateKey(sshKeyId);
    }

    @Test
    void should_openCircuitBreakerAfterThresholdFailures() {
        UUID sshKeyId = UUID.randomUUID();
        when(delegate.getDecryptedPrivateKey(sshKeyId))
                .thenThrow(new RuntimeException("Service unavailable"));

        for (int i = 0; i < 2; i++) {
            try {
                resilientClient.getDecryptedPrivateKey(sshKeyId);
            } catch (RuntimeException ignored) {
            }
        }

        assertEquals(CircuitBreaker.State.OPEN, circuitBreaker.getState());
    }

    @Test
    void should_throwCircuitBreakerOpenException_whenCircuitIsOpen() {
        UUID sshKeyId = UUID.randomUUID();
        when(delegate.getDecryptedPrivateKey(sshKeyId))
                .thenThrow(new RuntimeException("Service unavailable"));

        // Trip the circuit breaker
        for (int i = 0; i < 2; i++) {
            try {
                resilientClient.getDecryptedPrivateKey(sshKeyId);
            } catch (RuntimeException ignored) {
            }
        }

        // Next call should fail with circuit breaker open
        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> resilientClient.getDecryptedPrivateKey(sshKeyId));
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
