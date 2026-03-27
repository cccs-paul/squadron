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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FileReadToolTest {

    @Mock
    private WorkspaceClient workspaceClient;

    private FileReadTool fileReadTool;

    @BeforeEach
    void setUp() {
        fileReadTool = new FileReadTool(workspaceClient);
    }

    @Test
    void should_returnToolName() {
        assertEquals("file_read", fileReadTool.getName());
    }

    @Test
    void should_returnToolDescription() {
        assertEquals("Read the contents of a file in the workspace", fileReadTool.getDescription());
    }

    @Test
    void should_returnToolDefinition_with_parameters() {
        ToolDefinition definition = fileReadTool.getDefinition();
        assertNotNull(definition);
        assertEquals("file_read", definition.getName());
        assertEquals(1, definition.getParameters().size());
        assertEquals("path", definition.getParameters().get(0).getName());
        assertTrue(definition.getParameters().get(0).isRequired());
    }

    @Test
    void should_readFile_when_fileExists() {
        UUID workspaceId = UUID.randomUUID();
        ToolExecutionContext context = ToolExecutionContext.builder()
                .workspaceId(workspaceId)
                .taskId(UUID.randomUUID())
                .tenantId(UUID.randomUUID())
                .parameters(Map.of("path", "/workspace/src/Main.java"))
                .build();

        when(workspaceClient.exec(eq(workspaceId), eq("bash"), eq("-c"), eq("cat '/workspace/src/Main.java'")))
                .thenReturn(ExecResultDto.builder()
                        .exitCode(0)
                        .stdout("public class Main {}")
                        .stderr("")
                        .build());

        ToolResult result = fileReadTool.execute(context);

        assertTrue(result.isSuccess());
        assertEquals("file_read", result.getToolName());
        assertEquals("public class Main {}", result.getOutput());
        assertNull(result.getError());
    }

    @Test
    void should_returnError_when_fileNotFound() {
        UUID workspaceId = UUID.randomUUID();
        ToolExecutionContext context = ToolExecutionContext.builder()
                .workspaceId(workspaceId)
                .taskId(UUID.randomUUID())
                .tenantId(UUID.randomUUID())
                .parameters(Map.of("path", "/workspace/missing.txt"))
                .build();

        when(workspaceClient.exec(eq(workspaceId), eq("bash"), eq("-c"), eq("cat '/workspace/missing.txt'")))
                .thenReturn(ExecResultDto.builder()
                        .exitCode(1)
                        .stdout("")
                        .stderr("cat: /workspace/missing.txt: No such file or directory")
                        .build());

        ToolResult result = fileReadTool.execute(context);

        assertFalse(result.isSuccess());
        assertEquals("file_read", result.getToolName());
        assertTrue(result.getError().contains("No such file or directory"));
    }

    @Test
    void should_returnError_when_pathIsMissing() {
        ToolExecutionContext context = ToolExecutionContext.builder()
                .workspaceId(UUID.randomUUID())
                .taskId(UUID.randomUUID())
                .tenantId(UUID.randomUUID())
                .parameters(Map.of())
                .build();

        ToolResult result = fileReadTool.execute(context);

        assertFalse(result.isSuccess());
        assertTrue(result.getError().contains("'path' is missing"));
    }

    @Test
    void should_returnError_when_pathIsBlank() {
        ToolExecutionContext context = ToolExecutionContext.builder()
                .workspaceId(UUID.randomUUID())
                .taskId(UUID.randomUUID())
                .tenantId(UUID.randomUUID())
                .parameters(Map.of("path", "   "))
                .build();

        ToolResult result = fileReadTool.execute(context);

        assertFalse(result.isSuccess());
        assertTrue(result.getError().contains("'path' is missing"));
    }

    @Test
    void should_returnError_when_pathIsNull() {
        Map<String, Object> params = new HashMap<>();
        params.put("path", null);

        ToolExecutionContext context = ToolExecutionContext.builder()
                .workspaceId(UUID.randomUUID())
                .taskId(UUID.randomUUID())
                .tenantId(UUID.randomUUID())
                .parameters(params)
                .build();

        ToolResult result = fileReadTool.execute(context);

        assertFalse(result.isSuccess());
        assertTrue(result.getError().contains("'path' is missing"));
    }

    @Test
    void should_truncateOutput_when_fileExceeds100KB() {
        UUID workspaceId = UUID.randomUUID();
        String largeContent = "x".repeat(150 * 1024); // 150KB

        ToolExecutionContext context = ToolExecutionContext.builder()
                .workspaceId(workspaceId)
                .taskId(UUID.randomUUID())
                .tenantId(UUID.randomUUID())
                .parameters(Map.of("path", "/workspace/large.txt"))
                .build();

        when(workspaceClient.exec(eq(workspaceId), eq("bash"), eq("-c"), eq("cat '/workspace/large.txt'")))
                .thenReturn(ExecResultDto.builder()
                        .exitCode(0)
                        .stdout(largeContent)
                        .stderr("")
                        .build());

        ToolResult result = fileReadTool.execute(context);

        assertTrue(result.isSuccess());
        assertTrue(result.getOutput().contains("[truncated"));
        assertTrue(result.getOutput().length() < largeContent.length());
    }

    @Test
    void should_returnError_when_workspaceClientThrowsException() {
        UUID workspaceId = UUID.randomUUID();
        ToolExecutionContext context = ToolExecutionContext.builder()
                .workspaceId(workspaceId)
                .taskId(UUID.randomUUID())
                .tenantId(UUID.randomUUID())
                .parameters(Map.of("path", "/workspace/file.txt"))
                .build();

        when(workspaceClient.exec(eq(workspaceId), eq("bash"), eq("-c"), eq("cat '/workspace/file.txt'")))
                .thenThrow(new WorkspaceClient.WorkspaceClientException("Connection refused"));

        ToolResult result = fileReadTool.execute(context);

        assertFalse(result.isSuccess());
        assertTrue(result.getError().contains("Connection refused"));
    }

    @Test
    void should_returnError_when_exitCodeNonZeroWithEmptyStderr() {
        UUID workspaceId = UUID.randomUUID();
        ToolExecutionContext context = ToolExecutionContext.builder()
                .workspaceId(workspaceId)
                .taskId(UUID.randomUUID())
                .tenantId(UUID.randomUUID())
                .parameters(Map.of("path", "/workspace/denied.txt"))
                .build();

        when(workspaceClient.exec(eq(workspaceId), eq("bash"), eq("-c"), eq("cat '/workspace/denied.txt'")))
                .thenReturn(ExecResultDto.builder()
                        .exitCode(2)
                        .stdout("")
                        .stderr("")
                        .build());

        ToolResult result = fileReadTool.execute(context);

        assertFalse(result.isSuccess());
        assertTrue(result.getError().contains("exited with code 2"));
    }

    @Test
    void should_handleNullStdout_when_execReturnsNull() {
        UUID workspaceId = UUID.randomUUID();
        ToolExecutionContext context = ToolExecutionContext.builder()
                .workspaceId(workspaceId)
                .taskId(UUID.randomUUID())
                .tenantId(UUID.randomUUID())
                .parameters(Map.of("path", "/workspace/empty.txt"))
                .build();

        when(workspaceClient.exec(eq(workspaceId), eq("bash"), eq("-c"), eq("cat '/workspace/empty.txt'")))
                .thenReturn(ExecResultDto.builder()
                        .exitCode(0)
                        .stdout(null)
                        .stderr("")
                        .build());

        ToolResult result = fileReadTool.execute(context);

        assertTrue(result.isSuccess());
        assertEquals("", result.getOutput());
    }

    @Test
    void should_shellEscape_singleQuotes() {
        assertEquals("'hello'", FileReadTool.shellEscape("hello"));
        assertEquals("'it'\\''s'", FileReadTool.shellEscape("it's"));
        assertEquals("'/workspace/my file.txt'", FileReadTool.shellEscape("/workspace/my file.txt"));
    }
}
