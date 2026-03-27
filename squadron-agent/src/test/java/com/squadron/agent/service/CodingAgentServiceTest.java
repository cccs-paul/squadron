package com.squadron.agent.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.squadron.agent.dto.AgentConfigDto;
import com.squadron.agent.entity.Conversation;
import com.squadron.agent.entity.ConversationMessage;
import com.squadron.agent.entity.TaskPlan;
import com.squadron.agent.provider.AgentProvider;
import com.squadron.agent.provider.AgentProviderRegistry;
import com.squadron.agent.tool.ToolCall;
import com.squadron.agent.tool.ToolDefinition;
import com.squadron.agent.tool.ToolExecutionContext;
import com.squadron.agent.tool.ToolExecutionEngine;
import com.squadron.agent.tool.ToolParameter;
import com.squadron.agent.tool.ToolRegistry;
import com.squadron.agent.tool.ToolResult;
import com.squadron.common.config.NatsEventPublisher;
import com.squadron.common.event.AgentCompletedEvent;
import com.squadron.common.event.TaskStateChangedEvent;
import org.junit.jupiter.api.BeforeEach;
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
class CodingAgentServiceTest {

    @Mock
    private PlanService planService;

    @Mock
    private ConversationService conversationService;

    @Mock
    private SquadronConfigService configService;

    @Mock
    private AgentProviderRegistry providerRegistry;

    @Mock
    private SystemPromptBuilder promptBuilder;

    @Mock
    private ToolRegistry toolRegistry;

    @Mock
    private ToolExecutionEngine toolExecutionEngine;

    @Mock
    private NatsEventPublisher natsEventPublisher;

    @Mock
    private AgentProvider agentProvider;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private CodingAgentService codingAgentService;

    // Reusable test identifiers
    private UUID taskId;
    private UUID tenantId;
    private UUID userId;
    private UUID conversationId;

    @BeforeEach
    void setUp() {
        codingAgentService = new CodingAgentService(
                planService, conversationService, configService, providerRegistry,
                promptBuilder, toolRegistry, toolExecutionEngine, natsEventPublisher,
                objectMapper);

        taskId = UUID.randomUUID();
        tenantId = UUID.randomUUID();
        userId = UUID.randomUUID();
        conversationId = UUID.randomUUID();
    }

    // ---------------------------------------------------------------------------
    // executeCodeGeneration tests
    // ---------------------------------------------------------------------------

    @Test
    void should_executeCodeGeneration_successfully() {
        // Arrange
        TaskStateChangedEvent event = createProposeCodeEvent();

        TaskPlan plan = TaskPlan.builder()
                .id(UUID.randomUUID())
                .tenantId(tenantId)
                .taskId(taskId)
                .planContent("1. Create file\n2. Write tests")
                .status("APPROVED")
                .version(1)
                .build();
        when(planService.getLatestPlan(taskId)).thenReturn(plan);

        Conversation conversation = createConversation();
        when(conversationService.startConversation(tenantId, taskId, userId, "CODING"))
                .thenReturn(conversation);

        when(configService.resolveAgentConfig(tenantId, null, userId, "CODING"))
                .thenReturn(AgentConfigDto.builder().provider("openai-compatible").build());

        when(toolRegistry.getAllToolDefinitions()).thenReturn(Collections.emptyList());

        when(providerRegistry.getProvider("openai-compatible")).thenReturn(agentProvider);

        // LLM returns a completion signal on first call
        when(agentProvider.chat(anyString(), anyList(), anyString(), any(AgentConfigDto.class)))
                .thenReturn("All changes implemented. [DONE] Created file and wrote tests.");

        when(conversationService.addMessage(any(), anyString(), anyString(), any()))
                .thenReturn(ConversationMessage.builder().id(UUID.randomUUID()).build());

        when(natsEventPublisher.publishAsync(anyString(), any()))
                .thenReturn(CompletableFuture.completedFuture(null));

        // Act
        codingAgentService.executeCodeGeneration(event);

        // Assert
        verify(planService).getLatestPlan(taskId);
        verify(conversationService).startConversation(tenantId, taskId, userId, "CODING");
        verify(agentProvider).chat(anyString(), anyList(), anyString(), any(AgentConfigDto.class));

        ArgumentCaptor<String> subjectCaptor = ArgumentCaptor.forClass(String.class);
        verify(natsEventPublisher, times(2)).publishAsync(subjectCaptor.capture(), any(AgentCompletedEvent.class));
        assertEquals("squadron.agent.coding.completed", subjectCaptor.getAllValues().get(0));
        assertEquals("squadron.agents.completed", subjectCaptor.getAllValues().get(1));
    }

