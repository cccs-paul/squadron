package com.squadron.workspace.provider;

import com.squadron.workspace.dto.ExecResult;
import com.squadron.workspace.dto.WorkspaceSpec;
import io.kubernetes.client.Exec;
import io.kubernetes.client.openapi.ApiClient;
import io.kubernetes.client.openapi.ApiException;
import io.kubernetes.client.openapi.apis.CoreV1Api;
import io.kubernetes.client.openapi.models.V1Container;
import io.kubernetes.client.openapi.models.V1ContainerStatus;
import io.kubernetes.client.openapi.models.V1ObjectMeta;
import io.kubernetes.client.openapi.models.V1Pod;
import io.kubernetes.client.openapi.models.V1PodSpec;
import io.kubernetes.client.openapi.models.V1PodStatus;
import io.kubernetes.client.openapi.models.V1ResourceRequirements;
import io.kubernetes.client.util.Config;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Component
@ConditionalOnProperty(name = "squadron.workspace.provider", havingValue = "kubernetes", matchIfMissing = true)
public class KubernetesWorkspaceProvider implements WorkspaceProvider {

    private static final Logger log = LoggerFactory.getLogger(KubernetesWorkspaceProvider.class);

    private final CoreV1Api coreV1Api;
    private final ApiClient apiClient;
    private final String namespace;
    private final String defaultImage;

    public KubernetesWorkspaceProvider(
            @Value("${squadron.workspace.kubernetes.namespace:squadron-workspaces}") String namespace,
            @Value("${squadron.workspace.kubernetes.image:ubuntu:22.04}") String defaultImage) {
        this.namespace = namespace;
        this.defaultImage = defaultImage;
        try {
            this.apiClient = Config.defaultClient();
            this.coreV1Api = new CoreV1Api(apiClient);
        } catch (Exception e) {
            throw new RuntimeException("Failed to initialize Kubernetes client", e);
        }
    }

    // Visible for testing
    KubernetesWorkspaceProvider(CoreV1Api coreV1Api, ApiClient apiClient,
                                 String namespace, String defaultImage) {
        this.coreV1Api = coreV1Api;
        this.apiClient = apiClient;
        this.namespace = namespace;
        this.defaultImage = defaultImage;
    }

    @Override
    public String getProviderType() {
        return "KUBERNETES";
    }

    @Override
    public String createContainer(WorkspaceSpec spec) {
        String podName = "workspace-" + UUID.randomUUID().toString().substring(0, 8);
        String image = spec.getBaseImage() != null ? spec.getBaseImage() : defaultImage;

        try {
            Map<String, String> labels = new HashMap<>();
            labels.put("app", "squadron-workspace");
            labels.put("task-id", spec.getTaskId().toString());
            labels.put("tenant-id", spec.getTenantId().toString());

            V1ResourceRequirements resources = new V1ResourceRequirements();
            if (spec.getResourceLimits() != null) {
                Map<String, io.kubernetes.client.custom.Quantity> limits = new HashMap<>();
                spec.getResourceLimits().forEach((key, value) ->
                        limits.put(key, new io.kubernetes.client.custom.Quantity(value.toString())));
                resources.setLimits(limits);
            }

            V1Container container = new V1Container()
                    .name("workspace")
                    .image(image)
                    .command(List.of("sleep", "infinity"))
                    .resources(resources);

            V1Pod pod = new V1Pod()
                    .metadata(new V1ObjectMeta()
                            .name(podName)
                            .namespace(namespace)
                            .labels(labels))
                    .spec(new V1PodSpec()
                            .containers(Collections.singletonList(container))
                            .restartPolicy("Never"));

            coreV1Api.createNamespacedPod(namespace, pod, null, null, null, null);
            log.info("Created Kubernetes pod {} in namespace {}", podName, namespace);
            return podName;
        } catch (ApiException e) {
            log.error("Failed to create Kubernetes pod {}: {}", podName, e.getResponseBody(), e);
            throw new RuntimeException("Failed to create Kubernetes workspace", e);
        }
    }

