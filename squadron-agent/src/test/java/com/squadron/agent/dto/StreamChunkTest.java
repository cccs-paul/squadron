package com.squadron.agent.dto;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class StreamChunkTest {

    @Test
    void should_buildStreamChunk_when_usingBuilder() {
        UUID conversationId = UUID.randomUUID();
        UUID messageId = UUID.randomUUID();

        StreamChunk chunk = StreamChunk.builder()
                .conversationId(conversationId)
                .messageId(messageId)
                .content("Hello world")
                .type("chunk")
                .tokenCount(50)
                .build();

        assertEquals(conversationId, chunk.getConversationId());
        assertEquals(messageId, chunk.getMessageId());
        assertEquals("Hello world", chunk.getContent());
        assertEquals("chunk", chunk.getType());
        assertEquals(50, chunk.getTokenCount());
    }

    @Test
    void should_createStreamChunk_when_usingNoArgsConstructor() {
        StreamChunk chunk = new StreamChunk();
        assertNull(chunk.getConversationId());
        assertNull(chunk.getMessageId());
        assertNull(chunk.getContent());
        assertNull(chunk.getType());
        assertNull(chunk.getTokenCount());
    }

    @Test
    void should_setAndGetFields_when_usingSetters() {
        StreamChunk chunk = new StreamChunk();
        UUID conversationId = UUID.randomUUID();
        UUID messageId = UUID.randomUUID();

        chunk.setConversationId(conversationId);
        chunk.setMessageId(messageId);
        chunk.setContent("Some content");
        chunk.setType("done");
        chunk.setTokenCount(100);

        assertEquals(conversationId, chunk.getConversationId());
        assertEquals(messageId, chunk.getMessageId());
        assertEquals("Some content", chunk.getContent());
        assertEquals("done", chunk.getType());
        assertEquals(100, chunk.getTokenCount());
    }

    @Test
    void should_beEqual_when_sameFieldValues() {
        UUID convId = UUID.randomUUID();
        UUID msgId = UUID.randomUUID();
        StreamChunk c1 = StreamChunk.builder().conversationId(convId).messageId(msgId)
                .content("test").type("chunk").tokenCount(10).build();
        StreamChunk c2 = StreamChunk.builder().conversationId(convId).messageId(msgId)
                .content("test").type("chunk").tokenCount(10).build();

        assertEquals(c1, c2);
        assertEquals(c1.hashCode(), c2.hashCode());
    }

    @Test
    void should_notBeEqual_when_differentFieldValues() {
        StreamChunk c1 = StreamChunk.builder().type("chunk").content("hello").build();
        StreamChunk c2 = StreamChunk.builder().type("done").content("hello").build();

        assertNotEquals(c1, c2);
    }

    @Test
    void should_haveToString_when_called() {
        StreamChunk chunk = StreamChunk.builder().type("chunk").content("test").build();
        String toString = chunk.toString();

        assertNotNull(toString);
        assertTrue(toString.contains("chunk"));
        assertTrue(toString.contains("test"));
    }

    @Test
    void should_createStreamChunk_when_usingAllArgsConstructor() {
        UUID convId = UUID.randomUUID();
        UUID msgId = UUID.randomUUID();

        StreamChunk chunk = new StreamChunk(convId, msgId, "content", "error", 25);

        assertEquals(convId, chunk.getConversationId());
        assertEquals(msgId, chunk.getMessageId());
        assertEquals("content", chunk.getContent());
        assertEquals("error", chunk.getType());
        assertEquals(25, chunk.getTokenCount());
    }
}
