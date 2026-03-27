package com.squadron.common.dto;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class ConversationMessageDtoTest {

    @Test
    void should_buildWithAllFields_when_builderUsed() {
        UUID id = UUID.randomUUID();
        UUID conversationId = UUID.randomUUID();
        Map<String, Object> toolCalls = Map.of("function", "search", "args", Map.of("query", "test"));
        Instant now = Instant.now();

        ConversationMessageDto dto = ConversationMessageDto.builder()
                .id(id)
                .conversationId(conversationId)
                .role("assistant")
                .content("Hello, how can I help?")
                .toolCalls(toolCalls)
                .tokenCount(150)
                .createdAt(now)
                .build();

        assertEquals(id, dto.getId());
        assertEquals(conversationId, dto.getConversationId());
        assertEquals("assistant", dto.getRole());
        assertEquals("Hello, how can I help?", dto.getContent());
        assertEquals(toolCalls, dto.getToolCalls());
        assertEquals(150, dto.getTokenCount());
        assertEquals(now, dto.getCreatedAt());
    }

    @Test
    void should_createEmptyInstance_when_noArgsConstructorUsed() {
        ConversationMessageDto dto = new ConversationMessageDto();

        assertNull(dto.getId());
        assertNull(dto.getConversationId());
        assertNull(dto.getRole());
        assertNull(dto.getContent());
        assertNull(dto.getToolCalls());
        assertNull(dto.getTokenCount());
        assertNull(dto.getCreatedAt());
    }

    @Test
    void should_createInstance_when_allArgsConstructorUsed() {
        UUID id = UUID.randomUUID();
        UUID conversationId = UUID.randomUUID();
        Map<String, Object> toolCalls = Map.of("fn", "read_file");
        Instant now = Instant.now();

        ConversationMessageDto dto = new ConversationMessageDto(
                id, conversationId, "user", "Fix the bug", toolCalls, 42, now
        );

        assertEquals(id, dto.getId());
        assertEquals(conversationId, dto.getConversationId());
        assertEquals("user", dto.getRole());
        assertEquals("Fix the bug", dto.getContent());
        assertEquals(toolCalls, dto.getToolCalls());
        assertEquals(42, dto.getTokenCount());
        assertEquals(now, dto.getCreatedAt());
    }

    @Test
    void should_setAndGetFields_when_settersCalled() {
        ConversationMessageDto dto = new ConversationMessageDto();
        UUID id = UUID.randomUUID();
        dto.setId(id);
        dto.setRole("system");
        dto.setContent("You are a helpful assistant.");
        dto.setTokenCount(25);

        assertEquals(id, dto.getId());
        assertEquals("system", dto.getRole());
        assertEquals("You are a helpful assistant.", dto.getContent());
        assertEquals(25, dto.getTokenCount());
    }

    @Test
    void should_beEqual_when_sameFieldValues() {
        UUID id = UUID.randomUUID();
        UUID conversationId = UUID.randomUUID();

        ConversationMessageDto dto1 = ConversationMessageDto.builder()
                .id(id)
                .conversationId(conversationId)
                .role("user")
                .content("hello")
                .build();

        ConversationMessageDto dto2 = ConversationMessageDto.builder()
                .id(id)
                .conversationId(conversationId)
                .role("user")
                .content("hello")
                .build();

        assertEquals(dto1, dto2);
        assertEquals(dto1.hashCode(), dto2.hashCode());
    }

    @Test
    void should_notBeEqual_when_differentFieldValues() {
        ConversationMessageDto dto1 = ConversationMessageDto.builder()
                .role("user")
                .content("hello")
                .build();

        ConversationMessageDto dto2 = ConversationMessageDto.builder()
                .role("assistant")
                .content("hi")
                .build();

        assertNotEquals(dto1, dto2);
    }

    @Test
    void should_includeFieldsInToString_when_toStringCalled() {
        ConversationMessageDto dto = ConversationMessageDto.builder()
                .role("assistant")
                .content("Test content")
                .build();

        String str = dto.toString();
        assertTrue(str.contains("assistant"));
        assertTrue(str.contains("Test content"));
    }

    @Test
    void should_handleNullValues_when_fieldsAreNull() {
        ConversationMessageDto dto = ConversationMessageDto.builder()
                .id(null)
                .conversationId(null)
                .role(null)
                .content(null)
                .toolCalls(null)
                .tokenCount(null)
                .createdAt(null)
                .build();

        assertNull(dto.getId());
        assertNull(dto.getConversationId());
        assertNull(dto.getRole());
        assertNull(dto.getContent());
        assertNull(dto.getToolCalls());
        assertNull(dto.getTokenCount());
        assertNull(dto.getCreatedAt());
    }

    @Test
    void should_handleEmptyToolCalls_when_emptyMapProvided() {
        ConversationMessageDto dto = ConversationMessageDto.builder()
                .toolCalls(Map.of())
                .build();

        assertNotNull(dto.getToolCalls());
        assertTrue(dto.getToolCalls().isEmpty());
    }
}
