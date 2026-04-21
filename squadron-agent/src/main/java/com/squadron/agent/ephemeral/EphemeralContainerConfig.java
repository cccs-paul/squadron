package com.squadron.agent.ephemeral;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Configuration properties for ephemeral agent sandbox containers.
 */
@Data
@Configuration
@ConfigurationProperties(prefix = "squadron.ephemeral")
public class EphemeralContainerConfig {

    /** Docker/K8s image for the agent sandbox container. */
    private String image = "squadron/agent-sandbox:latest";

    /** Port that OpenCode server listens on inside the container. */
    private int openCodePort = 4096;

    /** Maximum time in seconds to wait for the container to become healthy. */
    private int healthCheckTimeoutSeconds = 60;

    /** Interval in seconds between health check retries. */
    private int healthCheckIntervalSeconds = 2;

    /** Memory limit for the container (e.g., "512Mi", "1Gi"). */
    private String memoryLimit = "512Mi";

    /** CPU limit for the container (e.g., "1", "0.5"). */
    private String cpuLimit = "1";

    /** Password for OpenCode server HTTP basic auth. */
    private String openCodePassword = "squadron-ephemeral";

    /** Username for OpenCode server HTTP basic auth. */
    private String openCodeUsername = "opencode";

    /**
     * Whether ephemeral containers are enabled. When false, the service falls back
     * to direct LLM provider calls (legacy behavior).
     */
    private boolean enabled = true;
}
