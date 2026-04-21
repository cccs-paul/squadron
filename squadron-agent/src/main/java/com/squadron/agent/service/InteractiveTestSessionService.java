package com.squadron.agent.service;

import com.squadron.agent.dto.AgentConfigDto;
import com.squadron.agent.dto.InteractiveTestMessageRequest;
import com.squadron.agent.dto.InteractiveTestSessionDto;
import com.squadron.agent.dto.InteractiveTestSessionDto.InteractiveTestMessage;
import com.squadron.agent.entity.UserAgentConfig;
import com.squadron.agent.ephemeral.EphemeralContainerConfig;
import com.squadron.agent.ephemeral.EphemeralContainerService;
import com.squadron.agent.ephemeral.OpenCodeContainerClient;
import com.squadron.agent.ephemeral.OpenCodeContainerClient.OpenCodeResponse;
import com.squadron.agent.provider.AgentProvider;
import com.squadron.agent.provider.AgentProviderRegistry;
import com.squadron.agent.provider.ChatMessage;
import com.squadron.agent.repository.UserAgentConfigRepository;
import com.squadron.common.exception.ResourceNotFoundException;
import com.squadron.common.security.TenantScopedLookup;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.FluxSink;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Manages interactive test sessions — ephemeral conversational sessions that let users
 * converse with their configured agents from the "My Agent Squadron" UI.
 *
 * <p>When ephemeral containers are enabled, each session runs inside a real sandbox
 * container with an OpenCode server that provides agentic capabilities (read, write,
 * bash, etc.) backed by the configured LLM provider (local, cloud, or remote).</p>
 *
 * <p>When ephemeral containers are disabled (fallback mode), sessions call the LLM
 * provider directly without sandbox isolation.</p>
 *
 * <p>Sessions are stored in memory with a configurable TTL. They are not persisted
 * to the database — they are purely for testing/evaluation purposes.</p>
 */
@Service
public class InteractiveTestSessionService {

    private static final Logger log = LoggerFactory.getLogger(InteractiveTestSessionService.class);

    /** Maximum session lifetime in milliseconds (30 minutes). */
    private static final long SESSION_TTL_MS = 30 * 60 * 1000L;

    /** Maximum number of concurrent sessions per user. */
    private static final int MAX_SESSIONS_PER_USER = 8;

    /** Maximum number of messages per session. */
    private static final int MAX_MESSAGES_PER_SESSION = 100;

    private final UserAgentConfigRepository agentConfigRepository;
    private final AgentProviderRegistry providerRegistry;
    private final SystemPromptBuilder promptBuilder;
    private final EphemeralContainerService containerService;
    private final EphemeralContainerConfig containerConfig;

    /** In-memory session store: sessionId -> SessionState. */
    private final Map<UUID, SessionState> sessions = new ConcurrentHashMap<>();

    public InteractiveTestSessionService(
            UserAgentConfigRepository agentConfigRepository,
            AgentProviderRegistry providerRegistry,
            SystemPromptBuilder promptBuilder,
            EphemeralContainerService containerService,
            EphemeralContainerConfig containerConfig) {
        this.agentConfigRepository = agentConfigRepository;
        this.providerRegistry = providerRegistry;
        this.promptBuilder = promptBuilder;
        this.containerService = containerService;
        this.containerConfig = containerConfig;
    }