    @Test
    void should_skipIfPlanNotApproved() {
        TaskStateChangedEvent event = createProposeCodeEvent();

        TaskPlan plan = TaskPlan.builder()
                .id(UUID.randomUUID())
                .tenantId(tenantId)
                .taskId(taskId)
                .planContent("Draft plan")
                .status("DRAFT")
                .version(1)
                .build();
        when(planService.getLatestPlan(taskId)).thenReturn(plan);

        codingAgentService.executeCodeGeneration(event);

        verify(planService).getLatestPlan(taskId);
        verifyNoInteractions(conversationService);
        verifyNoInteractions(providerRegistry);
        verifyNoInteractions(natsEventPublisher);
    }

    @Test
    void should_handlePlanNotFound() {
        TaskStateChangedEvent event = createProposeCodeEvent();

        when(planService.getLatestPlan(taskId))
                .thenThrow(new RuntimeException("Plan not found"));

        when(natsEventPublisher.publishAsync(anyString(), any()))
                .thenReturn(CompletableFuture.completedFuture(null));

        assertDoesNotThrow(() -> codingAgentService.executeCodeGeneration(event));

        ArgumentCaptor<String> subjectCaptor = ArgumentCaptor.forClass(String.class);
        verify(natsEventPublisher, times(2)).publishAsync(subjectCaptor.capture(), any(AgentCompletedEvent.class));
        assertEquals("squadron.agent.coding.failed", subjectCaptor.getAllValues().get(0));
        assertEquals("squadron.agents.completed", subjectCaptor.getAllValues().get(1));
    }

    @Test
    void should_publishFailureEvent_onError() {
        TaskStateChangedEvent event = createProposeCodeEvent();

        TaskPlan plan = TaskPlan.builder()
                .id(UUID.randomUUID())
                .tenantId(tenantId)
                .taskId(taskId)
                .planContent("Plan content")
                .status("APPROVED")
                .version(1)
                .build();
        when(planService.getLatestPlan(taskId)).thenReturn(plan);

        when(conversationService.startConversation(tenantId, taskId, userId, "CODING"))
                .thenThrow(new RuntimeException("DB connection failed"));

        when(natsEventPublisher.publishAsync(anyString(), any()))
                .thenReturn(CompletableFuture.completedFuture(null));

        codingAgentService.executeCodeGeneration(event);

        ArgumentCaptor<String> subjectCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<AgentCompletedEvent> eventCaptor =
                ArgumentCaptor.forClass(AgentCompletedEvent.class);
        verify(natsEventPublisher, times(2)).publishAsync(subjectCaptor.capture(), eventCaptor.capture());

        assertEquals("squadron.agent.coding.failed", subjectCaptor.getAllValues().get(0));
        assertFalse(eventCaptor.getAllValues().get(0).isSuccess());
        assertEquals("CODING", eventCaptor.getAllValues().get(0).getAgentType());
    }

