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
class ListFilesToolTest {

    @Mock
    private WorkspaceClient workspaceClient;

    private ListFilesTool listFilesTool;

    @BeforeEach
    void setUp() {
        listFilesTool = new ListFilesTool(workspaceClient);
    }

    @Test
    void should_returnToolName() {
        assertEquals("list_files", listFilesTool.getName());
    }

    @Test
    void should_returnToolDescription() {
        assertNotNull(listFilesTool.getDescription());
        assertTrue(listFilesTool.getDescription().contains("List files"));
    }

    @Test
    void should_returnToolDefinition_with_parameters() {
        ToolDefinition definition = listFilesTool.getDefinition();
        assertNotNull(definition);
        assertEquals("list_files", definition.getName());
        assertEquals(3, definition.getParameters().size());
        assertEquals("path", definition.getParameters().get(0).getName());
        assertFalse(definition.getParameters().get(0).isRequired());
        assertEquals("recursive", definition.getParameters().get(1).getName());
        assertFalse(definition.getParameters().get(1).isRequired());
        assertEquals("pattern", definition.getParameters().get(2).getName());
        assertFalse(definition.getParameters().get(2).isRequired());
    }

    @Test
    void should_listFiles_when_nonRecursive() {
        UUID workspaceId = UUID.randomUUID();

        ToolExecutionContext context = ToolExecutionContext.builder()
                .workspaceId(workspaceId)
                .taskId(UUID.randomUUID())
                .tenantId(UUID.randomUUID())
                .parameters(Map.of())
                .build();

        when(workspaceClient.exec(eq(workspaceId), eq("bash"), eq("-c"),
                eq("ls -la '/workspace'")))
                .thenReturn(ExecResultDto.builder()
                        .exitCode(0)
                        .stdout("total 8\ndrwxr-xr-x 2 root root 4096 Jan 1 00:00 .\n-rw-r--r-- 1 root root 100 Jan 1 00:00 Main.java")
                        .stderr("")
                        .build());

        ToolResult result = listFilesTool.execute(context);

        assertTrue(result.isSuccess());
        assertEquals("list_files", result.getToolName());
        assertTrue(result.getOutput().contains("Main.java"));
    }

    @Test
    void should_listFilesRecursively_when_recursiveTrue() {
        UUID workspaceId = UUID.randomUUID();

        ToolExecutionContext context = ToolExecutionContext.builder()
                .workspaceId(workspaceId)
                .taskId(UUID.randomUUID())
                .tenantId(UUID.randomUUID())
                .parameters(Map.of("recursive", true))
                .build();

        when(workspaceClient.exec(eq(workspaceId), eq("bash"), eq("-c"),
                eq("find '/workspace'")))
                .thenReturn(ExecResultDto.builder()
                        .exitCode(0)
                        .stdout("/workspace\n/workspace/src\n/workspace/src/Main.java")
                        .stderr("")
                        .build());

        ToolResult result = listFilesTool.execute(context);

        assertTrue(result.isSuccess());
        assertTrue(result.getOutput().contains("/workspace/src/Main.java"));
    }

    @Test
    void should_listFilesRecursively_when_recursiveStringTrue() {
        UUID workspaceId = UUID.randomUUID();

        ToolExecutionContext context = ToolExecutionContext.builder()
                .workspaceId(workspaceId)
                .taskId(UUID.randomUUID())
                .tenantId(UUID.randomUUID())
                .parameters(Map.of("recursive", "true"))
                .build();

        when(workspaceClient.exec(eq(workspaceId), eq("bash"), eq("-c"),
                eq("find '/workspace'")))
                .thenReturn(ExecResultDto.builder()
                        .exitCode(0)
                        .stdout("/workspace\n/workspace/file.txt")
                        .stderr("")
                        .build());

        ToolResult result = listFilesTool.execute(context);

        assertTrue(result.isSuccess());
    }

    @Test
    void should_listFilesWithPattern_when_patternProvided() {
        UUID workspaceId = UUID.randomUUID();

        ToolExecutionContext context = ToolExecutionContext.builder()
                .workspaceId(workspaceId)
                .taskId(UUID.randomUUID())
                .tenantId(UUID.randomUUID())
                .parameters(Map.of("recursive", true, "pattern", "*.java"))
                .build();

        when(workspaceClient.exec(eq(workspaceId), eq("bash"), eq("-c"),
                eq("find '/workspace' -name '*.java'")))
                .thenReturn(ExecResultDto.builder()
                        .exitCode(0)
                        .stdout("/workspace/src/Main.java\n/workspace/src/App.java")
                        .stderr("")
                        .build());

        ToolResult result = listFilesTool.execute(context);

        assertTrue(result.isSuccess());
        assertTrue(result.getOutput().contains("Main.java"));
        assertTrue(result.getOutput().contains("App.java"));
    }