    /**
     * Starts a new interactive test session for the given agent configuration.
     * If ephemeral containers are enabled, provisions a real sandbox container
     * with an OpenCode server.
     */
    public InteractiveTestSessionDto startSession(UUID tenantId, UUID userId, UUID agentConfigId) {
        // Validate agent config belongs to this user
        UserAgentConfig agentConfig = TenantScopedLookup.findByIdScoped(
                agentConfigId,
                agentConfigRepository::findById,
                agentConfigRepository::findByIdAndTenantId,
                () -> new ResourceNotFoundException("UserAgentConfig", agentConfigId));

        if (!agentConfig.getTenantId().equals(tenantId) || !agentConfig.getUserId().equals(userId)) {
            throw new ResourceNotFoundException("UserAgentConfig", agentConfigId);
        }

        // Enforce per-user session limit
        long userSessionCount = sessions.values().stream()
                .filter(s -> s.userId.equals(userId))
                .count();
        if (userSessionCount >= MAX_SESSIONS_PER_USER) {
            throw new IllegalStateException(
                    "Maximum number of concurrent interactive test sessions (" + MAX_SESSIONS_PER_USER + ") reached. "
                    + "Close an existing session before starting a new one.");
        }

        // Build agent config DTO
        AgentConfigDto configDto = AgentConfigDto.builder()
                .provider(agentConfig.getProvider())
                .model(agentConfig.getModel())
                .maxTokens(agentConfig.getMaxTokens())
                .temperature(agentConfig.getTemperature())
                .systemPromptOverride(agentConfig.getSystemPromptOverride())
                .baseUrl(agentConfig.getBaseUrl())
                .hostingType(agentConfig.getHostingType())
                .build();

        // Build the system prompt
        String systemPrompt = buildInteractiveSystemPrompt(agentConfig);

        UUID sessionId = UUID.randomUUID();

        // Create session state
        SessionState state = new SessionState(
                sessionId, tenantId, userId, agentConfigId,
                agentConfig.getAgentName(),
                agentConfig.getProvider(),
                agentConfig.getModel(),
                configDto, systemPrompt,
                null // containerId set below
        );

        // Start ephemeral container or fall back to direct LLM
        if (containerConfig.isEnabled()) {
            startWithEphemeralContainer(state, agentConfig);
        } else {
            startWithDirectProvider(state);
        }

        sessions.put(sessionId, state);
        log.info("Interactive test session {} started for agent '{}' by user {} (ephemeral={})",
                sessionId, agentConfig.getAgentName(), userId, containerConfig.isEnabled());

        return toDto(state);
    }

    /**
     * Sends a user message to an interactive test session and streams the agent's response.
     */
    public Flux<ServerSentEvent<InteractiveTestSessionDto>> sendMessage(
            UUID tenantId, UUID userId, InteractiveTestMessageRequest request) {

        SessionState state = getValidatedSession(request.getSessionId(), tenantId, userId);

        if (state.messages.size() >= MAX_MESSAGES_PER_SESSION) {
            throw new IllegalStateException(
                    "Maximum messages per session (" + MAX_MESSAGES_PER_SESSION + ") reached. "
                    + "Close this session and start a new one.");
        }

        // Add user message
        InteractiveTestMessage userMsg = InteractiveTestMessage.builder()
                .id(UUID.randomUUID())
                .role("USER")
                .content(request.getMessage())
                .createdAt(Instant.now())
                .build();
        state.messages.add(userMsg);
        state.status = "STREAMING";
        state.lastActivityAt = Instant.now();

        if (containerConfig.isEnabled() && state.openCodeSessionId != null) {
            return sendViaEphemeralContainer(state, request.getMessage());
        } else {
            return sendViaDirectProvider(state, request.getMessage());
        }
    }

    /**
     * Gets the current state of an interactive test session.
     */
    public InteractiveTestSessionDto getSession(UUID sessionId, UUID tenantId, UUID userId) {
        SessionState state = getValidatedSession(sessionId, tenantId, userId);
        return toDto(state);
    }

    /**
     * Closes an interactive test session and frees resources (including container).
     */
    public void closeSession(UUID sessionId, UUID tenantId, UUID userId) {
        SessionState state = getValidatedSession(sessionId, tenantId, userId);

        // Destroy the ephemeral container if active
        if (containerConfig.isEnabled()) {
            containerService.stopContainer(sessionId);
        }

        sessions.remove(sessionId);
        log.info("Interactive test session {} closed by user {} (container {} destroyed)",
                sessionId, userId, state.containerId);
    }

    /**
     * Returns all active interactive test sessions for a user.
     */
    public List<InteractiveTestSessionDto> getUserSessions(UUID tenantId, UUID userId) {
        return sessions.values().stream()
                .filter(s -> s.tenantId.equals(tenantId) && s.userId.equals(userId))
                .map(this::toDto)
                .collect(Collectors.toList());
    }

    /**
     * Scheduled cleanup of expired sessions (runs every 5 minutes).
     */
    @Scheduled(fixedRate = 300_000)
    public void cleanupExpiredSessions() {
        Instant cutoff = Instant.now().minusMillis(SESSION_TTL_MS);
        List<UUID> expired = sessions.entrySet().stream()
                .filter(e -> e.getValue().lastActivityAt.isBefore(cutoff))
                .map(Map.Entry::getKey)
                .collect(Collectors.toList());

        for (UUID sessionId : expired) {
            SessionState state = sessions.remove(sessionId);
            if (state != null && containerConfig.isEnabled()) {
                containerService.stopContainer(sessionId);
            }
            log.info("Expired interactive test session {} (inactive > {} min)", sessionId, SESSION_TTL_MS / 60000);
        }
    }