    @Test
    void should_publishCompletionEvent_onSuccess() {
        TaskStateChangedEvent event = createProposeCodeEvent();

        TaskPlan plan = TaskPlan.builder()
                .id(UUID.randomUUID())
                .tenantId(tenantId)
                .taskId(taskId)
                .planContent("Plan")
                .status("APPROVED")
                .version(1)
                .build();
        when(planService.getLatestPlan(taskId)).thenReturn(plan);

        Conversation conversation = createConversation();
        when(conversationService.startConversation(tenantId, taskId, userId, "CODING"))
                .thenReturn(conversation);
        when(configService.resolveAgentConfig(tenantId, null, userId, "CODING"))
                .thenReturn(AgentConfigDto.builder().provider("openai-compatible").build());
        when(toolRegistry.getAllToolDefinitions()).thenReturn(Collections.emptyList());
        when(providerRegistry.getProvider("openai-compatible")).thenReturn(agentProvider);
        when(agentProvider.chat(anyString(), anyList(), anyString(), any()))
                .thenReturn("[DONE] Implementation complete.");
        when(conversationService.addMessage(any(), anyString(), anyString(), any()))
                .thenReturn(ConversationMessage.builder().id(UUID.randomUUID()).build());
        when(natsEventPublisher.publishAsync(anyString(), any()))
                .thenReturn(CompletableFuture.completedFuture(null));

        codingAgentService.executeCodeGeneration(event);

        ArgumentCaptor<String> subjectCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<AgentCompletedEvent> eventCaptor =
                ArgumentCaptor.forClass(AgentCompletedEvent.class);
        verify(natsEventPublisher, times(2)).publishAsync(subjectCaptor.capture(),
                eventCaptor.capture());

        // First call is the specific subject, second is the aggregated subject
        assertEquals("squadron.agent.coding.completed", subjectCaptor.getAllValues().get(0));
        assertEquals("squadron.agents.completed", subjectCaptor.getAllValues().get(1));

        AgentCompletedEvent completedEvent = eventCaptor.getAllValues().get(0);
        assertTrue(completedEvent.isSuccess());
        assertEquals("CODING", completedEvent.getAgentType());
        assertEquals(tenantId, completedEvent.getTenantId());
        assertEquals(taskId, completedEvent.getTaskId());
        assertEquals(conversationId, completedEvent.getConversationId());
        assertEquals("squadron-agent", completedEvent.getSource());
    }

    @Test
    void should_useDefaultConfig_when_configIsNull() {
        TaskStateChangedEvent event = createProposeCodeEvent();

        TaskPlan plan = TaskPlan.builder()
                .id(UUID.randomUUID())
                .tenantId(tenantId)
                .taskId(taskId)
                .planContent("Plan")
                .status("APPROVED")
                .version(1)
                .build();
        when(planService.getLatestPlan(taskId)).thenReturn(plan);

        Conversation conversation = createConversation();
        when(conversationService.startConversation(tenantId, taskId, userId, "CODING"))
                .thenReturn(conversation);
        when(configService.resolveAgentConfig(tenantId, null, userId, "CODING"))
                .thenReturn(null);
        when(toolRegistry.getAllToolDefinitions()).thenReturn(Collections.emptyList());
        // null provider -> falls back to "openai-compatible"
        when(providerRegistry.getProvider("openai-compatible")).thenReturn(agentProvider);
        when(agentProvider.chat(anyString(), anyList(), anyString(), any()))
                .thenReturn("[DONE] Done.");
        when(conversationService.addMessage(any(), anyString(), anyString(), any()))
                .thenReturn(ConversationMessage.builder().id(UUID.randomUUID()).build());
        when(natsEventPublisher.publishAsync(anyString(), any()))
                .thenReturn(CompletableFuture.completedFuture(null));

        assertDoesNotThrow(() -> codingAgentService.executeCodeGeneration(event));

        verify(providerRegistry).getProvider("openai-compatible");
    }

    // ---------------------------------------------------------------------------
    // runAgentLoop tests
    // ---------------------------------------------------------------------------

    @Test
    void should_runAgentLoop_withToolCalls() {
        AgentConfigDto config = AgentConfigDto.builder().provider("openai-compatible").build();
        when(providerRegistry.getProvider("openai-compatible")).thenReturn(agentProvider);

        // First call: agent returns a tool call
        String responseWithToolCall = "Let me read the file.\n"
                + "<tool_call name=\"file_read\">{\"path\": \"/workspace/Main.java\"}</tool_call>";
        // Second call: agent returns completion
        String completionResponse = "I've read the file and made changes. [DONE] All done.";

        when(agentProvider.chat(anyString(), anyList(), anyString(), any()))
                .thenReturn(responseWithToolCall)
                .thenReturn(completionResponse);

        ToolResult fileReadResult = ToolResult.builder()
                .toolName("file_read")
                .success(true)
                .output("public class Main { ... }")
                .build();
        when(toolExecutionEngine.executeTools(anyList(), any(ToolExecutionContext.class)))
                .thenReturn(List.of(fileReadResult));

        when(conversationService.addMessage(any(), anyString(), anyString(), any()))
                .thenReturn(ConversationMessage.builder().id(UUID.randomUUID()).build());

        AgentLoopResult result = codingAgentService.runAgentLoop(
                conversationId, tenantId, userId, config,
                "System prompt", "Implement the plan", taskId);

        assertTrue(result.isSuccess());
        assertEquals(2, result.getIterations());
        assertTrue(result.getSummary().contains("All done"));

        // Verify tool execution happened
        verify(toolExecutionEngine).executeTools(anyList(), any(ToolExecutionContext.class));
    }

