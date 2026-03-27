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
import com.squadron.agent.tool.builtin.ExecResultDto;
import com.squadron.agent.tool.builtin.ReviewClient;
import com.squadron.agent.tool.builtin.ReviewClient.ReviewCommentRequest;
import com.squadron.agent.tool.builtin.ReviewClient.ReviewResponse;
import com.squadron.agent.tool.builtin.WorkspaceClient;
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
class ReviewAgentServiceTest {

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
    private ReviewClient reviewClient;

    @Mock
    private WorkspaceClient workspaceClient;

    @Mock
    private AgentProvider agentProvider;

    private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
    private ReviewAgentService reviewAgentService;

    private UUID taskId;
    private UUID tenantId;
    private UUID userId;
    private UUID conversationId;

    @BeforeEach
    void setUp() {
        reviewAgentService = new ReviewAgentService(
                conversationService, configService, providerRegistry, promptBuilder,
                toolRegistry, toolExecutionEngine, natsEventPublisher,
                reviewClient, workspaceClient, objectMapper);

        taskId = UUID.randomUUID();
        tenantId = UUID.randomUUID();
        userId = UUID.randomUUID();
        conversationId = UUID.randomUUID();
    }

    // ---------------------------------------------------------------------------
    // executeReview tests
    // ---------------------------------------------------------------------------

    @Test
    void should_executeReview_successfully() {
        TaskStateChangedEvent event = createReviewEvent();

        UUID reviewId = UUID.randomUUID();
        ReviewResponse reviewResponse = ReviewResponse.builder()
                .id(reviewId).taskId(taskId).status("PENDING").build();
        when(reviewClient.createReview(tenantId, taskId, "AI")).thenReturn(reviewResponse);

        ExecResultDto diffResult = ExecResultDto.builder()
                .exitCode(0).stdout("diff --git a/Foo.java b/Foo.java\n+added line").stderr("").build();
        when(workspaceClient.exec(eq(taskId), eq("bash"), eq("-c"), eq("git diff main...HEAD")))
                .thenReturn(diffResult);

        Conversation conversation = createConversation();
        when(conversationService.startConversation(tenantId, taskId, userId, "REVIEW"))
                .thenReturn(conversation);
        when(configService.resolveAgentConfig(tenantId, null, userId, "REVIEW"))
                .thenReturn(AgentConfigDto.builder().provider("openai-compatible").build());
        when(toolRegistry.getAllToolDefinitions()).thenReturn(Collections.emptyList());
        when(providerRegistry.getProvider("openai-compatible")).thenReturn(agentProvider);

        when(agentProvider.chat(anyString(), anyList(), anyString(), any(AgentConfigDto.class)))
                .thenReturn("[DONE] All looks good");

        when(conversationService.addMessage(any(), anyString(), anyString(), any()))
                .thenReturn(ConversationMessage.builder().id(UUID.randomUUID()).build());
        when(natsEventPublisher.publishAsync(anyString(), any()))
                .thenReturn(CompletableFuture.completedFuture(null));

        reviewAgentService.executeReview(event);

        verify(reviewClient).createReview(tenantId, taskId, "AI");
        verify(reviewClient).submitReview(eq(reviewId), eq("APPROVED"), anyString(), anyList());

        ArgumentCaptor<String> subjectCaptor = ArgumentCaptor.forClass(String.class);
        verify(natsEventPublisher).publishAsync(subjectCaptor.capture(), any(AgentCompletedEvent.class));
        assertEquals("squadron.agent.review.completed", subjectCaptor.getValue());
    }

