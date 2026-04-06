package com.squadron.review.entity;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ReviewBotConfigTest {

    @Test
    void should_buildConfig_when_usingBuilder() {
        UUID tenantId = UUID.randomUUID();
        UUID connectionId = UUID.randomUUID();

        ReviewBotConfig config = ReviewBotConfig.builder()
                .tenantId(tenantId)
                .connectionId(connectionId)
                .botUsername("squadron-bot")
                .botAccessToken("encrypted-token-value")
                .build();

        assertEquals(tenantId, config.getTenantId());
        assertEquals(connectionId, config.getConnectionId());
        assertEquals("squadron-bot", config.getBotUsername());
        assertEquals("encrypted-token-value", config.getBotAccessToken());
        assertTrue(config.isEnabled());
        assertTrue(config.isAutoAssign());
    }

    @Test
    void should_setDefaults_when_notExplicitlySet() {
        ReviewBotConfig config = ReviewBotConfig.builder()
                .tenantId(UUID.randomUUID())
                .connectionId(UUID.randomUUID())
                .botUsername("bot")
                .botAccessToken("token")
                .build();

        assertTrue(config.isEnabled());
        assertTrue(config.isAutoAssign());
    }

    @Test
    void should_setTimestamps_when_onCreateCalled() {
        ReviewBotConfig config = ReviewBotConfig.builder()
                .tenantId(UUID.randomUUID())
                .connectionId(UUID.randomUUID())
                .botUsername("bot")
                .botAccessToken("token")
                .build();

        config.onCreate();

        assertNotNull(config.getCreatedAt());
        assertNotNull(config.getUpdatedAt());
        // createdAt and updatedAt are set via two separate Instant.now() calls,
        // so they may differ by a few nanoseconds
        assertTrue(Math.abs(config.getCreatedAt().toEpochMilli() - config.getUpdatedAt().toEpochMilli()) < 100);
    }

    @Test
    void should_updateTimestamp_when_onUpdateCalled() throws InterruptedException {
        ReviewBotConfig config = ReviewBotConfig.builder()
                .tenantId(UUID.randomUUID())
                .connectionId(UUID.randomUUID())
                .botUsername("bot")
                .botAccessToken("token")
                .build();

        config.onCreate();
        Instant originalCreated = config.getCreatedAt();

        Thread.sleep(10);
        config.onUpdate();

        assertNotNull(config.getUpdatedAt());
        assertEquals(originalCreated, config.getCreatedAt());
    }

    @Test
    void should_useNoArgsConstructor() {
        ReviewBotConfig config = new ReviewBotConfig();
        assertNull(config.getId());
        assertNull(config.getTenantId());
        assertNull(config.getBotUsername());
    }

    @Test
    void should_useAllArgsConstructor() {
        UUID id = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        UUID connectionId = UUID.randomUUID();
        Instant now = Instant.now();

        ReviewBotConfig config = new ReviewBotConfig(
                id, tenantId, connectionId, "bot-user", "enc-token",
                true, false, now, now);

        assertEquals(id, config.getId());
        assertEquals(tenantId, config.getTenantId());
        assertEquals(connectionId, config.getConnectionId());
        assertEquals("bot-user", config.getBotUsername());
        assertEquals("enc-token", config.getBotAccessToken());
        assertTrue(config.isEnabled());
        assertEquals(false, config.isAutoAssign());
        assertEquals(now, config.getCreatedAt());
        assertEquals(now, config.getUpdatedAt());
    }

    @Test
    void should_supportSetters() {
        ReviewBotConfig config = new ReviewBotConfig();
        UUID id = UUID.randomUUID();
        config.setId(id);
        config.setBotUsername("new-bot");
        config.setEnabled(false);

        assertEquals(id, config.getId());
        assertEquals("new-bot", config.getBotUsername());
        assertEquals(false, config.isEnabled());
    }
}
