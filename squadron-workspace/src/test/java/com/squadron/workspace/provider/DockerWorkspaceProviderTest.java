package com.squadron.workspace.provider;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.command.CopyArchiveFromContainerCmd;
import com.github.dockerjava.api.command.CopyArchiveToContainerCmd;
import com.github.dockerjava.api.command.CreateContainerCmd;
import com.github.dockerjava.api.command.CreateContainerResponse;
import com.github.dockerjava.api.command.ExecCreateCmd;
import com.github.dockerjava.api.command.ExecCreateCmdResponse;
import com.github.dockerjava.api.command.ExecStartCmd;
import com.github.dockerjava.api.command.InspectContainerCmd;
import com.github.dockerjava.api.command.InspectContainerResponse;
import com.github.dockerjava.api.command.InspectExecCmd;
import com.github.dockerjava.api.command.InspectExecResponse;
import com.github.dockerjava.api.command.RemoveContainerCmd;
import com.github.dockerjava.api.command.StartContainerCmd;
import com.github.dockerjava.api.command.StopContainerCmd;
import com.github.dockerjava.core.command.ExecStartResultCallback;
import com.squadron.workspace.dto.ExecResult;
import com.squadron.workspace.dto.WorkspaceSpec;
import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.lang.reflect.Constructor;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DockerWorkspaceProviderTest {

    @Mock
    private DockerClient dockerClient;

    private DockerWorkspaceProvider provider;

    @BeforeEach
    void setUp() throws Exception {
        Constructor<DockerWorkspaceProvider> ctor = DockerWorkspaceProvider.class
                .getDeclaredConstructor(DockerClient.class);
        ctor.setAccessible(true);
        provider = ctor.newInstance(dockerClient);
    }

    @Test
    void should_returnProviderType() {
        assertEquals("DOCKER", provider.getProviderType());
    }

    @Test
    void should_createContainer_successfully() {
        WorkspaceSpec spec = WorkspaceSpec.builder()
                .tenantId(UUID.randomUUID())
                .taskId(UUID.randomUUID())
                .userId(UUID.randomUUID())
                .repoUrl("https://github.com/test/repo.git")
                .baseImage("node:18")
                .build();

        CreateContainerCmd createCmd = mock(CreateContainerCmd.class);
        CreateContainerResponse response = mock(CreateContainerResponse.class);
        StartContainerCmd startCmd = mock(StartContainerCmd.class);

        when(dockerClient.createContainerCmd("node:18")).thenReturn(createCmd);
        when(createCmd.withHostConfig(any())).thenReturn(createCmd);
        when(createCmd.withCmd(anyString(), anyString())).thenReturn(createCmd);
        when(createCmd.withLabels(any())).thenReturn(createCmd);
        when(createCmd.exec()).thenReturn(response);
        when(response.getId()).thenReturn("container-abc123");
        when(dockerClient.startContainerCmd("container-abc123")).thenReturn(startCmd);

        String containerId = provider.createContainer(spec);

        assertEquals("container-abc123", containerId);
        verify(startCmd).exec();
    }

    @Test
    void should_createContainer_withDefaultImage_whenBaseImageIsNull() {
        WorkspaceSpec spec = WorkspaceSpec.builder()
                .tenantId(UUID.randomUUID())
                .taskId(UUID.randomUUID())
                .userId(UUID.randomUUID())
                .repoUrl("https://github.com/test/repo.git")
                .build();

        CreateContainerCmd createCmd = mock(CreateContainerCmd.class);
        CreateContainerResponse response = mock(CreateContainerResponse.class);
        StartContainerCmd startCmd = mock(StartContainerCmd.class);

        when(dockerClient.createContainerCmd("ubuntu:22.04")).thenReturn(createCmd);
        when(createCmd.withHostConfig(any())).thenReturn(createCmd);
        when(createCmd.withCmd(anyString(), anyString())).thenReturn(createCmd);
        when(createCmd.withLabels(any())).thenReturn(createCmd);
        when(createCmd.exec()).thenReturn(response);
        when(response.getId()).thenReturn("container-def456");
        when(dockerClient.startContainerCmd("container-def456")).thenReturn(startCmd);

        String containerId = provider.createContainer(spec);

        assertEquals("container-def456", containerId);
    }

    @Test
    void should_createContainer_withResourceLimits() {
        WorkspaceSpec spec = WorkspaceSpec.builder()
                .tenantId(UUID.randomUUID())
                .taskId(UUID.randomUUID())
                .userId(UUID.randomUUID())
                .repoUrl("https://github.com/test/repo.git")
                .resourceLimits(Map.of("memory", "512Mi", "cpu", "1.5"))
                .build();

        CreateContainerCmd createCmd = mock(CreateContainerCmd.class);
        CreateContainerResponse response = mock(CreateContainerResponse.class);
        StartContainerCmd startCmd = mock(StartContainerCmd.class);

        when(dockerClient.createContainerCmd("ubuntu:22.04")).thenReturn(createCmd);
        when(createCmd.withHostConfig(any())).thenReturn(createCmd);
        when(createCmd.withCmd(anyString(), anyString())).thenReturn(createCmd);
        when(createCmd.withLabels(any())).thenReturn(createCmd);
        when(createCmd.exec()).thenReturn(response);
        when(response.getId()).thenReturn("container-limits");
        when(dockerClient.startContainerCmd("container-limits")).thenReturn(startCmd);

        String containerId = provider.createContainer(spec);

        assertEquals("container-limits", containerId);
    }

    @Test
    void should_throwRuntimeException_whenCreateFails() {
        WorkspaceSpec spec = WorkspaceSpec.builder()
                .tenantId(UUID.randomUUID())
                .taskId(UUID.randomUUID())
                .userId(UUID.randomUUID())
                .repoUrl("https://github.com/test/repo.git")
                .build();

        when(dockerClient.createContainerCmd("ubuntu:22.04")).thenThrow(new RuntimeException("Docker error"));

        assertThrows(RuntimeException.class, () -> provider.createContainer(spec));
    }

    @Test
    void should_destroyContainer_successfully() {
        StopContainerCmd stopCmd = mock(StopContainerCmd.class);
        RemoveContainerCmd removeCmd = mock(RemoveContainerCmd.class);

        when(dockerClient.stopContainerCmd("container-123")).thenReturn(stopCmd);
        when(stopCmd.withTimeout(10)).thenReturn(stopCmd);
        when(dockerClient.removeContainerCmd("container-123")).thenReturn(removeCmd);
        when(removeCmd.withForce(true)).thenReturn(removeCmd);

        assertDoesNotThrow(() -> provider.destroyContainer("container-123"));
        verify(stopCmd).exec();
        verify(removeCmd).exec();
    }

    @Test
    void should_throwRuntimeException_whenDestroyFails() {
        StopContainerCmd stopCmd = mock(StopContainerCmd.class);
        when(dockerClient.stopContainerCmd("container-123")).thenReturn(stopCmd);
        when(stopCmd.withTimeout(10)).thenReturn(stopCmd);
        doThrow(new RuntimeException("stop error")).when(stopCmd).exec();

        assertThrows(RuntimeException.class, () -> provider.destroyContainer("container-123"));
    }

    @Test
    void should_getContainerStatus_running() {
        InspectContainerCmd inspectCmd = mock(InspectContainerCmd.class);
        InspectContainerResponse inspectResponse = mock(InspectContainerResponse.class);
        InspectContainerResponse.ContainerState state = mock(InspectContainerResponse.ContainerState.class);

        when(dockerClient.inspectContainerCmd("container-123")).thenReturn(inspectCmd);
        when(inspectCmd.exec()).thenReturn(inspectResponse);
        when(inspectResponse.getState()).thenReturn(state);
        when(state.getRunning()).thenReturn(true);

        assertEquals("READY", provider.getContainerStatus("container-123"));
    }

    @Test
    void should_getContainerStatus_paused() {
        InspectContainerCmd inspectCmd = mock(InspectContainerCmd.class);
        InspectContainerResponse inspectResponse = mock(InspectContainerResponse.class);
        InspectContainerResponse.ContainerState state = mock(InspectContainerResponse.ContainerState.class);

        when(dockerClient.inspectContainerCmd("container-123")).thenReturn(inspectCmd);
        when(inspectCmd.exec()).thenReturn(inspectResponse);
        when(inspectResponse.getState()).thenReturn(state);
        when(state.getRunning()).thenReturn(false);
        when(state.getPaused()).thenReturn(true);

        assertEquals("PAUSED", provider.getContainerStatus("container-123"));
    }

    @Test
    void should_getContainerStatus_restarting() {
        InspectContainerCmd inspectCmd = mock(InspectContainerCmd.class);
        InspectContainerResponse inspectResponse = mock(InspectContainerResponse.class);
        InspectContainerResponse.ContainerState state = mock(InspectContainerResponse.ContainerState.class);

        when(dockerClient.inspectContainerCmd("container-123")).thenReturn(inspectCmd);
        when(inspectCmd.exec()).thenReturn(inspectResponse);
        when(inspectResponse.getState()).thenReturn(state);
        when(state.getRunning()).thenReturn(false);
        when(state.getPaused()).thenReturn(false);
        when(state.getRestarting()).thenReturn(true);

        assertEquals("CREATING", provider.getContainerStatus("container-123"));
    }

    @Test
    void should_getContainerStatus_terminated() {
        InspectContainerCmd inspectCmd = mock(InspectContainerCmd.class);
        InspectContainerResponse inspectResponse = mock(InspectContainerResponse.class);
        InspectContainerResponse.ContainerState state = mock(InspectContainerResponse.ContainerState.class);

        when(dockerClient.inspectContainerCmd("container-123")).thenReturn(inspectCmd);
        when(inspectCmd.exec()).thenReturn(inspectResponse);
        when(inspectResponse.getState()).thenReturn(state);
        when(state.getRunning()).thenReturn(false);
        when(state.getPaused()).thenReturn(false);
        when(state.getRestarting()).thenReturn(false);

        assertEquals("TERMINATED", provider.getContainerStatus("container-123"));
    }

    @Test
    void should_getContainerStatus_nullState() {
        InspectContainerCmd inspectCmd = mock(InspectContainerCmd.class);
        InspectContainerResponse inspectResponse = mock(InspectContainerResponse.class);

        when(dockerClient.inspectContainerCmd("container-123")).thenReturn(inspectCmd);
        when(inspectCmd.exec()).thenReturn(inspectResponse);
        when(inspectResponse.getState()).thenReturn(null);

        assertEquals("UNKNOWN", provider.getContainerStatus("container-123"));
    }

    @Test
    void should_getContainerStatus_unknown_onException() {
        when(dockerClient.inspectContainerCmd("container-123")).thenThrow(new RuntimeException("not found"));

        assertEquals("UNKNOWN", provider.getContainerStatus("container-123"));
    }

    @Test
    void should_exec_returnError_onException() {
        when(dockerClient.execCreateCmd("container-123")).thenThrow(new RuntimeException("exec error"));

        ExecResult result = provider.exec("container-123", new String[]{"ls"});

        assertEquals(-1, result.getExitCode());
        assertTrue(result.getStderr().contains("Exec failed"));
    }

    @Test
    void should_exec_successfully() throws Exception {
        ExecCreateCmd execCreateCmd = mock(ExecCreateCmd.class);
        ExecCreateCmdResponse execCreateResponse = mock(ExecCreateCmdResponse.class);
        ExecStartCmd execStartCmd = mock(ExecStartCmd.class);
        ExecStartResultCallback callback = mock(ExecStartResultCallback.class);
        InspectExecCmd inspectExecCmd = mock(InspectExecCmd.class);
        InspectExecResponse inspectExecResponse = mock(InspectExecResponse.class);

        when(dockerClient.execCreateCmd("container-123")).thenReturn(execCreateCmd);
        when(execCreateCmd.withAttachStdout(true)).thenReturn(execCreateCmd);
        when(execCreateCmd.withAttachStderr(true)).thenReturn(execCreateCmd);
        when(execCreateCmd.withCmd(any(String[].class))).thenReturn(execCreateCmd);
        when(execCreateCmd.exec()).thenReturn(execCreateResponse);
        when(execCreateResponse.getId()).thenReturn("exec-id-123");

        when(dockerClient.execStartCmd("exec-id-123")).thenReturn(execStartCmd);
        doReturn(callback).when(execStartCmd).exec(any(ExecStartResultCallback.class));
        when(callback.awaitCompletion(300, java.util.concurrent.TimeUnit.SECONDS)).thenReturn(true);

        when(dockerClient.inspectExecCmd("exec-id-123")).thenReturn(inspectExecCmd);
        when(inspectExecCmd.exec()).thenReturn(inspectExecResponse);
        when(inspectExecResponse.getExitCodeLong()).thenReturn(0L);

        ExecResult result = provider.exec("container-123", new String[]{"echo", "hello"});

        assertEquals(0, result.getExitCode());
        assertTrue(result.getDurationMs() >= 0);
    }

    @Test
    void should_copyToContainer_successfully() {
        CopyArchiveToContainerCmd copyCmd = mock(CopyArchiveToContainerCmd.class);

        when(dockerClient.copyArchiveToContainerCmd("container-123")).thenReturn(copyCmd);
        when(copyCmd.withRemotePath("/tmp")).thenReturn(copyCmd);
        when(copyCmd.withTarInputStream(any(InputStream.class))).thenReturn(copyCmd);

        byte[] content = "hello world".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        assertDoesNotThrow(() -> provider.copyToContainer("container-123", content, "/tmp/test.txt"));
        verify(copyCmd).exec();
    }

    @Test
    void should_copyToContainer_throwOnFailure() {
        when(dockerClient.copyArchiveToContainerCmd("container-123")).thenThrow(new RuntimeException("Docker error"));

        byte[] content = "hello".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> provider.copyToContainer("container-123", content, "/tmp/test.txt"));
        assertTrue(ex.getMessage().contains("Failed to copy to container"));
    }

    @Test
    void should_copyFromContainer_successfully() throws Exception {
        CopyArchiveFromContainerCmd copyCmd = mock(CopyArchiveFromContainerCmd.class);

        // Build a tar archive with file content
        byte[] fileContent = "file data".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        ByteArrayOutputStream tarBytes = new ByteArrayOutputStream();
        try (TarArchiveOutputStream tarOut = new TarArchiveOutputStream(tarBytes)) {
            TarArchiveEntry entry = new TarArchiveEntry("test.txt");
            entry.setSize(fileContent.length);
            tarOut.putArchiveEntry(entry);
            tarOut.write(fileContent);
            tarOut.closeArchiveEntry();
            tarOut.finish();
        }

        InputStream tarStream = new ByteArrayInputStream(tarBytes.toByteArray());

        when(dockerClient.copyArchiveFromContainerCmd("container-123", "/tmp/test.txt")).thenReturn(copyCmd);
        when(copyCmd.exec()).thenReturn(tarStream);

        byte[] result = provider.copyFromContainer("container-123", "/tmp/test.txt");
        assertNotNull(result);
        assertEquals("file data", new String(result, java.nio.charset.StandardCharsets.UTF_8));
    }

    @Test
    void should_copyFromContainer_throwOnFailure() {
        when(dockerClient.copyArchiveFromContainerCmd("container-123", "/tmp/missing.txt"))
                .thenThrow(new RuntimeException("not found"));

        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> provider.copyFromContainer("container-123", "/tmp/missing.txt"));
        assertTrue(ex.getMessage().contains("not found"));
    }
}