    @Test
    void should_stopLoop_onCompletionSignal() {
        AgentConfigDto config = AgentConfigDto.builder().provider("openai-compatible").build();
        when(providerRegistry.getProvider("openai-compatible")).thenReturn(agentProvider);

        when(agentProvider.chat(anyString(), anyList(), anyString(), any()))
                .thenReturn("[COMPLETE] Everything is implemented.");

        when(conversationService.addMessage(any(), anyString(), anyString(), any()))
                .thenReturn(ConversationMessage.builder().id(UUID.randomUUID()).build());

        AgentLoopResult result = codingAgentService.runAgentLoop(
                conversationId, tenantId, userId, config,
                "System prompt", "Implement the plan", taskId);

        assertTrue(result.isSuccess());
        assertEquals(1, result.getIterations());
        assertTrue(result.getSummary().contains("Everything is implemented"));
    }

    @Test
    void should_stopLoop_onMaxIterations() {
        AgentConfigDto config = AgentConfigDto.builder().provider("openai-compatible").build();
        when(providerRegistry.getProvider("openai-compatible")).thenReturn(agentProvider);

        // Always return a tool call (never signals done)
        when(agentProvider.chat(anyString(), anyList(), anyString(), any()))
                .thenReturn("<tool_call name=\"shell_exec\">{\"command\": \"echo hello\"}</tool_call>");

        ToolResult shellResult = ToolResult.builder()
                .toolName("shell_exec")
                .success(true)
                .output("hello")
                .build();
        when(toolExecutionEngine.executeTools(anyList(), any(ToolExecutionContext.class)))
                .thenReturn(List.of(shellResult));

        when(conversationService.addMessage(any(), anyString(), anyString(), any()))
                .thenReturn(ConversationMessage.builder().id(UUID.randomUUID()).build());

        AgentLoopResult result = codingAgentService.runAgentLoop(
                conversationId, tenantId, userId, config,
                "System prompt", "Implement the plan", taskId);

        assertFalse(result.isSuccess());
        assertEquals(CodingAgentService.MAX_ITERATIONS, result.getIterations());
        assertEquals("Max iterations reached", result.getSummary());
    }

    @Test
    void should_handleLlmCallFailure() {
        AgentConfigDto config = AgentConfigDto.builder().provider("openai-compatible").build();
        when(providerRegistry.getProvider("openai-compatible")).thenReturn(agentProvider);

        when(agentProvider.chat(anyString(), anyList(), anyString(), any()))
                .thenThrow(new RuntimeException("API rate limit exceeded"));

        when(conversationService.addMessage(any(), anyString(), anyString(), any()))
                .thenReturn(ConversationMessage.builder().id(UUID.randomUUID()).build());

        AgentLoopResult result = codingAgentService.runAgentLoop(
                conversationId, tenantId, userId, config,
                "System prompt", "Implement the plan", taskId);

        assertFalse(result.isSuccess());
        assertEquals(1, result.getIterations());
        assertTrue(result.getSummary().contains("LLM call failed"));
        assertTrue(result.getSummary().contains("API rate limit exceeded"));
    }

    @Test
    void should_promptContinuation_when_noToolCallsAndNoCompletion() {
        AgentConfigDto config = AgentConfigDto.builder().provider("openai-compatible").build();
        when(providerRegistry.getProvider("openai-compatible")).thenReturn(agentProvider);

        // First call: conversational response (no tools, no completion)
        // Second call: completion
        when(agentProvider.chat(anyString(), anyList(), anyString(), any()))
                .thenReturn("I understand the plan. Let me think about it.")
                .thenReturn("[DONE] Implemented everything.");

        when(conversationService.addMessage(any(), anyString(), anyString(), any()))
                .thenReturn(ConversationMessage.builder().id(UUID.randomUUID()).build());

        AgentLoopResult result = codingAgentService.runAgentLoop(
                conversationId, tenantId, userId, config,
                "System prompt", "Implement the plan", taskId);

        assertTrue(result.isSuccess());
        assertEquals(2, result.getIterations());

        // The second message should be the continuation prompt
        ArgumentCaptor<String> messageCaptor = ArgumentCaptor.forClass(String.class);
        verify(agentProvider, times(2)).chat(anyString(), anyList(), messageCaptor.capture(), any());
        String secondMessage = messageCaptor.getAllValues().get(1);
        assertTrue(secondMessage.contains("Please continue"));
    }

