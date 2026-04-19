package com.squadron.agent.dto;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class AgentTestRequestTest {

    @Test
    void should_buildWithAllFields() {
        UUID configId = UUID.randomUUID();
        AgentTestRequest request = AgentTestRequest.builder()
                .agentConfigId(configId)
                .testMode("PLANNING")
                .build();

        assertEquals(configId, request.getAgentConfigId());
        assertEquals("PLANNING", request.getTestMode());
    }

    @Test
    void should_supportNoArgsConstructor() {
        AgentTestRequest request = new AgentTestRequest();
        assertNull(request.getAgentConfigId());
        assertNull(request.getTestMode());
    }

    @Test
    void should_supportSettersAndGetters() {
        UUID configId = UUID.randomUUID();
        AgentTestRequest request = new AgentTestRequest();
        request.setAgentConfigId(configId);
        request.setTestMode("CODE_REVIEW");

        assertEquals(configId, request.getAgentConfigId());
        assertEquals("CODE_REVIEW", request.getTestMode());
    }

    @Test
    void should_supportAllArgsConstructor() {
        UUID configId = UUID.randomUUID();
        AgentTestRequest request = new AgentTestRequest(configId, "CODE_GENERATION");

        assertEquals(configId, request.getAgentConfigId());
        assertEquals("CODE_GENERATION", request.getTestMode());
    }
}
