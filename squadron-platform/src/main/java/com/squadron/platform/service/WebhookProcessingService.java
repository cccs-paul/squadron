package com.squadron.platform.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.squadron.common.config.NatsEventPublisher;
import com.squadron.common.event.SquadronEvent;
import com.squadron.common.security.TokenEncryptionService;
import com.squadron.platform.dto.WebhookPayload;
import com.squadron.platform.entity.PlatformConnection;
import com.squadron.platform.repository.PlatformConnectionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Processes incoming webhook payloads by correlating them to tenant connections,
 * validating signatures, normalizing payloads, and publishing enriched NATS events.
 */
@Service
public class WebhookProcessingService {

    private static final Logger log = LoggerFactory.getLogger(WebhookProcessingService.class);

    private final PlatformConnectionRepository connectionRepository;
    private final TokenEncryptionService encryptionService;
    private final WebhookSignatureValidator signatureValidator;
    private final NatsEventPublisher natsEventPublisher;
    private final ObjectMapper objectMapper;

    public WebhookProcessingService(PlatformConnectionRepository connectionRepository,
                                    TokenEncryptionService encryptionService,
                                    WebhookSignatureValidator signatureValidator,
                                    NatsEventPublisher natsEventPublisher,
                                    ObjectMapper objectMapper) {
        this.connectionRepository = connectionRepository;
        this.encryptionService = encryptionService;
        this.signatureValidator = signatureValidator;
        this.natsEventPublisher = natsEventPublisher;
        this.objectMapper = objectMapper;
    }

    /**
     * Processes a webhook by correlating it to a tenant connection, validating the signature,
     * normalizing the payload, and publishing an enriched NATS event.
     *
     * @param platform        the platform identifier (e.g., "JIRA", "GITHUB", "GITLAB", "AZURE_DEVOPS")
     * @param payload         the parsed webhook payload as a map
     * @param signatureHeader the signature header value from the HTTP request (may be null)
     * @param rawBody         the raw request body bytes for signature verification
     * @return the enriched WebhookPayload
     */
    public WebhookPayload processWebhook(String platform, Map<String, Object> payload,
                                          String signatureHeader, byte[] rawBody) {
        log.info("Processing {} webhook", platform);

        // Build initial webhook payload
        WebhookPayload webhookPayload = WebhookPayload.builder()
                .platform(platform)
                .eventType(extractEventType(platform, payload))
                .payload(payload)
                .receivedAt(Instant.now())
                .signatureValid(false)
                .build();

        // Find matching active connections
        List<PlatformConnection> connections = findActiveConnections(platform);

        // Attempt signature validation and tenant correlation
        boolean signatureValidated = false;
        PlatformConnection matchedConnection = null;

        if (connections.isEmpty()) {
            log.warn("No active connections found for platform {}; processing webhook without tenant correlation", platform);
        } else {
            for (PlatformConnection connection : connections) {
                if (connection.getWebhookSecret() != null && !connection.getWebhookSecret().isEmpty()) {
                    try {
                        String decryptedSecret = encryptionService.decrypt(connection.getWebhookSecret());
                        boolean valid = signatureValidator.validateSignature(
                                platform, decryptedSecret, signatureHeader, rawBody);
                        if (valid) {
                            matchedConnection = connection;
                            signatureValidated = true;
                            break;
                        }
                    } catch (Exception e) {
                        log.warn("Failed to decrypt webhook secret for connection {}: {}",
                                connection.getId(), e.getMessage());
                    }
                } else {
                    // Connection without webhook secret — use as fallback
                    if (matchedConnection == null) {
                        matchedConnection = connection;
                    }
                }
            }

            // If no connection had a valid signature but we have a fallback (no secret configured)
            if (matchedConnection != null && !signatureValidated) {
                // Check if ANY connection had a secret configured
                boolean anySecretConfigured = connections.stream()
                        .anyMatch(c -> c.getWebhookSecret() != null && !c.getWebhookSecret().isEmpty());
                if (!anySecretConfigured) {
                    // No secrets configured at all — signature validation is not applicable
                    signatureValidated = true;
                    log.debug("No webhook secrets configured for platform {}; skipping signature verification", platform);
                } else {
                    log.warn("Signature validation failed for all connections with secrets for platform {}", platform);
                }
            }
        }

        // Enrich the webhook payload
        if (matchedConnection != null) {
            webhookPayload.setTenantId(matchedConnection.getTenantId());
            webhookPayload.setConnectionId(matchedConnection.getId());
        }
        webhookPayload.setSignatureValid(signatureValidated);

        // Normalize the payload to extract action and externalId
        normalizeWebhookPayload(platform, webhookPayload, payload);

        // Publish enriched event
        publishWebhookEvent(webhookPayload);

        return webhookPayload;
    }

