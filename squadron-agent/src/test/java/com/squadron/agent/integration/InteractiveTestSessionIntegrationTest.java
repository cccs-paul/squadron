package com.squadron.agent.integration;

import com.squadron.agent.dto.AgentConfigDto;
import com.squadron.agent.dto.InteractiveTestMessageRequest;
import com.squadron.agent.dto.InteractiveTestSessionDto;
import com.squadron.agent.dto.InteractiveTestSessionDto.InteractiveTestMessage;
import com.squadron.agent.entity.UserAgentConfig;
import com.squadron.agent.provider.AgentProvider;
import com.squadron.agent.provider.AgentProviderRegistry;
import com.squadron.agent.repository.UserAgentConfigRepository;
import com.squadron.agent.service.InteractiveTestSessionService;
import com.squadron.agent.service.SystemPromptBuilder;
import com.squadron.common.exception.ResourceNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.codec.ServerSentEvent;
import reactor.core.publisher.Flux;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Integration tests for InteractiveTestSessionService focusing on multi-turn
 * conversation flows and session lifecycle management.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("Interactive Test Session — Multi-Turn Conversation Integration Tests")
class InteractiveTestSessionIntegrationTest {

    @Mock
    private UserAgentConfigRepository agentConfigRepository;

    @Mock
    private AgentProviderRegistry providerRegistry;

    @Mock
    private SystemPromptBuilder promptBuilder;

