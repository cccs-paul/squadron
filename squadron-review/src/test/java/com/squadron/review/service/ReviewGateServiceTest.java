package com.squadron.review.service;

import com.squadron.review.dto.ReviewSummary;
import com.squadron.review.entity.Review;
import com.squadron.review.entity.ReviewPolicy;
import com.squadron.review.repository.ReviewCommentRepository;
import com.squadron.review.repository.ReviewRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ReviewGateServiceTest {

    @Mock
    private ReviewRepository reviewRepository;

    @Mock
    private ReviewCommentRepository reviewCommentRepository;

    @Mock
    private ReviewPolicyService reviewPolicyService;

    private ReviewGateService reviewGateService;

    @BeforeEach
    void setUp() {
        reviewGateService = new ReviewGateService(reviewRepository, reviewCommentRepository, reviewPolicyService);
    }

    @Test
    void should_returnPolicyMet_when_allRequirementsSatisfied() {
        UUID taskId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        UUID teamId = UUID.randomUUID();

        ReviewPolicy policy = ReviewPolicy.builder()
                .tenantId(tenantId)
                .teamId(teamId)
                .minHumanApprovals(1)
                .requireAiReview(true)
                .selfReviewAllowed(true)
                .build();

        Instant now = Instant.now();
        Review humanReview = Review.builder()
                .id(UUID.randomUUID())
                .tenantId(tenantId)
                .taskId(taskId)
                .reviewerType("HUMAN")
                .status("APPROVED")
                .createdAt(now)
                .updatedAt(now)
                .build();

        Review aiReview = Review.builder()
                .id(UUID.randomUUID())
                .tenantId(tenantId)
                .taskId(taskId)
                .reviewerType("AI")
                .status("APPROVED")
                .createdAt(now)
                .updatedAt(now)
                .build();

        when(reviewPolicyService.resolvePolicy(tenantId, teamId)).thenReturn(policy);
        when(reviewRepository.findByTaskId(taskId)).thenReturn(List.of(humanReview, aiReview));
        when(reviewRepository.countByTaskIdAndReviewerTypeAndStatus(taskId, "HUMAN", "APPROVED")).thenReturn(1L);
        when(reviewRepository.countByTaskIdAndReviewerTypeAndStatus(taskId, "AI", "APPROVED")).thenReturn(1L);
        when(reviewCommentRepository.findByReviewId(any())).thenReturn(Collections.emptyList());

        ReviewSummary summary = reviewGateService.checkReviewGate(taskId, tenantId, teamId);

        assertTrue(summary.isPolicyMet());
        assertEquals(2, summary.getTotalReviews());
        assertEquals(1, summary.getHumanApprovals());
        assertTrue(summary.isAiApproval());
    }

    @Test
    void should_returnPolicyNotMet_when_insufficientHumanApprovals() {
        UUID taskId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        UUID teamId = UUID.randomUUID();

        ReviewPolicy policy = ReviewPolicy.builder()
                .tenantId(tenantId)
                .minHumanApprovals(2)
                .requireAiReview(false)
                .selfReviewAllowed(true)
                .build();

        Instant now2 = Instant.now();
        Review humanReview = Review.builder()
                .id(UUID.randomUUID())
                .tenantId(tenantId)
                .taskId(taskId)
                .reviewerType("HUMAN")
                .status("APPROVED")
                .createdAt(now2)
                .updatedAt(now2)
                .build();

        when(reviewPolicyService.resolvePolicy(tenantId, teamId)).thenReturn(policy);
        when(reviewRepository.findByTaskId(taskId)).thenReturn(List.of(humanReview));
        when(reviewRepository.countByTaskIdAndReviewerTypeAndStatus(taskId, "HUMAN", "APPROVED")).thenReturn(1L);
        when(reviewRepository.countByTaskIdAndReviewerTypeAndStatus(taskId, "AI", "APPROVED")).thenReturn(0L);
        when(reviewCommentRepository.findByReviewId(any())).thenReturn(Collections.emptyList());

        ReviewSummary summary = reviewGateService.checkReviewGate(taskId, tenantId, teamId);

        assertFalse(summary.isPolicyMet());
        assertEquals(1, summary.getHumanApprovals());
    }

    @Test
    void should_returnPolicyNotMet_when_aiReviewRequired_butMissing() {
        UUID taskId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        UUID teamId = UUID.randomUUID();

        ReviewPolicy policy = ReviewPolicy.builder()
                .tenantId(tenantId)
                .minHumanApprovals(1)
                .requireAiReview(true)
                .selfReviewAllowed(true)
                .build();

        Instant now3 = Instant.now();
        Review humanReview = Review.builder()
                .id(UUID.randomUUID())
                .tenantId(tenantId)
                .taskId(taskId)
                .reviewerType("HUMAN")
                .status("APPROVED")
                .createdAt(now3)
                .updatedAt(now3)
                .build();

        when(reviewPolicyService.resolvePolicy(tenantId, teamId)).thenReturn(policy);
        when(reviewRepository.findByTaskId(taskId)).thenReturn(List.of(humanReview));
        when(reviewRepository.countByTaskIdAndReviewerTypeAndStatus(taskId, "HUMAN", "APPROVED")).thenReturn(1L);
        when(reviewRepository.countByTaskIdAndReviewerTypeAndStatus(taskId, "AI", "APPROVED")).thenReturn(0L);
        when(reviewCommentRepository.findByReviewId(any())).thenReturn(Collections.emptyList());

        ReviewSummary summary = reviewGateService.checkReviewGate(taskId, tenantId, teamId);

        assertFalse(summary.isPolicyMet());
        assertFalse(summary.isAiApproval());
    }

    @Test
    void should_returnPolicyMet_when_aiReviewNotRequired() {
        UUID taskId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        UUID teamId = UUID.randomUUID();

        ReviewPolicy policy = ReviewPolicy.builder()
                .tenantId(tenantId)
                .minHumanApprovals(1)
                .requireAiReview(false)
                .selfReviewAllowed(true)
                .build();

        Instant now4 = Instant.now();
        Review humanReview = Review.builder()
                .id(UUID.randomUUID())
                .tenantId(tenantId)
                .taskId(taskId)
                .reviewerType("HUMAN")
                .status("APPROVED")
                .createdAt(now4)
                .updatedAt(now4)
                .build();

        when(reviewPolicyService.resolvePolicy(tenantId, teamId)).thenReturn(policy);
        when(reviewRepository.findByTaskId(taskId)).thenReturn(List.of(humanReview));
        when(reviewRepository.countByTaskIdAndReviewerTypeAndStatus(taskId, "HUMAN", "APPROVED")).thenReturn(1L);
        when(reviewRepository.countByTaskIdAndReviewerTypeAndStatus(taskId, "AI", "APPROVED")).thenReturn(0L);
        when(reviewCommentRepository.findByReviewId(any())).thenReturn(Collections.emptyList());

        ReviewSummary summary = reviewGateService.checkReviewGate(taskId, tenantId, teamId);

        assertTrue(summary.isPolicyMet());
        assertFalse(summary.isAiApproval());
    }

    @Test
    void should_returnEmptyReviews_when_noReviewsExist() {
        UUID taskId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();

        ReviewPolicy policy = ReviewPolicy.builder()
                .tenantId(tenantId)
                .minHumanApprovals(1)
                .requireAiReview(true)
                .selfReviewAllowed(true)
                .build();

        when(reviewPolicyService.resolvePolicy(tenantId, null)).thenReturn(policy);
        when(reviewRepository.findByTaskId(taskId)).thenReturn(Collections.emptyList());
        when(reviewRepository.countByTaskIdAndReviewerTypeAndStatus(taskId, "HUMAN", "APPROVED")).thenReturn(0L);
        when(reviewRepository.countByTaskIdAndReviewerTypeAndStatus(taskId, "AI", "APPROVED")).thenReturn(0L);

        ReviewSummary summary = reviewGateService.checkReviewGate(taskId, tenantId, null);

        assertFalse(summary.isPolicyMet());
        assertEquals(0, summary.getTotalReviews());
        assertEquals(0, summary.getHumanApprovals());
        assertFalse(summary.isAiApproval());
        assertEquals(0, summary.getReviews().size());
    }

    @Test
    void should_countMultipleHumanApprovals() {
        UUID taskId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        UUID teamId = UUID.randomUUID();

        ReviewPolicy policy = ReviewPolicy.builder()
                .tenantId(tenantId)
                .minHumanApprovals(2)
                .requireAiReview(false)
                .selfReviewAllowed(true)
                .build();

        Instant now5 = Instant.now();
        Review r1 = Review.builder()
                .id(UUID.randomUUID()).tenantId(tenantId).taskId(taskId)
                .reviewerType("HUMAN").status("APPROVED")
                .createdAt(now5).updatedAt(now5).build();
        Review r2 = Review.builder()
                .id(UUID.randomUUID()).tenantId(tenantId).taskId(taskId)
                .reviewerType("HUMAN").status("APPROVED")
                .createdAt(now5).updatedAt(now5).build();

        when(reviewPolicyService.resolvePolicy(tenantId, teamId)).thenReturn(policy);
        when(reviewRepository.findByTaskId(taskId)).thenReturn(List.of(r1, r2));
        when(reviewRepository.countByTaskIdAndReviewerTypeAndStatus(taskId, "HUMAN", "APPROVED")).thenReturn(2L);
        when(reviewRepository.countByTaskIdAndReviewerTypeAndStatus(taskId, "AI", "APPROVED")).thenReturn(0L);
        when(reviewCommentRepository.findByReviewId(any())).thenReturn(Collections.emptyList());

        ReviewSummary summary = reviewGateService.checkReviewGate(taskId, tenantId, teamId);

        assertTrue(summary.isPolicyMet());
        assertEquals(2, summary.getHumanApprovals());
    }
}
