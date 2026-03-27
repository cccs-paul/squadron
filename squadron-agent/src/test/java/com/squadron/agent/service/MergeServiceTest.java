package com.squadron.agent.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.squadron.agent.tool.builtin.GitClient;
import com.squadron.agent.tool.builtin.ReviewClient;
import com.squadron.common.config.NatsEventPublisher;
import com.squadron.common.event.AgentCompletedEvent;
import com.squadron.common.event.TaskStateChangedEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MergeServiceTest {

    @Mock
    private GitClient gitClient;

    @Mock
    private ReviewClient reviewClient;

    @Mock
    private NatsEventPublisher natsEventPublisher;

    private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
    private MergeService mergeService;

    @BeforeEach
    void setUp() {
        mergeService = new MergeService(gitClient, reviewClient, natsEventPublisher, objectMapper);
    }

    @Test
    void should_mergePR_when_allGatesMet() {
        UUID taskId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        String prId = UUID.randomUUID().toString();

        TaskStateChangedEvent event = new TaskStateChangedEvent();
        event.setTaskId(taskId);
        event.setTenantId(tenantId);
        event.setTriggeredBy(UUID.randomUUID());
        event.setFromState("REVIEW");
        event.setToState("MERGE");

        GitClient.PullRequestResponse prResponse = GitClient.PullRequestResponse.builder()
                .id(prId)
                .prNumber("42")
                .url("https://github.com/owner/repo/pull/42")
                .status("OPEN")
                .build();

        ReviewClient.ReviewGateResponse gateResponse = ReviewClient.ReviewGateResponse.builder()
                .taskId(taskId)
                .totalReviews(2)
                .humanApprovals(1)
                .aiApproval(true)
                .policyMet(true)
                .build();

        GitClient.MergeabilityResponse mergeability = GitClient.MergeabilityResponse.builder()
                .mergeable(true)
                .conflictFiles(List.of())
                .build();

        when(gitClient.getPullRequestByTaskId(taskId)).thenReturn(prResponse);
        when(reviewClient.checkReviewGate(taskId, tenantId, null)).thenReturn(gateResponse);
        when(gitClient.checkMergeability(prId)).thenReturn(mergeability);

        mergeService.executeMerge(event);

        verify(gitClient).mergePullRequest(prId, "MERGE");

        ArgumentCaptor<AgentCompletedEvent> eventCaptor = ArgumentCaptor.forClass(AgentCompletedEvent.class);
        verify(natsEventPublisher).publishAsync(eq("squadron.agent.merge.completed"), eventCaptor.capture());

        AgentCompletedEvent completedEvent = eventCaptor.getValue();
        assertTrue(completedEvent.isSuccess());
        assertEquals("MERGE", completedEvent.getAgentType());
        assertEquals(tenantId, completedEvent.getTenantId());
        assertEquals(taskId, completedEvent.getTaskId());
    }

    @Test
    void should_publishFailure_when_reviewGateNotMet() {
        UUID taskId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        String prId = UUID.randomUUID().toString();

        TaskStateChangedEvent event = new TaskStateChangedEvent();
        event.setTaskId(taskId);
        event.setTenantId(tenantId);
        event.setTriggeredBy(UUID.randomUUID());
        event.setFromState("REVIEW");
        event.setToState("MERGE");

        GitClient.PullRequestResponse prResponse = GitClient.PullRequestResponse.builder()
                .id(prId)
                .prNumber("42")
                .url("https://github.com/owner/repo/pull/42")
                .status("OPEN")
                .build();

        ReviewClient.ReviewGateResponse gateResponse = ReviewClient.ReviewGateResponse.builder()
                .taskId(taskId)
                .totalReviews(0)
                .humanApprovals(0)
                .aiApproval(false)
                .policyMet(false)
                .build();

        when(gitClient.getPullRequestByTaskId(taskId)).thenReturn(prResponse);
        when(reviewClient.checkReviewGate(taskId, tenantId, null)).thenReturn(gateResponse);

        mergeService.executeMerge(event);

        verify(gitClient, never()).mergePullRequest(anyString(), anyString());

        ArgumentCaptor<AgentCompletedEvent> eventCaptor = ArgumentCaptor.forClass(AgentCompletedEvent.class);
        verify(natsEventPublisher).publishAsync(eq("squadron.agent.merge.failed"), eventCaptor.capture());

        AgentCompletedEvent completedEvent = eventCaptor.getValue();
        assertFalse(completedEvent.isSuccess());
        assertEquals("MERGE", completedEvent.getAgentType());
    }

    @Test
    void should_publishFailure_when_prNotMergeable() {
        UUID taskId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        String prId = UUID.randomUUID().toString();

        TaskStateChangedEvent event = new TaskStateChangedEvent();
        event.setTaskId(taskId);
        event.setTenantId(tenantId);
        event.setTriggeredBy(UUID.randomUUID());
        event.setFromState("REVIEW");
        event.setToState("MERGE");

        GitClient.PullRequestResponse prResponse = GitClient.PullRequestResponse.builder()
                .id(prId)
                .prNumber("42")
                .url("https://github.com/owner/repo/pull/42")
                .status("OPEN")
                .build();

        ReviewClient.ReviewGateResponse gateResponse = ReviewClient.ReviewGateResponse.builder()
                .taskId(taskId)
                .totalReviews(2)
                .humanApprovals(1)
                .aiApproval(true)
                .policyMet(true)
                .build();

        GitClient.MergeabilityResponse mergeability = GitClient.MergeabilityResponse.builder()
                .mergeable(false)
                .conflictFiles(List.of("src/Main.java", "src/App.java"))
                .build();

        when(gitClient.getPullRequestByTaskId(taskId)).thenReturn(prResponse);
        when(reviewClient.checkReviewGate(taskId, tenantId, null)).thenReturn(gateResponse);
        when(gitClient.checkMergeability(prId)).thenReturn(mergeability);

        mergeService.executeMerge(event);

        verify(gitClient, never()).mergePullRequest(anyString(), anyString());

        ArgumentCaptor<AgentCompletedEvent> eventCaptor = ArgumentCaptor.forClass(AgentCompletedEvent.class);
        verify(natsEventPublisher).publishAsync(eq("squadron.agent.merge.failed"), eventCaptor.capture());

        AgentCompletedEvent completedEvent = eventCaptor.getValue();
        assertFalse(completedEvent.isSuccess());
        assertEquals("MERGE", completedEvent.getAgentType());
    }

    @Test
    void should_publishFailure_when_mergeThrows() {
        UUID taskId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        String prId = UUID.randomUUID().toString();

        TaskStateChangedEvent event = new TaskStateChangedEvent();
        event.setTaskId(taskId);
        event.setTenantId(tenantId);
        event.setTriggeredBy(UUID.randomUUID());
        event.setFromState("REVIEW");
        event.setToState("MERGE");

        GitClient.PullRequestResponse prResponse = GitClient.PullRequestResponse.builder()
                .id(prId)
                .prNumber("42")
                .url("https://github.com/owner/repo/pull/42")
                .status("OPEN")
                .build();

        ReviewClient.ReviewGateResponse gateResponse = ReviewClient.ReviewGateResponse.builder()
                .taskId(taskId)
                .totalReviews(2)
                .humanApprovals(1)
                .aiApproval(true)
                .policyMet(true)
                .build();

        GitClient.MergeabilityResponse mergeability = GitClient.MergeabilityResponse.builder()
                .mergeable(true)
                .conflictFiles(List.of())
                .build();

        when(gitClient.getPullRequestByTaskId(taskId)).thenReturn(prResponse);
        when(reviewClient.checkReviewGate(taskId, tenantId, null)).thenReturn(gateResponse);
        when(gitClient.checkMergeability(prId)).thenReturn(mergeability);
        doThrow(new GitClient.GitClientException("Merge conflict"))
                .when(gitClient).mergePullRequest(prId, "MERGE");

        mergeService.executeMerge(event);

        ArgumentCaptor<AgentCompletedEvent> eventCaptor = ArgumentCaptor.forClass(AgentCompletedEvent.class);
        verify(natsEventPublisher).publishAsync(eq("squadron.agent.merge.failed"), eventCaptor.capture());

        AgentCompletedEvent completedEvent = eventCaptor.getValue();
        assertFalse(completedEvent.isSuccess());
        assertEquals("MERGE", completedEvent.getAgentType());
    }

    @Test
    void should_publishFailure_when_prNotFound() {
        UUID taskId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();

        TaskStateChangedEvent event = new TaskStateChangedEvent();
        event.setTaskId(taskId);
        event.setTenantId(tenantId);
        event.setTriggeredBy(UUID.randomUUID());
        event.setFromState("REVIEW");
        event.setToState("MERGE");

        when(gitClient.getPullRequestByTaskId(taskId))
                .thenThrow(new GitClient.GitClientException("Failed to get PR for task: 404 NOT_FOUND"));

        mergeService.executeMerge(event);

        verify(gitClient, never()).mergePullRequest(anyString(), anyString());

        ArgumentCaptor<AgentCompletedEvent> eventCaptor = ArgumentCaptor.forClass(AgentCompletedEvent.class);
        verify(natsEventPublisher).publishAsync(eq("squadron.agent.merge.failed"), eventCaptor.capture());

        AgentCompletedEvent completedEvent = eventCaptor.getValue();
        assertFalse(completedEvent.isSuccess());
        assertEquals("MERGE", completedEvent.getAgentType());
    }

    @Test
    void should_handleNullTenantId() {
        UUID taskId = UUID.randomUUID();
        String prId = UUID.randomUUID().toString();

        TaskStateChangedEvent event = new TaskStateChangedEvent();
        event.setTaskId(taskId);
        event.setTenantId(null);
        event.setTriggeredBy(UUID.randomUUID());
        event.setFromState("REVIEW");
        event.setToState("MERGE");

        GitClient.PullRequestResponse prResponse = GitClient.PullRequestResponse.builder()
                .id(prId)
                .prNumber("42")
                .url("https://github.com/owner/repo/pull/42")
                .status("OPEN")
                .build();

        ReviewClient.ReviewGateResponse gateResponse = ReviewClient.ReviewGateResponse.builder()
                .taskId(taskId)
                .policyMet(true)
                .build();

        GitClient.MergeabilityResponse mergeability = GitClient.MergeabilityResponse.builder()
                .mergeable(true)
                .conflictFiles(List.of())
                .build();

        when(gitClient.getPullRequestByTaskId(taskId)).thenReturn(prResponse);
        when(reviewClient.checkReviewGate(taskId, null, null)).thenReturn(gateResponse);
        when(gitClient.checkMergeability(prId)).thenReturn(mergeability);

        // Should not throw even with null tenantId
        assertDoesNotThrow(() -> mergeService.executeMerge(event));
        verify(gitClient).mergePullRequest(prId, "MERGE");
    }

    @Test
    void should_handleNullTriggeredBy() {
        UUID taskId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        String prId = UUID.randomUUID().toString();

        TaskStateChangedEvent event = new TaskStateChangedEvent();
        event.setTaskId(taskId);
        event.setTenantId(tenantId);
        event.setTriggeredBy(null);
        event.setFromState("REVIEW");
        event.setToState("MERGE");

        GitClient.PullRequestResponse prResponse = GitClient.PullRequestResponse.builder()
                .id(prId)
                .prNumber("42")
                .url("https://github.com/owner/repo/pull/42")
                .status("OPEN")
                .build();

        ReviewClient.ReviewGateResponse gateResponse = ReviewClient.ReviewGateResponse.builder()
                .taskId(taskId)
                .policyMet(true)
                .build();

        GitClient.MergeabilityResponse mergeability = GitClient.MergeabilityResponse.builder()
                .mergeable(true)
                .conflictFiles(List.of())
                .build();

        when(gitClient.getPullRequestByTaskId(taskId)).thenReturn(prResponse);
        when(reviewClient.checkReviewGate(taskId, tenantId, null)).thenReturn(gateResponse);
        when(gitClient.checkMergeability(prId)).thenReturn(mergeability);

        // Should not throw even with null triggeredBy
        assertDoesNotThrow(() -> mergeService.executeMerge(event));
        verify(gitClient).mergePullRequest(prId, "MERGE");
    }
}
