package com.squadron.agent.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.squadron.agent.dto.AgentConfigDto;
import com.squadron.agent.entity.Conversation;
import com.squadron.agent.entity.ConversationMessage;
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
class QAAgentServiceTest {

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
    private CoverageService coverageService;

    @Mock
    private AgentProvider agentProvider;

    private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
    private QAAgentService qaAgentService;

    private UUID taskId;
    private UUID tenantId;
    private UUID userId;
    private UUID conversationId;

    @BeforeEach
    void setUp() {
        qaAgentService = new QAAgentService(
                conversationService, configService, providerRegistry, promptBuilder,
                toolRegistry, toolExecutionEngine, natsEventPublisher,
                coverageService, objectMapper);

        taskId = UUID.randomUUID();
        tenantId = UUID.randomUUID();
        userId = UUID.randomUUID();
        conversationId = UUID.randomUUID();
    }

    // ---------------------------------------------------------------------------
    // executeQA tests
    // ---------------------------------------------------------------------------

    @Test
    void should_executeQA_successfully() {
        TaskStateChangedEvent event = createQAEvent();

        Conversation conversation = createConversation();
        when(conversationService.startConversation(tenantId, taskId, userId, "QA"))
                .thenReturn(conversation);
        when(configService.resolveAgentConfig(tenantId, null, userId, "QA"))
                .thenReturn(AgentConfigDto.builder().provider("openai-compatible").build());
        when(toolRegistry.getAllToolDefinitions()).thenReturn(Collections.emptyList());
        when(providerRegistry.getProvider("openai-compatible")).thenReturn(agentProvider);

        when(agentProvider.chat(anyString(), anyList(), anyString(), any(AgentConfigDto.class)))
                .thenReturn("[DONE] [QA_PASS] All tests pass and coverage is adequate.");

        when(conversationService.addMessage(any(), anyString(), anyString(), any()))
                .thenReturn(ConversationMessage.builder().id(UUID.randomUUID()).build());
        when(natsEventPublisher.publishAsync(anyString(), any()))
                .thenReturn(CompletableFuture.completedFuture(null));

        qaAgentService.executeQA(event);

        ArgumentCaptor<String> subjectCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<AgentCompletedEvent> eventCaptor =
                ArgumentCaptor.forClass(AgentCompletedEvent.class);
        verify(natsEventPublisher, times(2)).publishAsync(subjectCaptor.capture(), eventCaptor.capture());

        assertEquals("squadron.agent.qa.completed", subjectCaptor.getAllValues().get(0));
        assertTrue(eventCaptor.getAllValues().get(0).isSuccess());
        assertEquals("QA", eventCaptor.getAllValues().get(0).getAgentType());
    }

    @Test
    void should_executeQA_withFailVerdict() {
        TaskStateChangedEvent event = createQAEvent();

        Conversation conversation = createConversation();
        when(conversationService.startConversation(tenantId, taskId, userId, "QA"))
                .thenReturn(conversation);
        when(configService.resolveAgentConfig(tenantId, null, userId, "QA"))
                .thenReturn(AgentConfigDto.builder().build());
        when(toolRegistry.getAllToolDefinitions()).thenReturn(Collections.emptyList());
        when(providerRegistry.getProvider("openai-compatible")).thenReturn(agentProvider);

        when(agentProvider.chat(anyString(), anyList(), anyString(), any(AgentConfigDto.class)))
                .thenReturn("[DONE] [QA_FAIL] Tests are failing and coverage is below threshold.");

        when(conversationService.addMessage(any(), anyString(), anyString(), any()))
                .thenReturn(ConversationMessage.builder().id(UUID.randomUUID()).build());
        when(natsEventPublisher.publishAsync(anyString(), any()))
                .thenReturn(CompletableFuture.completedFuture(null));

        qaAgentService.executeQA(event);

        ArgumentCaptor<String> subjectCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<AgentCompletedEvent> eventCaptor =
                ArgumentCaptor.forClass(AgentCompletedEvent.class);
        verify(natsEventPublisher, times(2)).publishAsync(subjectCaptor.capture(), eventCaptor.capture());

        assertEquals("squadron.agent.qa.failed", subjectCaptor.getAllValues().get(0));
        assertFalse(eventCaptor.getAllValues().get(0).isSuccess());
    }

