package com.squadron.agent.entity;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class ConversationTest {

    @Test
    void should_buildConversation_when_usingBuilder() {
        UUID id = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        UUID taskId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();

        Conversation conversation = Conversation.builder()
                .id(id)
                .tenantId(tenantId)
                .taskId(taskId)
                .userId(userId)
                .agentType("CODING")
                .provider("openai-compatible")
                .model("gpt-4")
                .status("ACTIVE")
                .totalTokens(500L)
                .build();

        assertEquals(id, conversation.getId());
        assertEquals(tenantId, conversation.getTenantId());
        assertEquals(taskId, conversation.getTaskId());
        assertEquals(userId, conversation.getUserId());
        assertEquals("CODING", conversation.getAgentType());
        assertEquals("openai-compatible", conversation.getProvider());
        assertEquals("gpt-4", conversation.getModel());
        assertEquals("ACTIVE", conversation.getStatus());
        assertEquals(500L, conversation.getTotalTokens());
    }

    @Test
    void should_haveDefaultValues_when_usingBuilder() {
        Conversation conversation = Conversation.builder()
                .tenantId(UUID.randomUUID())
                .taskId(UUID.randomUUID())
                .userId(UUID.randomUUID())
                .agentType("PLANNING")
                .build();

        assertEquals("ACTIVE", conversation.getStatus());
        assertEquals(0L, conversation.getTotalTokens());
    }

    @Test
    void should_setTimestamps_when_onCreateCalled() {
        Conversation conversation = Conversation.builder()
                .tenantId(UUID.randomUUID())
                .taskId(UUID.randomUUID())
                .userId(UUID.randomUUID())
                .agentType("CODING")
                .build();

        conversation.onCreate();

        assertNotNull(conversation.getCreatedAt());
        assertNotNull(conversation.getUpdatedAt());
        assertEquals(conversation.getCreatedAt(), conversation.getUpdatedAt());
    }

    @Test
    void should_updateTimestamp_when_onUpdateCalled() throws InterruptedException {
        Conversation conversation = Conversation.builder()
                .tenantId(UUID.randomUUID())
                .taskId(UUID.randomUUID())
                .userId(UUID.randomUUID())
                .agentType("CODING")
                .build();

        conversation.onCreate();
        Instant createdAt = conversation.getCreatedAt();

        // Small delay to ensure different timestamp
        Thread.sleep(5);
        conversation.onUpdate();

        assertEquals(createdAt, conversation.getCreatedAt());
        assertNotNull(conversation.getUpdatedAt());
        assertTrue(conversation.getUpdatedAt().isAfter(createdAt) || conversation.getUpdatedAt().equals(createdAt));
    }

    @Test
    void should_setAndGetFields_when_usingSetters() {
        Conversation conversation = new Conversation();
        UUID tenantId = UUID.randomUUID();

        conversation.setTenantId(tenantId);
        conversation.setAgentType("REVIEW");
        conversation.setStatus("COMPLETED");
        conversation.setTotalTokens(1000L);
        conversation.setProvider("openai-compatible");
        conversation.setModel("gpt-4-turbo");

        assertEquals(tenantId, conversation.getTenantId());
        assertEquals("REVIEW", conversation.getAgentType());
        assertEquals("COMPLETED", conversation.getStatus());
        assertEquals(1000L, conversation.getTotalTokens());
        assertEquals("openai-compatible", conversation.getProvider());
        assertEquals("gpt-4-turbo", conversation.getModel());
    }

    @Test
    void should_beEqual_when_sameFieldValues() {
        UUID id = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        Conversation c1 = Conversation.builder().id(id).tenantId(tenantId).agentType("CODING").status("ACTIVE").totalTokens(0L).build();
        Conversation c2 = Conversation.builder().id(id).tenantId(tenantId).agentType("CODING").status("ACTIVE").totalTokens(0L).build();

        assertEquals(c1, c2);
        assertEquals(c1.hashCode(), c2.hashCode());
    }
}
