package com.squadron.common.audit;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.squadron.common.config.NatsEventPublisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.UUID;

/**
 * Service responsible for logging audit events to a structured SLF4J logger
 * and optionally publishing them to NATS for downstream consumers.
 * <p>
 * This service is designed to be resilient: failures in audit logging or NATS
 * publishing are caught and logged but never propagated to callers.
 */
public class AuditService {

    private static final Logger AUDIT = LoggerFactory.getLogger("AUDIT");
    private static final Logger log = LoggerFactory.getLogger(AuditService.class);

    private final AuditProperties properties;
    private final ObjectMapper objectMapper;
    private final NatsEventPublisher natsEventPublisher;
    private final AuditQueryService auditQueryService;

    public AuditService(AuditProperties properties,
                        ObjectMapper objectMapper,
                        NatsEventPublisher natsEventPublisher,
                        AuditQueryService auditQueryService) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.natsEventPublisher = natsEventPublisher;
        this.auditQueryService = auditQueryService;
    }

    /**
     * Logs a fully-constructed audit event.
     * Writes to the structured AUDIT logger and optionally publishes to NATS.
     *
     * @param event the audit event to log; if null the call is silently ignored
     */
    public void logEvent(AuditEvent event) {
        if (event == null) {
            return;
        }
        if (!properties.isEnabled()) {
            return;
        }
        if (event.getAction() != null && properties.getExcludedActions().contains(event.getAction())) {
            return;
        }

        // Ensure ID and timestamp are set
        if (event.getId() == null) {
            event.setId(UUID.randomUUID());
        }
        if (event.getTimestamp() == null) {
            event.setTimestamp(Instant.now());
        }

        try {
            String json = objectMapper.writeValueAsString(event);
            AUDIT.info(json);
        } catch (Exception e) {
            log.warn("Failed to serialize audit event for logging: {}", e.getMessage());
        }

        // Store in query service
        try {
            auditQueryService.store(event);
        } catch (Exception e) {
            log.warn("Failed to store audit event: {}", e.getMessage());
        }

        // Publish to NATS if enabled
        if (properties.isPublishToNats() && natsEventPublisher != null) {
            try {
                byte[] data = objectMapper.writeValueAsBytes(event);
                natsEventPublisher.publishRaw(properties.getNatsSubject(), data);
            } catch (Exception e) {
                log.warn("Failed to publish audit event to NATS: {}", e.getMessage());
            }
        }
    }

    /**
     * Convenience method to log an audit event by specifying individual fields.
     *
     * @param tenantId    the tenant ID
     * @param userId      the user ID
     * @param action      the action name
     * @param resourceType the resource type
     * @param resourceId  the resource ID
     * @param auditAction the audit action type
     * @param details     JSON string with additional details
     */
    public void logEvent(UUID tenantId, UUID userId, String action,
                         String resourceType, String resourceId,
                         AuditAction auditAction, String details) {
        AuditEvent event = AuditEvent.builder()
                .id(UUID.randomUUID())
                .tenantId(tenantId)
                .userId(userId)
                .action(action)
                .resourceType(resourceType)
                .resourceId(resourceId)
                .auditAction(auditAction)
                .details(details)
                .timestamp(Instant.now())
                .build();
        logEvent(event);
    }
}