    // --- Ephemeral container mode ---

    private void startWithEphemeralContainer(SessionState state, UserAgentConfig agentConfig) {
        // Add container lifecycle messages
        state.messages.add(systemMessage("Requesting ephemeral sandbox container for session " + state.sessionId + "..."));
        state.messages.add(systemMessage("Pulling workspace image: " + containerConfig.getImage()));
        state.messages.add(systemMessage("Allocating resources: " + containerConfig.getCpuLimit() + " vCPU, "
                + containerConfig.getMemoryLimit() + " memory"));

        try {
            OpenCodeContainerClient client = containerService.startContainer(
                    state.sessionId, state.tenantId,
                    agentConfig.getProvider(), agentConfig.getModel(),
                    agentConfig.getBaseUrl(), resolveApiKey(agentConfig),
                    agentConfig.getHostingType(), state.systemPrompt);

            String workspaceId = containerService.getWorkspaceId(state.sessionId);
            state.containerId = workspaceId;

            state.messages.add(systemMessage("Container " + workspaceId + " created — OpenCode server starting"));
            state.messages.add(systemMessage("OpenCode server healthy — sandbox environment active"));

            // Create an OpenCode session inside the container
            String openCodeSessionId = client.createSession("Interactive Test: " + agentConfig.getAgentName());
            state.openCodeSessionId = openCodeSessionId;

            state.messages.add(systemMessage(
                    "Interactive test session started with agent '" + agentConfig.getAgentName()
                    + "' (" + agentConfig.getProvider() + "/" + agentConfig.getModel() + ") "
                    + "inside container " + workspaceId + ". "
                    + "The agent has access to tools: read, write, edit, bash, glob, grep. "
                    + "Ask anything — the agent will respond using its configured model."));

        } catch (Exception e) {
            log.error("Failed to start ephemeral container for session {}: {}", state.sessionId, e.getMessage());
            state.messages.add(systemMessage("Failed to start ephemeral container: " + e.getMessage()
                    + ". Falling back to direct provider mode."));
            // Fall back to direct provider mode
            state.containerId = "fallback-" + UUID.randomUUID().toString().substring(0, 8);
            addDirectProviderStartMessage(state);
        }
    }

    private void startWithDirectProvider(SessionState state) {
        state.containerId = "direct-" + UUID.randomUUID().toString().substring(0, 8);
        addDirectProviderStartMessage(state);
    }

    private void addDirectProviderStartMessage(SessionState state) {
        state.messages.add(systemMessage(
                "Interactive test session started with agent '" + state.agentName
                + "' (" + state.provider + "/" + state.model + ") in direct mode. "
                + "You can ask anything — the agent will respond using its configured model and system prompt. "
                + "Note: direct mode does not provide tool access (read, write, bash)."));
    }

    private Flux<ServerSentEvent<InteractiveTestSessionDto>> sendViaEphemeralContainer(
            SessionState state, String userMessage) {

        return Flux.create(sink -> {
            Thread.ofVirtual().name("interactive-container-" + state.sessionId).start(() -> {
                try {
                    // Emit snapshot showing user message (status: STREAMING)
                    emitSnapshot(sink, state);

                    OpenCodeContainerClient client = containerService.getClient(state.sessionId);
                    if (client == null) {
                        throw new RuntimeException("No active container for session " + state.sessionId);
                    }

                    // Send message to OpenCode server (blocking — waits for full response)
                    // The agent may invoke tools inside the container before responding
                    OpenCodeResponse response = client.sendMessage(
                            state.openCodeSessionId, userMessage, null);

                    // Add the agent's response as a message
                    InteractiveTestMessage agentMsg = InteractiveTestMessage.builder()
                            .id(UUID.randomUUID())
                            .role("AGENT")
                            .content(response.getContent())
                            .tokenCount(response.getOutputTokens() > 0
                                    ? response.getOutputTokens()
                                    : response.getContent().length() / 4)
                            .createdAt(Instant.now())
                            .build();
                    state.messages.add(agentMsg);

                    // Add tool usage info if tools were used
                    if (response.getToolsUsed() > 0) {
                        state.messages.add(systemMessage(
                                "Agent used " + response.getToolsUsed() + " tool(s) to process this request."));
                    }

                    state.streamingContent = null;
                    state.status = "ACTIVE";
                    state.lastActivityAt = Instant.now();

                    emitSnapshot(sink, state);
                    sink.complete();

                } catch (Exception e) {
                    handleSendError(state, sink, e);
                }
            });
        }, FluxSink.OverflowStrategy.BUFFER);
    }

