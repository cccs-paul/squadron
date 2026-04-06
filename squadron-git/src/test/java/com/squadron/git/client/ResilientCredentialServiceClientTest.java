package com.squadron.git.client;

import com.squadron.common.dto.ApiResponse;
import com.squadron.common.dto.CredentialResolutionResult;
import com.squadron.common.dto.ResolveCredentialRequest;
import com.squadron.common.resilience.CircuitBreaker;
import com.squadron.common.resilience.ResilientClient;
import com.squadron.common.resilience.RetryHelper;
import com.squadron.common.security.CredentialPurpose;
import com.squadron.common.security.CredentialType;
import com.squadron.common.security.GitAuthMode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ResilientCredentialServiceClientTest {

    @Mock
    private CredentialServiceClient delegate;

    private ResilientCredentialServiceClient resilientClient;
    private CircuitBreaker circuitBreaker;

    @BeforeEach
    void setUp() {
        circuitBreaker = CircuitBreaker.of("credential-test", 2, Duration.ofSeconds(30));
        RetryHelper retryHelper = RetryHelper.of(2, Duration.ofMillis(1), 2.0);
        ResilientClient client = ResilientClient.of(circuitBreaker, retryHelper);
        resilientClient = new ResilientCredentialServiceClient(delegate, client);
    }

    @Test
    void should_delegateResolveCredentials_successfully() {
        ResolveCredentialRequest request = ResolveCredentialRequest.builder()
                .userId(UUID.randomUUID())
                .connectionId(UUID.randomUUID())
                .purpose(CredentialPurpose.PLATFORM_API)
                .build();

        CredentialResolutionResult result = CredentialResolutionResult.builder()
                .accessToken("resolved-token-123")
                .credentialType(CredentialType.PAT)
                .gitAuthMode(GitAuthMode.HTTPS_TOKEN)
                .build();

        ApiResponse<CredentialResolutionResult> expectedResponse = ApiResponse.success(result);
        when(delegate.resolveCredentials(request)).thenReturn(expectedResponse);

        ApiResponse<CredentialResolutionResult> response = resilientClient.resolveCredentials(request);

        assertNotNull(response);
        assertTrue(response.isSuccess());
        assertEquals("resolved-token-123", response.getData().getAccessToken());
        verify(delegate).resolveCredentials(request);
    }

    @Test
    void should_retryOnTransientFailure() {
        ResolveCredentialRequest request = ResolveCredentialRequest.builder()
                .userId(UUID.randomUUID())
                .connectionId(UUID.randomUUID())
                .purpose(CredentialPurpose.GIT_PUSH)
                .build();

        ApiResponse<CredentialResolutionResult> expectedResponse = ApiResponse.success(
                CredentialResolutionResult.builder()
                        .accessToken("retry-token")
                        .credentialType(CredentialType.OAUTH2)
                        .build());

        when(delegate.resolveCredentials(request))
                .thenThrow(new RuntimeException("Connection refused"))
                .thenReturn(expectedResponse);

        ApiResponse<CredentialResolutionResult> response = resilientClient.resolveCredentials(request);

        assertNotNull(response);
        assertTrue(response.isSuccess());
        assertEquals("retry-token", response.getData().getAccessToken());
        verify(delegate, times(2)).resolveCredentials(request);
    }

    @Test
    void should_openCircuitBreakerAfterThresholdFailures() {
        ResolveCredentialRequest request = ResolveCredentialRequest.builder()
                .userId(UUID.randomUUID())
                .connectionId(UUID.randomUUID())
                .purpose(CredentialPurpose.PLATFORM_API)
                .build();

        when(delegate.resolveCredentials(any()))
                .thenThrow(new RuntimeException("Service unavailable"));

        for (int i = 0; i < 2; i++) {
            assertThrows(RuntimeException.class,
                    () -> resilientClient.resolveCredentials(request));
        }

        assertEquals(CircuitBreaker.State.OPEN, circuitBreaker.getState());
    }

    @Test
    void should_throwCircuitBreakerOpenException_whenCircuitIsOpen() {
        ResolveCredentialRequest request = ResolveCredentialRequest.builder()
                .userId(UUID.randomUUID())
                .connectionId(UUID.randomUUID())
                .purpose(CredentialPurpose.PLATFORM_API)
                .build();

        when(delegate.resolveCredentials(any()))
                .thenThrow(new RuntimeException("Service unavailable"));

        // Open the circuit
        for (int i = 0; i < 2; i++) {
            try {
                resilientClient.resolveCredentials(request);
            } catch (RuntimeException ignored) {
            }
        }

        assertEquals(CircuitBreaker.State.OPEN, circuitBreaker.getState());

        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> resilientClient.resolveCredentials(request));

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