    @Test
    void should_handleReviewWithCriticalFindings() {
        TaskStateChangedEvent event = createReviewEvent();

        UUID reviewId = UUID.randomUUID();
        ReviewResponse reviewResponse = ReviewResponse.builder()
                .id(reviewId).taskId(taskId).status("PENDING").build();
        when(reviewClient.createReview(tenantId, taskId, "AI")).thenReturn(reviewResponse);

        ExecResultDto diffResult = ExecResultDto.builder()
                .exitCode(0).stdout("diff content").stderr("").build();
        when(workspaceClient.exec(eq(taskId), eq("bash"), eq("-c"), eq("git diff main...HEAD")))
                .thenReturn(diffResult);

        Conversation conversation = createConversation();
        when(conversationService.startConversation(tenantId, taskId, userId, "REVIEW"))
                .thenReturn(conversation);
        when(configService.resolveAgentConfig(tenantId, null, userId, "REVIEW"))
                .thenReturn(AgentConfigDto.builder().build());
        when(toolRegistry.getAllToolDefinitions()).thenReturn(Collections.emptyList());
        when(providerRegistry.getProvider("openai-compatible")).thenReturn(agentProvider);

        String responseWithCritical = "[DONE] Found issues.\n\n"
                + "**Severity:** CRITICAL\n"
                + "**Location:** Foo.java:10\n"
                + "**Category:** bug\n"
                + "**Issue:** Null pointer dereference\n"
                + "**Suggestion:** Add null check";

        when(agentProvider.chat(anyString(), anyList(), anyString(), any(AgentConfigDto.class)))
                .thenReturn(responseWithCritical);
        when(conversationService.addMessage(any(), anyString(), anyString(), any()))
                .thenReturn(ConversationMessage.builder().id(UUID.randomUUID()).build());
        when(natsEventPublisher.publishAsync(anyString(), any()))
                .thenReturn(CompletableFuture.completedFuture(null));

        reviewAgentService.executeReview(event);

        verify(reviewClient).submitReview(eq(reviewId), eq("CHANGES_REQUESTED"), anyString(), anyList());
    }

    @Test
    void should_handleDiffRetrievalFailure() {
        TaskStateChangedEvent event = createReviewEvent();

        UUID reviewId = UUID.randomUUID();
        ReviewResponse reviewResponse = ReviewResponse.builder()
                .id(reviewId).taskId(taskId).status("PENDING").build();
        when(reviewClient.createReview(tenantId, taskId, "AI")).thenReturn(reviewResponse);

        when(workspaceClient.exec(eq(taskId), eq("bash"), eq("-c"), eq("git diff main...HEAD")))
                .thenThrow(new RuntimeException("Workspace unavailable"));

        Conversation conversation = createConversation();
        when(conversationService.startConversation(tenantId, taskId, userId, "REVIEW"))
                .thenReturn(conversation);
        when(configService.resolveAgentConfig(tenantId, null, userId, "REVIEW"))
                .thenReturn(AgentConfigDto.builder().build());
        when(toolRegistry.getAllToolDefinitions()).thenReturn(Collections.emptyList());
        when(providerRegistry.getProvider("openai-compatible")).thenReturn(agentProvider);

        when(agentProvider.chat(anyString(), anyList(), anyString(), any(AgentConfigDto.class)))
                .thenReturn("[DONE] Reviewed with limited context.");
        when(conversationService.addMessage(any(), anyString(), anyString(), any()))
                .thenReturn(ConversationMessage.builder().id(UUID.randomUUID()).build());
        when(natsEventPublisher.publishAsync(anyString(), any()))
                .thenReturn(CompletableFuture.completedFuture(null));

        // Should still complete - diff falls back to "(diff unavailable)"
        assertDoesNotThrow(() -> reviewAgentService.executeReview(event));

        verify(reviewClient).submitReview(eq(reviewId), anyString(), anyString(), anyList());
    }

    @Test
    void should_handleReviewClientFailure() {
        TaskStateChangedEvent event = createReviewEvent();

        when(reviewClient.createReview(tenantId, taskId, "AI"))
                .thenThrow(new ReviewClient.ReviewClientException("Connection refused"));

        when(natsEventPublisher.publishAsync(anyString(), any()))
                .thenReturn(CompletableFuture.completedFuture(null));

        assertDoesNotThrow(() -> reviewAgentService.executeReview(event));

        ArgumentCaptor<String> subjectCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<AgentCompletedEvent> eventCaptor =
                ArgumentCaptor.forClass(AgentCompletedEvent.class);
        verify(natsEventPublisher).publishAsync(subjectCaptor.capture(), eventCaptor.capture());

        assertEquals("squadron.agent.review.failed", subjectCaptor.getValue());
        assertFalse(eventCaptor.getValue().isSuccess());
    }

    // ---------------------------------------------------------------------------
    // parseReviewFindings tests
    // ---------------------------------------------------------------------------

