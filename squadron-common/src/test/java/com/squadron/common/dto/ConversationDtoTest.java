package com.squadron.common.dto;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class ConversationDtoTest {

    @Test
    void should_buildWithAllFields_when_builderUsed() {
        UUID id = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        UUID taskId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        Instant now = Instant.now();

        ConversationDto dto = ConversationDto.builder()
                .id(id)
                .tenantId(tenantId)
                .taskId(taskId)
                .userId(userId)
                .agentType("planner")
                .provider("openai")
                .model("gpt-4")
                .status("ACTIVE")
                .totalTokens(1500L)
                .createdAt(now)
                .build();

        assertEquals(id, dto.getId());
        assertEquals(tenantId, dto.getTenantId());
        assertEquals(taskId, dto.getTaskId());
        assertEquals(userId, dto.getUserId());
        assertEquals("planner", dto.getAgentType());
        assertEquals("openai", dto.getProvider());
        assertEquals("gpt-4", dto.getModel());
        assertEquals("ACTIVE", dto.getStatus());
        assertEquals(1500L, dto.getTotalTokens());
        assertEquals(now, dto.getCreatedAt());
    }

    @Test
    void should_createEmptyInstance_when_noArgsConstructorUsed() {
        ConversationDto dto = new ConversationDto();

        assertNull(dto.getId());
        assertNull(dto.getTenantId());
        assertNull(dto.getTaskId());
        assertNull(dto.getUserId());
        assertNull(dto.getAgentType());
        assertNull(dto.getProvider());
        assertNull(dto.getModel());
        assertNull(dto.getStatus());
        assertNull(dto.getTotalTokens());
        assertNull(dto.getCreatedAt());
    }

    @Test
    void should_createInstance_when_allArgsConstructorUsed() {
        UUID id = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        UUID taskId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        Instant now = Instant.now();

        ConversationDto dto = new ConversationDto(
                id, tenantId, taskId, userId, "coder", "anthropic",
                "claude-3", "COMPLETED", 2500L, now
        );

        assertEquals(id, dto.getId());
        assertEquals(tenantId, dto.getTenantId());
        assertEquals(taskId, dto.getTaskId());
        assertEquals(userId, dto.getUserId());
        assertEquals("coder", dto.getAgentType());
        assertEquals("anthropic", dto.getProvider());
        assertEquals("claude-3", dto.getModel());
        assertEquals("COMPLETED", dto.getStatus());
        assertEquals(2500L, dto.getTotalTokens());
        assertEquals(now, dto.getCreatedAt());
    }

    @Test
    void should_setAndGetFields_when_settersCalled() {
        ConversationDto dto = new ConversationDto();
        UUID id = UUID.randomUUID();
        dto.setId(id);
        dto.setAgentType("reviewer");
        dto.setTotalTokens(500L);

        assertEquals(id, dto.getId());
        assertEquals("reviewer", dto.getAgentType());
        assertEquals(500L, dto.getTotalTokens());
    }

    @Test
    void should_beEqual_when_sameFieldValues() {
        UUID id = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();

        ConversationDto dto1 = ConversationDto.builder()
                .id(id)
                .tenantId(tenantId)
                .agentType("planner")
                .build();

        ConversationDto dto2 = ConversationDto.builder()
                .id(id)
                .tenantId(tenantId)
                .agentType("planner")
                .build();

        assertEquals(dto1, dto2);
        assertEquals(dto1.hashCode(), dto2.hashCode());
    }

    @Test
    void should_notBeEqual_when_differentFieldValues() {
        ConversationDto dto1 = ConversationDto.builder()
                .id(UUID.randomUUID())
                .build();

        ConversationDto dto2 = ConversationDto.builder()
                .id(UUID.randomUUID())
                .build();

        assertNotEquals(dto1, dto2);
    }

    @Test
    void should_includeFieldsInToString_when_toStringCalled() {
        ConversationDto dto = ConversationDto.builder()
                .agentType("planner")
                .model("gpt-4")
                .status("ACTIVE")
                .build();

        String str = dto.toString();
        assertTrue(str.contains("planner"));
        assertTrue(str.contains("gpt-4"));
        assertTrue(str.contains("ACTIVE"));
    }

    @Test
    void should_handleNullValues_when_fieldsAreNull() {
        ConversationDto dto = ConversationDto.builder()
                .id(null)
                .tenantId(null)
                .taskId(null)
                .userId(null)
                .agentType(null)
                .totalTokens(null)
                .build();

        assertNull(dto.getId());
        assertNull(dto.getTenantId());
        assertNull(dto.getTaskId());
        assertNull(dto.getUserId());
        assertNull(dto.getAgentType());
        assertNull(dto.getTotalTokens());
    }
}
