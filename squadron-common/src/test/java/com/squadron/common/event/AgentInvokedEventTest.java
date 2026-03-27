package com.squadron.common.event;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class AgentInvokedEventTest {

    @Test
    void should_setEventType_when_defaultConstructorUsed() {
        AgentInvokedEvent event = new AgentInvokedEvent();

        assertEquals("AGENT_INVOKED", event.getEventType());
    }

    @Test
    void should_setEventType_when_allArgsConstructorUsed() {
        UUID taskId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UUID conversationId = UUID.randomUUID();

        AgentInvokedEvent event = new AgentInvokedEvent(taskId, userId, "PLANNER", conversationId);

        assertEquals("AGENT_INVOKED", event.getEventType());
    }

    @Test
    void should_setAllFields_when_allArgsConstructorUsed() {
        UUID taskId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UUID conversationId = UUID.randomUUID();

        AgentInvokedEvent event = new AgentInvokedEvent(taskId, userId, "CODER", conversationId);

        assertEquals(taskId, event.getTaskId());
        assertEquals(userId, event.getUserId());
        assertEquals("CODER", event.getAgentType());
        assertEquals(conversationId, event.getConversationId());
    }

    @Test
    void should_haveNullFields_when_defaultConstructorUsed() {
        AgentInvokedEvent event = new AgentInvokedEvent();

        assertNull(event.getTaskId());
        assertNull(event.getUserId());
        assertNull(event.getAgentType());
        assertNull(event.getConversationId());
    }

    @Test
    void should_inheritBaseEventFields_when_created() {
        AgentInvokedEvent event = new AgentInvokedEvent();

        assertNotNull(event.getEventId());
        assertNotNull(event.getTimestamp());
    }

    @Test
    void should_allowSettingFields_when_settersUsed() {
        AgentInvokedEvent event = new AgentInvokedEvent();
        UUID taskId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UUID conversationId = UUID.randomUUID();

        event.setTaskId(taskId);
        event.setUserId(userId);
        event.setAgentType("QA");
        event.setConversationId(conversationId);

        assertEquals(taskId, event.getTaskId());
        assertEquals(userId, event.getUserId());
        assertEquals("QA", event.getAgentType());
        assertEquals(conversationId, event.getConversationId());
    }

    @Test
    void should_beInstanceOfSquadronEvent_when_created() {
        AgentInvokedEvent event = new AgentInvokedEvent();

        assertInstanceOf(SquadronEvent.class, event);
    }
}