    @Test
    void should_parseReviewFindings_withMultipleFindings() {
        String response = "Here are my findings:\n\n"
                + "**Severity:** CRITICAL\n"
                + "**Location:** Service.java:42\n"
                + "**Category:** bug\n"
                + "**Issue:** Unclosed resource\n"
                + "**Suggestion:** Use try-with-resources\n\n"
                + "**Severity:** MINOR\n"
                + "**Location:** Util.java:10\n"
                + "**Category:** style\n"
                + "**Issue:** Inconsistent naming convention\n"
                + "**Suggestion:** Rename to camelCase\n";

        List<ReviewCommentRequest> findings = reviewAgentService.parseReviewFindings(response);

        assertEquals(2, findings.size());

        ReviewCommentRequest first = findings.get(0);
        assertEquals("Service.java", first.getFilePath());
        assertEquals(42, first.getLineNumber());
        assertEquals("CRITICAL", first.getSeverity());
        assertEquals("bug", first.getCategory());
        assertTrue(first.getBody().contains("Unclosed resource"));
        assertTrue(first.getBody().contains("try-with-resources"));

        ReviewCommentRequest second = findings.get(1);
        assertEquals("Util.java", second.getFilePath());
        assertEquals(10, second.getLineNumber());
        assertEquals("MINOR", second.getSeverity());
    }

    @Test
    void should_parseReviewFindings_withEmptyResponse() {
        List<ReviewCommentRequest> findings = reviewAgentService.parseReviewFindings("");
        assertTrue(findings.isEmpty());
    }

    @Test
    void should_parseReviewFindings_withNullResponse() {
        List<ReviewCommentRequest> findings = reviewAgentService.parseReviewFindings(null);
        assertTrue(findings.isEmpty());
    }

    // ---------------------------------------------------------------------------
    // determineReviewStatus tests
    // ---------------------------------------------------------------------------

    @Test
    void should_determineReviewStatus_approved_whenNoBlockingFindings() {
        List<ReviewCommentRequest> comments = List.of(
                ReviewCommentRequest.builder()
                        .filePath("Foo.java").lineNumber(5).body("Minor thing")
                        .severity("MINOR").category("style").build(),
                ReviewCommentRequest.builder()
                        .filePath("Bar.java").lineNumber(20).body("Consider this")
                        .severity("SUGGESTION").category("design").build()
        );

        String status = reviewAgentService.determineReviewStatus(comments);
        assertEquals("APPROVED", status);
    }

    @Test
    void should_determineReviewStatus_changesRequested_whenCriticalFound() {
        List<ReviewCommentRequest> comments = List.of(
                ReviewCommentRequest.builder()
                        .filePath("Foo.java").lineNumber(10).body("Critical bug")
                        .severity("CRITICAL").category("bug").build()
        );

        String status = reviewAgentService.determineReviewStatus(comments);
        assertEquals("CHANGES_REQUESTED", status);
    }

    @Test
    void should_determineReviewStatus_changesRequested_whenMajorFound() {
        List<ReviewCommentRequest> comments = List.of(
                ReviewCommentRequest.builder()
                        .filePath("Foo.java").lineNumber(15).body("Major issue")
                        .severity("MAJOR").category("security").build()
        );

        String status = reviewAgentService.determineReviewStatus(comments);
        assertEquals("CHANGES_REQUESTED", status);
    }

    @Test
    void should_determineReviewStatus_approved_whenEmptyList() {
        String status = reviewAgentService.determineReviewStatus(Collections.emptyList());
        assertEquals("APPROVED", status);
    }

    @Test
    void should_determineReviewStatus_approved_whenNullList() {
        String status = reviewAgentService.determineReviewStatus(null);
        assertEquals("APPROVED", status);
    }

    // ---------------------------------------------------------------------------
    // buildReviewPromptWithTools tests
    // ---------------------------------------------------------------------------

    @Test
    void should_buildReviewPromptWithTools() {
        List<ToolDefinition> tools = List.of(
                ToolDefinition.builder()
                        .name("file_read")
                        .description("Reads a file from the workspace")
                        .parameters(List.of(
                                ToolParameter.builder()
                                        .name("path").type("string")
                                        .description("The file path to read")
                                        .required(true).build()
                        ))
                        .build(),
                ToolDefinition.builder()
                        .name("shell_exec")
                        .description("Executes a shell command")
                        .parameters(List.of(
                                ToolParameter.builder()
                                        .name("command").type("string")
                                        .description("The command to execute")
                                        .required(true).build()
                        ))
                        .build()
        );

        String diffContent = "diff --git a/App.java b/App.java\n+new code";
        String prompt = reviewAgentService.buildReviewPromptWithTools(diffContent, tools);

        assertTrue(prompt.contains("file_read"));
        assertTrue(prompt.contains("Reads a file from the workspace"));
        assertTrue(prompt.contains("`path` (string) **required**"));
        assertTrue(prompt.contains("shell_exec"));
        assertTrue(prompt.contains("diff --git a/App.java b/App.java"));
        assertTrue(prompt.contains("[DONE]"));
        assertTrue(prompt.contains("tool_call"));
        assertTrue(prompt.contains("CRITICAL|MAJOR|MINOR|SUGGESTION"));
    }

