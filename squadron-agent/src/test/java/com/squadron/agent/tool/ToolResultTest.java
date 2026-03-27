package com.squadron.agent.tool;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ToolResultTest {

    @Test
    void should_buildSuccessResult_when_usingBuilder() {
        ToolResult result = ToolResult.builder()
                .toolName("file_read")
                .success(true)
                .output("file contents here")
                .executionTimeMs(42)
                .build();

        assertEquals("file_read", result.getToolName());
        assertTrue(result.isSuccess());
        assertEquals("file contents here", result.getOutput());
        assertNull(result.getError());
        assertEquals(42, result.getExecutionTimeMs());
    }

    @Test
    void should_buildErrorResult_when_usingBuilder() {
        ToolResult result = ToolResult.builder()
                .toolName("shell_exec")
                .success(false)
                .error("Command timed out")
                .executionTimeMs(5000)
                .build();

        assertEquals("shell_exec", result.getToolName());
        assertFalse(result.isSuccess());
        assertNull(result.getOutput());
        assertEquals("Command timed out", result.getError());
        assertEquals(5000, result.getExecutionTimeMs());
    }

    @Test
    void should_createResult_when_usingNoArgsConstructor() {
        ToolResult result = new ToolResult();

        assertNull(result.getToolName());
        assertFalse(result.isSuccess());
        assertNull(result.getOutput());
        assertNull(result.getError());
        assertEquals(0, result.getExecutionTimeMs());
    }

    @Test
    void should_createResult_when_usingAllArgsConstructor() {
        ToolResult result = new ToolResult("file_write", true, "Written 42 bytes", null, 15);

        assertEquals("file_write", result.getToolName());
        assertTrue(result.isSuccess());
        assertEquals("Written 42 bytes", result.getOutput());
        assertNull(result.getError());
        assertEquals(15, result.getExecutionTimeMs());
    }

    @Test
    void should_setAndGetFields_when_usingSetters() {
        ToolResult result = new ToolResult();
        result.setToolName("search");
        result.setSuccess(true);
        result.setOutput("Found 3 matches");
        result.setExecutionTimeMs(100);

        assertEquals("search", result.getToolName());
        assertTrue(result.isSuccess());
        assertEquals("Found 3 matches", result.getOutput());
        assertEquals(100, result.getExecutionTimeMs());
    }

    @Test
    void should_beEqual_when_sameFieldValues() {
        ToolResult r1 = ToolResult.builder()
                .toolName("file_read").success(true).output("content").executionTimeMs(10).build();
        ToolResult r2 = ToolResult.builder()
                .toolName("file_read").success(true).output("content").executionTimeMs(10).build();

        assertEquals(r1, r2);
        assertEquals(r1.hashCode(), r2.hashCode());
    }

    @Test
    void should_notBeEqual_when_differentFieldValues() {
        ToolResult r1 = ToolResult.builder().toolName("file_read").success(true).build();
        ToolResult r2 = ToolResult.builder().toolName("file_read").success(false).build();

        assertNotEquals(r1, r2);
    }

    @Test
    void should_haveToString_when_called() {
        ToolResult result = ToolResult.builder()
                .toolName("file_read")
                .success(true)
                .output("test content")
                .build();
        String toString = result.toString();

        assertNotNull(toString);
        assertTrue(toString.contains("file_read"));
    }
}