    // ---------------------------------------------------------------------------
    // parseToolCalls tests
    // ---------------------------------------------------------------------------

    @Test
    void should_parseToolCalls_fromResponse() {
        String response = "I'll read the file first.\n"
                + "<tool_call name=\"file_read\">{\"path\": \"/workspace/src/Main.java\"}</tool_call>\n"
                + "And then execute the tests.\n"
                + "<tool_call name=\"shell_exec\">{\"command\": \"mvn test\", \"workdir\": \"/workspace\"}</tool_call>";

        List<ToolCall> toolCalls = codingAgentService.parseToolCalls(response);

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
        String response = "I've completed all the changes. [DONE]";

        List<ToolCall> toolCalls = codingAgentService.parseToolCalls(response);

        assertTrue(toolCalls.isEmpty());
    }

    @Test
    void should_parseToolCalls_handleInvalidJson() {
        String response = "<tool_call name=\"file_read\">not valid json</tool_call>";

        List<ToolCall> toolCalls = codingAgentService.parseToolCalls(response);

        assertTrue(toolCalls.isEmpty());
    }

    @Test
    void should_parseToolCalls_handleNullResponse() {
        List<ToolCall> toolCalls = codingAgentService.parseToolCalls(null);
        assertTrue(toolCalls.isEmpty());
    }

    @Test
    void should_parseToolCalls_handleEmptyResponse() {
        List<ToolCall> toolCalls = codingAgentService.parseToolCalls("");
        assertTrue(toolCalls.isEmpty());
    }

    @Test
    void should_parseToolCalls_withMultilineJsonBody() {
        String response = "<tool_call name=\"file_write\">{\n"
                + "  \"path\": \"/workspace/src/App.java\",\n"
                + "  \"content\": \"public class App {}\"\n"
                + "}</tool_call>";

        List<ToolCall> toolCalls = codingAgentService.parseToolCalls(response);

        assertEquals(1, toolCalls.size());
        assertEquals("file_write", toolCalls.get(0).getToolName());
        assertEquals("/workspace/src/App.java", toolCalls.get(0).getArguments().get("path"));
        assertEquals("public class App {}", toolCalls.get(0).getArguments().get("content"));
    }

    // ---------------------------------------------------------------------------
    // formatToolResults tests
    // ---------------------------------------------------------------------------

    @Test
    void should_formatToolResults() {
        List<ToolResult> results = List.of(
                ToolResult.builder()
                        .toolName("file_read")
                        .success(true)
                        .output("public class Main {}")
                        .build(),
                ToolResult.builder()
                        .toolName("shell_exec")
                        .success(false)
                        .error("Command failed with exit code 1")
                        .build()
        );

        String formatted = codingAgentService.formatToolResults(results);

        assertTrue(formatted.contains("## Tool: file_read"));
        assertTrue(formatted.contains("Status: SUCCESS"));
        assertTrue(formatted.contains("public class Main {}"));
        assertTrue(formatted.contains("## Tool: shell_exec"));
        assertTrue(formatted.contains("Status: FAILED"));
        assertTrue(formatted.contains("Command failed with exit code 1"));
    }

    @Test
    void should_formatToolResults_emptyList() {
        String formatted = codingAgentService.formatToolResults(Collections.emptyList());
        assertEquals("No tool results.", formatted);
    }

    @Test
    void should_formatToolResults_nullList() {
        String formatted = codingAgentService.formatToolResults(null);
        assertEquals("No tool results.", formatted);
    }

    // ---------------------------------------------------------------------------
    // buildCodingPromptWithTools tests
    // ---------------------------------------------------------------------------

