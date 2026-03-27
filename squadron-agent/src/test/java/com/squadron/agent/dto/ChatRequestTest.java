package com.squadron.agent.dto;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class ChatRequestTest {

    @Test
    void should_buildChatRequest_when_usingBuilder() {
        UUID conversationId = UUID.randomUUID();
        UUID taskId = UUID.randomUUID();

        ChatRequest request = ChatRequest.builder()
                .conversationId(conversationId)
                .taskId(taskId)
                .agentType("CODING")
                .message("Implement the feature")
                .build();

        assertEquals(conversationId, request.getConversationId());
        assertEquals(taskId, request.getTaskId());
        assertEquals("CODING", request.getAgentType());
        assertEquals("Implement the feature", request.getMessage());
    }

    @Test
    void should_createChatRequest_when_usingNoArgsConstructor() {
        ChatRequest request = new ChatRequest();
        assertNull(request.getConversationId());
        assertNull(request.getTaskId());
        assertNull(request.getAgentType());
        assertNull(request.getMessage());
    }

    @Test
    void should_setAndGetFields_when_usingSetters() {
        ChatRequest request = new ChatRequest();
        UUID taskId = UUID.randomUUID();

        request.setTaskId(taskId);
        request.setAgentType("PLANNING");
        request.setMessage("Analyze the task");

        assertEquals(taskId, request.getTaskId());
        assertEquals("PLANNING", request.getAgentType());
        assertEquals("Analyze the task", request.getMessage());
    }

    @Test
    void should_beEqual_when_sameFieldValues() {
        UUID taskId = UUID.randomUUID();
        ChatRequest r1 = ChatRequest.builder().taskId(taskId).agentType("CODING").message("test").build();
        ChatRequest r2 = ChatRequest.builder().taskId(taskId).agentType("CODING").message("test").build();

        assertEquals(r1, r2);
        assertEquals(r1.hashCode(), r2.hashCode());
    }

    @Test
    void should_notBeEqual_when_differentFieldValues() {
        ChatRequest r1 = ChatRequest.builder().agentType("CODING").message("test").build();
        ChatRequest r2 = ChatRequest.builder().agentType("REVIEW").message("test").build();

        assertNotEquals(r1, r2);
    }

    @Test
    void should_haveToString_when_called() {
        ChatRequest request = ChatRequest.builder().agentType("CODING").message("test").build();
        String toString = request.toString();

        assertNotNull(toString);
        assertTrue(toString.contains("CODING"));
        assertTrue(toString.contains("test"));
    }

    @Test
    void should_createChatRequest_when_usingAllArgsConstructor() {
        UUID conversationId = UUID.randomUUID();
        UUID taskId = UUID.randomUUID();

        ChatRequest request = new ChatRequest(conversationId, taskId, "QA", "Verify changes");

        assertEquals(conversationId, request.getConversationId());
        assertEquals(taskId, request.getTaskId());
        assertEquals("QA", request.getAgentType());
        assertEquals("Verify changes", request.getMessage());
    }
}