    // --- Direct provider mode (fallback) ---

    private Flux<ServerSentEvent<InteractiveTestSessionDto>> sendViaDirectProvider(
            SessionState state, String userMessage) {

        return Flux.create(sink -> {
            Thread.ofVirtual().name("interactive-direct-" + state.sessionId).start(() -> {
                try {
                    // Build chat history from session messages
                    List<ChatMessage> history = state.messages.stream()
                            .filter(m -> "USER".equals(m.getRole()) || "AGENT".equals(m.getRole()))
                            .map(m -> ChatMessage.builder()
                                    .role("USER".equals(m.getRole()) ? "user" : "assistant")
                                    .content(m.getContent())
                                    .build())
                            .collect(Collectors.toList());

                    // Remove the last user message from history (it's the current request)
                    if (!history.isEmpty()) {
                        history.remove(history.size() - 1);
                    }

                    emitSnapshot(sink, state);

                    // Get provider and stream response
                    AgentProvider provider = providerRegistry.getProvider(state.agentConfig.getProvider());
                    StringBuilder fullResponse = new StringBuilder();

                    provider.chatStream(state.systemPrompt, history, userMessage, state.agentConfig)
                            .doOnNext(chunk -> {
                                fullResponse.append(chunk);
                                if (fullResponse.length() % 20 < chunk.length()) {
                                    state.streamingContent = fullResponse.toString();
                                    emitSnapshot(sink, state);
                                }
                            })
                            .blockLast();

                    String completeResponse = fullResponse.toString();
                    int estimatedTokens = completeResponse.length() / 4;

                    InteractiveTestMessage agentMsg = InteractiveTestMessage.builder()
                            .id(UUID.randomUUID())
                            .role("AGENT")
                            .content(completeResponse)
                            .tokenCount(estimatedTokens)
                            .createdAt(Instant.now())
                            .build();
                    state.messages.add(agentMsg);
                    state.streamingContent = null;
                    state.status = "ACTIVE";
                    state.lastActivityAt = Instant.now();

                    emitSnapshot(sink, state);
                    sink.complete();

                } catch (Exception e) {
                    handleSendError(state, sink, e);
                }
            });
        }, FluxSink.OverflowStrategy.BUFFER);
    }

    // --- Helpers ---

    private void handleSendError(SessionState state,
                                  FluxSink<ServerSentEvent<InteractiveTestSessionDto>> sink,
                                  Exception e) {
        log.error("Interactive test error for session {}: {}", state.sessionId, e.getMessage(), e);

        String userMessage = extractUserFriendlyErrorMessage(e, state);
        state.messages.add(systemMessage(userMessage));
        state.streamingContent = null;
        state.status = "ACTIVE";
        state.lastActivityAt = Instant.now();

        emitSnapshot(sink, state);
        sink.complete();
    }

