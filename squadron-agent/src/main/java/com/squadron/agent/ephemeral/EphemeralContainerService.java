package com.squadron.agent.ephemeral;

import com.squadron.agent.client.ResilientWorkspaceServiceClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Orchestrates the lifecycle of ephemeral agent sandbox containers.
 *
 * <p>Each container runs an OpenCode server configured for the specific
 * LLM provider/model. The container is created via the workspace service
 * (which delegates to DockerWorkspaceProvider or KubernetesWorkspaceProvider),
 * then OpenCode is started inside it, and an HTTP client is returned for
 * communication.</p>
 *
 * <p>Supports any provider: local (Ollama), cloud (OpenAI, Anthropic),
 * remote (custom endpoints). The LLM itself runs externally — the container
 * provides the agentic sandbox with tools (read, write, bash, etc.).</p>
 *
 * <p>Containers are rootless (UID 1000), use read-only root filesystem with
 * tmpfs mounts, and work on both Docker and Kubernetes.</p>
 */
@Service
public class EphemeralContainerService {

    private static final Logger log = LoggerFactory.getLogger(EphemeralContainerService.class);

    private final ResilientWorkspaceServiceClient workspaceClient;
    private final EphemeralContainerConfig config;

    /** Tracks active containers: sessionId -> ContainerInfo. */
    private final Map<UUID, ContainerInfo> activeContainers = new ConcurrentHashMap<>();

    public EphemeralContainerService(
            ResilientWorkspaceServiceClient workspaceClient,
            EphemeralContainerConfig config) {
        this.workspaceClient = workspaceClient;
        this.config = config;
    }

    /**
     * Creates an ephemeral container, injects the OpenCode configuration,
     * starts the OpenCode server, and waits for it to become healthy.
     *
     * @param sessionId   the interactive test session ID (used for tracking)
     * @param tenantId    the tenant ID
     * @param provider    the LLM provider name
     * @param model       the model name
     * @param baseUrl     the provider base URL (nullable)
     * @param apiKey      the API key (nullable)
     * @param hostingType CLOUD, SELF_HOSTED, or CUSTOM
     * @param systemPrompt custom system instructions (nullable)
     * @return an OpenCodeContainerClient connected to the running server
     */
    public OpenCodeContainerClient startContainer(
            UUID sessionId, UUID tenantId, String provider, String model,
            String baseUrl, String apiKey, String hostingType, String systemPrompt) {

        log.info("Starting ephemeral container for session {} (provider={}, model={})",
                sessionId, provider, model);

        // Step 1: Create the container via workspace service
        String workspaceId = createWorkspace(sessionId, tenantId);
        log.info("Container created for session {}: workspaceId={}", sessionId, workspaceId);

        try {
            // Step 2: Generate and inject OpenCode configuration
            String openCodeConfig = OpenCodeConfigGenerator.generate(
                    provider, model, baseUrl, apiKey, hostingType,
                    systemPrompt, config.getOpenCodePassword());

            injectConfig(workspaceId, openCodeConfig);
            log.debug("OpenCode config injected into container {}", workspaceId);

            // Step 3: Start OpenCode server inside the container
            startOpenCodeServer(workspaceId);
            log.info("OpenCode server starting in container {}", workspaceId);

            // Step 4: Resolve container IP for HTTP communication
            String containerIp = resolveContainerIp(workspaceId);
            log.info("Container {} IP resolved: {}", workspaceId, containerIp);

            // Step 5: Create HTTP client and wait for health
            OpenCodeContainerClient client = new OpenCodeContainerClient(
                    containerIp, config.getOpenCodePort(),
                    config.getOpenCodeUsername(), config.getOpenCodePassword());

            waitForHealth(client, workspaceId);
            log.info("OpenCode server healthy in container {} for session {}", workspaceId, sessionId);

            // Track the container
            ContainerInfo info = new ContainerInfo(workspaceId, containerIp, client);
            activeContainers.put(sessionId, info);

            return client;

        } catch (Exception e) {
            // Clean up on failure
            log.error("Failed to start ephemeral container for session {}: {}",
                    sessionId, e.getMessage(), e);
            try {
                destroyContainer(sessionId, workspaceId);
            } catch (Exception cleanup) {
                log.warn("Failed to clean up container {} after error: {}",
                        workspaceId, cleanup.getMessage());
            }
            throw new RuntimeException("Failed to start ephemeral agent container: " + e.getMessage(), e);
        }
    }

    /**
     * Destroys the ephemeral container for the given session.
     *
     * @param sessionId the session ID
     */
    public void stopContainer(UUID sessionId) {
        ContainerInfo info = activeContainers.remove(sessionId);
        if (info == null) {
            log.debug("No active container for session {} — nothing to destroy", sessionId);
            return;
        }

        destroyContainer(sessionId, info.workspaceId);
    }

    /**
     * Gets the OpenCode client for an active container, if any.
     *
     * @param sessionId the session ID
     * @return the client, or null if no container is active
     */
    public OpenCodeContainerClient getClient(UUID sessionId) {
        ContainerInfo info = activeContainers.get(sessionId);
        return info != null ? info.client : null;
    }

    /**
     * Returns the workspace ID (container ID) for the given session.
     */
    public String getWorkspaceId(UUID sessionId) {
        ContainerInfo info = activeContainers.get(sessionId);
        return info != null ? info.workspaceId : null;
    }

    /**
     * Returns whether a container is active for the given session.
     */
    public boolean isContainerActive(UUID sessionId) {
        return activeContainers.containsKey(sessionId);
    }

    /**
     * Returns the number of active containers.
     */
    public int getActiveContainerCount() {
        return activeContainers.size();
    }

