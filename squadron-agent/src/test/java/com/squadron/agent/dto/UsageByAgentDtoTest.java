package com.squadron.agent.dto;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class UsageByAgentDtoTest {

    @Test
    void should_buildUsageByAgentDto_when_usingBuilder() {
        UsageByAgentDto dto = UsageByAgentDto.builder()
                .agentType("CODING")
                .totalTokens(5000)
                .totalCost(0.03)
                .invocations(2)
                .build();

        assertEquals("CODING", dto.getAgentType());
        assertEquals(5000, dto.getTotalTokens());
        assertEquals(0.03, dto.getTotalCost(), 0.001);
        assertEquals(2, dto.getInvocations());
    }

    @Test
    void should_createUsageByAgentDto_when_usingNoArgsConstructor() {
        UsageByAgentDto dto = new UsageByAgentDto();

        assertNull(dto.getAgentType());
        assertEquals(0, dto.getTotalTokens());
        assertEquals(0.0, dto.getTotalCost());
        assertEquals(0, dto.getInvocations());
    }

    @Test
    void should_createUsageByAgentDto_when_usingAllArgsConstructor() {
        UsageByAgentDto dto = new UsageByAgentDto("REVIEW", 3000, 0.02, 1);

        assertEquals("REVIEW", dto.getAgentType());
        assertEquals(3000, dto.getTotalTokens());
        assertEquals(0.02, dto.getTotalCost(), 0.001);
        assertEquals(1, dto.getInvocations());
    }

    @Test
    void should_setAndGetFields_when_usingSetters() {
        UsageByAgentDto dto = new UsageByAgentDto();

        dto.setAgentType("PLANNING");
        dto.setTotalTokens(10000);
        dto.setTotalCost(0.05);
        dto.setInvocations(4);

        assertEquals("PLANNING", dto.getAgentType());
        assertEquals(10000, dto.getTotalTokens());
        assertEquals(0.05, dto.getTotalCost(), 0.001);
        assertEquals(4, dto.getInvocations());
    }

    @Test
    void should_beEqual_when_sameFieldValues() {
        UsageByAgentDto d1 = UsageByAgentDto.builder()
                .agentType("CODING").totalTokens(1000).totalCost(0.01).invocations(1).build();
        UsageByAgentDto d2 = UsageByAgentDto.builder()
                .agentType("CODING").totalTokens(1000).totalCost(0.01).invocations(1).build();

        assertEquals(d1, d2);
        assertEquals(d1.hashCode(), d2.hashCode());
    }

    @Test
    void should_notBeEqual_when_differentFieldValues() {
        UsageByAgentDto d1 = UsageByAgentDto.builder().agentType("CODING").totalTokens(1000).build();
        UsageByAgentDto d2 = UsageByAgentDto.builder().agentType("REVIEW").totalTokens(2000).build();

        assertNotEquals(d1, d2);
    }

    @Test
    void should_haveToString_when_called() {
        UsageByAgentDto dto = UsageByAgentDto.builder()
                .agentType("QA")
                .totalTokens(500)
                .totalCost(0.005)
                .invocations(1)
                .build();

        String toString = dto.toString();
        assertNotNull(toString);
        assertTrue(toString.contains("QA"));
        assertTrue(toString.contains("500"));
    }
}
