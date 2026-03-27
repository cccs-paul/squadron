package com.squadron.agent.entity;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class ConversationMessageTest {

    @Test
    void should_buildConversationMessage_when_usingBuilder() {
        UUID id = UUID.randomUUID();
        UUID conversationId = UUID.randomUUID();

        ConversationMessage message = ConversationMessage.builder()
                .id(id)
                .conversationId(conversationId)
                .role("USER")
                .content("Hello, agent!")
                .toolCalls("{\"tool\": \"search\"}")
                .tokenCount(25)
                .build();

        assertEquals(id, message.getId());
        assertEquals(conversationId, message.getConversationId());
        assertEquals("USER", message.getRole());
        assertEquals("Hello, agent!", message.getContent());
        assertEquals("{\"tool\": \"search\"}", message.getToolCalls());
        assertEquals(25, message.getTokenCount());
    }

    @Test
    void should_createConversationMessage_when_usingNoArgsConstructor() {
        ConversationMessage message = new ConversationMessage();
        assertNull(message.getId());
        assertNull(message.getConversationId());
        assertNull(message.getRole());
        assertNull(message.getContent());
        assertNull(message.getToolCalls());
        assertNull(message.getTokenCount());
    }

    @Test
    void should_setTimestamp_when_onCreateCalled() {
        ConversationMessage message = ConversationMessage.builder()
                .conversationId(UUID.randomUUID())
                .role("ASSISTANT")
                .content("Response")
                .build();

        message.onCreate();

        assertNotNull(message.getCreatedAt());
    }

    @Test
    void should_setAndGetFields_when_usingSetters() {
        ConversationMessage message = new ConversationMessage();
        UUID conversationId = UUID.randomUUID();

        message.setConversationId(conversationId);
        message.setRole("ASSISTANT");
        message.setContent("Here is the answer");
        message.setTokenCount(50);

        assertEquals(conversationId, message.getConversationId());
        assertEquals("ASSISTANT", message.getRole());
        assertEquals("Here is the answer", message.getContent());
        assertEquals(50, message.getTokenCount());
    }

    @Test
    void should_beEqual_when_sameFieldValues() {
        UUID id = UUID.randomUUID();
        UUID convId = UUID.randomUUID();
        ConversationMessage m1 = ConversationMessage.builder().id(id).conversationId(convId).role("USER").content("test").build();
        ConversationMessage m2 = ConversationMessage.builder().id(id).conversationId(convId).role("USER").content("test").build();

        assertEquals(m1, m2);
        assertEquals(m1.hashCode(), m2.hashCode());
    }

    @Test
    void should_handleNullContent_when_contentNotSet() {
        ConversationMessage message = ConversationMessage.builder()
                .conversationId(UUID.randomUUID())
                .role("USER")
                .build();

        assertNull(message.getContent());
    }
}
