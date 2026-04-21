package com.squadron.agent.ephemeral;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Base64;
import java.util.List;
import java.util.Map;

/**
 * HTTP client for communicating with an OpenCode server running inside an
 * ephemeral container. Talks to the OpenCode REST API (OpenAPI 3.1 spec).
 *
 * <p>Each instance targets a single container at a specific base URL.</p>
 */
public class OpenCodeContainerClient {

    private static final Logger log = LoggerFactory.getLogger(OpenCodeContainerClient.class);

    private final String baseUrl;
    private final String authHeader;
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;

    /**
     * Creates a client for the OpenCode server at the given URL.
     *
     * @param containerIp the IP address of the container on the Docker/K8s network
     * @param port        the port OpenCode server listens on (default 4096)
     * @param username    HTTP basic auth username
     * @param password    HTTP basic auth password
     */
    public OpenCodeContainerClient(String containerIp, int port, String username, String password) {
        this.baseUrl = "http://" + containerIp + ":" + port;
        this.authHeader = "Basic " + Base64.getEncoder().encodeToString(
                (username + ":" + password).getBytes());
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
        this.objectMapper = new ObjectMapper();
    }

    /** Constructor for testing with a custom HttpClient. */
    OpenCodeContainerClient(String baseUrl, String authHeader,
                             HttpClient httpClient, ObjectMapper objectMapper) {
        this.baseUrl = baseUrl;
        this.authHeader = authHeader;
        this.httpClient = httpClient;
        this.objectMapper = objectMapper;
    }

