package com.squadron.agent.tool.builtin;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ReviewBotClientTest {

    @Mock
    private WebClient webClient;

    @Mock
    private WebClient.RequestHeadersUriSpec requestHeadersUriSpec;

    @Mock
    private WebClient.RequestHeadersSpec requestHeadersSpec;

    @Mock
    private WebClient.ResponseSpec responseSpec;

    private ReviewBotClient reviewBotClient;

    @BeforeEach
    void setUp() {
        reviewBotClient = new ReviewBotClient(webClient);
    }

    // ---------------------------------------------------------------------------
    // getEnabledBotConfig tests
    // ---------------------------------------------------------------------------

    @Test
    @SuppressWarnings("unchecked")
    void should_returnBotConfig_when_enabledConfigExists() {
        UUID tenantId = UUID.randomUUID();
        UUID connectionId = UUID.randomUUID();
        UUID configId = UUID.randomUUID();

        Map<String, Object> configData = Map.of(
                "id", configId.toString(),
                "tenantId", tenantId.toString(),
                "connectionId", connectionId.toString(),
                "botUsername", "squadron-bot",
                "enabled", true,
                "autoAssign", true
        );

        Map<String, Object> responseData = Map.of(
                "success", true,
                "data", List.of(configData)
        );

        when(webClient.get()).thenReturn(requestHeadersUriSpec);
        when(requestHeadersUriSpec.uri(any(Function.class))).thenReturn(requestHeadersSpec);
        when(requestHeadersSpec.retrieve()).thenReturn(responseSpec);
        when(responseSpec.bodyToMono(any(ParameterizedTypeReference.class)))
                .thenReturn(Mono.just(responseData));

        Optional<ReviewBotClient.BotConfig> result = reviewBotClient.getEnabledBotConfig(tenantId, connectionId);

        assertTrue(result.isPresent());
        ReviewBotClient.BotConfig config = result.get();
        assertEquals(configId, config.getId());
        assertEquals(tenantId, config.getTenantId());
        assertEquals(connectionId, config.getConnectionId());
        assertEquals("squadron-bot", config.getBotUsername());
        assertTrue(config.isEnabled());
        assertTrue(config.isAutoAssign());
    }

    @Test
    @SuppressWarnings("unchecked")
    void should_returnEmpty_when_noConfigForConnection() {
        UUID tenantId = UUID.randomUUID();
        UUID connectionId = UUID.randomUUID();
        UUID otherConnectionId = UUID.randomUUID();

        Map<String, Object> configData = Map.of(
                "id", UUID.randomUUID().toString(),
                "tenantId", tenantId.toString(),
                "connectionId", otherConnectionId.toString(),
                "botUsername", "squadron-bot",
                "enabled", true,
                "autoAssign", false
        );

        Map<String, Object> responseData = Map.of(
                "success", true,
                "data", List.of(configData)
        );

        when(webClient.get()).thenReturn(requestHeadersUriSpec);
        when(requestHeadersUriSpec.uri(any(Function.class))).thenReturn(requestHeadersSpec);
        when(requestHeadersSpec.retrieve()).thenReturn(responseSpec);
        when(responseSpec.bodyToMono(any(ParameterizedTypeReference.class)))
                .thenReturn(Mono.just(responseData));

        Optional<ReviewBotClient.BotConfig> result = reviewBotClient.getEnabledBotConfig(tenantId, connectionId);

        assertTrue(result.isEmpty());
    }

    @Test
    @SuppressWarnings("unchecked")
    void should_returnEmpty_when_configDisabled() {
        UUID tenantId = UUID.randomUUID();
        UUID connectionId = UUID.randomUUID();

        Map<String, Object> configData = Map.of(
                "id", UUID.randomUUID().toString(),
                "tenantId", tenantId.toString(),
                "connectionId", connectionId.toString(),
                "botUsername", "squadron-bot",
                "enabled", false,
                "autoAssign", true
        );

        Map<String, Object> responseData = Map.of(
                "success", true,
                "data", List.of(configData)
        );

        when(webClient.get()).thenReturn(requestHeadersUriSpec);
        when(requestHeadersUriSpec.uri(any(Function.class))).thenReturn(requestHeadersSpec);
        when(requestHeadersSpec.retrieve()).thenReturn(responseSpec);
        when(responseSpec.bodyToMono(any(ParameterizedTypeReference.class)))
                .thenReturn(Mono.just(responseData));

        Optional<ReviewBotClient.BotConfig> result = reviewBotClient.getEnabledBotConfig(tenantId, connectionId);

        assertTrue(result.isEmpty());
    }

    @Test
    @SuppressWarnings("unchecked")
    void should_returnEmpty_when_serviceUnavailable() {
        UUID tenantId = UUID.randomUUID();
        UUID connectionId = UUID.randomUUID();

        when(webClient.get()).thenReturn(requestHeadersUriSpec);
        when(requestHeadersUriSpec.uri(any(Function.class))).thenReturn(requestHeadersSpec);
        when(requestHeadersSpec.retrieve()).thenReturn(responseSpec);
        when(responseSpec.bodyToMono(any(ParameterizedTypeReference.class)))
                .thenReturn(Mono.error(WebClientResponseException.create(
                        503, "Service Unavailable", null,
                        "service down".getBytes(StandardCharsets.UTF_8), StandardCharsets.UTF_8)));

        Optional<ReviewBotClient.BotConfig> result = reviewBotClient.getEnabledBotConfig(tenantId, connectionId);

        assertTrue(result.isEmpty());
    }

    @Test
    @SuppressWarnings("unchecked")
    void should_returnEmpty_when_emptyResponse() {
        UUID tenantId = UUID.randomUUID();
        UUID connectionId = UUID.randomUUID();

        Map<String, Object> responseData = Map.of("success", true);

        when(webClient.get()).thenReturn(requestHeadersUriSpec);
        when(requestHeadersUriSpec.uri(any(Function.class))).thenReturn(requestHeadersSpec);
        when(requestHeadersSpec.retrieve()).thenReturn(responseSpec);
        when(responseSpec.bodyToMono(any(ParameterizedTypeReference.class)))
                .thenReturn(Mono.just(responseData));

        Optional<ReviewBotClient.BotConfig> result = reviewBotClient.getEnabledBotConfig(tenantId, connectionId);

        assertTrue(result.isEmpty());
    }

    @Test
    @SuppressWarnings("unchecked")
    void should_returnEmpty_when_nullResponse() {
        UUID tenantId = UUID.randomUUID();
        UUID connectionId = UUID.randomUUID();

        when(webClient.get()).thenReturn(requestHeadersUriSpec);
        when(requestHeadersUriSpec.uri(any(Function.class))).thenReturn(requestHeadersSpec);
        when(requestHeadersSpec.retrieve()).thenReturn(responseSpec);
        when(responseSpec.bodyToMono(any(ParameterizedTypeReference.class)))
                .thenReturn(Mono.justOrEmpty(Optional.empty()));

        Optional<ReviewBotClient.BotConfig> result = reviewBotClient.getEnabledBotConfig(tenantId, connectionId);

        assertTrue(result.isEmpty());
    }

    // ---------------------------------------------------------------------------
    // getBotAccessToken tests
    // ---------------------------------------------------------------------------

    @Test
    @SuppressWarnings("unchecked")
    void should_returnBotToken_when_configExists() {
        UUID configId = UUID.randomUUID();

        Map<String, Object> responseData = Map.of(
                "success", true,
                "data", "decrypted-bot-token-123"
        );

        when(webClient.get()).thenReturn(requestHeadersUriSpec);
        when(requestHeadersUriSpec.uri(anyString(), any(Object[].class))).thenReturn(requestHeadersSpec);
        when(requestHeadersSpec.retrieve()).thenReturn(responseSpec);
        when(responseSpec.bodyToMono(any(ParameterizedTypeReference.class)))
                .thenReturn(Mono.just(responseData));

        String token = reviewBotClient.getBotAccessToken(configId);

        assertEquals("decrypted-bot-token-123", token);
    }

    @Test
    @SuppressWarnings("unchecked")
    void should_throwException_when_tokenFetchFails() {
        UUID configId = UUID.randomUUID();

        when(webClient.get()).thenReturn(requestHeadersUriSpec);
        when(requestHeadersUriSpec.uri(anyString(), any(Object[].class))).thenReturn(requestHeadersSpec);
        when(requestHeadersSpec.retrieve()).thenReturn(responseSpec);
        when(responseSpec.bodyToMono(any(ParameterizedTypeReference.class)))
                .thenReturn(Mono.error(WebClientResponseException.create(
                        404, "Not Found", null,
                        "not found".getBytes(StandardCharsets.UTF_8), StandardCharsets.UTF_8)));

        assertThrows(ReviewBotClient.ReviewBotClientException.class,
                () -> reviewBotClient.getBotAccessToken(configId));
    }
}
