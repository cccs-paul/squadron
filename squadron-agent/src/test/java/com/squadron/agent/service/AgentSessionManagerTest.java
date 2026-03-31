package com.squadron.agent.service;

import com.squadron.agent.dto.AgentProgressDto;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import reactor.core.Disposable;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AgentSessionManagerTest {

    @Mock
    private SimpMessagingTemplate messagingTemplate;

    private AgentSessionManager sessionManager;

    @BeforeEach
    void setUp() {
        sessionManager = new AgentSessionManager(messagingTemplate);
    }

    @Test
    void should_registerStream_when_called() {
        UUID convId = UUID.randomUUID();
        Disposable disposable = mock(Disposable.class);

        sessionManager.registerStream(convId, disposable);

        assertTrue(sessionManager.isActive(convId));
    }

    @Test
    void should_cancelStream_when_streamIsActive() {
        UUID convId = UUID.randomUUID();
        Disposable disposable = mock(Disposable.class);
        when(disposable.isDisposed()).thenReturn(false);

        sessionManager.registerStream(convId, disposable);
        boolean result = sessionManager.cancelStream(convId);

        assertTrue(result);
        verify(disposable).dispose();
        assertFalse(sessionManager.isActive(convId));
    }

    @Test
    void should_returnFalse_when_cancelStreamWithNoActiveStream() {
        UUID convId = UUID.randomUUID();

        boolean result = sessionManager.cancelStream(convId);

        assertFalse(result);
    }

    @Test
    void should_returnFalse_when_cancelStreamAlreadyDisposed() {
        UUID convId = UUID.randomUUID();
        Disposable disposable = mock(Disposable.class);
        when(disposable.isDisposed()).thenReturn(true);

        sessionManager.registerStream(convId, disposable);
        boolean result = sessionManager.cancelStream(convId);

        assertFalse(result);
        verify(disposable, never()).dispose();
    }

    @Test
    void should_removeStream_when_called() {
        UUID convId = UUID.randomUUID();
        Disposable disposable = mock(Disposable.class);

        sessionManager.registerStream(convId, disposable);
        sessionManager.removeStream(convId);

        assertFalse(sessionManager.isActive(convId));
    }

    @Test
    void should_returnTrue_when_streamIsActive() {
        UUID convId = UUID.randomUUID();
        Disposable disposable = mock(Disposable.class);
        when(disposable.isDisposed()).thenReturn(false);

        sessionManager.registerStream(convId, disposable);

        assertTrue(sessionManager.isActive(convId));
    }

    @Test
    void should_returnFalse_when_streamIsDisposed() {
        UUID convId = UUID.randomUUID();
        Disposable disposable = mock(Disposable.class);
        when(disposable.isDisposed()).thenReturn(true);

        sessionManager.registerStream(convId, disposable);

        assertFalse(sessionManager.isActive(convId));
    }

    @Test
    void should_returnFalse_when_noStreamRegistered() {
        UUID convId = UUID.randomUUID();

        assertFalse(sessionManager.isActive(convId));
    }

    @Test
    void should_updateProgress_and_sendToTopic() {
        UUID convId = UUID.randomUUID();
        AgentProgressDto progress = AgentProgressDto.builder()
                .conversationId(convId)
                .agentType("CODING")
                .phase("CODING")
                .currentStep("Writing tests")
                .completedSteps(2)
                .totalSteps(5)
                .build();

        sessionManager.updateProgress(convId, progress);

        // Verify sent to STOMP topic
        ArgumentCaptor<AgentProgressDto> captor = ArgumentCaptor.forClass(AgentProgressDto.class);
        verify(messagingTemplate).convertAndSend(eq("/topic/progress/" + convId), captor.capture());
        assertEquals(progress, captor.getValue());

        // Verify retrievable
        AgentProgressDto retrieved = sessionManager.getProgress(convId);
        assertNotNull(retrieved);
        assertEquals("CODING", retrieved.getPhase());
        assertEquals("Writing tests", retrieved.getCurrentStep());
    }

    @Test
    void should_getProgress_returnNull_when_noProgressSet() {
        UUID convId = UUID.randomUUID();

        AgentProgressDto progress = sessionManager.getProgress(convId);

        assertNull(progress);
    }

    @Test
    void should_removeProgressOnCancel_when_cancelStream() {
        UUID convId = UUID.randomUUID();
        Disposable disposable = mock(Disposable.class);
        when(disposable.isDisposed()).thenReturn(false);

        sessionManager.registerStream(convId, disposable);
        AgentProgressDto progress = AgentProgressDto.builder()
                .conversationId(convId)
                .phase("CODING")
                .build();
        sessionManager.updateProgress(convId, progress);

        sessionManager.cancelStream(convId);

        assertNull(sessionManager.getProgress(convId));
    }

    @Test
    void should_removeProgressOnRemove_when_removeStream() {
        UUID convId = UUID.randomUUID();
        Disposable disposable = mock(Disposable.class);

        sessionManager.registerStream(convId, disposable);
        AgentProgressDto progress = AgentProgressDto.builder()
                .conversationId(convId)
                .phase("TESTING")
                .build();
        sessionManager.updateProgress(convId, progress);

        sessionManager.removeStream(convId);

        assertNull(sessionManager.getProgress(convId));
    }

    @Test
    void should_getActiveSessionCount_when_multipleStreams() {
        Disposable d1 = mock(Disposable.class);
        Disposable d2 = mock(Disposable.class);
        Disposable d3 = mock(Disposable.class);
        when(d1.isDisposed()).thenReturn(false);
        when(d2.isDisposed()).thenReturn(false);
        when(d3.isDisposed()).thenReturn(true); // disposed

        sessionManager.registerStream(UUID.randomUUID(), d1);
        sessionManager.registerStream(UUID.randomUUID(), d2);
        sessionManager.registerStream(UUID.randomUUID(), d3);

        // d3 is disposed, so should be cleaned up
        int count = sessionManager.getActiveSessionCount();

        assertEquals(2, count);
    }

    @Test
    void should_getActiveSessionCount_returnZero_when_noStreams() {
        assertEquals(0, sessionManager.getActiveSessionCount());
    }
}