    // ---------------------------------------------------------------------------
    // runReviewLoop tests
    // ---------------------------------------------------------------------------

    @Test
    void should_runReviewLoop_completionSignal() {
        AgentConfigDto config = AgentConfigDto.builder().provider("openai-compatible").build();
        when(providerRegistry.getProvider("openai-compatible")).thenReturn(agentProvider);

        when(agentProvider.chat(anyString(), anyList(), anyString(), any(AgentConfigDto.class)))
                .thenReturn("[DONE] Review complete. No major issues found.");

        when(conversationService.addMessage(any(), anyString(), anyString(), any()))
                .thenReturn(ConversationMessage.builder().id(UUID.randomUUID()).build());

        AgentLoopResult result = reviewAgentService.runReviewLoop(
                conversationId, tenantId, userId, config,
                "System prompt", "Review this diff", taskId);

        assertTrue(result.isSuccess());
        assertEquals(1, result.getIterations());
        assertTrue(result.getSummary().contains("Review complete"));
    }

    @Test
    void should_runReviewLoop_maxIterations() {
        AgentConfigDto config = AgentConfigDto.builder().provider("openai-compatible").build();
        when(providerRegistry.getProvider("openai-compatible")).thenReturn(agentProvider);

        // Always return a tool call (never signals done)
        when(agentProvider.chat(anyString(), anyList(), anyString(), any()))
                .thenReturn("<tool_call name=\"file_read\">{\"path\": \"/workspace/Foo.java\"}</tool_call>");

        ToolResult fileReadResult = ToolResult.builder()
                .toolName("file_read").success(true).output("content").build();
        when(toolExecutionEngine.executeTools(anyList(), any(ToolExecutionContext.class)))
                .thenReturn(List.of(fileReadResult));

        when(conversationService.addMessage(any(), anyString(), anyString(), any()))
                .thenReturn(ConversationMessage.builder().id(UUID.randomUUID()).build());

        AgentLoopResult result = reviewAgentService.runReviewLoop(
                conversationId, tenantId, userId, config,
                "System prompt", "Review this diff", taskId);

        assertFalse(result.isSuccess());
        assertEquals(ReviewAgentService.MAX_ITERATIONS, result.getIterations());
        assertEquals("Max iterations reached", result.getSummary());
    }

    @Test
    void should_runReviewLoop_handleLlmCallFailure() {
        AgentConfigDto config = AgentConfigDto.builder().provider("openai-compatible").build();
        when(providerRegistry.getProvider("openai-compatible")).thenReturn(agentProvider);

        when(agentProvider.chat(anyString(), anyList(), anyString(), any()))
                .thenThrow(new RuntimeException("API rate limit exceeded"));

        when(conversationService.addMessage(any(), anyString(), anyString(), any()))
                .thenReturn(ConversationMessage.builder().id(UUID.randomUUID()).build());

        AgentLoopResult result = reviewAgentService.runReviewLoop(
                conversationId, tenantId, userId, config,
                "System prompt", "Review this diff", taskId);

        assertFalse(result.isSuccess());
        assertEquals(1, result.getIterations());
        assertTrue(result.getSummary().contains("LLM call failed"));
    }

    // ---------------------------------------------------------------------------
    // parseToolCalls tests
    // ---------------------------------------------------------------------------

    @Test
    void should_parseToolCalls_validXml() {
        String response = "Let me check.\n"
                + "<tool_call name=\"file_read\">{\"path\": \"/workspace/Main.java\"}</tool_call>\n"
                + "And also:\n"
                + "<tool_call name=\"shell_exec\">{\"command\": \"mvn test\"}</tool_call>";

        List<ToolCall> toolCalls = reviewAgentService.parseToolCalls(response);

        assertEquals(2, toolCalls.size());
        assertEquals("file_read", toolCalls.get(0).getToolName());
        assertEquals("/workspace/Main.java", toolCalls.get(0).getArguments().get("path"));
        assertEquals("shell_exec", toolCalls.get(1).getToolName());
        assertEquals("mvn test", toolCalls.get(1).getArguments().get("command"));
    }

