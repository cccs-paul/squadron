package com.squadron.agent.provider;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ChatMessageTest {

    @Test
    void should_buildChatMessage_when_usingBuilder() {
        ChatMessage message = ChatMessage.builder()
                .role("USER")
                .content("Hello, how are you?")
                .build();

        assertEquals("USER", message.getRole());
        assertEquals("Hello, how are you?", message.getContent());
    }

    @Test
    void should_createChatMessage_when_usingNoArgsConstructor() {
        ChatMessage message = new ChatMessage();
        assertNull(message.getRole());
        assertNull(message.getContent());
    }

    @Test
    void should_createChatMessage_when_usingAllArgsConstructor() {
        ChatMessage message = new ChatMessage("ASSISTANT", "Here is my response");

        assertEquals("ASSISTANT", message.getRole());
        assertEquals("Here is my response", message.getContent());
    }

    @Test
    void should_setAndGetFields_when_usingSetters() {
        ChatMessage message = new ChatMessage();
        message.setRole("SYSTEM");
        message.setContent("You are a helpful assistant");

        assertEquals("SYSTEM", message.getRole());
        assertEquals("You are a helpful assistant", message.getContent());
    }

    @Test
    void should_beEqual_when_sameFieldValues() {
        ChatMessage m1 = ChatMessage.builder().role("USER").content("test").build();
        ChatMessage m2 = ChatMessage.builder().role("USER").content("test").build();

        assertEquals(m1, m2);
        assertEquals(m1.hashCode(), m2.hashCode());
    }

    @Test
    void should_notBeEqual_when_differentFieldValues() {
        ChatMessage m1 = ChatMessage.builder().role("USER").content("test").build();
        ChatMessage m2 = ChatMessage.builder().role("ASSISTANT").content("test").build();

        assertNotEquals(m1, m2);
    }

    @Test
    void should_haveToString_when_called() {
        ChatMessage message = ChatMessage.builder().role("USER").content("hello").build();
        String toString = message.toString();

        assertNotNull(toString);
        assertTrue(toString.contains("USER"));
        assertTrue(toString.contains("hello"));
    }
}
