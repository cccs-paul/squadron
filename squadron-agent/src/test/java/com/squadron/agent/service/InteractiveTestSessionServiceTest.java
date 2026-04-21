package com.squadron.agent.service;

import com.squadron.agent.dto.AgentConfigDto;
import com.squadron.agent.dto.InteractiveTestMessageRequest;
import com.squadron.agent.dto.InteractiveTestSessionDto;
import com.squadron.agent.dto.InteractiveTestSessionDto.InteractiveTestMessage;
import com.squadron.agent.entity.UserAgentConfig;
import com.squadron.agent.provider.AgentProvider;
import com.squadron.agent.provider.AgentProviderRegistry;
import com.squadron.agent.repository.UserAgentConfigRepository;
import com.squadron.common.exception.ResourceNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.codec.ServerSentEvent;
import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class InteractiveTestSessionServiceTest {

    @Mock
    private UserAgentConfigRepository agentConfigRepository;

    @Mock
    private AgentProviderRegistry providerRegistry;

    @Mock
    private SystemPromptBuilder promptBuilder;

    @Mock
    private AgentProvider mockProvider;

    private InteractiveTestSessionService service;

    private UUID tenantId;
    private UUID userId;
    private UUID agentConfigId;
    private UserAgentConfig agentConfig;

    @BeforeEach
    void setUp() {
        service = new InteractiveTestSessionService(agentConfigRepository, providerRegistry, promptBuilder);
        tenantId = UUID.randomUUID();
        userId = UUID.randomUUID();
        agentConfigId = UUID.randomUUID();

        agentConfig = UserAgentConfig.builder()
                .id(agentConfigId)
                .tenantId(tenantId)
                .userId(userId)
                .agentName("Sol")
                .agentType("PLANNING")
                .provider("ollama")
                .model("gemma4")
                .hostingType("SELF_HOSTED")
                .baseUrl("http://localhost:11434")
                .enabled(true)
                .build();
    }

    @Test
    void should_startSession_when_validAgentConfig() {
        when(agentConfigRepository.findById(agentConfigId)).thenReturn(Optional.of(agentConfig));

        InteractiveTestSessionDto session = service.startSession(tenantId, userId, agentConfigId);

        assertNotNull(session);
        assertNotNull(session.getSessionId());
        assertEquals(agentConfigId, session.getAgentConfigId());
        assertEquals("Sol", session.getAgentName());
        assertEquals("ollama", session.getProvider());
        assertEquals("gemma4", session.getModel());
        assertEquals("ACTIVE", session.getStatus());
        assertNotNull(session.getCreatedAt());
        assertNotNull(session.getContainerId(), "Session should have an ephemeral container ID");
        assertEquals(12, session.getContainerId().length(), "Container ID should be 12 chars");
        // Should have container lifecycle messages + session start message
        assertTrue(session.getMessages().size() >= 7,
                "Should have container lifecycle messages and session start message");
        assertTrue(session.getMessages().stream().allMatch(m -> "SYSTEM".equals(m.getRole())),
                "All initial messages should be SYSTEM role");
        // First message should be about container provisioning
        assertTrue(session.getMessages().get(0).getContent().contains("ephemeral sandbox container"),
                "First message should reference ephemeral container");
        // Last message should be the session start message with agent name
        InteractiveTestMessage lastMsg = session.getMessages().get(session.getMessages().size() - 1);
        assertTrue(lastMsg.getContent().contains("Sol"),
                "Last message should contain agent name");
        assertTrue(lastMsg.getContent().contains("container"),
                "Last message should reference the container");
    }

    @Test
    void should_throwNotFound_when_agentConfigNotFound() {
        when(agentConfigRepository.findById(agentConfigId)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class,
                () -> service.startSession(tenantId, userId, agentConfigId));
    }

    @Test
    void should_throwNotFound_when_agentConfigBelongsToDifferentUser() {
        UUID otherUserId = UUID.randomUUID();
        when(agentConfigRepository.findById(agentConfigId)).thenReturn(Optional.of(agentConfig));

        assertThrows(ResourceNotFoundException.class,
                () -> service.startSession(tenantId, otherUserId, agentConfigId));
    }

    @Test
    void should_throwNotFound_when_agentConfigBelongsToDifferentTenant() {
        UUID otherTenantId = UUID.randomUUID();
        when(agentConfigRepository.findById(agentConfigId)).thenReturn(Optional.of(agentConfig));

        assertThrows(ResourceNotFoundException.class,
                () -> service.startSession(otherTenantId, userId, agentConfigId));
    }

    @Test
    void should_getSession_when_sessionExists() {
        when(agentConfigRepository.findById(agentConfigId)).thenReturn(Optional.of(agentConfig));
        InteractiveTestSessionDto created = service.startSession(tenantId, userId, agentConfigId);

        InteractiveTestSessionDto fetched = service.getSession(created.getSessionId(), tenantId, userId);

        assertEquals(created.getSessionId(), fetched.getSessionId());
        assertEquals("Sol", fetched.getAgentName());
    }

    @Test
    void should_throwNotFound_when_gettingNonexistentSession() {
        UUID randomSessionId = UUID.randomUUID();
        assertThrows(ResourceNotFoundException.class,
                () -> service.getSession(randomSessionId, tenantId, userId));
    }

    @Test
    void should_closeSession_when_sessionExists() {
        when(agentConfigRepository.findById(agentConfigId)).thenReturn(Optional.of(agentConfig));
        InteractiveTestSessionDto created = service.startSession(tenantId, userId, agentConfigId);

        service.closeSession(created.getSessionId(), tenantId, userId);

        assertThrows(ResourceNotFoundException.class,
                () -> service.getSession(created.getSessionId(), tenantId, userId));
    }

    @Test
    void should_throwNotFound_when_closingSessionForDifferentUser() {
        when(agentConfigRepository.findById(agentConfigId)).thenReturn(Optional.of(agentConfig));
        InteractiveTestSessionDto created = service.startSession(tenantId, userId, agentConfigId);
        UUID otherUserId = UUID.randomUUID();

        assertThrows(ResourceNotFoundException.class,
                () -> service.closeSession(created.getSessionId(), tenantId, otherUserId));
    }

    @Test
    void should_getUserSessions_when_userHasSessions() {
        when(agentConfigRepository.findById(agentConfigId)).thenReturn(Optional.of(agentConfig));
        service.startSession(tenantId, userId, agentConfigId);

        List<InteractiveTestSessionDto> sessions = service.getUserSessions(tenantId, userId);

        assertEquals(1, sessions.size());
        assertEquals("Sol", sessions.get(0).getAgentName());
    }

    @Test
    void should_returnEmptyList_when_userHasNoSessions() {
        List<InteractiveTestSessionDto> sessions = service.getUserSessions(tenantId, userId);
        assertTrue(sessions.isEmpty());
    }

    @Test
    void should_sendMessage_and_streamResponse() {
        when(agentConfigRepository.findById(agentConfigId)).thenReturn(Optional.of(agentConfig));
        when(promptBuilder.buildPlanningPrompt(anyString(), anyString()))
                .thenReturn("You are a planning agent for interactive testing.");
        when(providerRegistry.getProvider("ollama")).thenReturn(mockProvider);
        when(mockProvider.chatStream(anyString(), anyList(), anyString(), any(AgentConfigDto.class)))
                .thenReturn(Flux.just("Hello", " from", " agent!"));

        InteractiveTestSessionDto session = service.startSession(tenantId, userId, agentConfigId);

        InteractiveTestMessageRequest request = InteractiveTestMessageRequest.builder()
                .sessionId(session.getSessionId())
                .message("Hi there")
                .build();

        Flux<ServerSentEvent<InteractiveTestSessionDto>> flux = service.sendMessage(tenantId, userId, request);

        // Collect all SSE snapshots — block until the flux completes
        List<ServerSentEvent<InteractiveTestSessionDto>> events = flux.collectList().block(java.time.Duration.ofSeconds(10));

        assertNotNull(events, "Should have received SSE events");
        assertFalse(events.isEmpty(), "Should have received at least one SSE event");

        // The final snapshot should contain both the user message and the agent response
        InteractiveTestSessionDto finalSnapshot = events.get(events.size() - 1).data();
        assertNotNull(finalSnapshot, "Final SSE event should have data");

        boolean hasUserMsg = finalSnapshot.getMessages().stream()
                .anyMatch(m -> "USER".equals(m.getRole()) && "Hi there".equals(m.getContent()));
        assertTrue(hasUserMsg, "User message should be present in final snapshot");

        boolean hasAgentMsg = finalSnapshot.getMessages().stream()
                .anyMatch(m -> "AGENT".equals(m.getRole()));
        assertTrue(hasAgentMsg, "Agent response should be present in final snapshot");

        // Session status should be ACTIVE after streaming completes
        assertEquals("ACTIVE", finalSnapshot.getStatus(), "Status should be ACTIVE after streaming completes");
    }

    @Test
    void should_addErrorMessage_when_providerFails() {
        when(agentConfigRepository.findById(agentConfigId)).thenReturn(Optional.of(agentConfig));
        when(providerRegistry.getProvider("ollama"))
                .thenThrow(new RuntimeException("Provider not available"));

        InteractiveTestSessionDto session = service.startSession(tenantId, userId, agentConfigId);

        InteractiveTestMessageRequest request = InteractiveTestMessageRequest.builder()
                .sessionId(session.getSessionId())
                .message("Hi there")
                .build();

        Flux<ServerSentEvent<InteractiveTestSessionDto>> flux = service.sendMessage(tenantId, userId, request);

        StepVerifier.create(flux)
                .thenConsumeWhile(sse -> true)
                .verifyComplete();

        InteractiveTestSessionDto updated = service.getSession(session.getSessionId(), tenantId, userId);
        boolean hasError = updated.getMessages().stream()
                .anyMatch(m -> "SYSTEM".equals(m.getRole()) && m.getContent().contains("Error"));
        assertTrue(hasError, "Error message should be present");
    }

    @Test
    void should_throwIllegalState_when_maxSessionsReached() {
        when(agentConfigRepository.findById(agentConfigId)).thenReturn(Optional.of(agentConfig));

        // Create 8 sessions (max)
        for (int i = 0; i < 8; i++) {
            service.startSession(tenantId, userId, agentConfigId);
        }

        assertThrows(IllegalStateException.class,
                () -> service.startSession(tenantId, userId, agentConfigId));
    }

    @Test
    void should_cleanupExpiredSessions_when_scheduled() {
        when(agentConfigRepository.findById(agentConfigId)).thenReturn(Optional.of(agentConfig));
        InteractiveTestSessionDto session = service.startSession(tenantId, userId, agentConfigId);

        // Manually set last activity to past
        var sessionState = service.getSessionsMap().get(session.getSessionId());
        sessionState.lastActivityAt = Instant.now().minusMillis(31 * 60 * 1000L); // 31 minutes ago

        service.cleanupExpiredSessions();

        assertTrue(service.getUserSessions(tenantId, userId).isEmpty(),
                "Expired session should be cleaned up");
    }

    @Test
    void should_useCustomSystemPrompt_when_agentHasOne() {
        agentConfig = UserAgentConfig.builder()
                .id(agentConfigId)
                .tenantId(tenantId)
                .userId(userId)
                .agentName("Custom Agent")
                .agentType("CODING")
                .provider("ollama")
                .model("gemma4")
                .hostingType("SELF_HOSTED")
                .systemPromptOverride("You are a custom coding assistant.")
                .enabled(true)
                .build();
        when(agentConfigRepository.findById(agentConfigId)).thenReturn(Optional.of(agentConfig));

        InteractiveTestSessionDto session = service.startSession(tenantId, userId, agentConfigId);

        assertNotNull(session);
        assertEquals("Custom Agent", session.getAgentName());
    }

    @Test
    void should_allowMultipleSimultaneousSessions_forSameUser() {
        UUID agentConfigId2 = UUID.randomUUID();
        UserAgentConfig agentConfig2 = UserAgentConfig.builder()
                .id(agentConfigId2)
                .tenantId(tenantId)
                .userId(userId)
                .agentName("Titan")
                .agentType("CODING")
                .provider("ollama")
                .model("qwen2.5-coder")
                .hostingType("SELF_HOSTED")
                .enabled(true)
                .build();

        when(agentConfigRepository.findById(agentConfigId)).thenReturn(Optional.of(agentConfig));
        when(agentConfigRepository.findById(agentConfigId2)).thenReturn(Optional.of(agentConfig2));

        InteractiveTestSessionDto session1 = service.startSession(tenantId, userId, agentConfigId);
        InteractiveTestSessionDto session2 = service.startSession(tenantId, userId, agentConfigId2);

        assertNotEquals(session1.getSessionId(), session2.getSessionId());
        assertEquals("Sol", session1.getAgentName());
        assertEquals("Titan", session2.getAgentName());

        List<InteractiveTestSessionDto> sessions = service.getUserSessions(tenantId, userId);
        assertEquals(2, sessions.size());
    }

    @Test
    void should_throwNotFound_when_sendingMessageToDifferentUsersSession() {
        when(agentConfigRepository.findById(agentConfigId)).thenReturn(Optional.of(agentConfig));
        InteractiveTestSessionDto session = service.startSession(tenantId, userId, agentConfigId);

        UUID otherUserId = UUID.randomUUID();
        InteractiveTestMessageRequest request = InteractiveTestMessageRequest.builder()
                .sessionId(session.getSessionId())
                .message("Hi")
                .build();

        assertThrows(ResourceNotFoundException.class,
                () -> service.sendMessage(tenantId, otherUserId, request));
    }

    @Test
    void should_throwIllegalState_when_maxMessagesPerSessionReached() {
        when(agentConfigRepository.findById(agentConfigId)).thenReturn(Optional.of(agentConfig));
        InteractiveTestSessionDto session = service.startSession(tenantId, userId, agentConfigId);

        // Manually fill messages to 100
        var sessionState = service.getSessionsMap().get(session.getSessionId());
        for (int i = sessionState.messages.size(); i < 100; i++) {
            sessionState.messages.add(InteractiveTestMessage.builder()
                    .id(UUID.randomUUID())
                    .role("USER")
                    .content("msg " + i)
                    .createdAt(Instant.now())
                    .build());
        }

        InteractiveTestMessageRequest request = InteractiveTestMessageRequest.builder()
                .sessionId(session.getSessionId())
                .message("one more")
                .build();

        assertThrows(IllegalStateException.class,
                () -> service.sendMessage(tenantId, userId, request));
    }

    @Test
    void should_useCodingPrompt_when_agentTypeIsCoding() {
        UUID codingConfigId = UUID.randomUUID();
        UserAgentConfig codingConfig = UserAgentConfig.builder()
                .id(codingConfigId)
                .tenantId(tenantId)
                .userId(userId)
                .agentName("Coder")
                .agentType("CODING")
                .provider("ollama")
                .model("gemma4")
                .hostingType("SELF_HOSTED")
                .enabled(true)
                .build();

        when(agentConfigRepository.findById(codingConfigId)).thenReturn(Optional.of(codingConfig));
        lenient().when(promptBuilder.buildCodingPrompt(anyString(), anyString())).thenReturn("Coding prompt");

        service.startSession(tenantId, userId, codingConfigId);

        verify(promptBuilder).buildCodingPrompt(anyString(), anyString());
    }

    @Test
    void should_useReviewPrompt_when_agentTypeIsReview() {
        UUID reviewConfigId = UUID.randomUUID();
        UserAgentConfig reviewConfig = UserAgentConfig.builder()
                .id(reviewConfigId)
                .tenantId(tenantId)
                .userId(userId)
                .agentName("Reviewer")
                .agentType("REVIEW")
                .provider("ollama")
                .model("gemma4")
                .hostingType("SELF_HOSTED")
                .enabled(true)
                .build();

        when(agentConfigRepository.findById(reviewConfigId)).thenReturn(Optional.of(reviewConfig));
        lenient().when(promptBuilder.buildReviewPrompt(anyString())).thenReturn("Review prompt");

        service.startSession(tenantId, userId, reviewConfigId);

        verify(promptBuilder).buildReviewPrompt(anyString());
    }

    @Test
    void should_useQaPrompt_when_agentTypeIsQa() {
        UUID qaConfigId = UUID.randomUUID();
        UserAgentConfig qaConfig = UserAgentConfig.builder()
                .id(qaConfigId)
                .tenantId(tenantId)
                .userId(userId)
                .agentName("QA Bot")
                .agentType("QA")
                .provider("ollama")
                .model("gemma4")
                .hostingType("SELF_HOSTED")
                .enabled(true)
                .build();

        when(agentConfigRepository.findById(qaConfigId)).thenReturn(Optional.of(qaConfig));
        lenient().when(promptBuilder.buildQaPrompt(anyString(), anyString())).thenReturn("QA prompt");

        service.startSession(tenantId, userId, qaConfigId);

        verify(promptBuilder).buildQaPrompt(anyString(), anyString());
    }

    @Test
    void should_useDefaultPrompt_when_agentTypeIsGeneral() {
        UUID generalConfigId = UUID.randomUUID();
        UserAgentConfig generalConfig = UserAgentConfig.builder()
                .id(generalConfigId)
                .tenantId(tenantId)
                .userId(userId)
                .agentName("General")
                .agentType("GENERAL")
                .provider("ollama")
                .model("gemma4")
                .hostingType("SELF_HOSTED")
                .enabled(true)
                .build();

        when(agentConfigRepository.findById(generalConfigId)).thenReturn(Optional.of(generalConfig));

        service.startSession(tenantId, userId, generalConfigId);

        verify(promptBuilder, never()).buildPlanningPrompt(anyString(), anyString());
        verify(promptBuilder, never()).buildCodingPrompt(anyString(), anyString());
        verify(promptBuilder, never()).buildReviewPrompt(anyString());
        verify(promptBuilder, never()).buildQaPrompt(anyString(), anyString());
    }

    @Test
    void should_includeContainerLifecycleMessages_when_sessionStarts() {
        when(agentConfigRepository.findById(agentConfigId)).thenReturn(Optional.of(agentConfig));

        InteractiveTestSessionDto session = service.startSession(tenantId, userId, agentConfigId);

        assertNotNull(session.getContainerId());

        List<String> messageContents = session.getMessages().stream()
                .map(InteractiveTestMessage::getContent)
                .toList();

        // Verify container lifecycle messages are present in order
        assertTrue(messageContents.stream().anyMatch(c -> c.contains("Requesting ephemeral sandbox container")),
                "Should have container request message");
        assertTrue(messageContents.stream().anyMatch(c -> c.contains("Pulling workspace image")),
                "Should have image pull message");
        assertTrue(messageContents.stream().anyMatch(c -> c.contains("Allocating resources")),
                "Should have resource allocation message");
        assertTrue(messageContents.stream().anyMatch(c -> c.contains(session.getContainerId() + " created")),
                "Should reference the container ID in creation message");
        assertTrue(messageContents.stream().anyMatch(c -> c.contains("Installing language toolchains")),
                "Should have toolchain install message");
        assertTrue(messageContents.stream().anyMatch(c -> c.contains("is ready")),
                "Should have container ready message");
    }

    @Test
    void should_logContainerTeardown_when_sessionCloses() {
        when(agentConfigRepository.findById(agentConfigId)).thenReturn(Optional.of(agentConfig));
        InteractiveTestSessionDto session = service.startSession(tenantId, userId, agentConfigId);

        assertNotNull(session.getContainerId(), "Session should have a container ID");

        // Close session — should not throw
        assertDoesNotThrow(() -> service.closeSession(session.getSessionId(), tenantId, userId));

        // Session should be removed
        assertThrows(ResourceNotFoundException.class,
                () -> service.getSession(session.getSessionId(), tenantId, userId));
    }

    @Test
    void should_addStreamingMessage_inToDtoSnapshot_when_streamingContentPresent() {
        when(agentConfigRepository.findById(agentConfigId)).thenReturn(Optional.of(agentConfig));
        InteractiveTestSessionDto session = service.startSession(tenantId, userId, agentConfigId);

        var sessionState = service.getSessionsMap().get(session.getSessionId());
        sessionState.streamingContent = "partial response";
        sessionState.status = "STREAMING";

        InteractiveTestSessionDto dto = service.getSession(session.getSessionId(), tenantId, userId);

        assertEquals("STREAMING", dto.getStatus());
        // Should have the initial SYSTEM message + the streaming AGENT message
        boolean hasStreamingAgent = dto.getMessages().stream()
                .anyMatch(m -> "AGENT".equals(m.getRole()) && "partial response".equals(m.getContent()));
        assertTrue(hasStreamingAgent, "Should include streaming content as an AGENT message");
    }

    @Test
    void should_handleStreamError_when_providerStreamFails() {
        when(agentConfigRepository.findById(agentConfigId)).thenReturn(Optional.of(agentConfig));
        when(promptBuilder.buildPlanningPrompt(anyString(), anyString()))
                .thenReturn("You are a planning agent.");
        when(providerRegistry.getProvider("ollama")).thenReturn(mockProvider);
        when(mockProvider.chatStream(anyString(), anyList(), anyString(), any()))
                .thenReturn(Flux.error(new RuntimeException("stream failed")));

        InteractiveTestSessionDto session = service.startSession(tenantId, userId, agentConfigId);

        InteractiveTestMessageRequest request = InteractiveTestMessageRequest.builder()
                .sessionId(session.getSessionId())
                .message("Hello")
                .build();

        Flux<ServerSentEvent<InteractiveTestSessionDto>> flux = service.sendMessage(tenantId, userId, request);
        List<ServerSentEvent<InteractiveTestSessionDto>> events = flux.collectList().block(java.time.Duration.ofSeconds(10));

        assertNotNull(events);
        assertFalse(events.isEmpty());

        InteractiveTestSessionDto finalSnapshot = events.get(events.size() - 1).data();
        assertNotNull(finalSnapshot);
        boolean hasError = finalSnapshot.getMessages().stream()
                .anyMatch(m -> "SYSTEM".equals(m.getRole()) && m.getContent().contains("stream failed"));
        assertTrue(hasError, "Should contain error message about stream failure");
    }
}
