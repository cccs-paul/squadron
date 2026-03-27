package com.squadron.workspace.provider;

import com.squadron.workspace.dto.ExecResult;
import com.squadron.workspace.dto.WorkspaceSpec;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class WorkspaceProviderTest {

    @Test
    void should_beInterface_when_classInspected() {
        assertTrue(WorkspaceProvider.class.isInterface());
    }

    @Test
    void should_declareGetProviderTypeMethod_when_interfaceInspected() throws NoSuchMethodException {
        Method method = WorkspaceProvider.class.getMethod("getProviderType");
        assertEquals(String.class, method.getReturnType());
        assertEquals(0, method.getParameterCount());
    }

    @Test
    void should_declareCreateContainerMethod_when_interfaceInspected() throws NoSuchMethodException {
        Method method = WorkspaceProvider.class.getMethod("createContainer", WorkspaceSpec.class);
        assertEquals(String.class, method.getReturnType());
        assertEquals(1, method.getParameterCount());
    }

    @Test
    void should_declareDestroyContainerMethod_when_interfaceInspected() throws NoSuchMethodException {
        Method method = WorkspaceProvider.class.getMethod("destroyContainer", String.class);
        assertEquals(void.class, method.getReturnType());
        assertEquals(1, method.getParameterCount());
    }

    @Test
    void should_declareExecMethod_when_interfaceInspected() throws NoSuchMethodException {
        Method method = WorkspaceProvider.class.getMethod("exec", String.class, String[].class);
        assertEquals(ExecResult.class, method.getReturnType());
        assertEquals(2, method.getParameterCount());
    }

    @Test
    void should_declareGetContainerStatusMethod_when_interfaceInspected() throws NoSuchMethodException {
        Method method = WorkspaceProvider.class.getMethod("getContainerStatus", String.class);
        assertEquals(String.class, method.getReturnType());
        assertEquals(1, method.getParameterCount());
    }

    @Test
    void should_declareCopyToContainerMethod_when_interfaceInspected() throws NoSuchMethodException {
        Method method = WorkspaceProvider.class.getMethod("copyToContainer", String.class, byte[].class, String.class);
        assertEquals(void.class, method.getReturnType());
        assertEquals(3, method.getParameterCount());
    }

    @Test
    void should_declareCopyFromContainerMethod_when_interfaceInspected() throws NoSuchMethodException {
        Method method = WorkspaceProvider.class.getMethod("copyFromContainer", String.class, String.class);
        assertEquals(byte[].class, method.getReturnType());
        assertEquals(2, method.getParameterCount());
    }

    @Test
    void should_haveFiveMethods_when_interfaceInspected() {
        Method[] methods = WorkspaceProvider.class.getDeclaredMethods();
        assertEquals(7, methods.length);
    }

    @Test
    void should_implementAllMethods_when_concreteImplementation() {
        WorkspaceProvider provider = new WorkspaceProvider() {
            @Override
            public String getProviderType() {
                return "DOCKER";
            }

            @Override
            public String createContainer(WorkspaceSpec spec) {
                return "container-" + spec.getTaskId();
            }

            @Override
            public void destroyContainer(String containerId) {
                // no-op
            }

            @Override
            public ExecResult exec(String containerId, String[] command) {
                return ExecResult.builder().exitCode(0).stdout("ok").stderr("").durationMs(10).build();
            }

            @Override
            public String getContainerStatus(String containerId) {
                return "RUNNING";
            }

            @Override
            public void copyToContainer(String containerId, byte[] content, String containerPath) {
                // no-op
            }

            @Override
            public byte[] copyFromContainer(String containerId, String containerPath) {
                return new byte[0];
            }
        };

        assertEquals("DOCKER", provider.getProviderType());

        WorkspaceSpec spec = WorkspaceSpec.builder()
                .taskId(UUID.randomUUID())
                .repoUrl("https://github.com/test/repo.git")
                .build();
        assertNotNull(provider.createContainer(spec));

        assertDoesNotThrow(() -> provider.destroyContainer("container-123"));

        ExecResult result = provider.exec("container-123", new String[]{"ls", "-la"});
        assertEquals(0, result.getExitCode());

        assertEquals("RUNNING", provider.getContainerStatus("container-123"));
    }
}
