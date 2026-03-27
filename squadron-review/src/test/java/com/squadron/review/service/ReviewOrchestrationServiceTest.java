package com.squadron.review.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.squadron.common.config.NatsEventPublisher;
import com.squadron.review.dto.CreateReviewRequest;
import com.squadron.review.dto.ReviewSummary;
import com.squadron.review.entity.Review;
import com.squadron.review.entity.ReviewPolicy;
import com.squadron.review.service.ReviewOrchestrationService.ReviewOrchestrationResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ReviewOrchestrationServiceTest {

    @Mock
    private ReviewService reviewService;

    @Mock
    private ReviewPolicyService reviewPolicyService;

    @Mock
    private ReviewGateService reviewGateService;

    @Mock
    private NatsEventPublisher natsEventPublisher;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private ReviewOrchestrationService reviewOrchestrationService;

    private final UUID tenantId = UUID.randomUUID();
    private final UUID teamId = UUID.randomUUID();
    private final UUID taskId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        reviewOrchestrationService = new ReviewOrchestrationService(
                reviewService, reviewPolicyService, reviewGateService,
                natsEventPublisher, objectMapper);
    }

    @Test
    void should_orchestrateReview_withAiAndHumanReviewers() throws Exception {
        UUID reviewer1 = UUID.randomUUID();
        UUID reviewer2 = UUID.randomUUID();
        String reviewersJson = objectMapper.writeValueAsString(List.of(reviewer1.toString(), reviewer2.toString()));

        ReviewPolicy policy = ReviewPolicy.builder()
                .tenantId(tenantId)
                .teamId(teamId)
                .requireAiReview(true)
                .minHumanApprovals(1)
                .autoRequestReviewers(reviewersJson)
                .build();

        when(reviewPolicyService.resolvePolicy(tenantId, teamId)).thenReturn(policy);
        when(reviewService.createReview(any(CreateReviewRequest.class))).thenAnswer(invocation -> {
            CreateReviewRequest req = invocation.getArgument(0);
            return Review.builder()
                    .id(UUID.randomUUID())
                    .tenantId(req.getTenantId())
                    .taskId(req.getTaskId())
                    .reviewerType(req.getReviewerType())
                    .reviewerId(req.getReviewerId())
                    .status("PENDING")
                    .build();
        });
        when(reviewGateService.checkReviewGate(taskId, tenantId, teamId))
                .thenReturn(ReviewSummary.builder().taskId(taskId).policyMet(false).totalReviews(3).build());

        ReviewOrchestrationResult result = reviewOrchestrationService.orchestrateReview(taskId, tenantId, teamId);

        assertNotNull(result);
        assertTrue(result.isAiReviewCreated());
        assertEquals(3, result.getCreatedReviewIds().size());
        verify(reviewService, times(3)).createReview(any(CreateReviewRequest.class));

        // Verify AI review was created
        ArgumentCaptor<CreateReviewRequest> captor = ArgumentCaptor.forClass(CreateReviewRequest.class);
        verify(reviewService, times(3)).createReview(captor.capture());
        List<CreateReviewRequest> requests = captor.getAllValues();
        assertEquals("AI", requests.get(0).getReviewerType());
        assertEquals("HUMAN", requests.get(1).getReviewerType());
        assertEquals("HUMAN", requests.get(2).getReviewerType());
    }

    @Test
    void should_orchestrateReview_withAiOnly() {
        ReviewPolicy policy = ReviewPolicy.builder()
                .tenantId(tenantId)
                .teamId(teamId)
                .requireAiReview(true)
                .minHumanApprovals(0)
                .autoRequestReviewers(null)
                .build();

        when(reviewPolicyService.resolvePolicy(tenantId, teamId)).thenReturn(policy);
        when(reviewService.createReview(any(CreateReviewRequest.class))).thenReturn(
                Review.builder()
                        .id(UUID.randomUUID())
                        .tenantId(tenantId)
                        .taskId(taskId)
                        .reviewerType("AI")
                        .status("PENDING")
                        .build());
        when(reviewGateService.checkReviewGate(taskId, tenantId, teamId))
                .thenReturn(ReviewSummary.builder().taskId(taskId).policyMet(false).totalReviews(1).build());

        ReviewOrchestrationResult result = reviewOrchestrationService.orchestrateReview(taskId, tenantId, teamId);

        assertNotNull(result);
        assertTrue(result.isAiReviewCreated());
        assertEquals(1, result.getCreatedReviewIds().size());
        assertEquals(0, result.getPendingHumanReviews());
        verify(reviewService, times(1)).createReview(any(CreateReviewRequest.class));
    }

    @Test
    void should_orchestrateReview_withNoAiReview() {
        ReviewPolicy policy = ReviewPolicy.builder()
                .tenantId(tenantId)
                .teamId(teamId)
                .requireAiReview(false)
                .minHumanApprovals(2)
                .autoRequestReviewers(null)
                .build();

        when(reviewPolicyService.resolvePolicy(tenantId, teamId)).thenReturn(policy);
        when(reviewGateService.checkReviewGate(taskId, tenantId, teamId))
                .thenReturn(ReviewSummary.builder().taskId(taskId).policyMet(false).totalReviews(0).build());

        ReviewOrchestrationResult result = reviewOrchestrationService.orchestrateReview(taskId, tenantId, teamId);

        assertNotNull(result);
        assertFalse(result.isAiReviewCreated());
        assertEquals(0, result.getCreatedReviewIds().size());
        assertEquals(2, result.getPendingHumanReviews());

        // Verify createReview was NOT called for AI
        ArgumentCaptor<CreateReviewRequest> captor = ArgumentCaptor.forClass(CreateReviewRequest.class);
        verify(reviewService, never()).createReview(captor.capture());
    }

    @Test
    void should_orchestrateReview_withAutoAssignReviewers() throws Exception {
        UUID reviewerId = UUID.randomUUID();
        String reviewersJson = objectMapper.writeValueAsString(List.of(reviewerId.toString()));

        ReviewPolicy policy = ReviewPolicy.builder()
                .tenantId(tenantId)
                .teamId(teamId)
                .requireAiReview(false)
                .minHumanApprovals(1)
                .autoRequestReviewers(reviewersJson)
                .build();

        when(reviewPolicyService.resolvePolicy(tenantId, teamId)).thenReturn(policy);
        when(reviewService.createReview(any(CreateReviewRequest.class))).thenReturn(
                Review.builder()
                        .id(UUID.randomUUID())
                        .tenantId(tenantId)
                        .taskId(taskId)
                        .reviewerId(reviewerId)
                        .reviewerType("HUMAN")
                        .status("PENDING")
                        .build());
        when(reviewGateService.checkReviewGate(taskId, tenantId, teamId))
                .thenReturn(ReviewSummary.builder().taskId(taskId).policyMet(false).totalReviews(1).build());

        ReviewOrchestrationResult result = reviewOrchestrationService.orchestrateReview(taskId, tenantId, teamId);

        assertNotNull(result);
        assertFalse(result.isAiReviewCreated());
        assertEquals(1, result.getCreatedReviewIds().size());
        verify(reviewService, times(1)).createReview(any(CreateReviewRequest.class));

        ArgumentCaptor<CreateReviewRequest> captor = ArgumentCaptor.forClass(CreateReviewRequest.class);
        verify(reviewService).createReview(captor.capture());
        assertEquals(reviewerId, captor.getValue().getReviewerId());
        assertEquals("HUMAN", captor.getValue().getReviewerType());
    }

    @Test
    void should_checkAndTransition_returnsTrueWhenPolicyMet() {
        ReviewSummary summary = ReviewSummary.builder()
                .taskId(taskId)
                .policyMet(true)
                .totalReviews(2)
                .build();

        when(reviewGateService.checkReviewGate(taskId, tenantId, teamId)).thenReturn(summary);
        doNothing().when(natsEventPublisher).publish(anyString(), any());

        boolean result = reviewOrchestrationService.checkAndTransition(taskId, tenantId, teamId);

        assertTrue(result);
        verify(natsEventPublisher).publish(eq("squadron.review.gate.passed"), any());
    }

    @Test
    void should_checkAndTransition_returnsFalseWhenPolicyNotMet() {
        ReviewSummary summary = ReviewSummary.builder()
                .taskId(taskId)
                .policyMet(false)
                .totalReviews(1)
                .build();

        when(reviewGateService.checkReviewGate(taskId, tenantId, teamId)).thenReturn(summary);

        boolean result = reviewOrchestrationService.checkAndTransition(taskId, tenantId, teamId);

        assertFalse(result);
        verify(natsEventPublisher, never()).publish(anyString(), any());
    }

    @Test
    void should_autoAssignReviewers_parsesJson() throws Exception {
        UUID reviewer1 = UUID.randomUUID();
        UUID reviewer2 = UUID.randomUUID();
        String reviewersJson = objectMapper.writeValueAsString(List.of(reviewer1.toString(), reviewer2.toString()));

        ReviewPolicy policy = ReviewPolicy.builder()
                .tenantId(tenantId)
                .teamId(teamId)
                .autoRequestReviewers(reviewersJson)
                .build();

        when(reviewPolicyService.resolvePolicy(tenantId, teamId)).thenReturn(policy);

        List<UUID> reviewers = reviewOrchestrationService.autoAssignReviewers(taskId, tenantId, teamId);

        assertNotNull(reviewers);
        assertEquals(2, reviewers.size());
        assertEquals(reviewer1, reviewers.get(0));
        assertEquals(reviewer2, reviewers.get(1));
    }

    @Test
    void should_autoAssignReviewers_returnsEmptyOnNull() {
        ReviewPolicy policy = ReviewPolicy.builder()
                .tenantId(tenantId)
                .teamId(teamId)
                .autoRequestReviewers(null)
                .build();

        when(reviewPolicyService.resolvePolicy(tenantId, teamId)).thenReturn(policy);

        List<UUID> reviewers = reviewOrchestrationService.autoAssignReviewers(taskId, tenantId, teamId);

        assertNotNull(reviewers);
        assertTrue(reviewers.isEmpty());
    }

    @Test
    void should_autoAssignReviewers_returnsEmptyOnInvalidJson() {
        ReviewPolicy policy = ReviewPolicy.builder()
                .tenantId(tenantId)
                .teamId(teamId)
                .autoRequestReviewers("not valid json")
                .build();

        when(reviewPolicyService.resolvePolicy(tenantId, teamId)).thenReturn(policy);

        List<UUID> reviewers = reviewOrchestrationService.autoAssignReviewers(taskId, tenantId, teamId);

        assertNotNull(reviewers);
        assertTrue(reviewers.isEmpty());
    }

    @Test
    void should_orchestrateReview_policyMet_checksSummary() {
        ReviewPolicy policy = ReviewPolicy.builder()
                .tenantId(tenantId)
                .teamId(teamId)
                .requireAiReview(false)
                .minHumanApprovals(0)
                .autoRequestReviewers(null)
                .build();

        when(reviewPolicyService.resolvePolicy(tenantId, teamId)).thenReturn(policy);
        when(reviewGateService.checkReviewGate(taskId, tenantId, teamId))
                .thenReturn(ReviewSummary.builder().taskId(taskId).policyMet(false).totalReviews(0).build());

        ReviewOrchestrationResult result = reviewOrchestrationService.orchestrateReview(taskId, tenantId, teamId);

        assertNotNull(result);
        assertFalse(result.isPolicyMet());
        verify(reviewGateService).checkReviewGate(taskId, tenantId, teamId);
    }
}
