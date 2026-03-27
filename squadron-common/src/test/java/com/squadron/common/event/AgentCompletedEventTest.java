package com.squadron.common.event;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class AgentCompletedEventTest {

    @Test
    void should_setEventType_when_defaultConstructorUsed() {
        AgentCompletedEvent event = new AgentCompletedEvent();

        assertEquals("AGENT_COMPLETED", event.getEventType());
    }

    @Test
    void should_setEventType_when_allArgsConstructorUsed() {
        UUID taskId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UUID conversationId = UUID.randomUUID();

        AgentCompletedEvent event = new AgentCompletedEvent(taskId, userId, "PLANNER", conversationId, true, 1500L);

        assertEquals("AGENT_COMPLETED", event.getEventType());
    }

    @Test
    void should_setAllFields_when_allArgsConstructorUsed() {
        UUID taskId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UUID conversationId = UUID.randomUUID();

        AgentCompletedEvent event = new AgentCompletedEvent(taskId, userId, "CODER", conversationId, true, 2500L);

        assertEquals(taskId, event.getTaskId());
        assertEquals(userId, event.getUserId());
        assertEquals("CODER", event.getAgentType());
        assertEquals(conversationId, event.getConversationId());
        assertTrue(event.isSuccess());
        assertEquals(2500L, event.getTokenCount());
    }

    @Test
    void should_haveDefaultValues_when_defaultConstructorUsed() {
        AgentCompletedEvent event = new AgentCompletedEvent();

        assertNull(event.getTaskId());
        assertNull(event.getUserId());
        assertNull(event.getAgentType());
        assertNull(event.getConversationId());
        assertFalse(event.isSuccess());
        assertEquals(0L, event.getTokenCount());
    }

    @Test
    void should_inheritBaseEventFields_when_created() {
        AgentCompletedEvent event = new AgentCompletedEvent();

        assertNotNull(event.getEventId());
        assertNotNull(event.getTimestamp());
    }

    @Test
    void should_allowSettingFields_when_settersUsed() {
        AgentCompletedEvent event = new AgentCompletedEvent();
        UUID taskId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UUID conversationId = UUID.randomUUID();

        event.setTaskId(taskId);
        event.setUserId(userId);
        event.setAgentType("REVIEWER");
        event.setConversationId(conversationId);
        event.setSuccess(false);
        event.setTokenCount(999L);

        assertEquals(taskId, event.getTaskId());
        assertEquals(userId, event.getUserId());
        assertEquals("REVIEWER", event.getAgentType());
        assertEquals(conversationId, event.getConversationId());
        assertFalse(event.isSuccess());
        assertEquals(999L, event.getTokenCount());
    }

    @Test
    void should_beInstanceOfSquadronEvent_when_created() {
        AgentCompletedEvent event = new AgentCompletedEvent();

        assertInstanceOf(SquadronEvent.class, event);
    }
}
