package com.squadron.agent.tool;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class AgentToolTest {

    @Test
    void should_implementInterface_when_allMethodsDefined() {
        AgentTool tool = createTestTool("file_read", "Reads a file from the workspace");

        assertEquals("file_read", tool.getName());
        assertEquals("Reads a file from the workspace", tool.getDescription());
        assertNotNull(tool.getDefinition());
        assertEquals("file_read", tool.getDefinition().getName());
    }

    @Test
    void should_returnToolDefinition_when_getDefinitionCalled() {
        AgentTool tool = createTestTool("shell_exec", "Executes a shell command");

        ToolDefinition definition = tool.getDefinition();

        assertNotNull(definition);
        assertEquals("shell_exec", definition.getName());
        assertEquals("Executes a shell command", definition.getDescription());
        assertNotNull(definition.getParameters());
        assertEquals(1, definition.getParameters().size());
        assertEquals("command", definition.getParameters().get(0).getName());
    }

    @Test
    void should_executeSuccessfully_when_validContextProvided() {
        AgentTool tool = createTestTool("file_read", "Reads a file");

        ToolExecutionContext context = ToolExecutionContext.builder()
                .workspaceId(UUID.randomUUID())
                .taskId(UUID.randomUUID())
                .tenantId(UUID.randomUUID())
                .parameters(Map.of("filePath", "/src/Main.java"))
                .accessToken("token123")
                .build();

        ToolResult result = tool.execute(context);

        assertNotNull(result);
        assertTrue(result.isSuccess());
        assertEquals("file_read", result.getToolName());
        assertEquals("Executed successfully", result.getOutput());
    }

    @Test
    void should_returnError_when_executionFails() {
        AgentTool tool = new AgentTool() {
            @Override
            public String getName() { return "failing_tool"; }

            @Override
            public String getDescription() { return "A tool that always fails"; }

            @Override
            public ToolDefinition getDefinition() {
                return ToolDefinition.builder()
                        .name("failing_tool")
                        .description("A tool that always fails")
                        .parameters(List.of())
                        .build();
            }

            @Override
            public ToolResult execute(ToolExecutionContext context) {
                return ToolResult.builder()
                        .toolName("failing_tool")
                        .success(false)
                        .error("Simulated failure")
                        .executionTimeMs(100)
                        .build();
            }
        };

        ToolExecutionContext context = ToolExecutionContext.builder()
                .workspaceId(UUID.randomUUID())
                .taskId(UUID.randomUUID())
                .tenantId(UUID.randomUUID())
                .parameters(Map.of())
                .build();

        ToolResult result = tool.execute(context);

        assertNotNull(result);
        assertFalse(result.isSuccess());
        assertEquals("Simulated failure", result.getError());
    }

    @Test
    void should_haveDistinctNames_when_differentToolsCreated() {
        AgentTool tool1 = createTestTool("file_read", "Reads files");
        AgentTool tool2 = createTestTool("file_write", "Writes files");

        assertNotEquals(tool1.getName(), tool2.getName());
        assertNotEquals(tool1.getDescription(), tool2.getDescription());
    }

    @Test
    void should_passContextParameters_when_executing() {
        UUID workspaceId = UUID.randomUUID();
        UUID taskId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        Map<String, Object> params = Map.of("command", "ls -la", "timeout", 30);

        AgentTool tool = new AgentTool() {
            @Override
            public String getName() { return "param_checker"; }

            @Override
            public String getDescription() { return "Checks parameters"; }

            @Override
            public ToolDefinition getDefinition() {
                return ToolDefinition.builder()
                        .name("param_checker")
                        .description("Checks parameters")
                        .parameters(List.of())
                        .build();
            }

            @Override
            public ToolResult execute(ToolExecutionContext context) {
                assertEquals(workspaceId, context.getWorkspaceId());
                assertEquals(taskId, context.getTaskId());
                assertEquals(tenantId, context.getTenantId());
                assertEquals("ls -la", context.getParameters().get("command"));
                assertEquals(30, context.getParameters().get("timeout"));
                return ToolResult.builder()
                        .toolName("param_checker")
                        .success(true)
                        .output("Parameters verified")
                        .executionTimeMs(1)
                        .build();
            }
        };

        ToolExecutionContext context = ToolExecutionContext.builder()
                .workspaceId(workspaceId)
                .taskId(taskId)
                .tenantId(tenantId)
                .parameters(params)
                .build();

        ToolResult result = tool.execute(context);
        assertTrue(result.isSuccess());
    }

    @Test
    void should_beAssignableToInterface_when_implementingAgentTool() {
        AgentTool tool = createTestTool("test_tool", "A test tool");

        assertInstanceOf(AgentTool.class, tool);
    }

    @Test
    void should_declareAllMethods_when_interfaceUsed() {
        // Verify the interface has exactly 4 methods
        assertEquals(4, AgentTool.class.getMethods().length);
    }

    private AgentTool createTestTool(String name, String description) {
        return new AgentTool() {
            @Override
            public String getName() { return name; }

            @Override
            public String getDescription() { return description; }

            @Override
            public ToolDefinition getDefinition() {
                return ToolDefinition.builder()
                        .name(name)
                        .description(description)
                        .parameters(List.of(
                                ToolParameter.builder()
                                        .name("command")
                                        .type("string")
                                        .description("The command to run")
                                        .required(true)
                                        .build()
                        ))
                        .build();
            }

            @Override
            public ToolResult execute(ToolExecutionContext context) {
                return ToolResult.builder()
                        .toolName(name)
                        .success(true)
                        .output("Executed successfully")
                        .executionTimeMs(10)
                        .build();
            }
        };
    }
}