    @Test
    void should_handleQAExecutionFailure() {
        TaskStateChangedEvent event = createQAEvent();

        when(conversationService.startConversation(tenantId, taskId, userId, "QA"))
                .thenThrow(new RuntimeException("DB connection failed"));

        when(natsEventPublisher.publishAsync(anyString(), any()))
                .thenReturn(CompletableFuture.completedFuture(null));

        assertDoesNotThrow(() -> qaAgentService.executeQA(event));

        ArgumentCaptor<String> subjectCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<AgentCompletedEvent> eventCaptor =
                ArgumentCaptor.forClass(AgentCompletedEvent.class);
        verify(natsEventPublisher, times(2)).publishAsync(subjectCaptor.capture(), eventCaptor.capture());

        assertEquals("squadron.agent.qa.failed", subjectCaptor.getAllValues().get(0));
        assertFalse(eventCaptor.getAllValues().get(0).isSuccess());
    }

    // ---------------------------------------------------------------------------
    // parseQAVerdict tests
    // ---------------------------------------------------------------------------

    @Test
    void should_parseQAVerdict_pass() {
        assertEquals("PASS", qaAgentService.parseQAVerdict("[QA_PASS] All tests pass."));
    }

    @Test
    void should_parseQAVerdict_conditionalPass() {
        assertEquals("CONDITIONAL_PASS",
                qaAgentService.parseQAVerdict("[QA_CONDITIONAL_PASS] Some concerns."));
    }

    @Test
    void should_parseQAVerdict_fail() {
        assertEquals("FAIL", qaAgentService.parseQAVerdict("[QA_FAIL] Tests are failing."));
    }

    @Test
    void should_parseQAVerdict_freeTextPass() {
        assertEquals("PASS",
                qaAgentService.parseQAVerdict("Overall QA verdict: PASS\n\nAll tests pass."));
    }

    @Test
    void should_parseQAVerdict_freeTextFail() {
        assertEquals("FAIL",
                qaAgentService.parseQAVerdict("Overall QA verdict: FAIL\n\nTests fail."));
    }

    @Test
    void should_parseQAVerdict_null() {
        assertEquals("CONDITIONAL_PASS", qaAgentService.parseQAVerdict(null));
    }

    @Test
    void should_parseQAVerdict_empty() {
        assertEquals("CONDITIONAL_PASS", qaAgentService.parseQAVerdict(""));
    }

    @Test
    void should_parseQAVerdict_freeTextConditionalPass() {
        assertEquals("CONDITIONAL_PASS",
                qaAgentService.parseQAVerdict("Overall QA verdict: CONDITIONAL_PASS\nSome gaps."));
    }

    // ---------------------------------------------------------------------------
    // buildQAPromptWithTools tests
    // ---------------------------------------------------------------------------

    @Test
    void should_buildQAPromptWithTools() {
        List<ToolDefinition> tools = List.of(
                ToolDefinition.builder()
                        .name("shell_exec")
                        .description("Executes a shell command")
                        .parameters(List.of(
                                ToolParameter.builder()
                                        .name("command").type("string")
                                        .description("The command to execute")
                                        .required(true).build()
                        ))
                        .build(),
                ToolDefinition.builder()
                        .name("file_write")
                        .description("Writes content to a file")
                        .parameters(List.of(
                                ToolParameter.builder()
                                        .name("path").type("string")
                                        .description("The file path").required(true).build(),
                                ToolParameter.builder()
                                        .name("content").type("string")
                                        .description("The content to write").required(true).build()
                        ))
                        .build()
        );

        String taskDescription = "Implement user authentication";
        String prompt = qaAgentService.buildQAPromptWithTools(taskDescription, null, tools);

        assertTrue(prompt.contains("shell_exec"));
        assertTrue(prompt.contains("Executes a shell command"));
        assertTrue(prompt.contains("file_write"));
        assertTrue(prompt.contains("Implement user authentication"));
        assertTrue(prompt.contains("[QA_PASS]"));
        assertTrue(prompt.contains("[QA_FAIL]"));
        assertTrue(prompt.contains("[DONE]"));
        assertTrue(prompt.contains("tool_call"));
    }

