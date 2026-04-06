package com.squadron.agent.tool.builtin;

import com.squadron.common.dto.ApiResponse;
import com.squadron.common.dto.CredentialResolutionResult;
import com.squadron.common.security.CredentialPurpose;
import com.squadron.common.security.CredentialType;
import com.squadron.common.security.GitAuthMode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CredentialClientTest {

    @Mock
    private WebClient webClient;

    @Mock
    private WebClient.RequestBodyUriSpec requestBodyUriSpec;

    @Mock
    private WebClient.RequestBodySpec requestBodySpec;

    @Mock
    private WebClient.RequestHeadersSpec requestHeadersSpec;

    @Mock
    private WebClient.ResponseSpec responseSpec;

    private CredentialClient credentialClient;

    @BeforeEach
    void setUp() {
        credentialClient = new CredentialClient(webClient);
    }

    @Test
    void should_resolveCredentials_when_successfulResponse() {
        UUID userId = UUID.randomUUID();
        UUID connectionId = UUID.randomUUID();

        CredentialResolutionResult result = CredentialResolutionResult.builder()
                .accessToken("resolved-token-abc")
                .credentialType(CredentialType.PAT)
                .gitAuthMode(GitAuthMode.HTTPS_TOKEN)
                .build();

        ApiResponse<CredentialResolutionResult> apiResponse = ApiResponse.success(result);

        when(webClient.post()).thenReturn(requestBodyUriSpec);
        when(requestBodyUriSpec.uri(anyString())).thenReturn(requestBodySpec);
        when(requestBodySpec.bodyValue(any())).thenReturn(requestHeadersSpec);
        when(requestHeadersSpec.retrieve()).thenReturn(responseSpec);
        when(responseSpec.bodyToMono(any(ParameterizedTypeReference.class)))
                .thenReturn(Mono.just(apiResponse));

        CredentialResolutionResult resolved = credentialClient.resolveCredentials(
                userId, connectionId, CredentialPurpose.PLATFORM_API);

        assertNotNull(resolved);
        assertEquals("resolved-token-abc", resolved.getAccessToken());
        assertEquals(CredentialType.PAT, resolved.getCredentialType());
        assertEquals(GitAuthMode.HTTPS_TOKEN, resolved.getGitAuthMode());
    }

    @Test
    void should_resolveCredentials_forGitClone() {
        UUID userId = UUID.randomUUID();
        UUID connectionId = UUID.randomUUID();

        CredentialResolutionResult result = CredentialResolutionResult.builder()
                .accessToken("clone-token")
                .sshPrivateKey("ssh-key-content")
                .credentialType(CredentialType.DEPLOY_KEY)
                .gitAuthMode(GitAuthMode.SSH_KEY)
                .build();

        ApiResponse<CredentialResolutionResult> apiResponse = ApiResponse.success(result);

        when(webClient.post()).thenReturn(requestBodyUriSpec);
        when(requestBodyUriSpec.uri(anyString())).thenReturn(requestBodySpec);
        when(requestBodySpec.bodyValue(any())).thenReturn(requestHeadersSpec);
        when(requestHeadersSpec.retrieve()).thenReturn(responseSpec);
        when(responseSpec.bodyToMono(any(ParameterizedTypeReference.class)))
                .thenReturn(Mono.just(apiResponse));

        CredentialResolutionResult resolved = credentialClient.resolveCredentials(
                userId, connectionId, CredentialPurpose.GIT_CLONE);

        assertNotNull(resolved);
        assertEquals("ssh-key-content", resolved.getSshPrivateKey());
        assertEquals(CredentialType.DEPLOY_KEY, resolved.getCredentialType());
        assertEquals(GitAuthMode.SSH_KEY, resolved.getGitAuthMode());
    }

    @Test
    void should_throwException_when_unsuccessfulResponse() {
        UUID userId = UUID.randomUUID();
        UUID connectionId = UUID.randomUUID();

        ApiResponse<CredentialResolutionResult> apiResponse = ApiResponse.error("No credentials found");

        when(webClient.post()).thenReturn(requestBodyUriSpec);
        when(requestBodyUriSpec.uri(anyString())).thenReturn(requestBodySpec);
        when(requestBodySpec.bodyValue(any())).thenReturn(requestHeadersSpec);
        when(requestHeadersSpec.retrieve()).thenReturn(responseSpec);
        when(responseSpec.bodyToMono(any(ParameterizedTypeReference.class)))
                .thenReturn(Mono.just(apiResponse));

        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> credentialClient.resolveCredentials(userId, connectionId, CredentialPurpose.PLATFORM_API));

        assertTrue(ex.getMessage().contains("Credential resolution"));
    }

    @Test
    void should_throwException_when_httpErrorResponse() {
        UUID userId = UUID.randomUUID();
        UUID connectionId = UUID.randomUUID();

        when(webClient.post()).thenReturn(requestBodyUriSpec);
        when(requestBodyUriSpec.uri(anyString())).thenReturn(requestBodySpec);
        when(requestBodySpec.bodyValue(any())).thenReturn(requestHeadersSpec);
        when(requestHeadersSpec.retrieve()).thenReturn(responseSpec);
        when(responseSpec.bodyToMono(any(ParameterizedTypeReference.class)))
                .thenReturn(Mono.error(WebClientResponseException.create(
                        500, "Internal Server Error", null,
                        "server error".getBytes(StandardCharsets.UTF_8), StandardCharsets.UTF_8)));

        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> credentialClient.resolveCredentials(userId, connectionId, CredentialPurpose.GIT_PUSH));

        assertTrue(ex.getMessage().contains("Credential resolution failed"));
    }

    @Test
    void should_throwException_when_nullResponseBody() {
        UUID userId = UUID.randomUUID();
        UUID connectionId = UUID.randomUUID();

        when(webClient.post()).thenReturn(requestBodyUriSpec);
        when(requestBodyUriSpec.uri(anyString())).thenReturn(requestBodySpec);
        when(requestBodySpec.bodyValue(any())).thenReturn(requestHeadersSpec);
        when(requestHeadersSpec.retrieve()).thenReturn(responseSpec);
        when(responseSpec.bodyToMono(any(ParameterizedTypeReference.class)))
                .thenReturn(Mono.empty());

        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> credentialClient.resolveCredentials(userId, connectionId, CredentialPurpose.FULL));

        assertTrue(ex.getMessage().contains("Credential resolution"));
    }
}
