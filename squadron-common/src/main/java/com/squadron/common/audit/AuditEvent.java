package com.squadron.common.audit;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

/**
 * Represents a single audit event capturing a user action in the system.
 * Used for structured audit logging, NATS publishing, and query/display.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AuditEvent {

    private UUID id;
    private UUID tenantId;
    private UUID userId;
    private String username;
    private String action;
    private String resourceType;
    private String resourceId;
    private String details;
    private String ipAddress;
    private String userAgent;
    private String sourceService;
    private AuditAction auditAction;
    private Instant timestamp;
}
