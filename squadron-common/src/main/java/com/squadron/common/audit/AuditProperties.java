package com.squadron.common.audit;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.List;

/**
 * Configuration properties for the audit logging framework.
 * Prefix: {@code squadron.audit}
 */
@Data
@ConfigurationProperties(prefix = "squadron.audit")
public class AuditProperties {

    /**
     * Whether audit logging is enabled. Default: true.
     */
    private boolean enabled = true;

    /**
     * Whether to publish audit events to NATS. Default: true.
     */
    private boolean publishToNats = true;

    /**
     * The NATS subject to publish audit events to.
     */
    private String natsSubject = "squadron.audit.events";

    /**
     * List of action names to exclude from audit logging.
     */
    private List<String> excludedActions = new ArrayList<>();
}
