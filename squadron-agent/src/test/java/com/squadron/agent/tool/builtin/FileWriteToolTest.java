package com.squadron.agent.tool.builtin;

import com.squadron.agent.tool.ToolDefinition;
import com.squadron.agent.tool.ToolExecutionContext;
import com.squadron.agent.tool.ToolResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class FileWriteToolTest {

    @Mock
    private WorkspaceClient workspaceClient;

    private FileWriteTool fileWriteTool;

    @BeforeEach
    void setUp() {
        fileWriteTool = new FileWriteTool(workspaceClient);
    }

    @Test
    void should_returnToolName() {
        assertEquals("file_write", fileWriteTool.getName());
    }

    @Test
    void should_returnToolDescription() {
        assertNotNull(fileWriteTool.getDescription());
        assertTrue(fileWriteTool.getDescription().contains("Write content"));
    }

    @Test
    void should_returnToolDefinition_with_parameters() {
        ToolDefinition definition = fileWriteTool.getDefinition();
        assertNotNull(definition);
        assertEquals("file_write", definition.getName());
        assertEquals(2, definition.getParameters().size());
        assertEquals("path", definition.getParameters().get(0).getName());
        assertEquals("content", definition.getParameters().get(1).getName());
        assertTrue(definition.getParameters().get(0).isRequired());
        assertTrue(definition.getParameters().get(1).isRequired());
    }

    @Test
    void should_writeFile_when_validInput() {
        UUID workspaceId = UUID.randomUUID();
        String content = "public class Main {}";
        byte[] contentBytes = content.getBytes(StandardCharsets.UTF_8);

        ToolExecutionContext context = ToolExecutionContext.builder()
                .workspaceId(workspaceId)
                .taskId(UUID.randomUUID())
                .tenantId(UUID.randomUUID())
                .parameters(Map.of("path", "/workspace/src/Main.java", "content", content))
                .build();

        when(workspaceClient.exec(eq(workspaceId), eq("bash"), eq("-c"), eq("mkdir -p '/workspace/src'")))
                .thenReturn(ExecResultDto.builder().exitCode(0).stdout("").stderr("").build());

        ToolResult result = fileWriteTool.execute(context);

        assertTrue(result.isSuccess());
        assertEquals("file_write", result.getToolName());
        assertTrue(result.getOutput().contains(String.valueOf(contentBytes.length)));
        assertTrue(result.getOutput().contains("/workspace/src/Main.java"));
        verify(workspaceClient).writeFile(eq(workspaceId), eq("/workspace/src/Main.java"), eq(contentBytes));
    }

    @Test
    void should_returnError_when_pathIsMissing() {
        ToolExecutionContext context = ToolExecutionContext.builder()
                .workspaceId(UUID.randomUUID())
                .taskId(UUID.randomUUID())
                .tenantId(UUID.randomUUID())
                .parameters(Map.of("content", "some content"))
                .build();

        ToolResult result = fileWriteTool.execute(context);

        assertFalse(result.isSuccess());
        assertTrue(result.getError().contains("'path' is missing"));
    }

    @Test
    void should_returnError_when_contentIsMissing() {
        Map<String, Object> params = new HashMap<>();
        params.put("path", "/workspace/file.txt");
        params.put("content", null);

        ToolExecutionContext context = ToolExecutionContext.builder()
                .workspaceId(UUID.randomUUID())
                .taskId(UUID.randomUUID())
                .tenantId(UUID.randomUUID())
                .parameters(params)
                .build();

        ToolResult result = fileWriteTool.execute(context);

        assertFalse(result.isSuccess());
        assertTrue(result.getError().contains("'content' is missing"));
    }

    @Test
    void should_returnError_when_mkdirFails() {
        UUID workspaceId = UUID.randomUUID();

        ToolExecutionContext context = ToolExecutionContext.builder()
                .workspaceId(workspaceId)
                .taskId(UUID.randomUUID())
                .tenantId(UUID.randomUUID())
                .parameters(Map.of("path", "/workspace/restricted/file.txt", "content", "data"))
                .build();

        when(workspaceClient.exec(eq(workspaceId), eq("bash"), eq("-c"), eq("mkdir -p '/workspace/restricted'")))
                .thenReturn(ExecResultDto.builder()
                        .exitCode(1)
                        .stdout("")
                        .stderr("Permission denied")
                        .build());

        ToolResult result = fileWriteTool.execute(context);

        assertFalse(result.isSuccess());
        assertTrue(result.getError().contains("Permission denied"));
        verify(workspaceClient, never()).writeFile(any(), any(), any());
    }

    @Test
    void should_returnError_when_writeFileFails() {
        UUID workspaceId = UUID.randomUUID();

        ToolExecutionContext context = ToolExecutionContext.builder()
                .workspaceId(workspaceId)
                .taskId(UUID.randomUUID())
                .tenantId(UUID.randomUUID())
                .parameters(Map.of("path", "/workspace/file.txt", "content", "data"))
                .build();

        when(workspaceClient.exec(eq(workspaceId), eq("bash"), eq("-c"), eq("mkdir -p '/workspace'")))
                .thenReturn(ExecResultDto.builder().exitCode(0).stdout("").stderr("").build());
        doThrow(new WorkspaceClient.WorkspaceClientException("Write failed"))
                .when(workspaceClient).writeFile(eq(workspaceId), eq("/workspace/file.txt"), any());

        ToolResult result = fileWriteTool.execute(context);

        assertFalse(result.isSuccess());
        assertTrue(result.getError().contains("Write failed"));
    }

    @Test
    void should_returnError_when_pathIsBlank() {
        ToolExecutionContext context = ToolExecutionContext.builder()
                .workspaceId(UUID.randomUUID())
                .taskId(UUID.randomUUID())
                .tenantId(UUID.randomUUID())
                .parameters(Map.of("path", "  ", "content", "data"))
                .build();

        ToolResult result = fileWriteTool.execute(context);

        assertFalse(result.isSuccess());
        assertTrue(result.getError().contains("'path' is missing"));
    }

    @Test
    void should_writeEmptyContent_when_contentIsEmptyString() {
        UUID workspaceId = UUID.randomUUID();

        ToolExecutionContext context = ToolExecutionContext.builder()
                .workspaceId(workspaceId)
                .taskId(UUID.randomUUID())
                .tenantId(UUID.randomUUID())
                .parameters(Map.of("path", "/workspace/empty.txt", "content", ""))
                .build();

        when(workspaceClient.exec(eq(workspaceId), eq("bash"), eq("-c"), eq("mkdir -p '/workspace'")))
                .thenReturn(ExecResultDto.builder().exitCode(0).stdout("").stderr("").build());

        ToolResult result = fileWriteTool.execute(context);

        assertTrue(result.isSuccess());
        assertTrue(result.getOutput().contains("0 bytes"));
        verify(workspaceClient).writeFile(eq(workspaceId), eq("/workspace/empty.txt"),
                eq("".getBytes(StandardCharsets.UTF_8)));
    }
}
