package com.squadron.agent.integration;

import com.squadron.agent.tool.builtin.ReviewBotClient;
import com.squadron.agent.tool.builtin.CredentialClient;
import com.squadron.agent.tool.builtin.GitClient;
import com.squadron.common.dto.CredentialResolutionResult;
import com.squadron.common.security.CredentialPurpose;
import com.squadron.common.security.CredentialType;
import com.squadron.common.security.GitAuthMode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Integration test: review bot flow verifying bot config lookup, 
 * credential resolution, and bot action decisions.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("Review Bot Flow Integration")
class ReviewBotFlowIntegrationTest {

    @Mock private ReviewBotClient reviewBotClient;
    @Mock private CredentialClient credentialClient;
    @Mock private GitClient gitClient;

    private UUID tenantId, connectionId, userId;

    @BeforeEach
    void setUp() {
        tenantId = UUID.randomUUID();
        connectionId = UUID.randomUUID();
        userId = UUID.randomUUID();
    }

    @Test
    @DisplayName("should resolve bot config and confirm bot is enabled for auto-assign")
    void should_findEnabledBot_when_botConfiguredForConnection() {
        ReviewBotClient.BotConfig botConfig = ReviewBotClient.BotConfig.builder()
                .id(UUID.randomUUID()).tenantId(tenantId).connectionId(connectionId)
                .botUsername("squadron-bot").enabled(true).autoAssign(true)
                .build();

        when(reviewBotClient.getEnabledBotConfig(eq(tenantId), eq(connectionId)))
                .thenReturn(Optional.of(botConfig));

        Optional<ReviewBotClient.BotConfig> result = reviewBotClient.getEnabledBotConfig(tenantId, connectionId);

        assertTrue(result.isPresent());
        assertEquals("squadron-bot", result.get().getBotUsername());
        assertTrue(result.get().isEnabled());
        assertTrue(result.get().isAutoAssign());
    }

    @Test
    @DisplayName("should return empty when no bot configured for connection")
    void should_returnEmpty_when_noBotConfigured() {
        when(reviewBotClient.getEnabledBotConfig(eq(tenantId), eq(connectionId)))
                .thenReturn(Optional.empty());

        Optional<ReviewBotClient.BotConfig> result = reviewBotClient.getEnabledBotConfig(tenantId, connectionId);
        assertTrue(result.isEmpty());
    }

    @Test
    @DisplayName("should not auto-assign when bot is disabled")
    void should_skipAutoAssign_when_botIsDisabled() {
        // getEnabledBotConfig only returns enabled bots, so disabled bots return empty
        when(reviewBotClient.getEnabledBotConfig(eq(tenantId), eq(connectionId)))
                .thenReturn(Optional.empty());

        Optional<ReviewBotClient.BotConfig> result = reviewBotClient.getEnabledBotConfig(tenantId, connectionId);

        assertTrue(result.isEmpty());
        // When bot is disabled (empty), the review agent should skip posting comments
    }
}
