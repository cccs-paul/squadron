package com.squadron.agent.tool.builtin;

import com.squadron.agent.tool.ToolDefinition;
import com.squadron.agent.tool.ToolExecutionContext;
import com.squadron.agent.tool.ToolResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ShellExecToolTest {

    @Mock
    private WorkspaceClient workspaceClient;

    private ShellExecTool shellExecTool;

    @BeforeEach
    void setUp() {
        shellExecTool = new ShellExecTool(workspaceClient);
    }

    @Test
    void should_returnToolName() {
        assertEquals("shell_exec", shellExecTool.getName());
    }

    @Test
    void should_returnToolDescription() {
        assertNotNull(shellExecTool.getDescription());
        assertTrue(shellExecTool.getDescription().contains("Execute a shell command"));
    }

    @Test
    void should_returnToolDefinition_with_parameters() {
        ToolDefinition definition = shellExecTool.getDefinition();
        assertNotNull(definition);
        assertEquals("shell_exec", definition.getName());
        assertEquals(2, definition.getParameters().size());
        assertEquals("command", definition.getParameters().get(0).getName());
        assertTrue(definition.getParameters().get(0).isRequired());
        assertEquals("workdir", definition.getParameters().get(1).getName());
        assertFalse(definition.getParameters().get(1).isRequired());
        assertEquals("/workspace", definition.getParameters().get(1).getDefaultValue());
    }

    @Test
    void should_executeCommand_when_validInput() {
        UUID workspaceId = UUID.randomUUID();

        ToolExecutionContext context = ToolExecutionContext.builder()
                .workspaceId(workspaceId)
                .taskId(UUID.randomUUID())
                .tenantId(UUID.randomUUID())
                .parameters(Map.of("command", "ls -la"))
                .build();

        when(workspaceClient.exec(eq(workspaceId), eq("bash"), eq("-c"),
                eq("cd '/workspace' && ls -la")))
                .thenReturn(ExecResultDto.builder()
                        .exitCode(0)
                        .stdout("total 0\ndrwxr-xr-x 2 root root 40 Jan 1 00:00 .")
                        .stderr("")
                        .build());

        ToolResult result = shellExecTool.execute(context);

        assertTrue(result.isSuccess());
        assertEquals("shell_exec", result.getToolName());
        assertTrue(result.getOutput().contains("drwxr-xr-x"));
        assertNull(result.getError());
    }

    @Test
    void should_useCustomWorkdir_when_provided() {
        UUID workspaceId = UUID.randomUUID();

        ToolExecutionContext context = ToolExecutionContext.builder()
                .workspaceId(workspaceId)
                .taskId(UUID.randomUUID())
                .tenantId(UUID.randomUUID())
                .parameters(Map.of("command", "mvn compile", "workdir", "/workspace/project"))
                .build();

        when(workspaceClient.exec(eq(workspaceId), eq("bash"), eq("-c"),
                eq("cd '/workspace/project' && mvn compile")))
                .thenReturn(ExecResultDto.builder()
                        .exitCode(0)
                        .stdout("BUILD SUCCESS")
                        .stderr("")
                        .build());

        ToolResult result = shellExecTool.execute(context);

        assertTrue(result.isSuccess());
        assertTrue(result.getOutput().contains("BUILD SUCCESS"));
    }

    @Test
    void should_returnError_when_commandIsMissing() {
        ToolExecutionContext context = ToolExecutionContext.builder()
                .workspaceId(UUID.randomUUID())
                .taskId(UUID.randomUUID())
                .tenantId(UUID.randomUUID())
                .parameters(Map.of())
                .build();

        ToolResult result = shellExecTool.execute(context);

        assertFalse(result.isSuccess());
        assertTrue(result.getError().contains("'command' is missing"));
    }

    @Test
    void should_returnError_when_commandIsBlank() {
        ToolExecutionContext context = ToolExecutionContext.builder()
                .workspaceId(UUID.randomUUID())
                .taskId(UUID.randomUUID())
                .tenantId(UUID.randomUUID())
                .parameters(Map.of("command", "  "))
                .build();

        ToolResult result = shellExecTool.execute(context);

        assertFalse(result.isSuccess());
        assertTrue(result.getError().contains("'command' is missing"));
    }

    @Test
    void should_includeStderr_when_commandProducesStderr() {
        UUID workspaceId = UUID.randomUUID();

        ToolExecutionContext context = ToolExecutionContext.builder()
                .workspaceId(workspaceId)
                .taskId(UUID.randomUUID())
                .tenantId(UUID.randomUUID())
                .parameters(Map.of("command", "make build"))
                .build();

        when(workspaceClient.exec(eq(workspaceId), eq("bash"), eq("-c"),
                eq("cd '/workspace' && make build")))
                .thenReturn(ExecResultDto.builder()
                        .exitCode(0)
                        .stdout("built successfully")
                        .stderr("warning: deprecated API")
                        .build());

        ToolResult result = shellExecTool.execute(context);

        assertTrue(result.isSuccess());
        assertTrue(result.getOutput().contains("built successfully"));
        assertTrue(result.getOutput().contains("[stderr]"));
        assertTrue(result.getOutput().contains("warning: deprecated API"));
    }

    @Test
    void should_includeExitCode_when_commandFails() {
        UUID workspaceId = UUID.randomUUID();

        ToolExecutionContext context = ToolExecutionContext.builder()
                .workspaceId(workspaceId)
                .taskId(UUID.randomUUID())
                .tenantId(UUID.randomUUID())
                .parameters(Map.of("command", "exit 42"))
                .build();

        when(workspaceClient.exec(eq(workspaceId), eq("bash"), eq("-c"),
                eq("cd '/workspace' && exit 42")))
                .thenReturn(ExecResultDto.builder()
                        .exitCode(42)
                        .stdout("")
                        .stderr("error occurred")
                        .build());

        ToolResult result = shellExecTool.execute(context);

        assertFalse(result.isSuccess());
        assertTrue(result.getOutput().contains("[exit code 42]"));
        assertTrue(result.getError().contains("exited with code 42"));
    }

    @Test
    void should_truncateOutput_when_outputExceeds50KB() {
        UUID workspaceId = UUID.randomUUID();
        String largeOutput = "x".repeat(60 * 1024); // 60KB

        ToolExecutionContext context = ToolExecutionContext.builder()
                .workspaceId(workspaceId)
                .taskId(UUID.randomUUID())
                .tenantId(UUID.randomUUID())
                .parameters(Map.of("command", "cat bigfile"))
                .build();

        when(workspaceClient.exec(eq(workspaceId), eq("bash"), eq("-c"),
                eq("cd '/workspace' && cat bigfile")))
                .thenReturn(ExecResultDto.builder()
                        .exitCode(0)
                        .stdout(largeOutput)
                        .stderr("")
                        .build());

        ToolResult result = shellExecTool.execute(context);

        assertTrue(result.isSuccess());
        assertTrue(result.getOutput().contains("[truncated"));
        assertTrue(result.getOutput().length() < largeOutput.length());
    }

    @Test
    void should_returnError_when_workspaceClientThrowsException() {
        UUID workspaceId = UUID.randomUUID();

        ToolExecutionContext context = ToolExecutionContext.builder()
                .workspaceId(workspaceId)
                .taskId(UUID.randomUUID())
                .tenantId(UUID.randomUUID())
                .parameters(Map.of("command", "ls"))
                .build();

        when(workspaceClient.exec(eq(workspaceId), eq("bash"), eq("-c"),
                eq("cd '/workspace' && ls")))
                .thenThrow(new WorkspaceClient.WorkspaceClientException("Connection timeout"));

        ToolResult result = shellExecTool.execute(context);

        assertFalse(result.isSuccess());
        assertTrue(result.getError().contains("Connection timeout"));
    }

    @Test
    void should_useDefaultWorkdir_when_workdirIsNull() {
        UUID workspaceId = UUID.randomUUID();

        Map<String, Object> params = new HashMap<>();
        params.put("command", "pwd");
        params.put("workdir", null);

        ToolExecutionContext context = ToolExecutionContext.builder()
                .workspaceId(workspaceId)
                .taskId(UUID.randomUUID())
                .tenantId(UUID.randomUUID())
                .parameters(params)
                .build();

        when(workspaceClient.exec(eq(workspaceId), eq("bash"), eq("-c"),
                eq("cd '/workspace' && pwd")))
                .thenReturn(ExecResultDto.builder()
                        .exitCode(0)
                        .stdout("/workspace")
                        .stderr("")
                        .build());

        ToolResult result = shellExecTool.execute(context);

        assertTrue(result.isSuccess());
        assertEquals("/workspace", result.getOutput());
    }
}
