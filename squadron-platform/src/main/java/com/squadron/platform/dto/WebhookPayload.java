package com.squadron.platform.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WebhookPayload {

    private String platform;
    private String eventType;
    private Map<String, Object> payload;

    @Builder.Default
    private Instant receivedAt = Instant.now();

    /** Tenant ID correlated from the matching PlatformConnection */
    private UUID tenantId;

    /** Connection ID of the matched PlatformConnection */
    private UUID connectionId;

    /** Normalized action (e.g., "issue_created", "opened", "merge_request") */
    private String action;

    /** External task/issue ID extracted from the payload (e.g., "PROJ-123", "42") */
    private String externalId;

    /** Whether the webhook signature was successfully validated */
    @Builder.Default
    private boolean signatureValid = false;
}