    @Test
    void should_parseToolCalls_emptyResponse() {
        assertTrue(reviewAgentService.parseToolCalls("").isEmpty());
        assertTrue(reviewAgentService.parseToolCalls(null).isEmpty());
    }

    @Test
    void should_parseToolCalls_invalidJson() {
        String response = "<tool_call name=\"file_read\">not valid json</tool_call>";
        List<ToolCall> toolCalls = reviewAgentService.parseToolCalls(response);
        assertTrue(toolCalls.isEmpty());
    }

    // ---------------------------------------------------------------------------
    // isCompletionSignal tests
    // ---------------------------------------------------------------------------

    @Test
    void should_isCompletionSignal_done() {
        assertTrue(reviewAgentService.isCompletionSignal("Review complete. [DONE] All good."));
    }

    @Test
    void should_isCompletionSignal_complete() {
        assertTrue(reviewAgentService.isCompletionSignal("[COMPLETE] Review finished."));
    }

    @Test
    void should_isCompletionSignal_null() {
        assertFalse(reviewAgentService.isCompletionSignal(null));
    }

    @Test
    void should_isCompletionSignal_noMarker() {
        assertFalse(reviewAgentService.isCompletionSignal("Still working on it."));
    }

    // ---------------------------------------------------------------------------
    // extractSummary tests
    // ---------------------------------------------------------------------------

    @Test
    void should_extractSummary_afterDone() {
        String summary = reviewAgentService.extractSummary(
                "Findings listed above. [DONE] No critical issues found. Code looks good.");
        assertEquals("No critical issues found. Code looks good.", summary);
    }

    @Test
    void should_extractSummary_afterComplete() {
        String summary = reviewAgentService.extractSummary(
                "[COMPLETE] Review finished successfully.");
        assertEquals("Review finished successfully.", summary);
    }

    @Test
    void should_extractSummary_handleNull() {
        assertEquals("No summary provided", reviewAgentService.extractSummary(null));
    }

    @Test
    void should_extractSummary_handleEmpty() {
        assertEquals("No summary provided", reviewAgentService.extractSummary(""));
    }

    @Test
    void should_extractSummary_truncateLongText() {
        String longText = "[DONE] " + "A".repeat(600);
        String summary = reviewAgentService.extractSummary(longText);
        assertEquals(500, summary.length());
    }

    // ---------------------------------------------------------------------------
    // formatToolResults tests
    // ---------------------------------------------------------------------------

    @Test
    void should_formatToolResults() {
        List<ToolResult> results = List.of(
                ToolResult.builder()
                        .toolName("file_read").success(true)
                        .output("public class Main {}").build(),
                ToolResult.builder()
                        .toolName("shell_exec").success(false)
                        .error("Command failed with exit code 1").build()
        );

        String formatted = reviewAgentService.formatToolResults(results);

        assertTrue(formatted.contains("## Tool: file_read"));
        assertTrue(formatted.contains("Status: SUCCESS"));
        assertTrue(formatted.contains("public class Main {}"));
        assertTrue(formatted.contains("## Tool: shell_exec"));
        assertTrue(formatted.contains("Status: FAILED"));
        assertTrue(formatted.contains("Command failed with exit code 1"));
    }

    @Test
    void should_formatToolResults_emptyList() {
        assertEquals("No tool results.", reviewAgentService.formatToolResults(Collections.emptyList()));
    }

    @Test
    void should_formatToolResults_nullList() {
        assertEquals("No tool results.", reviewAgentService.formatToolResults(null));
    }

    // ---------------------------------------------------------------------------
    // Helper methods
    // ---------------------------------------------------------------------------

    private TaskStateChangedEvent createReviewEvent() {
        TaskStateChangedEvent event = new TaskStateChangedEvent();
        event.setTaskId(taskId);
        event.setTenantId(tenantId);
        event.setTriggeredBy(userId);
        event.setFromState("PROPOSE_CODE");
        event.setToState("REVIEW");
        return event;
    }

    private Conversation createConversation() {
        return Conversation.builder()
                .id(conversationId)
                .tenantId(tenantId)
                .taskId(taskId)
                .userId(userId)
                .agentType("REVIEW")
                .status("ACTIVE")
                .totalTokens(0L)
                .build();
    }
}
