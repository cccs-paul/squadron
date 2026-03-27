package com.squadron.agent.tool;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ToolParameterTest {

    @Test
    void should_buildToolParameter_when_usingBuilder() {
        ToolParameter param = ToolParameter.builder()
                .name("filePath")
                .type("string")
                .description("Path to the file")
                .required(true)
                .defaultValue(null)
                .build();

        assertEquals("filePath", param.getName());
        assertEquals("string", param.getType());
        assertEquals("Path to the file", param.getDescription());
        assertTrue(param.isRequired());
        assertNull(param.getDefaultValue());
    }

    @Test
    void should_createToolParameter_when_usingNoArgsConstructor() {
        ToolParameter param = new ToolParameter();

        assertNull(param.getName());
        assertNull(param.getType());
        assertNull(param.getDescription());
        assertFalse(param.isRequired());
        assertNull(param.getDefaultValue());
    }

    @Test
    void should_createToolParameter_when_usingAllArgsConstructor() {
        ToolParameter param = new ToolParameter("command", "string", "Shell command to execute", true, "/bin/bash");

        assertEquals("command", param.getName());
        assertEquals("string", param.getType());
        assertEquals("Shell command to execute", param.getDescription());
        assertTrue(param.isRequired());
        assertEquals("/bin/bash", param.getDefaultValue());
    }

    @Test
    void should_setAndGetFields_when_usingSetters() {
        ToolParameter param = new ToolParameter();
        param.setName("timeout");
        param.setType("integer");
        param.setDescription("Timeout in seconds");
        param.setRequired(false);
        param.setDefaultValue("30");

        assertEquals("timeout", param.getName());
        assertEquals("integer", param.getType());
        assertEquals("Timeout in seconds", param.getDescription());
        assertFalse(param.isRequired());
        assertEquals("30", param.getDefaultValue());
    }

    @Test
    void should_beEqual_when_sameFieldValues() {
        ToolParameter p1 = ToolParameter.builder()
                .name("filePath")
                .type("string")
                .description("Path")
                .required(true)
                .build();
        ToolParameter p2 = ToolParameter.builder()
                .name("filePath")
                .type("string")
                .description("Path")
                .required(true)
                .build();

        assertEquals(p1, p2);
        assertEquals(p1.hashCode(), p2.hashCode());
    }

    @Test
    void should_notBeEqual_when_differentFieldValues() {
        ToolParameter p1 = ToolParameter.builder().name("filePath").type("string").build();
        ToolParameter p2 = ToolParameter.builder().name("command").type("string").build();

        assertNotEquals(p1, p2);
    }

    @Test
    void should_haveToString_when_called() {
        ToolParameter param = ToolParameter.builder()
                .name("filePath")
                .type("string")
                .required(true)
                .build();
        String toString = param.toString();

        assertNotNull(toString);
        assertTrue(toString.contains("filePath"));
        assertTrue(toString.contains("string"));
    }

    @Test
    void should_handleAllTypes_when_differentTypesUsed() {
        List<String> types = List.of("string", "integer", "boolean", "array");
        for (String type : types) {
            ToolParameter param = ToolParameter.builder().name("param").type(type).build();
            assertEquals(type, param.getType());
        }
    }

    @Test
    void should_handleNullDefaultValue_when_notSet() {
        ToolParameter param = ToolParameter.builder()
                .name("test")
                .type("string")
                .required(true)
                .build();

        assertNull(param.getDefaultValue());
    }
}
