package com.squadron.workspace.provider;

import com.squadron.workspace.dto.ExecResult;
import com.squadron.workspace.dto.WorkspaceSpec;
import io.kubernetes.client.openapi.ApiClient;
import io.kubernetes.client.openapi.ApiException;
import io.kubernetes.client.openapi.apis.CoreV1Api;
import io.kubernetes.client.openapi.models.V1ContainerStatus;
import io.kubernetes.client.openapi.models.V1ObjectMeta;
import io.kubernetes.client.openapi.models.V1Pod;
import io.kubernetes.client.openapi.models.V1PodStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class KubernetesWorkspaceProviderTest {

    @Mock
    private CoreV1Api coreV1Api;

    @Mock
    private ApiClient apiClient;

    private KubernetesWorkspaceProvider provider;

    @BeforeEach
    void setUp() {
        provider = new KubernetesWorkspaceProvider(coreV1Api, apiClient, "test-namespace", "ubuntu:22.04");
    }

    @Test
    void should_returnProviderType() {
        assertEquals("KUBERNETES", provider.getProviderType());
    }

    @Test
    void should_createContainer_successfully() throws Exception {
        WorkspaceSpec spec = WorkspaceSpec.builder()
                .tenantId(UUID.randomUUID())
                .taskId(UUID.randomUUID())
                .userId(UUID.randomUUID())
                .repoUrl("https://github.com/test/repo.git")
                .branch("main")
                .baseImage("node:18")
                .resourceLimits(Map.of("memory", "512Mi"))
                .build();

        when(coreV1Api.createNamespacedPod(eq("test-namespace"), any(V1Pod.class),
                isNull(), isNull(), isNull(), isNull())).thenReturn(new V1Pod());

        String containerId = provider.createContainer(spec);

        assertNotNull(containerId);
        assertTrue(containerId.startsWith("workspace-"));
        verify(coreV1Api).createNamespacedPod(eq("test-namespace"), any(V1Pod.class),
                isNull(), isNull(), isNull(), isNull());
    }

    @Test
    void should_createContainer_withDefaultImage() throws Exception {
        WorkspaceSpec spec = WorkspaceSpec.builder()
                .tenantId(UUID.randomUUID())
                .taskId(UUID.randomUUID())
                .userId(UUID.randomUUID())
                .repoUrl("https://github.com/test/repo.git")
                .build();

        when(coreV1Api.createNamespacedPod(eq("test-namespace"), any(V1Pod.class),
                isNull(), isNull(), isNull(), isNull())).thenReturn(new V1Pod());

        String containerId = provider.createContainer(spec);

        assertNotNull(containerId);
    }

    @Test
    void should_throwRuntimeException_whenCreateFails() throws Exception {
        WorkspaceSpec spec = WorkspaceSpec.builder()
                .tenantId(UUID.randomUUID())
                .taskId(UUID.randomUUID())
                .userId(UUID.randomUUID())
                .repoUrl("https://github.com/test/repo.git")
                .build();

        when(coreV1Api.createNamespacedPod(eq("test-namespace"), any(V1Pod.class),
                isNull(), isNull(), isNull(), isNull())).thenThrow(new ApiException("create failed"));

        assertThrows(RuntimeException.class, () -> provider.createContainer(spec));
    }

    @Test
    void should_destroyContainer_successfully() throws Exception {
        when(coreV1Api.deleteNamespacedPod(eq("pod-123"), eq("test-namespace"),
                isNull(), isNull(), isNull(), isNull(), isNull(), isNull())).thenReturn(new V1Pod());

        assertDoesNotThrow(() -> provider.destroyContainer("pod-123"));
    }

    @Test
    void should_throwRuntimeException_whenDestroyFails() throws Exception {
        when(coreV1Api.deleteNamespacedPod(eq("pod-123"), eq("test-namespace"),
                isNull(), isNull(), isNull(), isNull(), isNull(), isNull()))
                .thenThrow(new ApiException("delete failed"));

        assertThrows(RuntimeException.class, () -> provider.destroyContainer("pod-123"));
    }

    @Test
    void should_getContainerStatus_running_ready() throws Exception {
        V1ContainerStatus containerStatus = new V1ContainerStatus();
        containerStatus.setReady(true);

        V1PodStatus podStatus = new V1PodStatus();
        podStatus.setPhase("Running");
        podStatus.setContainerStatuses(List.of(containerStatus));

        V1Pod pod = new V1Pod();
        pod.setStatus(podStatus);

        when(coreV1Api.readNamespacedPod("pod-123", "test-namespace", null)).thenReturn(pod);

        assertEquals("READY", provider.getContainerStatus("pod-123"));
    }

    @Test
    void should_getContainerStatus_running_notReady() throws Exception {
        V1ContainerStatus containerStatus = new V1ContainerStatus();
        containerStatus.setReady(false);

        V1PodStatus podStatus = new V1PodStatus();
        podStatus.setPhase("Running");
        podStatus.setContainerStatuses(List.of(containerStatus));

        V1Pod pod = new V1Pod();
        pod.setStatus(podStatus);

        when(coreV1Api.readNamespacedPod("pod-123", "test-namespace", null)).thenReturn(pod);

        assertEquals("RUNNING", provider.getContainerStatus("pod-123"));
    }

    @Test
    void should_getContainerStatus_running_noContainerStatuses() throws Exception {
        V1PodStatus podStatus = new V1PodStatus();
        podStatus.setPhase("Running");

        V1Pod pod = new V1Pod();
        pod.setStatus(podStatus);

        when(coreV1Api.readNamespacedPod("pod-123", "test-namespace", null)).thenReturn(pod);

        assertEquals("RUNNING", provider.getContainerStatus("pod-123"));
    }

    @Test
    void should_getContainerStatus_pending() throws Exception {
        V1PodStatus podStatus = new V1PodStatus();
        podStatus.setPhase("Pending");

        V1Pod pod = new V1Pod();
        pod.setStatus(podStatus);

        when(coreV1Api.readNamespacedPod("pod-123", "test-namespace", null)).thenReturn(pod);

        assertEquals("CREATING", provider.getContainerStatus("pod-123"));
    }

    @Test
    void should_getContainerStatus_succeeded() throws Exception {
        V1PodStatus podStatus = new V1PodStatus();
        podStatus.setPhase("Succeeded");

        V1Pod pod = new V1Pod();
        pod.setStatus(podStatus);

        when(coreV1Api.readNamespacedPod("pod-123", "test-namespace", null)).thenReturn(pod);

        assertEquals("TERMINATED", provider.getContainerStatus("pod-123"));
    }

    @Test
    void should_getContainerStatus_failed() throws Exception {
        V1PodStatus podStatus = new V1PodStatus();
        podStatus.setPhase("Failed");

        V1Pod pod = new V1Pod();
        pod.setStatus(podStatus);

        when(coreV1Api.readNamespacedPod("pod-123", "test-namespace", null)).thenReturn(pod);

        assertEquals("TERMINATED", provider.getContainerStatus("pod-123"));
    }

    @Test
    void should_getContainerStatus_nullStatus() throws Exception {
        V1Pod pod = new V1Pod();

        when(coreV1Api.readNamespacedPod("pod-123", "test-namespace", null)).thenReturn(pod);

        assertEquals("UNKNOWN", provider.getContainerStatus("pod-123"));
    }

    @Test
    void should_getContainerStatus_unknown_onException() throws Exception {
        when(coreV1Api.readNamespacedPod("pod-123", "test-namespace", null))
                .thenThrow(new ApiException("not found"));

        assertEquals("UNKNOWN", provider.getContainerStatus("pod-123"));
    }

    @Test
    void should_getContainerStatus_nullPhase() throws Exception {
        V1PodStatus podStatus = new V1PodStatus();

        V1Pod pod = new V1Pod();
        pod.setStatus(podStatus);

        when(coreV1Api.readNamespacedPod("pod-123", "test-namespace", null)).thenReturn(pod);

        assertEquals("UNKNOWN", provider.getContainerStatus("pod-123"));
    }

    @Test
    void should_copyToContainer_successfully() {
        // copyToContainer uses exec internally; we need to spy on the provider
        KubernetesWorkspaceProvider spyProvider = spy(provider);
        ExecResult successResult = ExecResult.builder()
                .exitCode(0)
                .stdout("")
                .stderr("")
                .durationMs(10)
                .build();

        doReturn(successResult).when(spyProvider).exec(eq("pod-123"), any(String[].class));

        byte[] content = "hello world".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        assertDoesNotThrow(() -> spyProvider.copyToContainer("pod-123", content, "/tmp/test.txt"));
        verify(spyProvider).exec(eq("pod-123"), any(String[].class));
    }

    @Test
    void should_copyToContainer_throwOnFailure() {
        KubernetesWorkspaceProvider spyProvider = spy(provider);
        ExecResult failResult = ExecResult.builder()
                .exitCode(1)
                .stdout("")
                .stderr("No such file or directory")
                .durationMs(10)
                .build();

        doReturn(failResult).when(spyProvider).exec(eq("pod-123"), any(String[].class));

        byte[] content = "hello world".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> spyProvider.copyToContainer("pod-123", content, "/tmp/test.txt"));
        assertTrue(ex.getMessage().contains("Failed to copy to container"));
    }

    @Test
    void should_copyFromContainer_successfully() {
        KubernetesWorkspaceProvider spyProvider = spy(provider);
        ExecResult successResult = ExecResult.builder()
                .exitCode(0)
                .stdout("file content here")
                .stderr("")
                .durationMs(10)
                .build();

        doReturn(successResult).when(spyProvider).exec(eq("pod-123"), any(String[].class));

        byte[] result = spyProvider.copyFromContainer("pod-123", "/tmp/test.txt");
        assertNotNull(result);
        assertEquals("file content here", new String(result, java.nio.charset.StandardCharsets.UTF_8));
        verify(spyProvider).exec(eq("pod-123"), eq(new String[]{"cat", "/tmp/test.txt"}));
    }

    @Test
    void should_copyFromContainer_throwOnFailure() {
        KubernetesWorkspaceProvider spyProvider = spy(provider);
        ExecResult failResult = ExecResult.builder()
                .exitCode(1)
                .stdout("")
                .stderr("cat: /tmp/missing.txt: No such file or directory")
                .durationMs(10)
                .build();

        doReturn(failResult).when(spyProvider).exec(eq("pod-123"), any(String[].class));

        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> spyProvider.copyFromContainer("pod-123", "/tmp/missing.txt"));
        assertTrue(ex.getMessage().contains("Failed to copy from container"));
    }
}
