package com.squadron.review.dto;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CreateReviewBotConfigRequestTest {

    @Test
    void should_buildRequest_when_usingBuilder() {
        UUID tenantId = UUID.randomUUID();
        UUID connectionId = UUID.randomUUID();

        CreateReviewBotConfigRequest request = CreateReviewBotConfigRequest.builder()
                .tenantId(tenantId)
                .connectionId(connectionId)
                .botUsername("squadron-bot")
                .botAccessToken("my-token")
                .enabled(true)
                .autoAssign(false)
                .build();

        assertEquals(tenantId, request.getTenantId());
        assertEquals(connectionId, request.getConnectionId());
        assertEquals("squadron-bot", request.getBotUsername());
        assertEquals("my-token", request.getBotAccessToken());
        assertTrue(request.isEnabled());
        assertEquals(false, request.isAutoAssign());
    }

    @Test
    void should_setDefaults_when_notExplicitlySet() {
        CreateReviewBotConfigRequest request = CreateReviewBotConfigRequest.builder()
                .tenantId(UUID.randomUUID())
                .connectionId(UUID.randomUUID())
                .botUsername("bot")
                .botAccessToken("token")
                .build();

        assertTrue(request.isEnabled());
        assertTrue(request.isAutoAssign());
    }

    @Test
    void should_useNoArgsConstructor() {
        CreateReviewBotConfigRequest request = new CreateReviewBotConfigRequest();
        assertNull(request.getTenantId());
        assertNull(request.getBotUsername());
    }

    @Test
    void should_supportSetters() {
        CreateReviewBotConfigRequest request = new CreateReviewBotConfigRequest();
        UUID tenantId = UUID.randomUUID();
        request.setTenantId(tenantId);
        request.setBotUsername("new-bot");
        request.setEnabled(false);

        assertEquals(tenantId, request.getTenantId());
        assertEquals("new-bot", request.getBotUsername());
        assertEquals(false, request.isEnabled());
    }

    @Test
    void should_supportEqualsAndHashCode() {
        UUID tenantId = UUID.randomUUID();
        UUID connectionId = UUID.randomUUID();

        CreateReviewBotConfigRequest r1 = CreateReviewBotConfigRequest.builder()
                .tenantId(tenantId).connectionId(connectionId).botUsername("bot").botAccessToken("t").build();
        CreateReviewBotConfigRequest r2 = CreateReviewBotConfigRequest.builder()
                .tenantId(tenantId).connectionId(connectionId).botUsername("bot").botAccessToken("t").build();

        assertEquals(r1, r2);
        assertEquals(r1.hashCode(), r2.hashCode());
    }

    @Test
    void should_supportToString() {
        CreateReviewBotConfigRequest request = CreateReviewBotConfigRequest.builder()
                .botUsername("bot")
                .build();
        assertNotNull(request.toString());
    }
}
