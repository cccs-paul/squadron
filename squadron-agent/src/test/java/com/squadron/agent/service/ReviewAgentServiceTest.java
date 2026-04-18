package com.squadron.agent.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.squadron.agent.dto.AgentConfigDto;
import com.squadron.agent.entity.Conversation;
import com.squadron.agent.entity.ConversationMessage;
import com.squadron.agent.provider.AgentProvider;
import com.squadron.agent.provider.AgentProviderRegistry;
import com.squadron.agent.tool.ToolDefinition;
import com.squadron.agent.tool.ToolExecutionEngine;
import com.squadron.agent.tool.ToolParameter;
import com.squadron.agent.tool.ToolRegistry;
import com.squadron.agent.tool.builtin.ExecResultDto;
import com.squadron.agent.tool.builtin.GitClient;
import com.squadron.agent.tool.builtin.ReviewBotClient;
import com.squadron.agent.tool.builtin.ReviewClient;
import com.squadron.agent.tool.builtin.ReviewClient.ReviewCommentRequest;
import com.squadron.agent.tool.builtin.ReviewClient.ReviewResponse;
import com.squadron.agent.tool.builtin.WorkspaceClient;
import com.squadron.common.config.NatsEventPublisher;
import com.squadron.common.dto.TaskContext;
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
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ReviewAgentServiceTest {

    @Mock private ConversationService conversationService;
    @Mock private SquadronConfigService configService;
    @Mock private AgentProviderRegistry providerRegistry;
    @Mock private SystemPromptBuilder promptBuilder;
    @Mock private ToolRegistry toolRegistry;
    @Mock private ToolExecutionEngine toolExecutionEngine;
    @Mock private NatsEventPublisher natsEventPublisher;
    @Mock private ReviewClient reviewClient;
    @Mock private ReviewBotClient reviewBotClient;
    @Mock private GitClient gitClient;
    @Mock private WorkspaceClient workspaceClient;
    @Mock private WorkspaceLifecycleService workspaceLifecycleService;
    @Mock private AgentProvider agentProvider;

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
                reviewClient, reviewBotClient, gitClient,
                workspaceClient, objectMapper, workspaceLifecycleService);

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
        when(conversationService.startConversation(tenantId, taskId, userId, "REVIEW")).thenReturn(conversation);
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
        verify(natsEventPublisher, times(2)).publishAsync(subjectCaptor.capture(), any(AgentCompletedEvent.class));
        assertEquals("squadron.agent.review.completed", subjectCaptor.getAllValues().get(0));
        assertEquals("squadron.agents.completed", subjectCaptor.getAllValues().get(1));
    }

    @Test
    void should_handleReviewWithCriticalFindings() {
        TaskStateChangedEvent event = createReviewEvent();

        UUID reviewId = UUID.randomUUID();
        when(reviewClient.createReview(tenantId, taskId, "AI"))
                .thenReturn(ReviewResponse.builder().id(reviewId).taskId(taskId).status("PENDING").build());
        when(workspaceClient.exec(eq(taskId), eq("bash"), eq("-c"), eq("git diff main...HEAD")))
                .thenReturn(ExecResultDto.builder().exitCode(0).stdout("diff content").stderr("").build());
        when(conversationService.startConversation(tenantId, taskId, userId, "REVIEW")).thenReturn(createConversation());
        when(configService.resolveAgentConfig(tenantId, null, userId, "REVIEW"))
                .thenReturn(AgentConfigDto.builder().build());
        when(toolRegistry.getAllToolDefinitions()).thenReturn(Collections.emptyList());
        when(providerRegistry.getProvider("openai-compatible")).thenReturn(agentProvider);

        String responseWithCritical = "[DONE] Found issues.\n\n"
                + "**Severity:** CRITICAL\n**Location:** Foo.java:10\n"
                + "**Category:** bug\n**Issue:** Null pointer dereference\n"
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
        when(reviewClient.createReview(tenantId, taskId, "AI"))
                .thenReturn(ReviewResponse.builder().id(reviewId).taskId(taskId).status("PENDING").build());
        when(workspaceClient.exec(eq(taskId), eq("bash"), eq("-c"), eq("git diff main...HEAD")))
                .thenThrow(new RuntimeException("Workspace unavailable"));
        when(conversationService.startConversation(tenantId, taskId, userId, "REVIEW")).thenReturn(createConversation());
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
        verify(natsEventPublisher, times(2)).publishAsync(subjectCaptor.capture(), any(AgentCompletedEvent.class));
        assertEquals("squadron.agent.review.failed", subjectCaptor.getAllValues().get(0));
    }

    // ---------------------------------------------------------------------------
    // parseReviewFindings tests
    // ---------------------------------------------------------------------------

    @Test
    void should_parseReviewFindings_withMultipleFindings() {
        String response = "Here are my findings:\n\n"
                + "**Severity:** CRITICAL\n**Location:** Service.java:42\n"
                + "**Category:** bug\n**Issue:** Unclosed resource\n"
                + "**Suggestion:** Use try-with-resources\n\n"
                + "**Severity:** MINOR\n**Location:** Util.java:10\n"
                + "**Category:** style\n**Issue:** Inconsistent naming convention\n"
                + "**Suggestion:** Rename to camelCase\n";

        List<ReviewCommentRequest> findings = reviewAgentService.parseReviewFindings(response);

        assertEquals(2, findings.size());
        assertEquals("Service.java", findings.get(0).getFilePath());
        assertEquals(42, findings.get(0).getLineNumber());
        assertEquals("CRITICAL", findings.get(0).getSeverity());
        assertEquals("Util.java", findings.get(1).getFilePath());
        assertEquals("MINOR", findings.get(1).getSeverity());
    }

    @Test
    void should_parseReviewFindings_withEmptyResponse() {
        assertTrue(reviewAgentService.parseReviewFindings("").isEmpty());
    }

    @Test
    void should_parseReviewFindings_withNullResponse() {
        assertTrue(reviewAgentService.parseReviewFindings(null).isEmpty());
    }

    // ---------------------------------------------------------------------------
    // determineReviewStatus tests
    // ---------------------------------------------------------------------------

    @Test
    void should_determineReviewStatus_approved_whenNoBlockingFindings() {
        List<ReviewCommentRequest> comments = List.of(
                ReviewCommentRequest.builder().filePath("Foo.java").lineNumber(5)
                        .body("Minor thing").severity("MINOR").category("style").build(),
                ReviewCommentRequest.builder().filePath("Bar.java").lineNumber(20)
                        .body("Consider this").severity("SUGGESTION").category("design").build()
        );
        assertEquals("APPROVED", reviewAgentService.determineReviewStatus(comments));
    }

    @Test
    void should_determineReviewStatus_changesRequested_whenCriticalFound() {
        List<ReviewCommentRequest> comments = List.of(
                ReviewCommentRequest.builder().filePath("Foo.java").lineNumber(10)
                        .body("Critical bug").severity("CRITICAL").category("bug").build()
        );
        assertEquals("CHANGES_REQUESTED", reviewAgentService.determineReviewStatus(comments));
    }

    @Test
    void should_determineReviewStatus_changesRequested_whenMajorFound() {
        List<ReviewCommentRequest> comments = List.of(
                ReviewCommentRequest.builder().filePath("Foo.java").lineNumber(15)
                        .body("Major issue").severity("MAJOR").category("security").build()
        );
        assertEquals("CHANGES_REQUESTED", reviewAgentService.determineReviewStatus(comments));
    }

    @Test
    void should_determineReviewStatus_approved_whenEmptyList() {
        assertEquals("APPROVED", reviewAgentService.determineReviewStatus(Collections.emptyList()));
    }

    @Test
    void should_determineReviewStatus_approved_whenNullList() {
        assertEquals("APPROVED", reviewAgentService.determineReviewStatus(null));
    }

    // ---------------------------------------------------------------------------
    // buildReviewPromptWithTools tests
    // ---------------------------------------------------------------------------

    @Test
    void should_buildReviewPromptWithTools() {
        List<ToolDefinition> tools = List.of(
                ToolDefinition.builder().name("file_read").description("Reads a file from the workspace")
                        .parameters(List.of(ToolParameter.builder().name("path").type("string")
                                .description("The file path to read").required(true).build())).build()
        );

        String prompt = reviewAgentService.buildReviewPromptWithTools("diff --git a/App.java b/App.java\n+new code", tools);

        assertTrue(prompt.contains("file_read"));
        assertTrue(prompt.contains("`path` (string) **required**"));
        assertTrue(prompt.contains("diff --git a/App.java b/App.java"));
        assertTrue(prompt.contains("[DONE]"));
        assertTrue(prompt.contains("CRITICAL|MAJOR|MINOR|SUGGESTION"));
    }

    // ---------------------------------------------------------------------------
    // retrieveDiff tests
    // ---------------------------------------------------------------------------

    @Test
    void should_retrieveDiff_successfully() {
        ExecResultDto diffResult = ExecResultDto.builder()
                .exitCode(0).stdout("diff content here").stderr("").build();
        when(workspaceClient.exec(eq(taskId), eq("bash"), eq("-c"), eq("git diff main...HEAD")))
                .thenReturn(diffResult);

        String diff = reviewAgentService.retrieveDiff(taskId);
        assertEquals("diff content here", diff);
    }

    @Test
    void should_retrieveDiff_fallbackToHead1() {
        when(workspaceClient.exec(eq(taskId), eq("bash"), eq("-c"), eq("git diff main...HEAD")))
                .thenReturn(ExecResultDto.builder().exitCode(1).stdout("").stderr("error").build());
        when(workspaceClient.exec(eq(taskId), eq("bash"), eq("-c"), eq("git diff HEAD~1")))
                .thenReturn(ExecResultDto.builder().exitCode(0).stdout("fallback diff").stderr("").build());

        String diff = reviewAgentService.retrieveDiff(taskId);
        assertEquals("fallback diff", diff);
    }

    @Test
    void should_retrieveDiff_handleException() {
        when(workspaceClient.exec(eq(taskId), eq("bash"), eq("-c"), eq("git diff main...HEAD")))
                .thenThrow(new RuntimeException("Workspace unavailable"));

        String diff = reviewAgentService.retrieveDiff(taskId);
        assertEquals("(diff unavailable)", diff);
    }

    // ---------------------------------------------------------------------------
    // postReviewBotComments tests
    // ---------------------------------------------------------------------------

    @Test
    void should_postReviewBotComment_when_botConfigured() {
        UUID connectionId = UUID.randomUUID();
        UUID botConfigId = UUID.randomUUID();
        String prRecordId = UUID.randomUUID().toString();

        TaskStateChangedEvent event = createReviewEventWithContext(connectionId);

        ReviewBotClient.BotConfig botConfig = ReviewBotClient.BotConfig.builder()
                .id(botConfigId).tenantId(tenantId).connectionId(connectionId)
                .botUsername("squadron-bot").enabled(true).autoAssign(false).build();
        when(reviewBotClient.getEnabledBotConfig(tenantId, connectionId)).thenReturn(Optional.of(botConfig));
        when(reviewBotClient.getBotAccessToken(botConfigId)).thenReturn("bot-token-123");
        when(gitClient.getPullRequestByTaskId(taskId))
                .thenReturn(GitClient.PullRequestResponse.builder()
                        .id(prRecordId).prNumber("42").url("https://github.com/owner/repo/pull/42").status("OPEN").build());

        List<ReviewCommentRequest> comments = List.of(
                ReviewCommentRequest.builder().filePath("Foo.java").lineNumber(10)
                        .body("Issue found").severity("MAJOR").category("bug").build()
        );

        reviewAgentService.postReviewBotComments(event, taskId, tenantId, "CHANGES_REQUESTED", comments, "Summary");

        verify(gitClient).addPrReviewComment(eq(prRecordId), anyString(), eq("bot-token-123"));
        verify(gitClient, never()).requestPrReviewers(any(), any(), any());
    }

    @Test
    void should_autoAssignBotReviewer_when_autoAssignEnabled() {
        UUID connectionId = UUID.randomUUID();
        UUID botConfigId = UUID.randomUUID();
        String prRecordId = UUID.randomUUID().toString();

        TaskStateChangedEvent event = createReviewEventWithContext(connectionId);

        ReviewBotClient.BotConfig botConfig = ReviewBotClient.BotConfig.builder()
                .id(botConfigId).tenantId(tenantId).connectionId(connectionId)
                .botUsername("squadron-bot").enabled(true).autoAssign(true).build();
        when(reviewBotClient.getEnabledBotConfig(tenantId, connectionId)).thenReturn(Optional.of(botConfig));
        when(reviewBotClient.getBotAccessToken(botConfigId)).thenReturn("bot-token");
        when(gitClient.getPullRequestByTaskId(taskId))
                .thenReturn(GitClient.PullRequestResponse.builder()
                        .id(prRecordId).prNumber("42").url("https://github.com/owner/repo/pull/42").status("OPEN").build());

        reviewAgentService.postReviewBotComments(event, taskId, tenantId, "APPROVED", Collections.emptyList(), "All good");

        verify(gitClient).addPrReviewComment(eq(prRecordId), anyString(), eq("bot-token"));
        verify(gitClient).requestPrReviewers(prRecordId, List.of("squadron-bot"), "bot-token");
    }

    @Test
    void should_skipBotComment_when_noBotConfigured() {
        UUID connectionId = UUID.randomUUID();
        TaskStateChangedEvent event = createReviewEventWithContext(connectionId);
        when(reviewBotClient.getEnabledBotConfig(tenantId, connectionId)).thenReturn(Optional.empty());

        reviewAgentService.postReviewBotComments(event, taskId, tenantId, "APPROVED", Collections.emptyList(), "Summary");

        verify(gitClient, never()).addPrReviewComment(any(), any(), any());
    }

    @Test
    void should_skipBotComment_when_noConnectionId() {
        TaskStateChangedEvent event = createReviewEvent();
        reviewAgentService.postReviewBotComments(event, taskId, tenantId, "APPROVED", Collections.emptyList(), "Summary");
        verify(reviewBotClient, never()).getEnabledBotConfig(any(), any());
    }

    @Test
    void should_handleBotTokenFetchFailure_gracefully() {
        UUID connectionId = UUID.randomUUID();
        UUID botConfigId = UUID.randomUUID();
        TaskStateChangedEvent event = createReviewEventWithContext(connectionId);

        ReviewBotClient.BotConfig botConfig = ReviewBotClient.BotConfig.builder()
                .id(botConfigId).tenantId(tenantId).connectionId(connectionId)
                .botUsername("bot").enabled(true).autoAssign(false).build();
        when(reviewBotClient.getEnabledBotConfig(tenantId, connectionId)).thenReturn(Optional.of(botConfig));
        when(reviewBotClient.getBotAccessToken(botConfigId))
                .thenThrow(new ReviewBotClient.ReviewBotClientException("Token fetch failed"));

        assertDoesNotThrow(() -> reviewAgentService.postReviewBotComments(
                event, taskId, tenantId, "APPROVED", Collections.emptyList(), "Summary"));
        verify(gitClient, never()).addPrReviewComment(any(), any(), any());
    }

    @Test
    void should_handleNoPrFound_gracefully() {
        UUID connectionId = UUID.randomUUID();
        UUID botConfigId = UUID.randomUUID();
        TaskStateChangedEvent event = createReviewEventWithContext(connectionId);

        ReviewBotClient.BotConfig botConfig = ReviewBotClient.BotConfig.builder()
                .id(botConfigId).tenantId(tenantId).connectionId(connectionId)
                .botUsername("bot").enabled(true).autoAssign(false).build();
        when(reviewBotClient.getEnabledBotConfig(tenantId, connectionId)).thenReturn(Optional.of(botConfig));
        when(reviewBotClient.getBotAccessToken(botConfigId)).thenReturn("token");
        when(gitClient.getPullRequestByTaskId(taskId))
                .thenThrow(new GitClient.GitClientException("Not found"));

        assertDoesNotThrow(() -> reviewAgentService.postReviewBotComments(
                event, taskId, tenantId, "APPROVED", Collections.emptyList(), "Summary"));
    }

    // ---------------------------------------------------------------------------
    // formatBotReviewComment tests
    // ---------------------------------------------------------------------------

    @Test
    void should_formatBotReviewComment_withFindings() {
        List<ReviewCommentRequest> comments = List.of(
                ReviewCommentRequest.builder().filePath("Foo.java").lineNumber(10)
                        .body("Null pointer risk").severity("CRITICAL").category("bug").build(),
                ReviewCommentRequest.builder().filePath("Bar.java").lineNumber(null)
                        .body("Style issue").severity("MINOR").category("style").build()
        );

        String body = reviewAgentService.formatBotReviewComment("CHANGES_REQUESTED", comments, "Found issues");

        assertTrue(body.contains("Squadron AI Review"));
        assertTrue(body.contains("CHANGES_REQUESTED"));
        assertTrue(body.contains("Findings (2)"));
        assertTrue(body.contains("**CRITICAL** `Foo.java:10`"));
        assertTrue(body.contains("**MINOR** `Bar.java`"));
        assertTrue(body.contains("Found issues"));
    }

    @Test
    void should_formatBotReviewComment_withNoFindings() {
        String body = reviewAgentService.formatBotReviewComment("APPROVED", Collections.emptyList(), "All good");
        assertTrue(body.contains("APPROVED"));
        assertFalse(body.contains("Findings"));
        assertTrue(body.contains("All good"));
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
                .id(conversationId).tenantId(tenantId).taskId(taskId).userId(userId)
                .agentType("REVIEW").status("ACTIVE").totalTokens(0L).build();
    }

    private TaskStateChangedEvent createReviewEventWithContext(UUID connectionId) {
        TaskStateChangedEvent event = createReviewEvent();
        TaskContext context = TaskContext.builder()
                .taskId(taskId).tenantId(tenantId).userId(userId).connectionId(connectionId).build();
        event.setTaskContext(context);
        return event;
    }
}
