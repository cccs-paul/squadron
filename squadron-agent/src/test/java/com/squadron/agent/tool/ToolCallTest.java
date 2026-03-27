package com.squadron.agent.tool;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class ToolCallTest {

    @Test
    void should_buildToolCall_when_usingBuilder() {
        Map<String, Object> args = Map.of("filePath", "/src/Main.java", "encoding", "UTF-8");

        ToolCall call = ToolCall.builder()
                .id("call_abc123")
                .toolName("file_read")
                .arguments(args)
                .build();

        assertEquals("call_abc123", call.getId());
        assertEquals("file_read", call.getToolName());
        assertEquals(args, call.getArguments());
    }

    @Test
    void should_createToolCall_when_usingNoArgsConstructor() {
        ToolCall call = new ToolCall();

        assertNull(call.getId());
        assertNull(call.getToolName());
        assertNull(call.getArguments());
    }

    @Test
    void should_createToolCall_when_usingAllArgsConstructor() {
        Map<String, Object> args = Map.of("command", "ls -la");

        ToolCall call = new ToolCall("call_xyz789", "shell_exec", args);

        assertEquals("call_xyz789", call.getId());
        assertEquals("shell_exec", call.getToolName());
        assertEquals(args, call.getArguments());
    }

    @Test
    void should_setAndGetFields_when_usingSetters() {
        ToolCall call = new ToolCall();
        Map<String, Object> args = Map.of("query", "findBugs");

        call.setId("call_001");
        call.setToolName("search");
        call.setArguments(args);

        assertEquals("call_001", call.getId());
        assertEquals("search", call.getToolName());
        assertEquals(args, call.getArguments());
    }

    @Test
    void should_beEqual_when_sameFieldValues() {
        Map<String, Object> args = Map.of("filePath", "/test.txt");

        ToolCall c1 = ToolCall.builder().id("call_1").toolName("file_read").arguments(args).build();
        ToolCall c2 = ToolCall.builder().id("call_1").toolName("file_read").arguments(args).build();

        assertEquals(c1, c2);
        assertEquals(c1.hashCode(), c2.hashCode());
    }

    @Test
    void should_notBeEqual_when_differentFieldValues() {
        ToolCall c1 = ToolCall.builder().id("call_1").toolName("file_read").build();
        ToolCall c2 = ToolCall.builder().id("call_2").toolName("file_read").build();

        assertNotEquals(c1, c2);
    }

    @Test
    void should_haveToString_when_called() {
        ToolCall call = ToolCall.builder()
                .id("call_1")
                .toolName("shell_exec")
                .arguments(Map.of("command", "pwd"))
                .build();
        String toString = call.toString();

        assertNotNull(toString);
        assertTrue(toString.contains("call_1"));
        assertTrue(toString.contains("shell_exec"));
    }

    @Test
    void should_handleEmptyArguments_when_noArgsProvided() {
        ToolCall call = ToolCall.builder()
                .id("call_empty")
                .toolName("workspace_info")
                .arguments(Map.of())
                .build();

        assertNotNull(call.getArguments());
        assertTrue(call.getArguments().isEmpty());
    }
}