    /**
     * Checks if the OpenCode server is healthy and responding.
     *
     * @return true if the server returns a healthy response
     */
    public boolean isHealthy() {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/global/health"))
                    .header("Authorization", authHeader)
                    .timeout(Duration.ofSeconds(5))
                    .GET()
                    .build();

            HttpResponse<String> response = httpClient.send(request,
                    HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 200) {
                JsonNode body = objectMapper.readTree(response.body());
                return body.has("healthy") && body.get("healthy").asBoolean();
            }
            return false;
        } catch (Exception e) {
            log.trace("Health check failed for {}: {}", baseUrl, e.getMessage());
            return false;
        }
    }

    /**
     * Creates a new conversation session in OpenCode.
     *
     * @param title optional session title
     * @return the session ID
     */
    public String createSession(String title) throws Exception {
        Map<String, Object> body = title != null ? Map.of("title", title) : Map.of();
        String jsonBody = objectMapper.writeValueAsString(body);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/session"))
                .header("Authorization", authHeader)
                .header("Content-Type", "application/json")
                .timeout(Duration.ofSeconds(30))
                .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
                .build();

        HttpResponse<String> response = httpClient.send(request,
                HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() != 200 && response.statusCode() != 201) {
            throw new RuntimeException("Failed to create OpenCode session: HTTP "
                    + response.statusCode() + " — " + response.body());
        }

        JsonNode session = objectMapper.readTree(response.body());
        return session.get("id").asText();
    }

    /**
     * Sends a message to an OpenCode session and waits for the complete response.
     * This is a blocking call — the agent may invoke tools (read, write, bash)
     * inside the container before responding.
     *
     * @param sessionId the OpenCode session ID
     * @param message   the user message
     * @param systemPrompt optional system instructions (null to use container config)
     * @return the agent's response containing message info and parts
     */
    public OpenCodeResponse sendMessage(String sessionId, String message,
                                         String systemPrompt) throws Exception {
        Map<String, Object> body = new java.util.HashMap<>();
        body.put("parts", List.of(Map.of("type", "text", "text", message)));
        if (systemPrompt != null && !systemPrompt.isBlank()) {
            body.put("system", systemPrompt);
        }

        String jsonBody = objectMapper.writeValueAsString(body);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/session/" + sessionId + "/message"))
                .header("Authorization", authHeader)
                .header("Content-Type", "application/json")
                .timeout(Duration.ofMinutes(5)) // Agents may take time (tool calls, etc.)
                .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
                .build();

        HttpResponse<String> response = httpClient.send(request,
                HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() != 200) {
            throw new RuntimeException("Failed to send message to OpenCode session "
                    + sessionId + ": HTTP " + response.statusCode() + " — " + response.body());
        }

        return parseResponse(response.body());
    }

    /**
     * Sends a message asynchronously (fire-and-forget). Use with SSE events
     * to stream the response.
     *
     * @param sessionId the OpenCode session ID
     * @param message   the user message
     * @param systemPrompt optional system instructions
     */
    public void sendMessageAsync(String sessionId, String message,
                                  String systemPrompt) throws Exception {
        Map<String, Object> body = new java.util.HashMap<>();
        body.put("parts", List.of(Map.of("type", "text", "text", message)));
        if (systemPrompt != null && !systemPrompt.isBlank()) {
            body.put("system", systemPrompt);
        }

        String jsonBody = objectMapper.writeValueAsString(body);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/session/" + sessionId + "/prompt_async"))
                .header("Authorization", authHeader)
                .header("Content-Type", "application/json")
                .timeout(Duration.ofSeconds(30))
                .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
                .build();

        HttpResponse<String> response = httpClient.send(request,
                HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() != 200 && response.statusCode() != 204) {
            throw new RuntimeException("Failed to send async message to OpenCode session "
                    + sessionId + ": HTTP " + response.statusCode());
        }
    }

    /**
     * Gets messages from an OpenCode session.
     *
     * @param sessionId the OpenCode session ID
     * @return the raw JSON response body
     */
    public String getMessages(String sessionId) throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/session/" + sessionId + "/message"))
                .header("Authorization", authHeader)
                .timeout(Duration.ofSeconds(30))
                .GET()
                .build();

        HttpResponse<String> response = httpClient.send(request,
                HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() != 200) {
            throw new RuntimeException("Failed to get messages from OpenCode session "
                    + sessionId + ": HTTP " + response.statusCode());
        }

        return response.body();
    }

    /**
     * Aborts a running OpenCode session.
     *
     * @param sessionId the OpenCode session ID
     */
    public void abortSession(String sessionId) {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/session/" + sessionId + "/abort"))
                    .header("Authorization", authHeader)
                    .header("Content-Type", "application/json")
                    .timeout(Duration.ofSeconds(10))
                    .POST(HttpRequest.BodyPublishers.noBody())
                    .build();

            httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        } catch (Exception e) {
            log.warn("Failed to abort OpenCode session {}: {}", sessionId, e.getMessage());
        }
    }

    /**
     * Deletes an OpenCode session.
     *
     * @param sessionId the OpenCode session ID
     */
    public void deleteSession(String sessionId) {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/session/" + sessionId))
                    .header("Authorization", authHeader)
                    .timeout(Duration.ofSeconds(10))
                    .DELETE()
                    .build();

            httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        } catch (Exception e) {
            log.warn("Failed to delete OpenCode session {}: {}", sessionId, e.getMessage());
        }
    }

    public String getBaseUrl() {
        return baseUrl;
    }

    // --- Response parsing ---

    private OpenCodeResponse parseResponse(String jsonBody) throws Exception {
        JsonNode root = objectMapper.readTree(jsonBody);

        OpenCodeResponse response = new OpenCodeResponse();

        // Extract text content from parts
        StringBuilder textContent = new StringBuilder();
        JsonNode parts = root.get("parts");
        if (parts != null && parts.isArray()) {
            for (JsonNode part : parts) {
                String type = part.has("type") ? part.get("type").asText() : "";
                if ("text".equals(type) && part.has("text")) {
                    textContent.append(part.get("text").asText());
                } else if ("tool-invocation".equals(type)) {
                    // Tool invocations indicate the agent used tools (read, write, bash, etc.)
                    String toolName = part.has("toolName") ? part.get("toolName").asText() : "unknown";
                    response.setToolsUsed(response.getToolsUsed() + 1);
                    log.debug("Agent used tool: {}", toolName);
                }
            }
        }

        response.setContent(textContent.toString());

        // Extract token usage from message info
        JsonNode info = root.get("info");
        if (info != null) {
            if (info.has("tokens")) {
                JsonNode tokens = info.get("tokens");
                response.setInputTokens(tokens.has("input") ? tokens.get("input").asInt() : 0);
                response.setOutputTokens(tokens.has("output") ? tokens.get("output").asInt() : 0);
            }
            if (info.has("id")) {
                response.setMessageId(info.get("id").asText());
            }
        }

        return response;
    }

    /**
     * Represents a parsed response from the OpenCode server.
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class OpenCodeResponse {
        private String content;
        private String messageId;
        private int inputTokens;
        private int outputTokens;
        @Builder.Default
        private int toolsUsed = 0;
    }
}
