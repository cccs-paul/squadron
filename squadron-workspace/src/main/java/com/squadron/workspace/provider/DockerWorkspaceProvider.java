package com.squadron.workspace.provider;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.command.CopyArchiveFromContainerCmd;
import com.github.dockerjava.api.command.CopyArchiveToContainerCmd;
import com.github.dockerjava.api.command.CreateContainerResponse;
import com.github.dockerjava.api.command.ExecCreateCmdResponse;
import com.github.dockerjava.api.command.InspectContainerResponse;
import com.github.dockerjava.api.model.Frame;
import com.github.dockerjava.api.model.HostConfig;
import com.github.dockerjava.core.DefaultDockerClientConfig;
import com.github.dockerjava.core.DockerClientConfig;
import com.github.dockerjava.core.DockerClientImpl;
import com.github.dockerjava.core.command.ExecStartResultCallback;
import com.github.dockerjava.httpclient5.ApacheDockerHttpClient;
import com.github.dockerjava.transport.DockerHttpClient;
import com.squadron.workspace.dto.ExecResult;
import com.squadron.workspace.dto.WorkspaceSpec;
import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@Component
@ConditionalOnProperty(name = "squadron.workspace.provider", havingValue = "docker")
public class DockerWorkspaceProvider implements WorkspaceProvider {

    private static final Logger log = LoggerFactory.getLogger(DockerWorkspaceProvider.class);

    private final DockerClient dockerClient;

    @Autowired
    public DockerWorkspaceProvider(
            @Value("${squadron.workspace.docker.host:unix:///var/run/docker.sock}") String dockerHost) {
        DockerClientConfig config = DefaultDockerClientConfig.createDefaultConfigBuilder()
                .withDockerHost(dockerHost)
                .build();
        DockerHttpClient httpClient = new ApacheDockerHttpClient.Builder()
                .dockerHost(URI.create(dockerHost))
                .build();
        this.dockerClient = DockerClientImpl.getInstance(config, httpClient);
    }

    // Visible for testing
    @SuppressWarnings("unused")
    private DockerWorkspaceProvider(DockerClient dockerClient) {
        this.dockerClient = dockerClient;
    }

    @Override
    public String getProviderType() {
        return "DOCKER";
    }

    @Override
    public String createContainer(WorkspaceSpec spec) {
        String image = spec.getBaseImage() != null ? spec.getBaseImage() : "ubuntu:22.04";

        try {
            HostConfig hostConfig = HostConfig.newHostConfig();

            if (spec.getResourceLimits() != null) {
                Map<String, Object> limits = spec.getResourceLimits();
                if (limits.containsKey("memory")) {
                    String memoryStr = limits.get("memory").toString();
                    long memoryBytes = parseMemoryLimit(memoryStr);
                    hostConfig.withMemory(memoryBytes);
                }
                if (limits.containsKey("cpu")) {
                    String cpuStr = limits.get("cpu").toString();
                    long nanoCpus = (long) (Double.parseDouble(cpuStr) * 1_000_000_000L);
                    hostConfig.withNanoCPUs(nanoCpus);
                }
            }

            CreateContainerResponse container = dockerClient.createContainerCmd(image)
                    .withHostConfig(hostConfig)
                    .withCmd("sleep", "infinity")
                    .withLabels(Map.of(
                            "squadron.task-id", spec.getTaskId().toString(),
                            "squadron.tenant-id", spec.getTenantId().toString(),
                            "squadron.app", "squadron-workspace"
                    ))
                    .exec();

            String containerId = container.getId();
            dockerClient.startContainerCmd(containerId).exec();
            log.info("Created and started Docker container {}", containerId);
            return containerId;
        } catch (Exception e) {
            log.error("Failed to create Docker container", e);
            throw new RuntimeException("Failed to create Docker workspace", e);
        }
    }

    @Override
    public void destroyContainer(String containerId) {
        try {
            dockerClient.stopContainerCmd(containerId).withTimeout(10).exec();
            dockerClient.removeContainerCmd(containerId).withForce(true).exec();
            log.info("Stopped and removed Docker container {}", containerId);
        } catch (Exception e) {
            log.error("Failed to destroy Docker container {}", containerId, e);
            throw new RuntimeException("Failed to destroy Docker workspace", e);
        }
    }

