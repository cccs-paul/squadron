package com.squadron.agent.tool;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class ToolRegistryTest {

    private AgentTool fileReadTool;
    private AgentTool shellExecTool;

    @BeforeEach
    void setUp() {
        fileReadTool = createStubTool("file_read", "Reads a file",
                List.of(ToolParameter.builder().name("filePath").type("string").required(true).build()));
        shellExecTool = createStubTool("shell_exec", "Executes a shell command",
                List.of(ToolParameter.builder().name("command").type("string").required(true).build()));
    }

    @Test
    void should_registerToolsOnConstruction() {
        ToolRegistry registry = new ToolRegistry(List.of(fileReadTool, shellExecTool));

        assertTrue(registry.hasTool("file_read"));
        assertTrue(registry.hasTool("shell_exec"));
    }

    @Test
    void should_getToolByName() {
        ToolRegistry registry = new ToolRegistry(List.of(fileReadTool, shellExecTool));

        AgentTool result = registry.getTool("file_read");

        assertEquals(fileReadTool, result);
        assertEquals("file_read", result.getName());
    }

    @Test
    void should_throwException_when_toolNotFound() {
        ToolRegistry registry = new ToolRegistry(List.of(fileReadTool));

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> registry.getTool("nonexistent_tool")
        );

        assertTrue(exception.getMessage().contains("nonexistent_tool"));
    }

    @Test
    void should_getAllToolDefinitions() {
        ToolRegistry registry = new ToolRegistry(List.of(fileReadTool, shellExecTool));

        List<ToolDefinition> definitions = registry.getAllToolDefinitions();

        assertEquals(2, definitions.size());
        List<String> defNames = definitions.stream().map(ToolDefinition::getName).sorted().toList();
        assertEquals(List.of("file_read", "shell_exec"), defNames);
    }

    @Test
    void should_getToolNames() {
        ToolRegistry registry = new ToolRegistry(List.of(fileReadTool, shellExecTool));

        List<String> names = registry.getToolNames();

        assertEquals(2, names.size());
        assertTrue(names.contains("file_read"));
        assertTrue(names.contains("shell_exec"));
    }

    @Test
    void should_hasTool_returnsTrue_when_toolExists() {
        ToolRegistry registry = new ToolRegistry(List.of(fileReadTool));

        assertTrue(registry.hasTool("file_read"));
    }

    @Test
    void should_hasTool_returnsFalse_when_toolMissing() {
        ToolRegistry registry = new ToolRegistry(List.of(fileReadTool));

        assertFalse(registry.hasTool("nonexistent_tool"));
    }

    @Test
    void should_handleEmptyToolList_when_noToolsRegistered() {
        ToolRegistry registry = new ToolRegistry(Collections.emptyList());

        assertTrue(registry.getAllToolDefinitions().isEmpty());
        assertTrue(registry.getToolNames().isEmpty());
        assertFalse(registry.hasTool("anything"));
    }

    @Test
    void should_returnCorrectDefinition_when_getToolCalled() {
        ToolRegistry registry = new ToolRegistry(List.of(fileReadTool));

        AgentTool tool = registry.getTool("file_read");
        ToolDefinition def = tool.getDefinition();

        assertEquals("file_read", def.getName());
        assertEquals("Reads a file", def.getDescription());
        assertEquals(1, def.getParameters().size());
        assertEquals("filePath", def.getParameters().get(0).getName());
    }

    /**
     * Creates a stub AgentTool implementation for testing.
     */
    private AgentTool createStubTool(String name, String description, List<ToolParameter> params) {
        return new AgentTool() {
            @Override
            public String getName() {
                return name;
            }

            @Override
            public String getDescription() {
                return description;
            }

            @Override
            public ToolDefinition getDefinition() {
                return ToolDefinition.builder()
                        .name(name)
                        .description(description)
                        .parameters(params)
                        .build();
            }

            @Override
            public ToolResult execute(ToolExecutionContext context) {
                return ToolResult.builder()
                        .toolName(name)
                        .success(true)
                        .output("Executed " + name)
                        .build();
            }
        };
    }
}