    @Test
    void should_listFilesWithCustomPath_when_pathProvided() {
        UUID workspaceId = UUID.randomUUID();

        ToolExecutionContext context = ToolExecutionContext.builder()
                .workspaceId(workspaceId)
                .taskId(UUID.randomUUID())
                .tenantId(UUID.randomUUID())
                .parameters(Map.of("path", "/workspace/src"))
                .build();

        when(workspaceClient.exec(eq(workspaceId), eq("bash"), eq("-c"),
                eq("ls -la '/workspace/src'")))
                .thenReturn(ExecResultDto.builder()
                        .exitCode(0)
                        .stdout("total 4\n-rw-r--r-- 1 root root 200 Jan 1 00:00 Main.java")
                        .stderr("")
                        .build());

        ToolResult result = listFilesTool.execute(context);

        assertTrue(result.isSuccess());
        assertTrue(result.getOutput().contains("Main.java"));
    }

    @Test
    void should_returnError_when_directoryNotFound() {
        UUID workspaceId = UUID.randomUUID();

        ToolExecutionContext context = ToolExecutionContext.builder()
                .workspaceId(workspaceId)
                .taskId(UUID.randomUUID())
                .tenantId(UUID.randomUUID())
                .parameters(Map.of("path", "/workspace/nonexistent"))
                .build();

        when(workspaceClient.exec(eq(workspaceId), eq("bash"), eq("-c"),
                eq("ls -la '/workspace/nonexistent'")))
                .thenReturn(ExecResultDto.builder()
                        .exitCode(2)
                        .stdout("")
                        .stderr("ls: cannot access '/workspace/nonexistent': No such file or directory")
                        .build());

        ToolResult result = listFilesTool.execute(context);

        assertFalse(result.isSuccess());
        assertTrue(result.getError().contains("No such file or directory"));
    }

    @Test
    void should_truncateOutput_when_outputExceeds50KB() {
        UUID workspaceId = UUID.randomUUID();
        String largeOutput = "file.txt\n".repeat(10000); // Large listing

        ToolExecutionContext context = ToolExecutionContext.builder()
                .workspaceId(workspaceId)
                .taskId(UUID.randomUUID())
                .tenantId(UUID.randomUUID())
                .parameters(Map.of("recursive", true))
                .build();

        when(workspaceClient.exec(eq(workspaceId), eq("bash"), eq("-c"),
                eq("find '/workspace'")))
                .thenReturn(ExecResultDto.builder()
                        .exitCode(0)
                        .stdout(largeOutput)
                        .stderr("")
                        .build());

        ToolResult result = listFilesTool.execute(context);

        assertTrue(result.isSuccess());
        // Output is 90000 bytes (9*10000), well over 50KB
        if (largeOutput.length() > ListFilesTool.MAX_OUTPUT_BYTES) {
            assertTrue(result.getOutput().contains("[truncated"));
        }
    }

    @Test
    void should_returnError_when_workspaceClientThrowsException() {
        UUID workspaceId = UUID.randomUUID();

        ToolExecutionContext context = ToolExecutionContext.builder()
                .workspaceId(workspaceId)
                .taskId(UUID.randomUUID())
                .tenantId(UUID.randomUUID())
                .parameters(Map.of())
                .build();

        when(workspaceClient.exec(eq(workspaceId), eq("bash"), eq("-c"),
                eq("ls -la '/workspace'")))
                .thenThrow(new WorkspaceClient.WorkspaceClientException("Connection refused"));

        ToolResult result = listFilesTool.execute(context);

        assertFalse(result.isSuccess());
        assertTrue(result.getError().contains("Connection refused"));
    }

    @Test
    void should_useDefaultPath_when_pathIsNull() {
        UUID workspaceId = UUID.randomUUID();

        Map<String, Object> params = new HashMap<>();
        params.put("path", null);

        ToolExecutionContext context = ToolExecutionContext.builder()
                .workspaceId(workspaceId)
                .taskId(UUID.randomUUID())
                .tenantId(UUID.randomUUID())
                .parameters(params)
                .build();

        when(workspaceClient.exec(eq(workspaceId), eq("bash"), eq("-c"),
                eq("ls -la '/workspace'")))
                .thenReturn(ExecResultDto.builder()
                        .exitCode(0)
                        .stdout("total 0")
                        .stderr("")
                        .build());

        ToolResult result = listFilesTool.execute(context);

        assertTrue(result.isSuccess());
    }

    @Test
    void should_resolveBoolean_correctly() {
        assertFalse(ListFilesTool.resolveBoolean(null));
        assertTrue(ListFilesTool.resolveBoolean(true));
        assertFalse(ListFilesTool.resolveBoolean(false));
        assertTrue(ListFilesTool.resolveBoolean("true"));
        assertFalse(ListFilesTool.resolveBoolean("false"));
        assertFalse(ListFilesTool.resolveBoolean("invalid"));
    }

    @Test
    void should_returnError_when_exitCodeNonZeroWithEmptyStderr() {
        UUID workspaceId = UUID.randomUUID();

        ToolExecutionContext context = ToolExecutionContext.builder()
                .workspaceId(workspaceId)
                .taskId(UUID.randomUUID())
                .tenantId(UUID.randomUUID())
                .parameters(Map.of("path", "/restricted"))
                .build();

        when(workspaceClient.exec(eq(workspaceId), eq("bash"), eq("-c"),
                eq("ls -la '/restricted'")))
                .thenReturn(ExecResultDto.builder()
                        .exitCode(1)
                        .stdout("")
                        .stderr("")
                        .build());

        ToolResult result = listFilesTool.execute(context);

        assertFalse(result.isSuccess());
        assertTrue(result.getError().contains("exited with code 1"));
    }
}