    /**
     * Translates raw exceptions from LLM providers into clear, actionable messages
     * for the user. Handles common failure modes: OOM, connection refused, model
     * not found, auth errors, timeouts, and rate limits.
     */
    private String extractUserFriendlyErrorMessage(Exception e, SessionState state) {
        String raw = buildFullExceptionMessage(e);
        String model = state.agentConfig != null ? state.agentConfig.getModel() : "unknown";
        String provider = state.provider != null ? state.provider : "unknown";

        // Ollama: model requires more memory than available
        if (raw.contains("model requires more system memory")) {
            String needed = extractBetween(raw, "memory (", ")");
            String available = extractBetween(raw, "available (", ")");
            return String.format(
                    "Error: The model '%s' cannot be loaded — it requires %s of memory but only %s is available. "
                    + "Try a smaller model (e.g. a lower quantization or fewer parameters), "
                    + "or free up memory by stopping other services.",
                    model, needed != null ? needed : "more", available != null ? available : "less");
        }

        // Ollama: model not found
        if (raw.contains("model") && (raw.contains("not found") || raw.contains("does not exist"))) {
            return String.format(
                    "Error: The model '%s' was not found on the Ollama server. "
                    + "Run 'ollama pull %s' to download it, or choose a different model in the agent configuration.",
                    model, model);
        }

        // Connection refused (provider not running)
        if (raw.contains("Connection refused")) {
            String baseUrl = state.agentConfig != null ? state.agentConfig.getBaseUrl() : null;
            String target = baseUrl != null ? baseUrl : provider;
            return String.format(
                    "Error: Could not connect to the %s provider at '%s' — connection refused. "
                    + "Ensure the service is running and accessible from the Squadron network.",
                    provider, target);
        }

        // DNS / host not found
        if (raw.contains("UnknownHostException") || raw.contains("nodename nor servname")
                || raw.contains("Name or service not known")) {
            return String.format(
                    "Error: Could not resolve the hostname for the %s provider. "
                    + "Check that the provider URL is correct and the service is reachable.",
                    provider);
        }

        // Timeout
        if (raw.contains("timed out") || raw.contains("TimeoutException") || raw.contains("Read timed out")) {
            return String.format(
                    "Error: The request to the %s provider timed out. "
                    + "The model may be loading or the server is under heavy load. Please try again.",
                    provider);
        }

        // HTTP 401/403 — auth failure
        if (raw.contains("401") || raw.contains("Unauthorized")) {
            return String.format(
                    "Error: Authentication failed for the %s provider. "
                    + "Check that the API key in the agent configuration is valid and has not expired.",
                    provider);
        }
        if (raw.contains("403") || raw.contains("Forbidden")) {
            return String.format(
                    "Error: Access denied by the %s provider. "
                    + "The API key may lack required permissions, or the model '%s' may not be available on your plan.",
                    provider, model);
        }

        // HTTP 429 — rate limit
        if (raw.contains("429") || raw.contains("rate limit") || raw.contains("Too Many Requests")) {
            return String.format(
                    "Error: Rate limited by the %s provider. Please wait a moment and try again.",
                    provider);
        }

        // HTTP 500 from provider (generic)
        if (raw.contains("500 Internal Server Error")) {
            return String.format(
                    "Error: The %s provider returned an internal server error for model '%s'. "
                    + "This is usually a transient issue — please try again. "
                    + "If it persists, check the provider's status or try a different model.",
                    provider, model);
        }

        // SSL/TLS errors
        if (raw.contains("SSLHandshakeException") || raw.contains("PKIX path") || raw.contains("certificate")) {
            return String.format(
                    "Error: SSL/TLS certificate error connecting to the %s provider. "
                    + "If behind a corporate proxy, ensure the CA certificates are correctly configured.",
                    provider);
        }

        // Fallback — include the raw message but prefix with context
        return String.format("Error: Failed to get a response from the %s provider (model: %s). %s",
                provider, model, e.getMessage());
    }

    /**
     * Builds a full exception message including cause chain, so we can match
     * against nested exception messages (e.g., Spring AI wrapping Ollama errors).
     */
    private String buildFullExceptionMessage(Exception e) {
        StringBuilder sb = new StringBuilder();
        Throwable current = e;
        while (current != null) {
            if (sb.length() > 0) sb.append(" -> ");
            sb.append(current.getClass().getSimpleName());
            if (current.getMessage() != null) {
                sb.append(": ").append(current.getMessage());
            }
            current = current.getCause();
        }
        return sb.toString();
    }

    /** Extracts text between two marker strings, or returns null if not found. */
    private String extractBetween(String text, String start, String end) {
        int startIdx = text.indexOf(start);
        if (startIdx < 0) return null;
        startIdx += start.length();
        int endIdx = text.indexOf(end, startIdx);
        if (endIdx < 0) return null;
        return text.substring(startIdx, endIdx);
    }

    private String resolveApiKey(UserAgentConfig agentConfig) {
        // apiKeyRef contains the API key reference — for now, return it directly.
        // In production, this would decrypt via a credential service.
        return agentConfig.getApiKeyRef();
    }

    private SessionState getValidatedSession(UUID sessionId, UUID tenantId, UUID userId) {
        SessionState state = sessions.get(sessionId);
        if (state == null) {
            throw new ResourceNotFoundException("InteractiveTestSession", sessionId);
        }
        if (!state.tenantId.equals(tenantId) || !state.userId.equals(userId)) {
            throw new ResourceNotFoundException("InteractiveTestSession", sessionId);
        }
        return state;
    }

