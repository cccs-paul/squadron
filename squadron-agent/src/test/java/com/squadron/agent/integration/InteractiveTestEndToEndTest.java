package com.squadron.agent.integration;

import com.squadron.agent.dto.AgentConfigDto;
import com.squadron.agent.dto.InteractiveTestMessageRequest;
import com.squadron.agent.dto.InteractiveTestSessionDto;
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
import reactor.core.publisher.Flux;

import java.time.Duration;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * End-to-end lifecycle tests exercising realistic scenarios including
 * session limits, concurrent messages, and tenant isolation.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("Interactive Test Session — End-to-End Lifecycle Tests")
class InteractiveTestEndToEndTest {

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
    @DisplayName("should enforce session limit then recover after close")
    void should_enforceSessionLimitThenRecoverAfterClose() {
        when(agentConfigRepository.findById(agentConfigId)).thenReturn(Optional.of(agentConfig));

        // Create MAX_SESSIONS_PER_USER (8) sessions
        InteractiveTestSessionDto firstSession = null;
        for (int i = 0; i < 8; i++) {
            InteractiveTestSessionDto s = service.startSession(tenantId, userId, agentConfigId);
            if (i == 0) firstSession = s;
        }

        // 9th should fail
        assertThrows(IllegalStateException.class,
                () -> service.startSession(tenantId, userId, agentConfigId));

        // Close one session
        service.closeSession(firstSession.getSessionId(), tenantId, userId);

        // Now a new session should succeed
        InteractiveTestSessionDto newSession = service.startSession(tenantId, userId, agentConfigId);
        assertNotNull(newSession);
        assertNotNull(newSession.getSessionId());
    }

    @Test
    @DisplayName("should handle sequential messages to same session")
    void should_handleConcurrentMessagesToSameSession() {
        when(agentConfigRepository.findById(agentConfigId)).thenReturn(Optional.of(agentConfig));
        when(promptBuilder.buildPlanningPrompt(anyString(), anyString())).thenReturn("test prompt");
        when(providerRegistry.getProvider("ollama")).thenReturn(agentProvider);
        when(agentProvider.chatStream(anyString(), anyList(), anyString(), any(AgentConfigDto.class)))
                .thenReturn(Flux.just("Reply A"))
                .thenReturn(Flux.just("Reply B"));

        InteractiveTestSessionDto session = service.startSession(tenantId, userId, agentConfigId);

        // Send two messages sequentially
        InteractiveTestMessageRequest reqA = InteractiveTestMessageRequest.builder()
                .sessionId(session.getSessionId())
                .message("Message A")
                .build();
        service.sendMessage(tenantId, userId, reqA).collectList().block(Duration.ofSeconds(10));

        InteractiveTestMessageRequest reqB = InteractiveTestMessageRequest.builder()
                .sessionId(session.getSessionId())
                .message("Message B")
                .build();
        service.sendMessage(tenantId, userId, reqB).collectList().block(Duration.ofSeconds(10));

        // Verify both messages are recorded
        InteractiveTestSessionDto finalSession = service.getSession(session.getSessionId(), tenantId, userId);
        long userMsgCount = finalSession.getMessages().stream()
                .filter(m -> "USER".equals(m.getRole())).count();
        long agentMsgCount = finalSession.getMessages().stream()
                .filter(m -> "AGENT".equals(m.getRole())).count();
        assertEquals(2, userMsgCount, "Both user messages should be recorded");
        assertEquals(2, agentMsgCount, "Both agent replies should be recorded");
    }

    @Test
    @DisplayName("should isolate sessions between tenants")
    void should_isolateSessionsBetweenTenants() {
        UUID tenantA = UUID.randomUUID();
        UUID tenantB = UUID.randomUUID();
        UUID userA = UUID.randomUUID();
        UUID userB = UUID.randomUUID();
        UUID configIdA = UUID.randomUUID();
        UUID configIdB = UUID.randomUUID();

        UserAgentConfig configA = UserAgentConfig.builder()
                .id(configIdA)
                .tenantId(tenantA)
                .userId(userA)
                .agentName("AgentA")
                .agentType("PLANNING")
                .provider("ollama")
                .model("gemma4")
                .hostingType("SELF_HOSTED")
                .enabled(true)
                .build();

        UserAgentConfig configB = UserAgentConfig.builder()
                .id(configIdB)
                .tenantId(tenantB)
                .userId(userB)
                .agentName("AgentB")
                .agentType("PLANNING")
                .provider("ollama")
                .model("gemma4")
                .hostingType("SELF_HOSTED")
                .enabled(true)
                .build();

        when(agentConfigRepository.findById(configIdA)).thenReturn(Optional.of(configA));
        when(agentConfigRepository.findById(configIdB)).thenReturn(Optional.of(configB));

        InteractiveTestSessionDto sessionA = service.startSession(tenantA, userA, configIdA);
        InteractiveTestSessionDto sessionB = service.startSession(tenantB, userB, configIdB);

        // Tenant A cannot access tenant B's session
        assertThrows(ResourceNotFoundException.class,
                () -> service.getSession(sessionB.getSessionId(), tenantA, userA));

        // Tenant B cannot access tenant A's session
        assertThrows(ResourceNotFoundException.class,
                () -> service.getSession(sessionA.getSessionId(), tenantB, userB));
    }
}