    @Test
    void should_buildQAPromptWithTools_withDiff() {
        String taskDescription = "Fix login bug";
        String diffContent = "diff --git a/Login.java b/Login.java\n-old code\n+new code";
        String prompt = qaAgentService.buildQAPromptWithTools(
                taskDescription, diffContent, Collections.emptyList());

        assertTrue(prompt.contains("Fix login bug"));
        assertTrue(prompt.contains("diff --git a/Login.java b/Login.java"));
        assertTrue(prompt.contains("-old code"));
        assertTrue(prompt.contains("+new code"));
    }

    @Test
    void should_buildQAPromptWithTools_withoutDiff() {
        String prompt = qaAgentService.buildQAPromptWithTools(
                "Test task", null, Collections.emptyList());

        assertTrue(prompt.contains("Test task"));
        assertFalse(prompt.contains("Code Changes (Diff)"));
    }

    // ---------------------------------------------------------------------------
    // runQALoop tests
    // ---------------------------------------------------------------------------

    @Test
    void should_runQALoop_completionSignal() {
        AgentConfigDto config = AgentConfigDto.builder().provider("openai-compatible").build();
        when(providerRegistry.getProvider("openai-compatible")).thenReturn(agentProvider);

        when(agentProvider.chat(anyString(), anyList(), anyString(), any(AgentConfigDto.class)))
                .thenReturn("[DONE] [QA_PASS] All tests pass.");

        when(conversationService.addMessage(any(), anyString(), anyString(), any()))
                .thenReturn(ConversationMessage.builder().id(UUID.randomUUID()).build());

        AgentLoopResult result = qaAgentService.runQALoop(
                conversationId, tenantId, userId, config,
                "System prompt", "Run QA analysis", taskId);

        assertTrue(result.isSuccess());
        assertEquals(1, result.getIterations());
        assertTrue(result.getSummary().contains("QA_PASS"));
    }

    @Test
    void should_runQALoop_maxIterations() {
        AgentConfigDto config = AgentConfigDto.builder().provider("openai-compatible").build();
        when(providerRegistry.getProvider("openai-compatible")).thenReturn(agentProvider);

        // Always return a tool call (never signals done)
        when(agentProvider.chat(anyString(), anyList(), anyString(), any()))
                .thenReturn("<tool_call name=\"shell_exec\">{\"command\": \"mvn test\"}</tool_call>");

        ToolResult testResult = ToolResult.builder()
                .toolName("shell_exec").success(true).output("Tests passed").build();
        when(toolExecutionEngine.executeTools(anyList(), any(ToolExecutionContext.class)))
                .thenReturn(List.of(testResult));

        when(conversationService.addMessage(any(), anyString(), anyString(), any()))
                .thenReturn(ConversationMessage.builder().id(UUID.randomUUID()).build());

        AgentLoopResult result = qaAgentService.runQALoop(
                conversationId, tenantId, userId, config,
                "System prompt", "Run QA analysis", taskId);

        assertFalse(result.isSuccess());
        assertEquals(QAAgentService.MAX_ITERATIONS, result.getIterations());
        assertEquals("Max iterations reached", result.getSummary());
    }

    @Test
    void should_runQALoop_handleLlmCallFailure() {
        AgentConfigDto config = AgentConfigDto.builder().provider("openai-compatible").build();
        when(providerRegistry.getProvider("openai-compatible")).thenReturn(agentProvider);

        when(agentProvider.chat(anyString(), anyList(), anyString(), any()))
                .thenThrow(new RuntimeException("API error"));

        when(conversationService.addMessage(any(), anyString(), anyString(), any()))
                .thenReturn(ConversationMessage.builder().id(UUID.randomUUID()).build());

        AgentLoopResult result = qaAgentService.runQALoop(
                conversationId, tenantId, userId, config,
                "System prompt", "Run QA analysis", taskId);

        assertFalse(result.isSuccess());
        assertEquals(1, result.getIterations());
        assertTrue(result.getSummary().contains("LLM call failed"));
    }

    // ---------------------------------------------------------------------------
    // parseToolCalls tests
    // ---------------------------------------------------------------------------

