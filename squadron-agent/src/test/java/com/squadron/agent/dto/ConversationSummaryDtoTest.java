package com.squadron.agent.dto;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class ConversationSummaryDtoTest {

    @Test
    void should_buildConversationSummaryDto_when_usingBuilder() {
        UUID id = UUID.randomUUID();
        UUID taskId = UUID.randomUUID();
        Instant now = Instant.now();

        ConversationSummaryDto dto = ConversationSummaryDto.builder()
                .id(id)
                .taskId(taskId)
                .agentType("CODING")
                .status("ACTIVE")
                .provider("openai")
                .model("gpt-4")
                .totalTokens(1500L)
                .messageCount(12L)
                .createdAt(now)
                .updatedAt(now)
                .build();

        assertEquals(id, dto.getId());
        assertEquals(taskId, dto.getTaskId());
        assertEquals("CODING", dto.getAgentType());
        assertEquals("ACTIVE", dto.getStatus());
        assertEquals("openai", dto.getProvider());
        assertEquals("gpt-4", dto.getModel());
        assertEquals(1500L, dto.getTotalTokens());
        assertEquals(12L, dto.getMessageCount());
        assertEquals(now, dto.getCreatedAt());
        assertEquals(now, dto.getUpdatedAt());
    }

    @Test
    void should_createConversationSummaryDto_when_usingNoArgsConstructor() {
        ConversationSummaryDto dto = new ConversationSummaryDto();

        assertNull(dto.getId());
        assertNull(dto.getTaskId());
        assertNull(dto.getAgentType());
        assertNull(dto.getStatus());
        assertNull(dto.getProvider());
        assertNull(dto.getModel());
        assertEquals(0L, dto.getTotalTokens());
        assertEquals(0L, dto.getMessageCount());
        assertNull(dto.getCreatedAt());
        assertNull(dto.getUpdatedAt());
    }

    @Test
    void should_setAndGetFields_when_usingSetters() {
        ConversationSummaryDto dto = new ConversationSummaryDto();
        UUID id = UUID.randomUUID();
        UUID taskId = UUID.randomUUID();
        Instant created = Instant.now().minusSeconds(60);
        Instant updated = Instant.now();

        dto.setId(id);
        dto.setTaskId(taskId);
        dto.setAgentType("PLANNING");
        dto.setStatus("COMPLETED");
        dto.setProvider("anthropic");
        dto.setModel("claude-3");
        dto.setTotalTokens(2000L);
        dto.setMessageCount(8L);
        dto.setCreatedAt(created);
        dto.setUpdatedAt(updated);

        assertEquals(id, dto.getId());
        assertEquals(taskId, dto.getTaskId());
        assertEquals("PLANNING", dto.getAgentType());
        assertEquals("COMPLETED", dto.getStatus());
        assertEquals("anthropic", dto.getProvider());
        assertEquals("claude-3", dto.getModel());
        assertEquals(2000L, dto.getTotalTokens());
        assertEquals(8L, dto.getMessageCount());
        assertEquals(created, dto.getCreatedAt());
        assertEquals(updated, dto.getUpdatedAt());
    }

    @Test
    void should_beEqual_when_sameFieldValues() {
        UUID id = UUID.randomUUID();
        UUID taskId = UUID.randomUUID();
        Instant now = Instant.now();

        ConversationSummaryDto dto1 = ConversationSummaryDto.builder()
                .id(id)
                .taskId(taskId)
                .agentType("CODING")
                .status("ACTIVE")
                .provider("openai")
                .model("gpt-4")
                .totalTokens(500L)
                .messageCount(5L)
                .createdAt(now)
                .updatedAt(now)
                .build();

        ConversationSummaryDto dto2 = ConversationSummaryDto.builder()
                .id(id)
                .taskId(taskId)
                .agentType("CODING")
                .status("ACTIVE")
                .provider("openai")
                .model("gpt-4")
                .totalTokens(500L)
                .messageCount(5L)
                .createdAt(now)
                .updatedAt(now)
                .build();

        assertEquals(dto1, dto2);
        assertEquals(dto1.hashCode(), dto2.hashCode());
    }

    @Test
    void should_notBeEqual_when_differentFieldValues() {
        ConversationSummaryDto dto1 = ConversationSummaryDto.builder()
                .agentType("CODING")
                .status("ACTIVE")
                .build();

        ConversationSummaryDto dto2 = ConversationSummaryDto.builder()
                .agentType("PLANNING")
                .status("COMPLETED")
                .build();

        assertNotEquals(dto1, dto2);
    }

    @Test
    void should_haveToString_when_called() {
        ConversationSummaryDto dto = ConversationSummaryDto.builder()
                .agentType("REVIEW")
                .status("ACTIVE")
                .provider("anthropic")
                .model("claude-3")
                .build();

        String toString = dto.toString();

        assertNotNull(toString);
        assertTrue(toString.contains("REVIEW"));
        assertTrue(toString.contains("ACTIVE"));
        assertTrue(toString.contains("anthropic"));
        assertTrue(toString.contains("claude-3"));
    }

    @Test
    void should_createConversationSummaryDto_when_usingAllArgsConstructor() {
        UUID id = UUID.randomUUID();
        UUID taskId = UUID.randomUUID();
        Instant created = Instant.now().minusSeconds(120);
        Instant updated = Instant.now();

        ConversationSummaryDto dto = new ConversationSummaryDto(
                id, taskId, "CODING", "ACTIVE", "openai", "gpt-4",
                3000L, 25L, created, updated);

        assertEquals(id, dto.getId());
        assertEquals(taskId, dto.getTaskId());
        assertEquals("CODING", dto.getAgentType());
        assertEquals("ACTIVE", dto.getStatus());
        assertEquals("openai", dto.getProvider());
        assertEquals("gpt-4", dto.getModel());
        assertEquals(3000L, dto.getTotalTokens());
        assertEquals(25L, dto.getMessageCount());
        assertEquals(created, dto.getCreatedAt());
        assertEquals(updated, dto.getUpdatedAt());
    }
}
