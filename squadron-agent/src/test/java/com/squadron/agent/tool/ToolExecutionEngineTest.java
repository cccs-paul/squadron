package com.squadron.agent.tool;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ToolExecutionEngineTest {

    @Mock
    private ToolRegistry toolRegistry;

    @Mock
    private AgentTool mockTool;

    private ToolExecutionEngine engine;

    private ToolExecutionContext baseContext;

    @BeforeEach
    void setUp() {
        engine = new ToolExecutionEngine(toolRegistry);
        baseContext = ToolExecutionContext.builder()
                .workspaceId(UUID.randomUUID())
                .taskId(UUID.randomUUID())
                .tenantId(UUID.randomUUID())
                .parameters(Map.of("filePath", "/src/Main.java"))
                .accessToken("test-token")
                .build();
    }

    @Test
    void should_executeTool_successfully() {
        ToolResult expectedResult = ToolResult.builder()
                .toolName("file_read")
                .success(true)
                .output("file contents")
                .build();

        when(toolRegistry.getTool("file_read")).thenReturn(mockTool);
        when(mockTool.execute(any(ToolExecutionContext.class))).thenReturn(expectedResult);

        ToolResult result = engine.executeTool("file_read", baseContext);

        assertTrue(result.isSuccess());
        assertEquals("file contents", result.getOutput());
        assertEquals("file_read", result.getToolName());
        verify(toolRegistry).getTool("file_read");
        verify(mockTool).execute(baseContext);
    }

    @Test
    void should_returnError_when_toolThrowsException() {
        when(toolRegistry.getTool("file_read")).thenReturn(mockTool);
        when(mockTool.execute(any(ToolExecutionContext.class)))
                .thenThrow(new RuntimeException("File not found: /missing.txt"));

        ToolResult result = engine.executeTool("file_read", baseContext);

        assertFalse(result.isSuccess());
        assertEquals("file_read", result.getToolName());
        assertEquals("File not found: /missing.txt", result.getError());
        assertNull(result.getOutput());
    }

    @Test
    void should_measureExecutionTime() {
        ToolResult expectedResult = ToolResult.builder()
                .toolName("shell_exec")
                .success(true)
                .output("command output")
                .build();

        when(toolRegistry.getTool("shell_exec")).thenReturn(mockTool);
        when(mockTool.execute(any(ToolExecutionContext.class))).thenReturn(expectedResult);

        ToolResult result = engine.executeTool("shell_exec", baseContext);

        assertTrue(result.getExecutionTimeMs() >= 0);
    }

    @Test
    void should_executeMultipleToolCalls() {
        AgentTool fileReadTool = mock(AgentTool.class);
        AgentTool shellExecTool = mock(AgentTool.class);

        when(toolRegistry.getTool("file_read")).thenReturn(fileReadTool);
        when(toolRegistry.getTool("shell_exec")).thenReturn(shellExecTool);

        when(fileReadTool.execute(any(ToolExecutionContext.class))).thenReturn(
                ToolResult.builder().toolName("file_read").success(true).output("file content").build()
        );
        when(shellExecTool.execute(any(ToolExecutionContext.class))).thenReturn(
                ToolResult.builder().toolName("shell_exec").success(true).output("command output").build()
        );

        List<ToolCall> calls = List.of(
                ToolCall.builder().id("call_1").toolName("file_read")
                        .arguments(Map.of("filePath", "/test.txt")).build(),
                ToolCall.builder().id("call_2").toolName("shell_exec")
                        .arguments(Map.of("command", "ls")).build()
        );

        List<ToolResult> results = engine.executeTools(calls, baseContext);

        assertEquals(2, results.size());
        assertTrue(results.get(0).isSuccess());
        assertEquals("file content", results.get(0).getOutput());
        assertTrue(results.get(1).isSuccess());
        assertEquals("command output", results.get(1).getOutput());
    }

    @Test
    void should_handleToolNotFound() {
        when(toolRegistry.getTool("nonexistent"))
                .thenThrow(new IllegalArgumentException("No tool registered with name: nonexistent"));

        ToolResult result = engine.executeTool("nonexistent", baseContext);

        assertFalse(result.isSuccess());
        assertEquals("nonexistent", result.getToolName());
        assertTrue(result.getError().contains("nonexistent"));
    }

    @Test
    void should_passCorrectParametersToTool() {
        Map<String, Object> callArgs = Map.of("filePath", "/specific/file.txt", "encoding", "UTF-8");
        ToolCall call = ToolCall.builder()
                .id("call_params")
                .toolName("file_read")
                .arguments(callArgs)
                .build();

        when(toolRegistry.getTool("file_read")).thenReturn(mockTool);
        when(mockTool.execute(any(ToolExecutionContext.class))).thenAnswer(invocation -> {
            ToolExecutionContext ctx = invocation.getArgument(0);
            // Verify the context has the call's arguments, not the base context's parameters
            assertEquals(callArgs, ctx.getParameters());
            assertEquals(baseContext.getWorkspaceId(), ctx.getWorkspaceId());
            assertEquals(baseContext.getTaskId(), ctx.getTaskId());
            assertEquals(baseContext.getTenantId(), ctx.getTenantId());
            assertEquals(baseContext.getAccessToken(), ctx.getAccessToken());
            return ToolResult.builder().toolName("file_read").success(true).output("ok").build();
        });

        List<ToolResult> results = engine.executeTools(List.of(call), baseContext);

        assertEquals(1, results.size());
        assertTrue(results.get(0).isSuccess());
        verify(mockTool).execute(any(ToolExecutionContext.class));
    }

    @Test
    void should_returnErrorForFailedCallsInBatch_when_oneToolFails() {
        AgentTool successTool = mock(AgentTool.class);

        when(toolRegistry.getTool("good_tool")).thenReturn(successTool);
        when(toolRegistry.getTool("bad_tool"))
                .thenThrow(new IllegalArgumentException("No tool registered with name: bad_tool"));

        when(successTool.execute(any(ToolExecutionContext.class))).thenReturn(
                ToolResult.builder().toolName("good_tool").success(true).output("ok").build()
        );

        List<ToolCall> calls = List.of(
                ToolCall.builder().id("call_1").toolName("good_tool").arguments(Map.of()).build(),
                ToolCall.builder().id("call_2").toolName("bad_tool").arguments(Map.of()).build()
        );

        List<ToolResult> results = engine.executeTools(calls, baseContext);

        assertEquals(2, results.size());
        assertTrue(results.get(0).isSuccess());
        assertFalse(results.get(1).isSuccess());
        assertTrue(results.get(1).getError().contains("bad_tool"));
    }

    @Test
    void should_measureExecutionTime_when_toolThrowsException() {
        when(toolRegistry.getTool("slow_fail")).thenReturn(mockTool);
        when(mockTool.execute(any(ToolExecutionContext.class)))
                .thenThrow(new RuntimeException("Timeout"));

        ToolResult result = engine.executeTool("slow_fail", baseContext);

        assertFalse(result.isSuccess());
        assertTrue(result.getExecutionTimeMs() >= 0);
        assertEquals("slow_fail", result.getToolName());
    }
}
