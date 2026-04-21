package com.squadron.agent.ephemeral;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class OpenCodeContainerClientTest {

    private HttpClient httpClient;
    private HttpResponse<String> httpResponse;
    private OpenCodeContainerClient client;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @SuppressWarnings("unchecked")
    @BeforeEach
    void setUp() {
        httpClient = mock(HttpClient.class);
        httpResponse = mock(HttpResponse.class);
        client = new OpenCodeContainerClient(
                "http://10.0.0.1:4096", "Basic dGVzdDp0ZXN0",
                httpClient, objectMapper);
    }

    @SuppressWarnings("unchecked")
    private void mockSend() throws Exception {
        when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenReturn(httpResponse);
    }

    @SuppressWarnings("unchecked")
    private void mockSendThrows(Exception ex) throws Exception {
        when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenThrow(ex);
    }

    @Test
    void should_returnTrue_whenHealthCheckSucceeds() throws Exception {
        when(httpResponse.statusCode()).thenReturn(200);
        when(httpResponse.body()).thenReturn("{\"healthy\": true}");
        mockSend();

        assertTrue(client.isHealthy());
    }

    @Test
    void should_returnFalse_whenHealthCheckReturnsUnhealthy() throws Exception {
        when(httpResponse.statusCode()).thenReturn(200);
        when(httpResponse.body()).thenReturn("{\"healthy\": false}");
        mockSend();

        assertFalse(client.isHealthy());
    }

    @Test
    void should_returnFalse_whenHealthCheckReturnsNon200() throws Exception {
        when(httpResponse.statusCode()).thenReturn(503);
        mockSend();

        assertFalse(client.isHealthy());
    }

    @Test
    void should_returnFalse_whenHealthCheckThrowsException() throws Exception {
        mockSendThrows(new RuntimeException("Connection refused"));

        assertFalse(client.isHealthy());
    }

    @Test
    void should_createSession_successfully() throws Exception {
        when(httpResponse.statusCode()).thenReturn(201);
        when(httpResponse.body()).thenReturn("{\"id\": \"sess-123\"}");
        mockSend();

        String sessionId = client.createSession("Test Session");
        assertEquals("sess-123", sessionId);
    }

    @Test
    void should_createSession_withNullTitle() throws Exception {
        when(httpResponse.statusCode()).thenReturn(200);
        when(httpResponse.body()).thenReturn("{\"id\": \"sess-456\"}");
        mockSend();

        String sessionId = client.createSession(null);
        assertEquals("sess-456", sessionId);
    }

    @Test
    void should_throwException_whenCreateSessionFails() throws Exception {
        when(httpResponse.statusCode()).thenReturn(500);
        when(httpResponse.body()).thenReturn("Internal Server Error");
        mockSend();

        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> client.createSession("Test"));
        assertTrue(ex.getMessage().contains("Failed to create OpenCode session"));
    }

    @Test
    void should_sendMessage_andParseResponse() throws Exception {
        String responseJson = """
                {
                    "parts": [
                        {"type": "text", "text": "Hello world"},
                        {"type": "tool-invocation", "toolName": "bash"}
                    ],
                    "info": {
                        "id": "msg-1",
                        "tokens": {"input": 100, "output": 50}
                    }
                }
                """;
        when(httpResponse.statusCode()).thenReturn(200);
        when(httpResponse.body()).thenReturn(responseJson);
        mockSend();

        OpenCodeContainerClient.OpenCodeResponse response =
                client.sendMessage("sess-1", "Do something", null);

        assertEquals("Hello world", response.getContent());
        assertEquals("msg-1", response.getMessageId());
        assertEquals(100, response.getInputTokens());
        assertEquals(50, response.getOutputTokens());
        assertEquals(1, response.getToolsUsed());
    }

    @Test
    void should_sendMessage_withSystemPrompt() throws Exception {
        when(httpResponse.statusCode()).thenReturn(200);
        when(httpResponse.body()).thenReturn("{\"parts\": [{\"type\": \"text\", \"text\": \"ok\"}]}");
        mockSend();

        OpenCodeContainerClient.OpenCodeResponse response =
                client.sendMessage("sess-1", "test", "You are helpful");
        assertEquals("ok", response.getContent());
    }

    @Test
    void should_throwException_whenSendMessageFails() throws Exception {
        when(httpResponse.statusCode()).thenReturn(500);
        when(httpResponse.body()).thenReturn("error");
        mockSend();

        assertThrows(RuntimeException.class,
                () -> client.sendMessage("sess-1", "test", null));
    }

    @Test
    void should_sendMessageAsync_successfully() throws Exception {
        when(httpResponse.statusCode()).thenReturn(200);
        mockSend();

        assertDoesNotThrow(() -> client.sendMessageAsync("sess-1", "test", null));
    }

    @Test
    void should_throwException_whenAsyncSendFails() throws Exception {
        when(httpResponse.statusCode()).thenReturn(500);
        mockSend();

        assertThrows(RuntimeException.class,
                () -> client.sendMessageAsync("sess-1", "test", null));
    }

    @Test
    void should_getMessages_successfully() throws Exception {
        when(httpResponse.statusCode()).thenReturn(200);
        when(httpResponse.body()).thenReturn("[{\"id\": \"msg-1\"}]");
        mockSend();

        String messages = client.getMessages("sess-1");
        assertEquals("[{\"id\": \"msg-1\"}]", messages);
    }

    @Test
    void should_throwException_whenGetMessagesFails() throws Exception {
        when(httpResponse.statusCode()).thenReturn(404);
        mockSend();

        assertThrows(RuntimeException.class, () -> client.getMessages("sess-1"));
    }

    @Test
    void should_abortSession_withoutThrowing() throws Exception {
        mockSend();
        assertDoesNotThrow(() -> client.abortSession("sess-1"));
    }

    @Test
    void should_abortSession_swallowException() throws Exception {
        mockSendThrows(new RuntimeException("Connection refused"));
        assertDoesNotThrow(() -> client.abortSession("sess-1"));
    }

    @Test
    void should_deleteSession_withoutThrowing() throws Exception {
        mockSend();
        assertDoesNotThrow(() -> client.deleteSession("sess-1"));
    }

    @Test
    void should_deleteSession_swallowException() throws Exception {
        mockSendThrows(new RuntimeException("Connection refused"));
        assertDoesNotThrow(() -> client.deleteSession("sess-1"));
    }

    @Test
    void should_returnBaseUrl() {
        assertEquals("http://10.0.0.1:4096", client.getBaseUrl());
    }

    @Test
    void should_constructWithIpAndPort() {
        OpenCodeContainerClient c = new OpenCodeContainerClient(
                "172.17.0.5", 4096, "user", "pass");
        assertEquals("http://172.17.0.5:4096", c.getBaseUrl());
    }

    @Test
    void should_parseResponse_withNoParts() throws Exception {
        when(httpResponse.statusCode()).thenReturn(200);
        when(httpResponse.body()).thenReturn("{}");
        mockSend();

        OpenCodeContainerClient.OpenCodeResponse response =
                client.sendMessage("sess-1", "test", null);
        assertEquals("", response.getContent());
        assertEquals(0, response.getToolsUsed());
    }

    @Test
    void should_parseResponse_withMultipleTextParts() throws Exception {
        String responseJson = """
                {
                    "parts": [
                        {"type": "text", "text": "Hello "},
                        {"type": "text", "text": "world"}
                    ]
                }
                """;
        when(httpResponse.statusCode()).thenReturn(200);
        when(httpResponse.body()).thenReturn(responseJson);
        mockSend();

        OpenCodeContainerClient.OpenCodeResponse response =
                client.sendMessage("sess-1", "test", null);
        assertEquals("Hello world", response.getContent());
    }

    @Test
    void should_countMultipleToolInvocations() throws Exception {
        String responseJson = """
                {
                    "parts": [
                        {"type": "tool-invocation", "toolName": "read"},
                        {"type": "text", "text": "done"},
                        {"type": "tool-invocation", "toolName": "bash"},
                        {"type": "tool-invocation", "toolName": "write"}
                    ]
                }
                """;
        when(httpResponse.statusCode()).thenReturn(200);
        when(httpResponse.body()).thenReturn(responseJson);
        mockSend();

        OpenCodeContainerClient.OpenCodeResponse response =
                client.sendMessage("sess-1", "test", null);
        assertEquals(3, response.getToolsUsed());
        assertEquals("done", response.getContent());
    }
}