    @Override
    public void destroyContainer(String containerId) {
        try {
            coreV1Api.deleteNamespacedPod(containerId, namespace, null, null, null, null, null, null);
            log.info("Deleted Kubernetes pod {} from namespace {}", containerId, namespace);
        } catch (ApiException e) {
            log.error("Failed to delete Kubernetes pod {}: {}", containerId, e.getResponseBody(), e);
            throw new RuntimeException("Failed to destroy Kubernetes workspace", e);
        }
    }

    @Override
    public ExecResult exec(String containerId, String[] command) {
        long startTime = System.currentTimeMillis();
        try {
            Exec exec = new Exec(apiClient);
            Process process = exec.exec(namespace, containerId, command, "workspace", true, true);

            ByteArrayOutputStream stdoutStream = new ByteArrayOutputStream();
            ByteArrayOutputStream stderrStream = new ByteArrayOutputStream();

            try (InputStream stdout = process.getInputStream();
                 InputStream stderr = process.getErrorStream()) {
                if (stdout != null) {
                    stdout.transferTo(stdoutStream);
                }
                if (stderr != null) {
                    stderr.transferTo(stderrStream);
                }
            }

            int exitCode = process.waitFor();
            long durationMs = System.currentTimeMillis() - startTime;

            return ExecResult.builder()
                    .exitCode(exitCode)
                    .stdout(stdoutStream.toString(StandardCharsets.UTF_8))
                    .stderr(stderrStream.toString(StandardCharsets.UTF_8))
                    .durationMs(durationMs)
                    .build();
        } catch (Exception e) {
            long durationMs = System.currentTimeMillis() - startTime;
            log.error("Failed to exec in pod {}", containerId, e);
            return ExecResult.builder()
                    .exitCode(-1)
                    .stderr("Exec failed: " + e.getMessage())
                    .durationMs(durationMs)
                    .build();
        }
    }

    @Override
    public void copyToContainer(String containerId, byte[] content, String containerPath) {
        String encoded = Base64.getEncoder().encodeToString(content);
        String[] command = new String[]{"sh", "-c", "echo '" + encoded + "' | base64 -d > " + containerPath};
        ExecResult result = exec(containerId, command);
        if (result.getExitCode() != 0) {
            throw new RuntimeException("Failed to copy to container " + containerId +
                    " at path " + containerPath + ": " + result.getStderr());
        }
    }

    @Override
    public byte[] copyFromContainer(String containerId, String containerPath) {
        String[] command = new String[]{"cat", containerPath};
        ExecResult result = exec(containerId, command);
        if (result.getExitCode() != 0) {
            throw new RuntimeException("Failed to copy from container " + containerId +
                    " at path " + containerPath + ": " + result.getStderr());
        }
        return result.getStdout().getBytes(StandardCharsets.UTF_8);
    }

    @Override
    public String getContainerStatus(String containerId) {
        try {
            V1Pod pod = coreV1Api.readNamespacedPod(containerId, namespace, null);
            V1PodStatus status = pod.getStatus();
            if (status == null) {
                return "UNKNOWN";
            }

            String phase = status.getPhase();
            if ("Running".equals(phase)) {
                List<V1ContainerStatus> containerStatuses = status.getContainerStatuses();
                if (containerStatuses != null && !containerStatuses.isEmpty()) {
                    V1ContainerStatus cs = containerStatuses.get(0);
                    if (Boolean.TRUE.equals(cs.getReady())) {
                        return "READY";
                    }
                }
                return "RUNNING";
            } else if ("Pending".equals(phase)) {
                return "CREATING";
            } else if ("Succeeded".equals(phase) || "Failed".equals(phase)) {
                return "TERMINATED";
            }
            return phase != null ? phase.toUpperCase() : "UNKNOWN";
        } catch (ApiException e) {
            log.error("Failed to get status for pod {}: {}", containerId, e.getResponseBody(), e);
            return "UNKNOWN";
        }
    }
}