    @Override
    public ExecResult exec(String containerId, String[] command) {
        long startTime = System.currentTimeMillis();
        try {
            ExecCreateCmdResponse execCreate = dockerClient.execCreateCmd(containerId)
                    .withAttachStdout(true)
                    .withAttachStderr(true)
                    .withCmd(command)
                    .exec();

            ByteArrayOutputStream stdoutStream = new ByteArrayOutputStream();
            ByteArrayOutputStream stderrStream = new ByteArrayOutputStream();

            dockerClient.execStartCmd(execCreate.getId())
                    .exec(new ExecStartResultCallback(stdoutStream, stderrStream))
                    .awaitCompletion(300, TimeUnit.SECONDS);

            Long inspectedExitCode = dockerClient.inspectExecCmd(execCreate.getId()).exec().getExitCodeLong();
            int exitCode = inspectedExitCode != null ? inspectedExitCode.intValue() : -1;
            long durationMs = System.currentTimeMillis() - startTime;

            return ExecResult.builder()
                    .exitCode(exitCode)
                    .stdout(stdoutStream.toString(StandardCharsets.UTF_8))
                    .stderr(stderrStream.toString(StandardCharsets.UTF_8))
                    .durationMs(durationMs)
                    .build();
        } catch (Exception e) {
            long durationMs = System.currentTimeMillis() - startTime;
            log.error("Failed to exec in container {}", containerId, e);
            return ExecResult.builder()
                    .exitCode(-1)
                    .stderr("Exec failed: " + e.getMessage())
                    .durationMs(durationMs)
                    .build();
        }
    }

    @Override
    public void copyToContainer(String containerId, byte[] content, String containerPath) {
        try {
            // Determine directory and filename from containerPath
            String directory;
            String fileName;
            int lastSlash = containerPath.lastIndexOf('/');
            if (lastSlash >= 0) {
                directory = containerPath.substring(0, lastSlash);
                fileName = containerPath.substring(lastSlash + 1);
            } else {
                directory = "/";
                fileName = containerPath;
            }
            if (directory.isEmpty()) {
                directory = "/";
            }

            // Build a tar archive containing the file
            ByteArrayOutputStream tarBytes = new ByteArrayOutputStream();
            try (TarArchiveOutputStream tarOut = new TarArchiveOutputStream(tarBytes)) {
                TarArchiveEntry entry = new TarArchiveEntry(fileName);
                entry.setSize(content.length);
                tarOut.putArchiveEntry(entry);
                tarOut.write(content);
                tarOut.closeArchiveEntry();
                tarOut.finish();
            }

            dockerClient.copyArchiveToContainerCmd(containerId)
                    .withRemotePath(directory)
                    .withTarInputStream(new ByteArrayInputStream(tarBytes.toByteArray()))
                    .exec();

            log.info("Copied {} bytes to container {} at {}", content.length, containerId, containerPath);
        } catch (Exception e) {
            log.error("Failed to copy to container {} at path {}", containerId, containerPath, e);
            throw new RuntimeException("Failed to copy to container", e);
        }
    }

    @Override
    public byte[] copyFromContainer(String containerId, String containerPath) {
        try {
            try (InputStream tarStream = dockerClient.copyArchiveFromContainerCmd(containerId, containerPath).exec()) {
                try (TarArchiveInputStream tarIn = new TarArchiveInputStream(tarStream)) {
                    TarArchiveEntry entry = tarIn.getNextEntry();
                    if (entry == null) {
                        throw new RuntimeException("No entry found in tar archive for path " + containerPath);
                    }
                    ByteArrayOutputStream out = new ByteArrayOutputStream();
                    tarIn.transferTo(out);
                    log.info("Copied {} bytes from container {} at {}", out.size(), containerId, containerPath);
                    return out.toByteArray();
                }
            }
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            log.error("Failed to copy from container {} at path {}", containerId, containerPath, e);
            throw new RuntimeException("Failed to copy from container", e);
        }
    }

    @Override
    public String getContainerStatus(String containerId) {
        try {
            InspectContainerResponse inspect = dockerClient.inspectContainerCmd(containerId).exec();
            InspectContainerResponse.ContainerState state = inspect.getState();
            if (state == null) {
                return "UNKNOWN";
            }
            if (Boolean.TRUE.equals(state.getRunning())) {
                return "READY";
            } else if (Boolean.TRUE.equals(state.getPaused())) {
                return "PAUSED";
            } else if (Boolean.TRUE.equals(state.getRestarting())) {
                return "CREATING";
            } else {
                return "TERMINATED";
            }
        } catch (Exception e) {
            log.error("Failed to get status for container {}", containerId, e);
            return "UNKNOWN";
        }
    }

    private long parseMemoryLimit(String memoryStr) {
        memoryStr = memoryStr.trim().toUpperCase();
        if (memoryStr.endsWith("GI")) {
            return Long.parseLong(memoryStr.replace("GI", "")) * 1024L * 1024L * 1024L;
        } else if (memoryStr.endsWith("MI")) {
            return Long.parseLong(memoryStr.replace("MI", "")) * 1024L * 1024L;
        } else if (memoryStr.endsWith("KI")) {
            return Long.parseLong(memoryStr.replace("KI", "")) * 1024L;
        } else if (memoryStr.endsWith("G")) {
            return Long.parseLong(memoryStr.replace("G", "")) * 1000L * 1000L * 1000L;
        } else if (memoryStr.endsWith("M")) {
            return Long.parseLong(memoryStr.replace("M", "")) * 1000L * 1000L;
        } else if (memoryStr.endsWith("K")) {
            return Long.parseLong(memoryStr.replace("K", "")) * 1000L;
        }
        return Long.parseLong(memoryStr);
    }
}