    private InteractiveTestMessage systemMessage(String content) {
        return InteractiveTestMessage.builder()
                .id(UUID.randomUUID()).role("SYSTEM")
                .content(content).createdAt(Instant.now()).build();
    }

    private void emitSnapshot(FluxSink<ServerSentEvent<InteractiveTestSessionDto>> sink, SessionState state) {
        InteractiveTestSessionDto dto = toDto(state);
        sink.next(ServerSentEvent.<InteractiveTestSessionDto>builder()
                .event("snapshot")
                .data(dto)
                .build());
    }

    private InteractiveTestSessionDto toDto(SessionState state) {
        InteractiveTestSessionDto dto = InteractiveTestSessionDto.builder()
                .sessionId(state.sessionId)
                .agentConfigId(state.agentConfigId)
                .agentName(state.agentName)
                .provider(state.provider)
                .model(state.model)
                .status(state.status)
                .containerId(state.containerId)
                .createdAt(state.createdAt)
                .messages(new ArrayList<>(state.messages))
                .build();

        // If there's streaming content, add a temporary "typing" message
        if (state.streamingContent != null && !state.streamingContent.isEmpty()) {
            InteractiveTestMessage streamingMsg = InteractiveTestMessage.builder()
                    .id(UUID.randomUUID())
                    .role("AGENT")
                    .content(state.streamingContent)
                    .createdAt(Instant.now())
                    .build();
            dto.getMessages().add(streamingMsg);
        }

        return dto;
    }

    private String buildInteractiveSystemPrompt(UserAgentConfig agentConfig) {
        if (agentConfig.getSystemPromptOverride() != null && !agentConfig.getSystemPromptOverride().isBlank()) {
            return agentConfig.getSystemPromptOverride();
        }

        String agentType = agentConfig.getAgentType();
        if (agentType == null) agentType = "GENERAL";

        return switch (agentType.toUpperCase()) {
            case "PLANNING" -> promptBuilder.buildPlanningPrompt("Interactive Test", "Help the user evaluate your planning capabilities");
            case "CODING" -> promptBuilder.buildCodingPrompt("Follow the user's instructions", "Interactive Test");
            case "REVIEW" -> promptBuilder.buildReviewPrompt("Review code provided by the user and give feedback");
            case "QA" -> promptBuilder.buildQaPrompt("Help the user test and verify code quality", "Interactive Test");
            default -> "You are a helpful AI assistant for the Squadron platform. "
                    + "This is an interactive test session — the user is evaluating your capabilities. "
                    + "You are running inside an ephemeral sandbox container with access to tools: "
                    + "read, write, edit, bash, glob, grep. "
                    + "Respond helpfully, clearly, and concisely. You can discuss code, architecture, "
                    + "planning, reviews, and any software engineering topic. "
                    + "Format your responses with markdown when appropriate.";
        };
    }

    /**
     * Internal session state held in memory.
     */
    static class SessionState {
        final UUID sessionId;
        final UUID tenantId;
        final UUID userId;
        final UUID agentConfigId;
        final String agentName;
        final String provider;
        final String model;
        final AgentConfigDto agentConfig;
        final String systemPrompt;
        String containerId;
        String openCodeSessionId; // OpenCode session ID inside the container
        final Instant createdAt;
        final List<InteractiveTestMessage> messages;
        String status;
        Instant lastActivityAt;
        volatile String streamingContent;

        SessionState(UUID sessionId, UUID tenantId, UUID userId, UUID agentConfigId,
                     String agentName, String provider, String model,
                     AgentConfigDto agentConfig, String systemPrompt,
                     String containerId) {
            this.sessionId = sessionId;
            this.tenantId = tenantId;
            this.userId = userId;
            this.agentConfigId = agentConfigId;
            this.agentName = agentName;
            this.provider = provider;
            this.model = model;
            this.agentConfig = agentConfig;
            this.systemPrompt = systemPrompt;
            this.containerId = containerId;
            this.createdAt = Instant.now();
            this.lastActivityAt = Instant.now();
            this.messages = new ArrayList<>();
            this.status = "ACTIVE";
        }
    }

    /** Visible for testing. */
    Map<UUID, SessionState> getSessionsMap() {
        return sessions;
    }
}
