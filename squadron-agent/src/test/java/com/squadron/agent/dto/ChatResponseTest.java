package com.squadron.agent.dto;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class ChatResponseTest {

    @Test
    void should_buildChatResponse_when_usingBuilder() {
        UUID conversationId = UUID.randomUUID();
        UUID messageId = UUID.randomUUID();

        ChatResponse response = ChatResponse.builder()
                .conversationId(conversationId)
                .messageId(messageId)
                .role("ASSISTANT")
                .content("Here is my response")
                .tokenCount(150)
                .status("ACTIVE")
                .build();

        assertEquals(conversationId, response.getConversationId());
        assertEquals(messageId, response.getMessageId());
        assertEquals("ASSISTANT", response.getRole());
        assertEquals("Here is my response", response.getContent());
        assertEquals(150, response.getTokenCount());
        assertEquals("ACTIVE", response.getStatus());
    }

    @Test
    void should_createChatResponse_when_usingNoArgsConstructor() {
        ChatResponse response = new ChatResponse();
        assertNull(response.getConversationId());
        assertNull(response.getMessageId());
        assertNull(response.getRole());
        assertNull(response.getContent());
        assertNull(response.getTokenCount());
        assertNull(response.getStatus());
    }

    @Test
    void should_setAndGetFields_when_usingSetters() {
        ChatResponse response = new ChatResponse();
        UUID conversationId = UUID.randomUUID();
        UUID messageId = UUID.randomUUID();

        response.setConversationId(conversationId);
        response.setMessageId(messageId);
        response.setRole("ASSISTANT");
        response.setContent("Response content");
        response.setTokenCount(200);
        response.setStatus("COMPLETED");

        assertEquals(conversationId, response.getConversationId());
        assertEquals(messageId, response.getMessageId());
        assertEquals("ASSISTANT", response.getRole());
        assertEquals("Response content", response.getContent());
        assertEquals(200, response.getTokenCount());
        assertEquals("COMPLETED", response.getStatus());
    }

    @Test
    void should_beEqual_when_sameFieldValues() {
        UUID convId = UUID.randomUUID();
        UUID msgId = UUID.randomUUID();
        ChatResponse r1 = ChatResponse.builder().conversationId(convId).messageId(msgId)
                .role("ASSISTANT").content("test").tokenCount(10).status("ACTIVE").build();
        ChatResponse r2 = ChatResponse.builder().conversationId(convId).messageId(msgId)
                .role("ASSISTANT").content("test").tokenCount(10).status("ACTIVE").build();

        assertEquals(r1, r2);
        assertEquals(r1.hashCode(), r2.hashCode());
    }

    @Test
    void should_notBeEqual_when_differentFieldValues() {
        ChatResponse r1 = ChatResponse.builder().role("ASSISTANT").content("hello").build();
        ChatResponse r2 = ChatResponse.builder().role("USER").content("hello").build();

        assertNotEquals(r1, r2);
    }

    @Test
    void should_haveToString_when_called() {
        ChatResponse response = ChatResponse.builder().role("ASSISTANT").content("test").build();
        String toString = response.toString();

        assertNotNull(toString);
        assertTrue(toString.contains("ASSISTANT"));
    }
}
