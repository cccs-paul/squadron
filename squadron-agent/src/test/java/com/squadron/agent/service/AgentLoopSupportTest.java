package com.squadron.agent.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.squadron.agent.dto.AgentConfigDto;
import com.squadron.agent.entity.ConversationMessage;
import com.squadron.agent.provider.AgentProvider;
import com.squadron.agent.provider.AgentProviderRegistry;
import com.squadron.agent.tool.ToolCall;
import com.squadron.agent.tool.ToolDefinition;
import com.squadron.agent.tool.ToolExecutionContext;
import com.squadron.agent.tool.ToolExecutionEngine;
import com.squadron.agent.tool.ToolParameter;
import com.squadron.agent.tool.ToolResult;
import com.squadron.common.config.NatsEventPublisher;
import com.squadron.common.event.AgentCompletedEvent;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AgentLoopSupportTest {

    @Mock
    private ConversationService conversationService;

    @Mock
    private AgentProviderRegistry providerRegistry;

    @Mock
    private ToolExecutionEngine toolExecutionEngine;

    @Mock
    private NatsEventPublisher natsEventPublisher;

    @Mock
    private AgentProvider agentProvider;

    private final ObjectMapper objectMapper = new ObjectMapper();

    // ---------------------------------------------------------------------------
    // parseToolCalls tests
    // ---------------------------------------------------------------------------

    @Test
    void should_parseToolCalls_fromResponse() {
        String response = "I'll read the file first.\n"
                + "<tool_call name=\"file_read\">{\"path\": \"/workspace/src/Main.java\"}</tool_call>\n"
                + "And then execute the tests.\n"
                + "<tool_call name=\"shell_exec\">{\"command\": \"mvn test\", \"workdir\": \"/workspace\"}</tool_call>";

        List<ToolCall> toolCalls = AgentLoopSupport.parseToolCalls(response, objectMapper);

        assertEquals(2, toolCalls.size());
        assertEquals("file_read", toolCalls.get(0).getToolName());
        assertEquals("/workspace/src/Main.java", toolCalls.get(0).getArguments().get("path"));
        assertNotNull(toolCalls.get(0).getId());
        assertEquals("shell_exec", toolCalls.get(1).getToolName());
        assertEquals("mvn test", toolCalls.get(1).getArguments().get("command"));
        assertEquals("/workspace", toolCalls.get(1).getArguments().get("workdir"));
    }

    @Test
    void should_parseEmptyToolCalls_when_noToolCallsInResponse() {
        assertTrue(AgentLoopSupport.parseToolCalls("I've completed all the changes. [DONE]", objectMapper).isEmpty());
    }

    @Test
    void should_parseToolCalls_handleInvalidJson() {
        assertTrue(AgentLoopSupport.parseToolCalls("<tool_call name=\"file_read\">not valid json</tool_call>", objectMapper).isEmpty());
    }

    @Test
    void should_parseToolCalls_handleNullResponse() {
        assertTrue(AgentLoopSupport.parseToolCalls(null, objectMapper).isEmpty());
    }

    @Test
    void should_parseToolCalls_handleEmptyResponse() {
        assertTrue(AgentLoopSupport.parseToolCalls("", objectMapper).isEmpty());
    }

    @Test
    void should_parseToolCalls_withMultilineJsonBody() {
        String response = "<tool_call name=\"file_write\">{\n"
                + "  \"path\": \"/workspace/src/App.java\",\n"
                + "  \"content\": \"public class App {}\"\n"
                + "}</tool_call>";

        List<ToolCall> toolCalls = AgentLoopSupport.parseToolCalls(response, objectMapper);

        assertEquals(1, toolCalls.size());
        assertEquals("file_write", toolCalls.get(0).getToolName());
        assertEquals("/workspace/src/App.java", toolCalls.get(0).getArguments().get("path"));
    }

    // ---------------------------------------------------------------------------
    // isCompletionSignal tests
    // ---------------------------------------------------------------------------

    @Test
    void should_detectDoneSignal() {
        assertTrue(AgentLoopSupport.isCompletionSignal("All changes made. [DONE] Summary here."));
    }

    @Test
    void should_detectCompleteSignal() {
        assertTrue(AgentLoopSupport.isCompletionSignal("Implementation finished. [COMPLETE]"));
    }

    @Test
    void should_notDetectCompletion_whenNoSignal() {
        assertFalse(AgentLoopSupport.isCompletionSignal("I'm still working on it."));
    }

    @Test
    void should_notDetectCompletion_whenNull() {
        assertFalse(AgentLoopSupport.isCompletionSignal(null));
    }

    // ---------------------------------------------------------------------------
    // extractSummary tests
    // ---------------------------------------------------------------------------

    @Test
    void should_extractSummary_afterDone() {
        assertEquals("Created 3 files and updated 2 tests.",
                AgentLoopSupport.extractSummary("Completed the implementation. [DONE] Created 3 files and updated 2 tests."));
    }

    @Test
    void should_extractSummary_afterComplete() {
        assertEquals("All features implemented successfully.",
                AgentLoopSupport.extractSummary("[COMPLETE] All features implemented successfully."));
    }

    @Test
    void should_extractSummary_fallback_whenNoMarker() {
        assertEquals("Some response without markers",
                AgentLoopSupport.extractSummary("Some response without markers"));
    }

    @Test
    void should_extractSummary_truncateLongText() {
        String longText = "[DONE] " + "A".repeat(600);
        assertEquals(500, AgentLoopSupport.extractSummary(longText).length());
    }

    @Test
    void should_extractSummary_handleNull() {
        assertEquals("No summary provided", AgentLoopSupport.extractSummary(null));
    }

    @Test
    void should_extractSummary_handleEmpty() {
        assertEquals("No summary provided", AgentLoopSupport.extractSummary(""));
    }

    // ---------------------------------------------------------------------------
    // formatToolResults tests
    // ---------------------------------------------------------------------------

    @Test
    void should_formatToolResults() {
        List<ToolResult> results = List.of(
                ToolResult.builder().toolName("file_read").success(true).output("public class Main {}").build(),
                ToolResult.builder().toolName("shell_exec").success(false).error("Command failed with exit code 1").build()
        );

        String formatted = AgentLoopSupport.formatToolResults(results, false);

        assertTrue(formatted.contains("## Tool: file_read"));
        assertTrue(formatted.contains("Status: SUCCESS"));
        assertTrue(formatted.contains("public class Main {}"));
        assertTrue(formatted.contains("## Tool: shell_exec"));
        assertTrue(formatted.contains("Status: FAILED"));
        assertTrue(formatted.contains("Command failed with exit code 1"));
    }

    @Test
    void should_formatToolResults_emptyList() {
        assertEquals("No tool results.", AgentLoopSupport.formatToolResults(Collections.emptyList(), false));
    }

    @Test
    void should_formatToolResults_nullList() {
        assertEquals("No tool results.", AgentLoopSupport.formatToolResults(null, false));
    }

    @Test
    void should_formatToolResults_withSanitization() {
        ToolResult result = ToolResult.builder()
                .toolName("shell_exec").success(false)
                .error("fatal: https://oauth2:ghp_TOKEN@github.com/org/repo.git - Access denied").build();
        String formatted = AgentLoopSupport.formatToolResults(List.of(result), true);
        assertFalse(formatted.contains("ghp_TOKEN"));
        assertTrue(formatted.contains("***@"));
    }

    // ---------------------------------------------------------------------------
    // sanitizeOutput tests
    // ---------------------------------------------------------------------------

    @Test
    void should_sanitizeOutput_removeOAuth2Token() {
        String input = "fatal: https://oauth2:ghp_SECRET@github.com/owner/repo.git not found";
        String sanitized = AgentLoopSupport.sanitizeOutput(input);
        assertFalse(sanitized.contains("ghp_SECRET"));
        assertTrue(sanitized.contains("***@"));
    }

    @Test
    void should_sanitizeOutput_removeUserPassword() {
        String input = "unable to access 'https://user:password123@github.com/owner/repo.git'";
        String sanitized = AgentLoopSupport.sanitizeOutput(input);
        assertFalse(sanitized.contains("password123"));
        assertTrue(sanitized.contains("***@"));
    }

    @Test
    void should_sanitizeOutput_handleNull() {
        assertEquals("", AgentLoopSupport.sanitizeOutput(null));
    }

    // ---------------------------------------------------------------------------
    // renderToolDefinitions tests
    // ---------------------------------------------------------------------------

    @Test
    void should_renderToolDefinitions() {
        List<ToolDefinition> tools = List.of(
                ToolDefinition.builder()
                        .name("file_read").description("Reads a file")
                        .parameters(List.of(
                                ToolParameter.builder().name("path").type("string")
                                        .description("The file path").required(true).build()
                        )).build(),
                ToolDefinition.builder()
                        .name("shell_exec").description("Executes a command")
                        .parameters(List.of(
                                ToolParameter.builder().name("command").type("string")
                                        .description("Command to run").required(true).build(),
                                ToolParameter.builder().name("workdir").type("string")
                                        .description("Working directory").required(false).build()
                        )).build()
        );

        String rendered = AgentLoopSupport.renderToolDefinitions(tools);
        assertTrue(rendered.contains("### file_read"));
        assertTrue(rendered.contains("Reads a file"));
        assertTrue(rendered.contains("`path` (string) **required**"));
        assertTrue(rendered.contains("### shell_exec"));
        assertTrue(rendered.contains("`workdir` (string) optional"));
    }

    @Test
    void should_renderToolDefinitions_emptyList() {
        assertEquals("", AgentLoopSupport.renderToolDefinitions(Collections.emptyList()));
    }

    // ---------------------------------------------------------------------------
    // runAgentLoop tests
    // ---------------------------------------------------------------------------

    @Test
    void should_runAgentLoop_withToolCalls() {
        UUID conversationId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        UUID taskId = UUID.randomUUID();
        AgentConfigDto config = AgentConfigDto.builder().provider("openai-compatible").build();
        when(providerRegistry.getProvider("openai-compatible")).thenReturn(agentProvider);

        when(agentProvider.chat(anyString(), anyList(), anyString(), any()))
                .thenReturn("<tool_call name=\"file_read\">{\"path\": \"/workspace/Main.java\"}</tool_call>")
                .thenReturn("[DONE] All done.");

        when(toolExecutionEngine.executeTools(anyList(), any(ToolExecutionContext.class)))
                .thenReturn(List.of(ToolResult.builder().toolName("file_read").success(true).output("content").build()));

        when(conversationService.addMessage(any(), anyString(), anyString(), any()))
                .thenReturn(ConversationMessage.builder().id(UUID.randomUUID()).build());

        AgentLoopResult result = AgentLoopSupport.runAgentLoop(
                conversationId, tenantId, config, "System prompt", "Do the thing",
                taskId, null, 25, "Continue", true,
                conversationService, providerRegistry, toolExecutionEngine, objectMapper);

        assertTrue(result.isSuccess());
        assertEquals(2, result.getIterations());
        assertTrue(result.getSummary().contains("All done"));
    }

    @Test
    void should_runAgentLoop_stopOnMaxIterations() {
        UUID conversationId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        UUID taskId = UUID.randomUUID();
        AgentConfigDto config = AgentConfigDto.builder().provider("openai-compatible").build();
        when(providerRegistry.getProvider("openai-compatible")).thenReturn(agentProvider);

        when(agentProvider.chat(anyString(), anyList(), anyString(), any()))
                .thenReturn("<tool_call name=\"shell_exec\">{\"command\": \"echo hello\"}</tool_call>");

        when(toolExecutionEngine.executeTools(anyList(), any(ToolExecutionContext.class)))
                .thenReturn(List.of(ToolResult.builder().toolName("shell_exec").success(true).output("hello").build()));

        when(conversationService.addMessage(any(), anyString(), anyString(), any()))
                .thenReturn(ConversationMessage.builder().id(UUID.randomUUID()).build());

        AgentLoopResult result = AgentLoopSupport.runAgentLoop(
                conversationId, tenantId, config, "System prompt", "Do the thing",
                taskId, null, 3, "Continue", false,
                conversationService, providerRegistry, toolExecutionEngine, objectMapper);

        assertFalse(result.isSuccess());
        assertEquals(3, result.getIterations());
        assertEquals("Max iterations reached", result.getSummary());
    }

    @Test
    void should_runAgentLoop_handleLlmFailure() {
        UUID conversationId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        UUID taskId = UUID.randomUUID();
        AgentConfigDto config = AgentConfigDto.builder().provider("openai-compatible").build();
        when(providerRegistry.getProvider("openai-compatible")).thenReturn(agentProvider);

        when(agentProvider.chat(anyString(), anyList(), anyString(), any()))
                .thenThrow(new RuntimeException("API rate limit exceeded"));

        when(conversationService.addMessage(any(), anyString(), anyString(), any()))
                .thenReturn(ConversationMessage.builder().id(UUID.randomUUID()).build());

        AgentLoopResult result = AgentLoopSupport.runAgentLoop(
                conversationId, tenantId, config, "System prompt", "Do the thing",
                taskId, null, 25, "Continue", false,
                conversationService, providerRegistry, toolExecutionEngine, objectMapper);

        assertFalse(result.isSuccess());
        assertEquals(1, result.getIterations());
        assertTrue(result.getSummary().contains("LLM call failed"));
    }

    @Test
    void should_runAgentLoop_nudgeOnNoToolCallsAndNoCompletion() {
        UUID conversationId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        UUID taskId = UUID.randomUUID();
        AgentConfigDto config = AgentConfigDto.builder().provider("openai-compatible").build();
        when(providerRegistry.getProvider("openai-compatible")).thenReturn(agentProvider);

        when(agentProvider.chat(anyString(), anyList(), anyString(), any()))
                .thenReturn("I'm thinking about it...")
                .thenReturn("[DONE] Implemented everything.");

        when(conversationService.addMessage(any(), anyString(), anyString(), any()))
                .thenReturn(ConversationMessage.builder().id(UUID.randomUUID()).build());

        AgentLoopResult result = AgentLoopSupport.runAgentLoop(
                conversationId, tenantId, config, "System prompt", "Do the thing",
                taskId, null, 25, "Please continue", false,
                conversationService, providerRegistry, toolExecutionEngine, objectMapper);

        assertTrue(result.isSuccess());
        assertEquals(2, result.getIterations());

        ArgumentCaptor<String> messageCaptor = ArgumentCaptor.forClass(String.class);
        verify(agentProvider, times(2)).chat(anyString(), anyList(), messageCaptor.capture(), any());
        assertEquals("Please continue", messageCaptor.getAllValues().get(1));
    }

    // ---------------------------------------------------------------------------
    // publishCompletedEvent tests
    // ---------------------------------------------------------------------------

    @Test
    void should_publishCompletedEvent_success() {
        UUID tenantId = UUID.randomUUID();
        UUID taskId = UUID.randomUUID();
        UUID conversationId = UUID.randomUUID();

        when(natsEventPublisher.publishAsync(anyString(), any()))
                .thenReturn(CompletableFuture.completedFuture(null));

        AgentLoopSupport.publishCompletedEvent(tenantId, taskId, conversationId,
                "CODING", true, "All done", natsEventPublisher);

        ArgumentCaptor<String> subjectCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<AgentCompletedEvent> eventCaptor = ArgumentCaptor.forClass(AgentCompletedEvent.class);
        verify(natsEventPublisher, times(2)).publishAsync(subjectCaptor.capture(), eventCaptor.capture());

        assertEquals("squadron.agent.coding.completed", subjectCaptor.getAllValues().get(0));
        assertEquals("squadron.agents.completed", subjectCaptor.getAllValues().get(1));
        assertTrue(eventCaptor.getAllValues().get(0).isSuccess());
        assertEquals("CODING", eventCaptor.getAllValues().get(0).getAgentType());
    }

    @Test
    void should_publishCompletedEvent_failure() {
        UUID tenantId = UUID.randomUUID();
        UUID taskId = UUID.randomUUID();

        when(natsEventPublisher.publishAsync(anyString(), any()))
                .thenReturn(CompletableFuture.completedFuture(null));

        AgentLoopSupport.publishCompletedEvent(tenantId, taskId, null,
                "REVIEW", false, "Error occurred", natsEventPublisher);

        ArgumentCaptor<String> subjectCaptor = ArgumentCaptor.forClass(String.class);
        verify(natsEventPublisher, times(2)).publishAsync(subjectCaptor.capture(), any());
        assertEquals("squadron.agent.review.failed", subjectCaptor.getAllValues().get(0));
    }
}
