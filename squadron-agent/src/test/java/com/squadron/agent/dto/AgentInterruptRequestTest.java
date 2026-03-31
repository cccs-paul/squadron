package com.squadron.agent.dto;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class AgentInterruptRequestTest {

    @Test
    void should_createAgentInterruptRequest_when_usingNoArgsConstructor() {
        AgentInterruptRequest request = new AgentInterruptRequest();

        assertNull(request.getConversationId());
        assertNull(request.getReason());
    }

    @Test
    void should_createAgentInterruptRequest_when_usingAllArgsConstructor() {
        UUID convId = UUID.randomUUID();
        AgentInterruptRequest request = new AgentInterruptRequest(convId, "USER_CANCEL");

        assertEquals(convId, request.getConversationId());
        assertEquals("USER_CANCEL", request.getReason());
    }

    @Test
    void should_setAndGetFields_when_usingSetters() {
        AgentInterruptRequest request = new AgentInterruptRequest();
        UUID convId = UUID.randomUUID();

        request.setConversationId(convId);
        request.setReason("TIMEOUT");

        assertEquals(convId, request.getConversationId());
        assertEquals("TIMEOUT", request.getReason());
    }

    @Test
    void should_beEqual_when_sameFieldValues() {
        UUID convId = UUID.randomUUID();

        AgentInterruptRequest r1 = new AgentInterruptRequest(convId, "USER_CANCEL");
        AgentInterruptRequest r2 = new AgentInterruptRequest(convId, "USER_CANCEL");

        assertEquals(r1, r2);
        assertEquals(r1.hashCode(), r2.hashCode());
    }

    @Test
    void should_notBeEqual_when_differentFieldValues() {
        UUID convId = UUID.randomUUID();

        AgentInterruptRequest r1 = new AgentInterruptRequest(convId, "USER_CANCEL");
        AgentInterruptRequest r2 = new AgentInterruptRequest(convId, "TIMEOUT");

        assertNotEquals(r1, r2);
    }

    @Test
    void should_haveToString_when_called() {
        UUID convId = UUID.randomUUID();
        AgentInterruptRequest request = new AgentInterruptRequest(convId, "USER_PROMPT");
        String toString = request.toString();

        assertNotNull(toString);
        assertTrue(toString.contains("USER_PROMPT"));
    }
}