    @Test
    void should_buildCodingPromptWithTools() {
        List<ToolDefinition> tools = List.of(
                ToolDefinition.builder()
                        .name("file_read")
                        .description("Reads a file from the workspace")
                        .parameters(List.of(
                                ToolParameter.builder()
                                        .name("path")
                                        .type("string")
                                        .description("The file path to read")
                                        .required(true)
                                        .build()
                        ))
                        .build(),
                ToolDefinition.builder()
                        .name("shell_exec")
                        .description("Executes a shell command")
                        .parameters(List.of(
                                ToolParameter.builder()
                                        .name("command")
                                        .type("string")
                                        .description("The command to execute")
                                        .required(true)
                                        .build(),
                                ToolParameter.builder()
                                        .name("workdir")
                                        .type("string")
                                        .description("Working directory")
                                        .required(false)
                                        .build()
                        ))
                        .build()
        );

        String prompt = codingAgentService.buildCodingPromptWithTools(
                "1. Create Main.java\n2. Write tests", tools);

        // Should contain tool definitions
        assertTrue(prompt.contains("file_read"));
        assertTrue(prompt.contains("Reads a file from the workspace"));
        assertTrue(prompt.contains("`path` (string) **required**"));
        assertTrue(prompt.contains("shell_exec"));
        assertTrue(prompt.contains("`workdir` (string) optional"));

        // Should contain the plan
        assertTrue(prompt.contains("1. Create Main.java"));
        assertTrue(prompt.contains("2. Write tests"));

        // Should contain instructions
        assertTrue(prompt.contains("[DONE]"));
        assertTrue(prompt.contains("tool_call"));
    }

    @Test
    void should_buildCodingPromptWithTools_emptyTools() {
        String prompt = codingAgentService.buildCodingPromptWithTools(
                "Simple plan", Collections.emptyList());

        assertTrue(prompt.contains("Simple plan"));
        assertTrue(prompt.contains("Available Tools"));
    }

    // ---------------------------------------------------------------------------
    // isCompletionSignal tests
    // ---------------------------------------------------------------------------

    @Test
    void should_detectDoneSignal() {
        assertTrue(codingAgentService.isCompletionSignal("All changes made. [DONE] Summary here."));
    }

    @Test
    void should_detectCompleteSignal() {
        assertTrue(codingAgentService.isCompletionSignal("Implementation finished. [COMPLETE]"));
    }

    @Test
    void should_notDetectCompletion_whenNoSignal() {
        assertFalse(codingAgentService.isCompletionSignal("I'm still working on it."));
    }

    @Test
    void should_notDetectCompletion_whenNull() {
        assertFalse(codingAgentService.isCompletionSignal(null));
    }

    // ---------------------------------------------------------------------------
    // extractSummary tests
    // ---------------------------------------------------------------------------

    @Test
    void should_extractSummary_afterDone() {
        String summary = codingAgentService.extractSummary(
                "Completed the implementation. [DONE] Created 3 files and updated 2 tests.");
        assertEquals("Created 3 files and updated 2 tests.", summary);
    }

    @Test
    void should_extractSummary_afterComplete() {
        String summary = codingAgentService.extractSummary(
                "[COMPLETE] All features implemented successfully.");
        assertEquals("All features implemented successfully.", summary);
    }

    @Test
    void should_extractSummary_fallback_whenNoMarker() {
        String summary = codingAgentService.extractSummary("Some response without markers");
        assertEquals("Some response without markers", summary);
    }

    @Test
    void should_extractSummary_truncateLongText() {
        String longText = "[DONE] " + "A".repeat(600);
        String summary = codingAgentService.extractSummary(longText);
        assertEquals(500, summary.length());
    }

    @Test
    void should_extractSummary_handleNull() {
        assertEquals("No summary provided", codingAgentService.extractSummary(null));
    }

    @Test
    void should_extractSummary_handleEmpty() {
        assertEquals("No summary provided", codingAgentService.extractSummary(""));
    }

    // ---------------------------------------------------------------------------
    // Helper methods
    // ---------------------------------------------------------------------------

    private TaskStateChangedEvent createProposeCodeEvent() {
        TaskStateChangedEvent event = new TaskStateChangedEvent();
        event.setTaskId(taskId);
        event.setTenantId(tenantId);
        event.setTriggeredBy(userId);
        event.setFromState("PLANNING");
        event.setToState("PROPOSE_CODE");
        return event;
    }

    private Conversation createConversation() {
        return Conversation.builder()
                .id(conversationId)
                .tenantId(tenantId)
                .taskId(taskId)
                .userId(userId)
                .agentType("CODING")
                .status("ACTIVE")
                .totalTokens(0L)
                .build();
    }
}
