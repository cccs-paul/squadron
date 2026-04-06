package com.squadron.agent.tool.builtin;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * REST client for fetching review bot configuration from the squadron-review service.
 * Used by ReviewAgentService to determine whether to post comments as a bot
 * on the git platform after completing an AI review.
 */
@Service
public class ReviewBotClient {

    private static final Logger log = LoggerFactory.getLogger(ReviewBotClient.class);

    private final WebClient webClient;

    @Autowired
    public ReviewBotClient(@Value("${squadron.review.url:http://localhost:8088}") String reviewUrl) {
        this.webClient = WebClient.builder()
                .baseUrl(reviewUrl)
                .build();
    }

    /**
     * Constructor for testing — accepts a pre-built WebClient.
     */
    ReviewBotClient(WebClient webClient) {
        this.webClient = webClient;
    }

    /**
     * Fetches the enabled review bot config for a tenant+connection pair.
     * Returns empty if no enabled config exists or the service is unavailable.
     *
     * @param tenantId     the tenant ID
     * @param connectionId the platform connection ID
     * @return the bot config if enabled, empty otherwise
     */
    public Optional<BotConfig> getEnabledBotConfig(UUID tenantId, UUID connectionId) {
        log.debug("Fetching review bot config for tenant {} connection {}", tenantId, connectionId);

        try {
            Map<String, Object> response = webClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/api/reviews/bot-config/tenant/{tenantId}")
                            .build(tenantId))
                    .retrieve()
                    .bodyToMono(new ParameterizedTypeReference<Map<String, Object>>() {})
                    .block();

            if (response == null || response.get("data") == null) {
                return Optional.empty();
            }

            @SuppressWarnings("unchecked")
            java.util.List<Map<String, Object>> configs = (java.util.List<Map<String, Object>>) response.get("data");

            // Find the config matching the connectionId that is enabled
            return configs.stream()
                    .filter(c -> connectionId.toString().equals(String.valueOf(c.get("connectionId"))))
                    .filter(c -> Boolean.TRUE.equals(c.get("enabled")))
                    .findFirst()
                    .map(c -> BotConfig.builder()
                            .id(parseUuid(c, "id"))
                            .tenantId(parseUuid(c, "tenantId"))
                            .connectionId(parseUuid(c, "connectionId"))
                            .botUsername(getStringValue(c, "botUsername"))
                            .enabled(Boolean.TRUE.equals(c.get("enabled")))
                            .autoAssign(Boolean.TRUE.equals(c.get("autoAssign")))
                            .build());
        } catch (WebClientResponseException e) {
            log.warn("Failed to fetch review bot config: {} {}", e.getStatusCode(), e.getResponseBodyAsString());
            return Optional.empty();
        } catch (Exception e) {
            log.warn("Error fetching review bot config: {}", e.getMessage());
            return Optional.empty();
        }
    }

    /**
     * Fetches the decrypted bot access token for a specific bot config.
     *
     * @param configId the bot config ID
     * @return the decrypted access token
     */
    public String getBotAccessToken(UUID configId) {
        log.debug("Fetching bot access token for config {}", configId);

        try {
            Map<String, Object> response = webClient.get()
                    .uri("/api/reviews/bot-config/{id}/token", configId)
                    .retrieve()
                    .bodyToMono(new ParameterizedTypeReference<Map<String, Object>>() {})
                    .block();

            if (response == null || response.get("data") == null) {
                throw new ReviewBotClientException("Empty response when fetching bot token");
            }

            return response.get("data").toString();
        } catch (WebClientResponseException e) {
            log.error("Failed to fetch bot token: {} {}", e.getStatusCode(), e.getResponseBodyAsString());
            throw new ReviewBotClientException("Failed to fetch bot token: " + e.getStatusCode(), e);
        }
    }

    private UUID parseUuid(Map<String, Object> map, String key) {
        Object value = map.get(key);
        if (value == null) return null;
        if (value instanceof UUID uuid) return uuid;
        try {
            return UUID.fromString(value.toString());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private String getStringValue(Map<String, Object> map, String key) {
        Object value = map.get(key);
        return value != null ? value.toString() : null;
    }

    // ---- Inner DTOs ----

    @lombok.Data
    @lombok.Builder
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    public static class BotConfig {
        private UUID id;
        private UUID tenantId;
        private UUID connectionId;
        private String botUsername;
        private boolean enabled;
        private boolean autoAssign;
    }

    public static class ReviewBotClientException extends RuntimeException {
        public ReviewBotClientException(String message) {
            super(message);
        }

        public ReviewBotClientException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