    @Test
    void should_parseToolCalls_validXml() {
        String response = "Running tests.\n"
                + "<tool_call name=\"shell_exec\">{\"command\": \"mvn test\"}</tool_call>";

        List<ToolCall> toolCalls = qaAgentService.parseToolCalls(response);

        assertEquals(1, toolCalls.size());
        assertEquals("shell_exec", toolCalls.get(0).getToolName());
        assertEquals("mvn test", toolCalls.get(0).getArguments().get("command"));
    }

    @Test
    void should_parseToolCalls_emptyResponse() {
        assertTrue(qaAgentService.parseToolCalls("").isEmpty());
        assertTrue(qaAgentService.parseToolCalls(null).isEmpty());
    }

    // ---------------------------------------------------------------------------
    // isCompletionSignal tests
    // ---------------------------------------------------------------------------

    @Test
    void should_isCompletionSignal_done() {
        assertTrue(qaAgentService.isCompletionSignal("QA analysis complete. [DONE] Pass."));
    }

    @Test
    void should_isCompletionSignal_complete() {
        assertTrue(qaAgentService.isCompletionSignal("[COMPLETE] QA finished."));
    }

    @Test
    void should_isCompletionSignal_null() {
        assertFalse(qaAgentService.isCompletionSignal(null));
    }

    @Test
    void should_isCompletionSignal_noMarker() {
        assertFalse(qaAgentService.isCompletionSignal("Still running tests."));
    }

    // ---------------------------------------------------------------------------
    // extractSummary tests
    // ---------------------------------------------------------------------------

    @Test
    void should_extractSummary_afterDone() {
        String summary = qaAgentService.extractSummary(
                "Analysis complete. [DONE] All 42 tests pass with 85% coverage.");
        assertEquals("All 42 tests pass with 85% coverage.", summary);
    }

    @Test
    void should_extractSummary_handleNull() {
        assertEquals("No summary provided", qaAgentService.extractSummary(null));
    }

    @Test
    void should_extractSummary_handleEmpty() {
        assertEquals("No summary provided", qaAgentService.extractSummary(""));
    }

    @Test
    void should_extractSummary_truncateLongText() {
        String longText = "[DONE] " + "X".repeat(600);
        String summary = qaAgentService.extractSummary(longText);
        assertEquals(500, summary.length());
    }

    // ---------------------------------------------------------------------------
    // formatToolResults tests
    // ---------------------------------------------------------------------------

    @Test
    void should_formatToolResults() {
        List<ToolResult> results = List.of(
                ToolResult.builder()
                        .toolName("shell_exec").success(true)
                        .output("BUILD SUCCESS").build(),
                ToolResult.builder()
                        .toolName("file_read").success(false)
                        .error("File not found").build()
        );

        String formatted = qaAgentService.formatToolResults(results);

        assertTrue(formatted.contains("## Tool: shell_exec"));
        assertTrue(formatted.contains("Status: SUCCESS"));
        assertTrue(formatted.contains("BUILD SUCCESS"));
        assertTrue(formatted.contains("## Tool: file_read"));
        assertTrue(formatted.contains("Status: FAILED"));
        assertTrue(formatted.contains("File not found"));
    }

    @Test
    void should_formatToolResults_emptyList() {
        assertEquals("No tool results.", qaAgentService.formatToolResults(Collections.emptyList()));
    }

    @Test
    void should_formatToolResults_nullList() {
        assertEquals("No tool results.", qaAgentService.formatToolResults(null));
    }

    // ---------------------------------------------------------------------------
    // Helper methods
    // ---------------------------------------------------------------------------

    private TaskStateChangedEvent createQAEvent() {
        TaskStateChangedEvent event = new TaskStateChangedEvent();
        event.setTaskId(taskId);
        event.setTenantId(tenantId);
        event.setTriggeredBy(userId);
        event.setFromState("REVIEW");
        event.setToState("QA");
        return event;
    }

    private Conversation createConversation() {
        return Conversation.builder()
                .id(conversationId)
                .tenantId(tenantId)
                .taskId(taskId)
                .userId(userId)
                .agentType("QA")
                .status("ACTIVE")
                .totalTokens(0L)
                .build();
    }
}
