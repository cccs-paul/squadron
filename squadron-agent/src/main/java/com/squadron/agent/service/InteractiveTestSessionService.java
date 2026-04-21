package com.squadron.agent.service;

import com.squadron.agent.dto.AgentConfigDto;
import com.squadron.agent.dto.InteractiveTestMessageRequest;
import com.squadron.agent.dto.InteractiveTestSessionDto;
import com.squadron.agent.dto.InteractiveTestSessionDto.InteractiveTestMessage;
import com.squadron.agent.entity.UserAgentConfig;
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
 * Manages interactive test sessions — ephemeral, in-memory conversational sessions
 * that let users converse with their configured agents from the "My Agent Squadron" UI.
 *
 * <p>Unlike automated tests (PLANNING/CODE_GENERATION/CODE_REVIEW), interactive tests
 * allow multi-turn, free-form conversation. The user sends messages and receives
 * streamed LLM responses, identical to the task-based agent chat but without
 * requiring a real task, workspace, or project.</p>
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

    /** In-memory session store: sessionId -> SessionState. */
    private final Map<UUID, SessionState> sessions = new ConcurrentHashMap<>();

    public InteractiveTestSessionService(
            UserAgentConfigRepository agentConfigRepository,
            AgentProviderRegistry providerRegistry,
            SystemPromptBuilder promptBuilder) {
        this.agentConfigRepository = agentConfigRepository;
        this.providerRegistry = providerRegistry;
        this.promptBuilder = promptBuilder;
    }

    /**
     * Starts a new interactive test session for the given agent configuration.
     *
     * @param tenantId        the tenant ID from the security context
     * @param userId          the user ID from the security context
     * @param agentConfigId   the agent configuration to test
     * @return the created session DTO with initial system message
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

        // Generate ephemeral container ID (simulated — same pattern as automated tests)
        String containerId = UUID.randomUUID().toString().substring(0, 12);

        // Create session state
        UUID sessionId = UUID.randomUUID();
        SessionState state = new SessionState(
                sessionId, tenantId, userId, agentConfigId,
                agentConfig.getAgentName(),
                agentConfig.getProvider(),
                agentConfig.getModel(),
                configDto, systemPrompt,
                containerId
        );

        // Add ephemeral container lifecycle messages (simulated — mirrors AgentTestExecutionService)
        state.messages.add(InteractiveTestMessage.builder()
                .id(UUID.randomUUID()).role("SYSTEM")
                .content("Requesting ephemeral sandbox container for session " + sessionId + "...")
                .createdAt(Instant.now()).build());
        state.messages.add(InteractiveTestMessage.builder()
                .id(UUID.randomUUID()).role("SYSTEM")
                .content("Pulling workspace image: squadron-workspace:latest")
                .createdAt(Instant.now()).build());
        state.messages.add(InteractiveTestMessage.builder()
                .id(UUID.randomUUID()).role("SYSTEM")
                .content("Allocating resources: 2 vCPU, 4 GiB memory, 10 GiB ephemeral storage")
                .createdAt(Instant.now()).build());
        state.messages.add(InteractiveTestMessage.builder()
                .id(UUID.randomUUID()).role("SYSTEM")
                .content("Container " + containerId + " created — mounting workspace volume")
                .createdAt(Instant.now()).build());
        state.messages.add(InteractiveTestMessage.builder()
                .id(UUID.randomUUID()).role("SYSTEM")
                .content("Installing language toolchains and dependencies in container " + containerId + "...")
                .createdAt(Instant.now()).build());
        state.messages.add(InteractiveTestMessage.builder()
                .id(UUID.randomUUID()).role("SYSTEM")
                .content("Ephemeral container " + containerId + " is ready — sandbox environment active")
                .createdAt(Instant.now()).build());

        // Add session start message
        InteractiveTestMessage systemMsg = InteractiveTestMessage.builder()
                .id(UUID.randomUUID())
                .role("SYSTEM")
                .content("Interactive test session started with agent '" + agentConfig.getAgentName()
                        + "' (" + agentConfig.getProvider() + "/" + agentConfig.getModel() + ") "
                        + "inside container " + containerId + ". "
                        + "You can ask anything — the agent will respond using its configured model and system prompt. "
                        + "This session simulates the real task environment where agents run in ephemeral containers.")
                .createdAt(Instant.now())
                .build();
        state.messages.add(systemMsg);

        sessions.put(sessionId, state);
        log.info("Interactive test session {} started for agent '{}' by user {}",
                sessionId, agentConfig.getAgentName(), userId);

        return toDto(state);
    }

    /**
     * Sends a user message to an interactive test session and streams the agent's response
     * back via Server-Sent Events.
     *
     * @param tenantId  the tenant ID from the security context
     * @param userId    the user ID from the security context
     * @param request   the message request (sessionId + message)
     * @return a Flux of SSE events containing streamed InteractiveTestMessage chunks
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

        return Flux.create(sink -> {
            Thread.ofVirtual().name("interactive-test-" + state.sessionId).start(() -> {
                try {
                    // Build chat history from session messages
                    List<ChatMessage> history = state.messages.stream()
                            .filter(m -> "USER".equals(m.getRole()) || "AGENT".equals(m.getRole()))
                            .map(m -> ChatMessage.builder()
                                    .role("USER".equals(m.getRole()) ? "user" : "assistant")
                                    .content(m.getContent())
                                    .build())
                            .collect(Collectors.toList());

                    // Remove the last user message from history since it's the current request
                    String currentUserMessage = request.getMessage();
                    if (!history.isEmpty()) {
                        history.remove(history.size() - 1);
                    }

                    // Emit snapshot with user message added (status: STREAMING)
                    emitSnapshot(sink, state);

                    // Get provider and stream response
                    AgentProvider provider = providerRegistry.getProvider(state.agentConfig.getProvider());
                    StringBuilder fullResponse = new StringBuilder();

                    // Use streaming API — block on the virtual thread (virtual threads handle blocking well)
                    try {
                        provider.chatStream(state.systemPrompt, history, currentUserMessage, state.agentConfig)
                                .doOnNext(chunk -> {
                                    fullResponse.append(chunk);
                                    // Emit intermediate snapshots periodically
                                    if (fullResponse.length() % 20 < chunk.length()) {
                                        state.streamingContent = fullResponse.toString();
                                        emitSnapshot(sink, state);
                                    }
                                })
                                .blockLast(); // Block until stream completes — safe on virtual thread

                        // Stream completed successfully — add the full response as a message
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

                        // Emit final snapshot
                        emitSnapshot(sink, state);
                        sink.complete();

                    } catch (Exception streamError) {
                        log.error("Interactive test streaming error for session {}: {}",
                                state.sessionId, streamError.getMessage());

                        InteractiveTestMessage errorMsg = InteractiveTestMessage.builder()
                                .id(UUID.randomUUID())
                                .role("SYSTEM")
                                .content("Error: " + streamError.getMessage())
                                .createdAt(Instant.now())
                                .build();
                        state.messages.add(errorMsg);
                        state.streamingContent = null;
                        state.status = "ACTIVE";
                        state.lastActivityAt = Instant.now();

                        emitSnapshot(sink, state);
                        sink.complete();
                    }

                } catch (Exception e) {
                    log.error("Failed to process interactive test message for session {}: {}",
                            state.sessionId, e.getMessage(), e);

                    InteractiveTestMessage errorMsg = InteractiveTestMessage.builder()
                            .id(UUID.randomUUID())
                            .role("SYSTEM")
                            .content("Error: " + e.getMessage())
                            .createdAt(Instant.now())
                            .build();
                    state.messages.add(errorMsg);
                    state.streamingContent = null;
                    state.status = "ACTIVE";
                    state.lastActivityAt = Instant.now();

                    emitSnapshot(sink, state);
                    sink.complete();
                }
            });
        }, FluxSink.OverflowStrategy.BUFFER);
    }

    /**
     * Gets the current state of an interactive test session.
     */
    public InteractiveTestSessionDto getSession(UUID sessionId, UUID tenantId, UUID userId) {
        SessionState state = getValidatedSession(sessionId, tenantId, userId);
        return toDto(state);
    }

    /**
     * Closes an interactive test session and frees resources.
     */
    public void closeSession(UUID sessionId, UUID tenantId, UUID userId) {
        SessionState state = getValidatedSession(sessionId, tenantId, userId);

        // Add container teardown messages before removing the session
        String containerId = state.containerId;
        if (containerId != null) {
            log.info("Tearing down ephemeral container {} for session {}", containerId, sessionId);
        }

        sessions.remove(sessionId);
        log.info("Interactive test session {} closed by user {} (container {} destroyed)",
                sessionId, userId, containerId);
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
            sessions.remove(sessionId);
            log.info("Expired interactive test session {} (inactive > {} min)", sessionId, SESSION_TTL_MS / 60000);
        }
    }

    // --- Internal helpers ---

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
        // If the agent has a custom system prompt, use it
        if (agentConfig.getSystemPromptOverride() != null && !agentConfig.getSystemPromptOverride().isBlank()) {
            return agentConfig.getSystemPromptOverride();
        }

        // Otherwise, build a default interactive test prompt based on agent type
        String agentType = agentConfig.getAgentType();
        if (agentType == null) agentType = "GENERAL";

        return switch (agentType.toUpperCase()) {
            case "PLANNING" -> promptBuilder.buildPlanningPrompt("Interactive Test", "Help the user evaluate your planning capabilities");
            case "CODING" -> promptBuilder.buildCodingPrompt("Follow the user's instructions", "Interactive Test");
            case "REVIEW" -> promptBuilder.buildReviewPrompt("Review code provided by the user and give feedback");
            case "QA" -> promptBuilder.buildQaPrompt("Help the user test and verify code quality", "Interactive Test");
            default -> "You are a helpful AI assistant for the Squadron platform. "
                    + "This is an interactive test session — the user is evaluating your capabilities. "
                    + "You are running inside an ephemeral sandbox container. "
                    + "Respond helpfully, clearly, and concisely. You can discuss code, architecture, "
                    + "planning, reviews, and any software engineering topic. "
                    + "If you need clarification from the user, ask questions — this is how agents "
                    + "communicate with users during real tasks. "
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
        final String containerId;
        final Instant createdAt;
        final List<InteractiveTestMessage> messages;
        String status;
        Instant lastActivityAt;
        /** Transient field for in-progress streaming content. */
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
