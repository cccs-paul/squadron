package com.squadron.agent.tool.builtin;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Mono;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class WorkspaceClientTest {

    @Mock
    private WebClient webClient;

    @Mock
    private WebClient.RequestBodyUriSpec requestBodyUriSpec;

    @Mock
    private WebClient.RequestBodySpec requestBodySpec;

    @Mock
    private WebClient.RequestHeadersSpec requestHeadersSpec;

    @Mock
    private WebClient.RequestHeadersUriSpec requestHeadersUriSpec;

    @Mock
    private WebClient.ResponseSpec responseSpec;

    private WorkspaceClient workspaceClient;

    @BeforeEach
    void setUp() {
        workspaceClient = new WorkspaceClient(webClient);
    }

    @Test
    void should_executeCommand_when_validWorkspaceAndCommand() {
        UUID workspaceId = UUID.randomUUID();

        Map<String, Object> responseData = Map.of(
                "data", Map.of(
                        "exitCode", 0,
                        "stdout", "hello world",
                        "stderr", ""
                )
        );

        when(webClient.post()).thenReturn(requestBodyUriSpec);
        when(requestBodyUriSpec.uri(anyString(), any(Object[].class))).thenReturn(requestBodySpec);
        when(requestBodySpec.contentType(MediaType.APPLICATION_JSON)).thenReturn(requestBodySpec);
        when(requestBodySpec.bodyValue(any())).thenReturn(requestHeadersSpec);
        when(requestHeadersSpec.retrieve()).thenReturn(responseSpec);
        when(responseSpec.bodyToMono(any(ParameterizedTypeReference.class)))
                .thenReturn(Mono.just(responseData));

        ExecResultDto result = workspaceClient.exec(workspaceId, "bash", "-c", "echo hello world");

        assertNotNull(result);
        assertEquals(0, result.getExitCode());
        assertEquals("hello world", result.getStdout());
        assertEquals("", result.getStderr());
    }

    @Test
    void should_handleNonZeroExitCode_when_commandFails() {
        UUID workspaceId = UUID.randomUUID();

        Map<String, Object> responseData = Map.of(
                "data", Map.of(
                        "exitCode", 1,
                        "stdout", "",
                        "stderr", "file not found"
                )
        );

        when(webClient.post()).thenReturn(requestBodyUriSpec);
        when(requestBodyUriSpec.uri(anyString(), any(Object[].class))).thenReturn(requestBodySpec);
        when(requestBodySpec.contentType(MediaType.APPLICATION_JSON)).thenReturn(requestBodySpec);
        when(requestBodySpec.bodyValue(any())).thenReturn(requestHeadersSpec);
        when(requestHeadersSpec.retrieve()).thenReturn(responseSpec);
        when(responseSpec.bodyToMono(any(ParameterizedTypeReference.class)))
                .thenReturn(Mono.just(responseData));

        ExecResultDto result = workspaceClient.exec(workspaceId, "bash", "-c", "cat /missing");

        assertNotNull(result);
        assertEquals(1, result.getExitCode());
        assertEquals("file not found", result.getStderr());
    }

    @Test
    void should_throwException_when_execGetsEmptyResponse() {
        UUID workspaceId = UUID.randomUUID();

        Map<String, Object> emptyResponse = Map.of("data", Map.of());

        when(webClient.post()).thenReturn(requestBodyUriSpec);
        when(requestBodyUriSpec.uri(anyString(), any(Object[].class))).thenReturn(requestBodySpec);
        when(requestBodySpec.contentType(MediaType.APPLICATION_JSON)).thenReturn(requestBodySpec);
        when(requestBodySpec.bodyValue(any())).thenReturn(requestHeadersSpec);
        when(requestHeadersSpec.retrieve()).thenReturn(responseSpec);
        when(responseSpec.bodyToMono(any(ParameterizedTypeReference.class)))
                .thenReturn(Mono.just(emptyResponse));

        // Should still succeed — missing fields default to 0 / ""
        ExecResultDto result = workspaceClient.exec(workspaceId, "bash", "-c", "true");
        assertNotNull(result);
        assertEquals(0, result.getExitCode());
        assertEquals("", result.getStdout());
        assertEquals("", result.getStderr());
    }

    @Test
    void should_throwException_when_execGetsNullDataResponse() {
        UUID workspaceId = UUID.randomUUID();

        Map<String, Object> nullDataResponse = Map.of("status", "error");

        when(webClient.post()).thenReturn(requestBodyUriSpec);
        when(requestBodyUriSpec.uri(anyString(), any(Object[].class))).thenReturn(requestBodySpec);
        when(requestBodySpec.contentType(MediaType.APPLICATION_JSON)).thenReturn(requestBodySpec);
        when(requestBodySpec.bodyValue(any())).thenReturn(requestHeadersSpec);
        when(requestHeadersSpec.retrieve()).thenReturn(responseSpec);
        when(responseSpec.bodyToMono(any(ParameterizedTypeReference.class)))
                .thenReturn(Mono.just(nullDataResponse));

        assertThrows(WorkspaceClient.WorkspaceClientException.class,
                () -> workspaceClient.exec(workspaceId, "bash", "-c", "true"));
    }

    @Test
    void should_throwException_when_execReceivesHttpError() {
        UUID workspaceId = UUID.randomUUID();

        when(webClient.post()).thenReturn(requestBodyUriSpec);
        when(requestBodyUriSpec.uri(anyString(), any(Object[].class))).thenReturn(requestBodySpec);
        when(requestBodySpec.contentType(MediaType.APPLICATION_JSON)).thenReturn(requestBodySpec);
        when(requestBodySpec.bodyValue(any())).thenReturn(requestHeadersSpec);
        when(requestHeadersSpec.retrieve()).thenReturn(responseSpec);
        when(responseSpec.bodyToMono(any(ParameterizedTypeReference.class)))
                .thenReturn(Mono.error(WebClientResponseException.create(
                        500, "Internal Server Error", null,
                        "workspace error".getBytes(StandardCharsets.UTF_8), StandardCharsets.UTF_8)));

        assertThrows(WorkspaceClient.WorkspaceClientException.class,
                () -> workspaceClient.exec(workspaceId, "bash", "-c", "true"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void should_writeFile_when_validInput() {
        UUID workspaceId = UUID.randomUUID();
        byte[] content = "hello content".getBytes(StandardCharsets.UTF_8);

        when(webClient.post()).thenReturn(requestBodyUriSpec);
        when(requestBodyUriSpec.uri(any(Function.class))).thenReturn(requestBodySpec);
        when(requestBodySpec.contentType(MediaType.APPLICATION_OCTET_STREAM)).thenReturn(requestBodySpec);
        when(requestBodySpec.bodyValue(content)).thenReturn(requestHeadersSpec);
        when(requestHeadersSpec.retrieve()).thenReturn(responseSpec);
        when(responseSpec.bodyToMono(Void.class)).thenReturn(Mono.empty());

        assertDoesNotThrow(() -> workspaceClient.writeFile(workspaceId, "/workspace/test.txt", content));
    }

    @Test
    @SuppressWarnings("unchecked")
    void should_throwException_when_writeFileHttpError() {
        UUID workspaceId = UUID.randomUUID();
        byte[] content = "hello".getBytes(StandardCharsets.UTF_8);

        when(webClient.post()).thenReturn(requestBodyUriSpec);
        when(requestBodyUriSpec.uri(any(Function.class))).thenReturn(requestBodySpec);
        when(requestBodySpec.contentType(MediaType.APPLICATION_OCTET_STREAM)).thenReturn(requestBodySpec);
        when(requestBodySpec.bodyValue(content)).thenReturn(requestHeadersSpec);
        when(requestHeadersSpec.retrieve()).thenReturn(responseSpec);
        when(responseSpec.bodyToMono(Void.class))
                .thenReturn(Mono.error(WebClientResponseException.create(
                        404, "Not Found", null,
                        "not found".getBytes(StandardCharsets.UTF_8), StandardCharsets.UTF_8)));

        assertThrows(WorkspaceClient.WorkspaceClientException.class,
                () -> workspaceClient.writeFile(workspaceId, "/workspace/test.txt", content));
    }

    @Test
    @SuppressWarnings("unchecked")
    void should_readFile_when_validPath() {
        UUID workspaceId = UUID.randomUUID();
        byte[] expectedContent = "file content bytes".getBytes(StandardCharsets.UTF_8);

        when(webClient.get()).thenReturn(requestHeadersUriSpec);
        when(requestHeadersUriSpec.uri(any(Function.class))).thenReturn(requestHeadersSpec);
        when(requestHeadersSpec.retrieve()).thenReturn(responseSpec);
        when(responseSpec.bodyToMono(byte[].class)).thenReturn(Mono.just(expectedContent));

        byte[] result = workspaceClient.readFile(workspaceId, "/workspace/test.txt");

        assertNotNull(result);
        assertArrayEquals(expectedContent, result);
    }

    @Test
    @SuppressWarnings("unchecked")
    void should_throwException_when_readFileHttpError() {
        UUID workspaceId = UUID.randomUUID();

        when(webClient.get()).thenReturn(requestHeadersUriSpec);
        when(requestHeadersUriSpec.uri(any(Function.class))).thenReturn(requestHeadersSpec);
        when(requestHeadersSpec.retrieve()).thenReturn(responseSpec);
        when(responseSpec.bodyToMono(byte[].class))
                .thenReturn(Mono.error(WebClientResponseException.create(
                        404, "Not Found", null,
                        "file not found".getBytes(StandardCharsets.UTF_8), StandardCharsets.UTF_8)));

        assertThrows(WorkspaceClient.WorkspaceClientException.class,
                () -> workspaceClient.readFile(workspaceId, "/workspace/missing.txt"));
    }
}
