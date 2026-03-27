package com.squadron.agent.tool;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ToolDefinitionTest {

    @Test
    void should_buildToolDefinition_when_usingBuilder() {
        List<ToolParameter> params = List.of(
                ToolParameter.builder().name("filePath").type("string").required(true).build()
        );

        ToolDefinition def = ToolDefinition.builder()
                .name("file_read")
                .description("Reads the content of a file")
                .parameters(params)
                .build();

        assertEquals("file_read", def.getName());
        assertEquals("Reads the content of a file", def.getDescription());
        assertEquals(1, def.getParameters().size());
        assertEquals("filePath", def.getParameters().get(0).getName());
    }

    @Test
    void should_createToolDefinition_when_usingNoArgsConstructor() {
        ToolDefinition def = new ToolDefinition();

        assertNull(def.getName());
        assertNull(def.getDescription());
        assertNull(def.getParameters());
    }

    @Test
    void should_createToolDefinition_when_usingAllArgsConstructor() {
        List<ToolParameter> params = List.of(
                ToolParameter.builder().name("command").type("string").required(true).build()
        );

        ToolDefinition def = new ToolDefinition("shell_exec", "Executes a shell command", params);

        assertEquals("shell_exec", def.getName());
        assertEquals("Executes a shell command", def.getDescription());
        assertEquals(params, def.getParameters());
    }

    @Test
    void should_setAndGetFields_when_usingSetters() {
        ToolDefinition def = new ToolDefinition();
        List<ToolParameter> params = List.of(
                ToolParameter.builder().name("query").type("string").build()
        );

        def.setName("search");
        def.setDescription("Searches code");
        def.setParameters(params);

        assertEquals("search", def.getName());
        assertEquals("Searches code", def.getDescription());
        assertEquals(params, def.getParameters());
    }

    @Test
    void should_beEqual_when_sameFieldValues() {
        List<ToolParameter> params = List.of(
                ToolParameter.builder().name("filePath").type("string").build()
        );

        ToolDefinition d1 = ToolDefinition.builder().name("file_read").description("Reads").parameters(params).build();
        ToolDefinition d2 = ToolDefinition.builder().name("file_read").description("Reads").parameters(params).build();

        assertEquals(d1, d2);
        assertEquals(d1.hashCode(), d2.hashCode());
    }

    @Test
    void should_notBeEqual_when_differentFieldValues() {
        ToolDefinition d1 = ToolDefinition.builder().name("file_read").build();
        ToolDefinition d2 = ToolDefinition.builder().name("shell_exec").build();

        assertNotEquals(d1, d2);
    }

    @Test
    void should_haveToString_when_called() {
        ToolDefinition def = ToolDefinition.builder()
                .name("file_read")
                .description("Reads a file")
                .build();
        String toString = def.toString();

        assertNotNull(toString);
        assertTrue(toString.contains("file_read"));
        assertTrue(toString.contains("Reads a file"));
    }

    @Test
    void should_handleMultipleParameters_when_builtWithList() {
        List<ToolParameter> params = List.of(
                ToolParameter.builder().name("filePath").type("string").required(true).build(),
                ToolParameter.builder().name("encoding").type("string").required(false).defaultValue("UTF-8").build(),
                ToolParameter.builder().name("lineCount").type("integer").required(false).build()
        );

        ToolDefinition def = ToolDefinition.builder()
                .name("file_read")
                .description("Reads a file")
                .parameters(params)
                .build();

        assertEquals(3, def.getParameters().size());
        assertEquals("filePath", def.getParameters().get(0).getName());
        assertEquals("encoding", def.getParameters().get(1).getName());
        assertEquals("lineCount", def.getParameters().get(2).getName());
    }

    @Test
    void should_handleEmptyParameters_when_noParamsProvided() {
        ToolDefinition def = ToolDefinition.builder()
                .name("workspace_info")
                .description("Returns workspace info")
                .parameters(List.of())
                .build();

        assertNotNull(def.getParameters());
        assertTrue(def.getParameters().isEmpty());
    }
}
