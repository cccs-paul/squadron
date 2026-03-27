package com.squadron.agent.dto;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class TokenUsageDtoTest {

    @Test
    void should_buildTokenUsageDto_when_usingBuilder() {
        UUID tenantId = UUID.randomUUID();
        UUID teamId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();

        TokenUsageDto dto = TokenUsageDto.builder()
                .tenantId(tenantId)
                .teamId(teamId)
                .userId(userId)
                .agentType("CODING")
                .totalTokens(50000L)
                .period("2026-03")
                .build();

        assertEquals(tenantId, dto.getTenantId());
        assertEquals(teamId, dto.getTeamId());
        assertEquals(userId, dto.getUserId());
        assertEquals("CODING", dto.getAgentType());
        assertEquals(50000L, dto.getTotalTokens());
        assertEquals("2026-03", dto.getPeriod());
    }

    @Test
    void should_createTokenUsageDto_when_usingNoArgsConstructor() {
        TokenUsageDto dto = new TokenUsageDto();
        assertNull(dto.getTenantId());
        assertNull(dto.getTeamId());
        assertNull(dto.getUserId());
        assertNull(dto.getAgentType());
        assertNull(dto.getTotalTokens());
        assertNull(dto.getPeriod());
    }

    @Test
    void should_setAndGetFields_when_usingSetters() {
        TokenUsageDto dto = new TokenUsageDto();
        UUID tenantId = UUID.randomUUID();

        dto.setTenantId(tenantId);
        dto.setAgentType("PLANNING");
        dto.setTotalTokens(10000L);
        dto.setPeriod("2026-01");

        assertEquals(tenantId, dto.getTenantId());
        assertEquals("PLANNING", dto.getAgentType());
        assertEquals(10000L, dto.getTotalTokens());
        assertEquals("2026-01", dto.getPeriod());
    }

    @Test
    void should_beEqual_when_sameFieldValues() {
        UUID tenantId = UUID.randomUUID();
        TokenUsageDto d1 = TokenUsageDto.builder().tenantId(tenantId).agentType("CODING").totalTokens(100L).build();
        TokenUsageDto d2 = TokenUsageDto.builder().tenantId(tenantId).agentType("CODING").totalTokens(100L).build();

        assertEquals(d1, d2);
        assertEquals(d1.hashCode(), d2.hashCode());
    }

    @Test
    void should_notBeEqual_when_differentFieldValues() {
        TokenUsageDto d1 = TokenUsageDto.builder().agentType("CODING").totalTokens(100L).build();
        TokenUsageDto d2 = TokenUsageDto.builder().agentType("CODING").totalTokens(200L).build();

        assertNotEquals(d1, d2);
    }

    @Test
    void should_createTokenUsageDto_when_usingAllArgsConstructor() {
        UUID tenantId = UUID.randomUUID();
        UUID teamId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();

        TokenUsageDto dto = new TokenUsageDto(tenantId, teamId, userId, "REVIEW", 3000L, "2026-02");

        assertEquals(tenantId, dto.getTenantId());
        assertEquals(teamId, dto.getTeamId());
        assertEquals(userId, dto.getUserId());
        assertEquals("REVIEW", dto.getAgentType());
        assertEquals(3000L, dto.getTotalTokens());
        assertEquals("2026-02", dto.getPeriod());
    }
}