    /**
     * Finds active platform connections matching the webhook platform.
     * JIRA webhooks may come from JIRA_CLOUD or JIRA_SERVER connections.
     */
    private List<PlatformConnection> findActiveConnections(String platform) {
        if ("JIRA".equalsIgnoreCase(platform)) {
            return connectionRepository.findByPlatformTypeInAndStatus(
                    List.of("JIRA_CLOUD", "JIRA_SERVER"), "ACTIVE");
        }
        return connectionRepository.findByPlatformTypeAndStatus(platform.toUpperCase(), "ACTIVE");
    }

    /**
     * Extracts the raw event type from the platform-specific payload.
     */
    private String extractEventType(String platform, Map<String, Object> payload) {
        return switch (platform.toUpperCase()) {
            case "JIRA" -> String.valueOf(payload.getOrDefault("webhookEvent", "unknown"));
            case "GITHUB" -> String.valueOf(payload.getOrDefault("action", "unknown"));
            case "GITLAB" -> String.valueOf(payload.getOrDefault("object_kind", "unknown"));
            case "AZURE_DEVOPS" -> String.valueOf(payload.getOrDefault("eventType", "unknown"));
            default -> "unknown";
        };
    }

    /**
     * Normalizes the webhook payload by extracting platform-specific action and external ID.
     */
    private void normalizeWebhookPayload(String platform, WebhookPayload webhookPayload,
                                          Map<String, Object> payload) {
        switch (platform.toUpperCase()) {
            case "JIRA" -> normalizeJiraPayload(webhookPayload, payload);
            case "GITHUB" -> normalizeGitHubPayload(webhookPayload, payload);
            case "GITLAB" -> normalizeGitLabPayload(webhookPayload, payload);
            case "AZURE_DEVOPS" -> normalizeAzureDevOpsPayload(webhookPayload, payload);
            default -> log.warn("Unknown platform for normalization: {}", platform);
        }
    }

    private void normalizeJiraPayload(WebhookPayload webhookPayload, Map<String, Object> payload) {
        // action = webhookEvent field (e.g., "jira:issue_updated")
        webhookPayload.setAction(String.valueOf(payload.getOrDefault("webhookEvent", "unknown")));

        // externalId = issue.key from nested map
        Object issueObj = payload.get("issue");
        if (issueObj instanceof Map<?, ?> issueMap) {
            Object key = issueMap.get("key");
            if (key != null) {
                webhookPayload.setExternalId(String.valueOf(key));
            }
        }
    }

    private void normalizeGitHubPayload(WebhookPayload webhookPayload, Map<String, Object> payload) {
        // action = action field (e.g., "opened", "closed")
        webhookPayload.setAction(String.valueOf(payload.getOrDefault("action", "unknown")));

        // externalId = issue.number as String from nested map
        Object issueObj = payload.get("issue");
        if (issueObj instanceof Map<?, ?> issueMap) {
            Object number = issueMap.get("number");
            if (number != null) {
                webhookPayload.setExternalId(String.valueOf(number));
            }
        }
    }

    private void normalizeGitLabPayload(WebhookPayload webhookPayload, Map<String, Object> payload) {
        // action = object_kind field (e.g., "issue", "merge_request")
        webhookPayload.setAction(String.valueOf(payload.getOrDefault("object_kind", "unknown")));

        // externalId = object_attributes.iid as String from nested map
        Object objectAttrs = payload.get("object_attributes");
        if (objectAttrs instanceof Map<?, ?> attrsMap) {
            Object iid = attrsMap.get("iid");
            if (iid != null) {
                webhookPayload.setExternalId(String.valueOf(iid));
            }
        }
    }

    private void normalizeAzureDevOpsPayload(WebhookPayload webhookPayload, Map<String, Object> payload) {
        // action = eventType field (e.g., "workitem.updated")
        webhookPayload.setAction(String.valueOf(payload.getOrDefault("eventType", "unknown")));

        // externalId = resource.id as String from nested map
        Object resourceObj = payload.get("resource");
        if (resourceObj instanceof Map<?, ?> resourceMap) {
            Object id = resourceMap.get("id");
            if (id != null) {
                webhookPayload.setExternalId(String.valueOf(id));
            }
        }
    }

    /**
     * Publishes an enriched webhook event to NATS.
     */
    private void publishWebhookEvent(WebhookPayload webhookPayload) {
        try {
            String action = webhookPayload.getAction() != null ? webhookPayload.getAction() : "unknown";
            String platform = webhookPayload.getPlatform().toLowerCase();

            SquadronEvent event = new SquadronEvent();
            event.setEventType("platform.webhook." + platform + "." + action);
            event.setTenantId(webhookPayload.getTenantId());
            event.setSource("squadron-platform");

            natsEventPublisher.publish("platform.webhooks." + platform, event);
            log.debug("Published webhook event for platform {} with action {}", platform, action);
        } catch (Exception e) {
            log.error("Failed to publish webhook event for platform {}", webhookPayload.getPlatform(), e);
        }
    }
}