    @Mock
    private AgentProvider agentProvider;

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
                .agentName("TestAgent")
                .agentType("PLANNING")
                .provider("ollama")
                .model("gemma4")
                .hostingType("SELF_HOSTED")
                .enabled(true)
                .build();
    }

    @Test
    @DisplayName("should conduct multi-turn conversation with full history")
    void should_conductMultiTurnConversation_withFullHistory() {
        when(agentConfigRepository.findById(agentConfigId)).thenReturn(Optional.of(agentConfig));
        when(promptBuilder.buildPlanningPrompt(anyString(), anyString())).thenReturn("test prompt");
        when(providerRegistry.getProvider("ollama")).thenReturn(agentProvider);
        when(agentProvider.chatStream(anyString(), anyList(), anyString(), any(AgentConfigDto.class)))
                .thenReturn(Flux.just("Response 1"))
                .thenReturn(Flux.just("Response 2"))
                .thenReturn(Flux.just("Response 3"));

        InteractiveTestSessionDto session = service.startSession(tenantId, userId, agentConfigId);
        UUID sessionId = session.getSessionId();

        // Send 3 messages sequentially
        for (int i = 1; i <= 3; i++) {
            InteractiveTestMessageRequest request = InteractiveTestMessageRequest.builder()
                    .sessionId(sessionId)
                    .message("User message " + i)
                    .build();

            List<ServerSentEvent<InteractiveTestSessionDto>> events =
                    service.sendMessage(tenantId, userId, request)
                            .collectList().block(Duration.ofSeconds(10));
            assertNotNull(events);
            assertFalse(events.isEmpty());
        }

        // Verify final snapshot has all messages
        InteractiveTestSessionDto finalSession = service.getSession(sessionId, tenantId, userId);
        // 1 SYSTEM + 3 USER + 3 AGENT = 7 messages
        assertEquals(7, finalSession.getMessages().size());

        long userMsgCount = finalSession.getMessages().stream()
                .filter(m -> "USER".equals(m.getRole())).count();
        long agentMsgCount = finalSession.getMessages().stream()
                .filter(m -> "AGENT".equals(m.getRole())).count();
        assertEquals(3, userMsgCount);
        assertEquals(3, agentMsgCount);
    }

    @Test
    @DisplayName("should maintain separate histories for multiple sessions")
    void should_maintainSeparateHistories_forMultipleSessions() {
        UUID agentConfigId2 = UUID.randomUUID();
        UserAgentConfig agentConfig2 = UserAgentConfig.builder()
                .id(agentConfigId2)
                .tenantId(tenantId)
                .userId(userId)
                .agentName("Agent2")
                .agentType("PLANNING")
                .provider("ollama")
                .model("gemma4")
                .hostingType("SELF_HOSTED")
                .enabled(true)
                .build();

        when(agentConfigRepository.findById(agentConfigId)).thenReturn(Optional.of(agentConfig));
        when(agentConfigRepository.findById(agentConfigId2)).thenReturn(Optional.of(agentConfig2));
        when(promptBuilder.buildPlanningPrompt(anyString(), anyString())).thenReturn("test prompt");
        when(providerRegistry.getProvider("ollama")).thenReturn(agentProvider);
        when(agentProvider.chatStream(anyString(), anyList(), anyString(), any(AgentConfigDto.class)))
                .thenReturn(Flux.just("Response for session 1"))
                .thenReturn(Flux.just("Response for session 2"));

        InteractiveTestSessionDto session1 = service.startSession(tenantId, userId, agentConfigId);
        InteractiveTestSessionDto session2 = service.startSession(tenantId, userId, agentConfigId2);

        // Send message to session 1
        InteractiveTestMessageRequest req1 = InteractiveTestMessageRequest.builder()
                .sessionId(session1.getSessionId())
                .message("Hello session 1")
                .build();
        service.sendMessage(tenantId, userId, req1).collectList().block(Duration.ofSeconds(10));

        // Send message to session 2
        InteractiveTestMessageRequest req2 = InteractiveTestMessageRequest.builder()
                .sessionId(session2.getSessionId())
                .message("Hello session 2")
                .build();
        service.sendMessage(tenantId, userId, req2).collectList().block(Duration.ofSeconds(10));

        // Verify isolation
        InteractiveTestSessionDto s1 = service.getSession(session1.getSessionId(), tenantId, userId);
        InteractiveTestSessionDto s2 = service.getSession(session2.getSessionId(), tenantId, userId);

        boolean s1HasSession2Msg = s1.getMessages().stream()
                .anyMatch(m -> m.getContent().contains("session 2"));
        boolean s2HasSession1Msg = s2.getMessages().stream()
                .anyMatch(m -> m.getContent().contains("session 1"));

        assertFalse(s1HasSession2Msg, "Session 1 should not contain session 2 messages");
        assertFalse(s2HasSession1Msg, "Session 2 should not contain session 1 messages");
    }

    @Test
    @DisplayName("should cleanup only expired sessions, not active sessions")
    void should_cleanupOnlyExpiredSessions_notActiveSessions() throws Exception {
        when(agentConfigRepository.findById(agentConfigId)).thenReturn(Optional.of(agentConfig));

        InteractiveTestSessionDto expiredSession = service.startSession(tenantId, userId, agentConfigId);
        InteractiveTestSessionDto activeSession = service.startSession(tenantId, userId, agentConfigId);

        // Use reflection to access the sessions map and expire one session
        Map<UUID, ?> sessionsMap = getSessionsMap();
        Object expiredState = sessionsMap.get(expiredSession.getSessionId());
        Field lastActivityField = expiredState.getClass().getDeclaredField("lastActivityAt");
        lastActivityField.setAccessible(true);
        lastActivityField.set(expiredState, Instant.now().minusMillis(31 * 60 * 1000L));

        service.cleanupExpiredSessions();

        // Active session should remain
        assertDoesNotThrow(() -> service.getSession(activeSession.getSessionId(), tenantId, userId));
        // Expired session should be gone
        assertThrows(ResourceNotFoundException.class,
                () -> service.getSession(expiredSession.getSessionId(), tenantId, userId));
    }

    @Test
    @DisplayName("should reject message after session closed")
    void should_rejectMessage_afterSessionClosed() {
        when(agentConfigRepository.findById(agentConfigId)).thenReturn(Optional.of(agentConfig));

        InteractiveTestSessionDto session = service.startSession(tenantId, userId, agentConfigId);
        service.closeSession(session.getSessionId(), tenantId, userId);

        InteractiveTestMessageRequest request = InteractiveTestMessageRequest.builder()
                .sessionId(session.getSessionId())
                .message("Should fail")
                .build();

        assertThrows(ResourceNotFoundException.class,
                () -> service.sendMessage(tenantId, userId, request));
    }

    @Test
    @DisplayName("should update lastActivityAt when message sent")
    void should_updateLastActivityAt_whenMessageSent() throws Exception {
        when(agentConfigRepository.findById(agentConfigId)).thenReturn(Optional.of(agentConfig));
        when(promptBuilder.buildPlanningPrompt(anyString(), anyString())).thenReturn("test prompt");
        when(providerRegistry.getProvider("ollama")).thenReturn(agentProvider);
        when(agentProvider.chatStream(anyString(), anyList(), anyString(), any(AgentConfigDto.class)))
                .thenReturn(Flux.just("Response"));

        InteractiveTestSessionDto session = service.startSession(tenantId, userId, agentConfigId);
        Map<UUID, ?> sessionsMap = getSessionsMap();
        Object sessionState = sessionsMap.get(session.getSessionId());
        Field lastActivityField = sessionState.getClass().getDeclaredField("lastActivityAt");
        lastActivityField.setAccessible(true);
        Instant initialActivity = (Instant) lastActivityField.get(sessionState);

        Thread.sleep(50);

        InteractiveTestMessageRequest request = InteractiveTestMessageRequest.builder()
                .sessionId(session.getSessionId())
                .message("Update activity")
                .build();
        service.sendMessage(tenantId, userId, request).collectList().block(Duration.ofSeconds(10));

        Instant updatedActivity = (Instant) lastActivityField.get(sessionState);
        assertTrue(updatedActivity.isAfter(initialActivity),
                "lastActivityAt should be updated after sending a message");
    }

    @Test
    @DisplayName("should handle provider returning empty stream")
    void should_handleProviderReturningEmptyStream() {
        when(agentConfigRepository.findById(agentConfigId)).thenReturn(Optional.of(agentConfig));
        when(promptBuilder.buildPlanningPrompt(anyString(), anyString())).thenReturn("test prompt");
        when(providerRegistry.getProvider("ollama")).thenReturn(agentProvider);
        when(agentProvider.chatStream(anyString(), anyList(), anyString(), any(AgentConfigDto.class)))
                .thenReturn(Flux.empty());

        InteractiveTestSessionDto session = service.startSession(tenantId, userId, agentConfigId);

        InteractiveTestMessageRequest request = InteractiveTestMessageRequest.builder()
                .sessionId(session.getSessionId())
                .message("Hello empty")
                .build();

        List<ServerSentEvent<InteractiveTestSessionDto>> events =
                service.sendMessage(tenantId, userId, request)
                        .collectList().block(Duration.ofSeconds(10));

        assertNotNull(events);
        assertFalse(events.isEmpty());

        // Session should still be ACTIVE after empty stream
        InteractiveTestSessionDto finalSession = service.getSession(session.getSessionId(), tenantId, userId);
        assertEquals("ACTIVE", finalSession.getStatus());

        // Agent response should be present with empty content
        boolean hasAgentMsg = finalSession.getMessages().stream()
                .anyMatch(m -> "AGENT".equals(m.getRole()) && "".equals(m.getContent()));
        assertTrue(hasAgentMsg, "Agent message with empty content should be present");
    }

    @SuppressWarnings("unchecked")
    private Map<UUID, ?> getSessionsMap() throws Exception {
        Method method = InteractiveTestSessionService.class.getDeclaredMethod("getSessionsMap");
        method.setAccessible(true);
        return (Map<UUID, ?>) method.invoke(service);
    }
}