    // --- Internal lifecycle methods ---

    private String createWorkspace(UUID sessionId, UUID tenantId) {
        Map<String, Object> request = new HashMap<>();
        request.put("tenantId", tenantId.toString());
        request.put("taskId", sessionId.toString()); // Use sessionId as pseudo-taskId
        request.put("userId", sessionId.toString());  // Will be replaced by real userId
        request.put("repoUrl", null); // No repo to clone — this is a sandbox
        request.put("baseImage", config.getImage());
        request.put("resourceLimits", Map.of(
                "memory", config.getMemoryLimit(),
                "cpu", config.getCpuLimit()
        ));

        Map<String, Object> response = workspaceClient.createWorkspace(request);

        // Extract workspace ID from ApiResponse wrapper
        @SuppressWarnings("unchecked")
        Map<String, Object> data = (Map<String, Object>) response.get("data");
        if (data == null) {
            throw new RuntimeException("Workspace creation returned no data: " + response);
        }

        String workspaceId = String.valueOf(data.get("id"));
        if (workspaceId == null || "null".equals(workspaceId)) {
            throw new RuntimeException("Workspace creation returned no ID: " + data);
        }

        return workspaceId;
    }

    private void injectConfig(String workspaceId, String openCodeConfig) {
        // The container rootfs is read-only and tmpfs mount on /home/squadron wipes
        // directories created during image build. We use exec (which writes to tmpfs)
        // instead of Docker's copyToContainer API (which writes to rootfs and fails).
        String base64Config = java.util.Base64.getEncoder().encodeToString(
                openCodeConfig.getBytes(StandardCharsets.UTF_8));

        Map<String, Object> writeRequest = new HashMap<>();
        writeRequest.put("command", List.of(
                "sh", "-c",
                "mkdir -p /home/squadron/.config/opencode && echo '" + base64Config
                + "' | base64 -d > /home/squadron/.config/opencode/opencode.json"
        ));
        workspaceClient.exec(workspaceId, writeRequest);
    }

    private void startOpenCodeServer(String workspaceId) {
        // Start opencode serve in the background via nohup
        // The server will read config from ~/.config/opencode/opencode.json
        Map<String, Object> execRequest = new HashMap<>();
        execRequest.put("command", List.of(
                "sh", "-c",
                "OPENCODE_SERVER_PASSWORD=" + config.getOpenCodePassword()
                + " OPENCODE_SERVER_USERNAME=" + config.getOpenCodeUsername()
                + " nohup opencode serve --port " + config.getOpenCodePort()
                + " --hostname 0.0.0.0 > /tmp/opencode.log 2>&1 &"
        ));

        workspaceClient.exec(workspaceId, execRequest);
    }

    private String resolveContainerIp(String workspaceId) {
        // Execute hostname -i inside the container to get its IP on the network
        Map<String, Object> execRequest = new HashMap<>();
        execRequest.put("command", List.of("hostname", "-i"));

        Map<String, Object> result = workspaceClient.exec(workspaceId, execRequest);

        @SuppressWarnings("unchecked")
        Map<String, Object> data = (Map<String, Object>) result.get("data");
        if (data == null) data = result; // Handle both wrapped and unwrapped responses

        String stdout = String.valueOf(data.getOrDefault("stdout", "")).trim();
        int exitCode = data.containsKey("exitCode")
                ? ((Number) data.get("exitCode")).intValue() : -1;

        if (exitCode != 0 || stdout.isBlank()) {
            throw new RuntimeException("Failed to resolve container IP for workspace "
                    + workspaceId + ": exitCode=" + exitCode + ", stdout=" + stdout);
        }

        // hostname -i may return multiple IPs separated by space; take the first
        String ip = stdout.split("\\s+")[0];
        log.debug("Resolved container IP for {}: {}", workspaceId, ip);
        return ip;
    }

    private void waitForHealth(OpenCodeContainerClient client, String workspaceId) {
        int maxAttempts = config.getHealthCheckTimeoutSeconds() / config.getHealthCheckIntervalSeconds();
        int intervalMs = config.getHealthCheckIntervalSeconds() * 1000;

        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            if (client.isHealthy()) {
                log.debug("OpenCode health check passed on attempt {}/{}", attempt, maxAttempts);
                return;
            }
            log.trace("OpenCode health check attempt {}/{} failed for {}",
                    attempt, maxAttempts, workspaceId);
            try {
                Thread.sleep(intervalMs);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new RuntimeException("Interrupted while waiting for OpenCode health", e);
            }
        }

        throw new RuntimeException("OpenCode server in container " + workspaceId
                + " did not become healthy within " + config.getHealthCheckTimeoutSeconds() + "s");
    }

    private void destroyContainer(UUID sessionId, String workspaceId) {
        try {
            workspaceClient.destroyWorkspace(workspaceId);
            log.info("Destroyed ephemeral container {} for session {}", workspaceId, sessionId);
        } catch (Exception e) {
            log.error("Failed to destroy container {} for session {}: {}",
                    workspaceId, sessionId, e.getMessage());
        }
    }

    /**
     * Internal record tracking active container state.
     */
    static class ContainerInfo {
        final String workspaceId;
        final String containerIp;
        final OpenCodeContainerClient client;

        ContainerInfo(String workspaceId, String containerIp, OpenCodeContainerClient client) {
            this.workspaceId = workspaceId;
            this.containerIp = containerIp;
            this.client = client;
        }
    }

    /** Visible for testing. */
    Map<UUID, ContainerInfo> getActiveContainers() {
        return activeContainers;
    }
}
