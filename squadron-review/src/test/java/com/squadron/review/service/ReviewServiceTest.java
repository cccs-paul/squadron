package com.squadron.review.service;

import com.squadron.common.config.NatsEventPublisher;
import com.squadron.common.exception.ResourceNotFoundException;
import com.squadron.review.dto.CreateReviewRequest;
import com.squadron.review.dto.ReviewCommentDto;
import com.squadron.review.dto.ReviewDto;
import com.squadron.review.dto.SubmitReviewRequest;
import com.squadron.review.entity.Review;
import com.squadron.review.entity.ReviewComment;
import com.squadron.review.repository.ReviewCommentRepository;
import com.squadron.review.repository.ReviewRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ReviewServiceTest {

    @Mock
    private ReviewRepository reviewRepository;

    @Mock
    private ReviewCommentRepository reviewCommentRepository;

    @Mock
    private NatsEventPublisher natsEventPublisher;

    private ReviewService reviewService;

    @BeforeEach
    void setUp() {
        reviewService = new ReviewService(reviewRepository, reviewCommentRepository, natsEventPublisher);
    }

    @Test
    void should_createReview_when_validRequest() {
        UUID tenantId = UUID.randomUUID();
        UUID taskId = UUID.randomUUID();
        UUID reviewerId = UUID.randomUUID();

        CreateReviewRequest request = CreateReviewRequest.builder()
                .tenantId(tenantId)
                .taskId(taskId)
                .reviewerId(reviewerId)
                .reviewerType("HUMAN")
                .build();

        Review savedReview = Review.builder()
                .id(UUID.randomUUID())
                .tenantId(tenantId)
                .taskId(taskId)
                .reviewerId(reviewerId)
                .reviewerType("HUMAN")
                .status("PENDING")
                .build();

        when(reviewRepository.save(any(Review.class))).thenReturn(savedReview);

        Review result = reviewService.createReview(request);

        assertNotNull(result);
        assertEquals("PENDING", result.getStatus());
        assertEquals("HUMAN", result.getReviewerType());
        verify(reviewRepository).save(any(Review.class));
    }

    @Test
    void should_createReview_when_aiReviewWithoutReviewerId() {
        CreateReviewRequest request = CreateReviewRequest.builder()
                .tenantId(UUID.randomUUID())
                .taskId(UUID.randomUUID())
                .reviewerType("AI")
                .build();

        Review savedReview = Review.builder()
                .id(UUID.randomUUID())
                .tenantId(request.getTenantId())
                .taskId(request.getTaskId())
                .reviewerType("AI")
                .status("PENDING")
                .build();

        when(reviewRepository.save(any(Review.class))).thenReturn(savedReview);

        Review result = reviewService.createReview(request);

        assertEquals("AI", result.getReviewerType());
    }

    @Test
    void should_submitReview_when_validRequest() {
        UUID reviewId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        UUID taskId = UUID.randomUUID();

        Review existingReview = Review.builder()
                .id(reviewId)
                .tenantId(tenantId)
                .taskId(taskId)
                .reviewerType("HUMAN")
                .status("PENDING")
                .build();
        Instant now = Instant.now();
        existingReview.setCreatedAt(now);
        existingReview.setUpdatedAt(now);

        ReviewCommentDto commentDto = ReviewCommentDto.builder()
                .filePath("src/App.java")
                .lineNumber(10)
                .body("Add null check")
                .severity("WARNING")
                .category("BUG")
                .build();

        SubmitReviewRequest request = SubmitReviewRequest.builder()
                .reviewId(reviewId)
                .status("CHANGES_REQUESTED")
                .summary("Needs fixes")
                .comments(List.of(commentDto))
                .build();

        when(reviewRepository.findById(reviewId)).thenReturn(Optional.of(existingReview));
        when(reviewRepository.save(any(Review.class))).thenReturn(existingReview);
        when(reviewCommentRepository.save(any(ReviewComment.class))).thenAnswer(invocation -> {
            ReviewComment c = invocation.getArgument(0);
            c.setId(UUID.randomUUID());
            return c;
        });
        doNothing().when(natsEventPublisher).publish(anyString(), any());

        ReviewDto result = reviewService.submitReview(request);

        assertNotNull(result);
        assertEquals("CHANGES_REQUESTED", existingReview.getStatus());
        assertEquals("Needs fixes", existingReview.getSummary());
        assertEquals(1, result.getComments().size());
        verify(natsEventPublisher).publish(anyString(), any());
    }

    @Test
    void should_submitReview_when_noComments() {
        UUID reviewId = UUID.randomUUID();
        Review existingReview = Review.builder()
                .id(reviewId)
                .tenantId(UUID.randomUUID())
                .taskId(UUID.randomUUID())
                .reviewerType("AI")
                .status("PENDING")
                .build();
        Instant now2 = Instant.now();
        existingReview.setCreatedAt(now2);
        existingReview.setUpdatedAt(now2);

        SubmitReviewRequest request = SubmitReviewRequest.builder()
                .reviewId(reviewId)
                .status("APPROVED")
                .summary("LGTM")
                .build();

        when(reviewRepository.findById(reviewId)).thenReturn(Optional.of(existingReview));
        when(reviewRepository.save(any(Review.class))).thenReturn(existingReview);
        doNothing().when(natsEventPublisher).publish(anyString(), any());

        ReviewDto result = reviewService.submitReview(request);

        assertEquals(0, result.getComments().size());
        verify(reviewCommentRepository, never()).save(any());
    }

    @Test
    void should_throwNotFound_when_submitReviewMissing() {
        UUID reviewId = UUID.randomUUID();
        SubmitReviewRequest request = SubmitReviewRequest.builder()
                .reviewId(reviewId)
                .status("APPROVED")
                .build();

        when(reviewRepository.findById(reviewId)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> reviewService.submitReview(request));
    }

    @Test
    void should_getReview_when_exists() {
        UUID reviewId = UUID.randomUUID();
        Review review = Review.builder()
                .id(reviewId)
                .tenantId(UUID.randomUUID())
                .taskId(UUID.randomUUID())
                .reviewerType("HUMAN")
                .status("APPROVED")
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();

        ReviewComment comment = ReviewComment.builder()
                .id(UUID.randomUUID())
                .reviewId(reviewId)
                .body("Nice work")
                .severity("INFO")
                .build();

        when(reviewRepository.findById(reviewId)).thenReturn(Optional.of(review));
        when(reviewCommentRepository.findByReviewId(reviewId)).thenReturn(List.of(comment));

        ReviewDto result = reviewService.getReview(reviewId);

        assertEquals(reviewId, result.getId());
        assertEquals("APPROVED", result.getStatus());
        assertEquals(1, result.getComments().size());
    }

    @Test
    void should_throwNotFound_when_getReviewMissing() {
        UUID reviewId = UUID.randomUUID();
        when(reviewRepository.findById(reviewId)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> reviewService.getReview(reviewId));
    }

    @Test
    void should_listReviewsForTask_when_reviewsExist() {
        UUID taskId = UUID.randomUUID();
        Instant now3 = Instant.now();
        Review r1 = Review.builder()
                .id(UUID.randomUUID())
                .tenantId(UUID.randomUUID())
                .taskId(taskId)
                .reviewerType("HUMAN")
                .status("APPROVED")
                .createdAt(now3)
                .updatedAt(now3)
                .build();
        Review r2 = Review.builder()
                .id(UUID.randomUUID())
                .tenantId(UUID.randomUUID())
                .taskId(taskId)
                .reviewerType("AI")
                .status("PENDING")
                .createdAt(now3)
                .updatedAt(now3)
                .build();

        when(reviewRepository.findByTaskId(taskId)).thenReturn(List.of(r1, r2));
        when(reviewCommentRepository.findByReviewId(any())).thenReturn(Collections.emptyList());

        List<ReviewDto> result = reviewService.listReviewsForTask(taskId);

        assertEquals(2, result.size());
    }

    @Test
    void should_returnEmptyList_when_noReviewsForTask() {
        UUID taskId = UUID.randomUUID();
        when(reviewRepository.findByTaskId(taskId)).thenReturn(Collections.emptyList());

        List<ReviewDto> result = reviewService.listReviewsForTask(taskId);

        assertEquals(0, result.size());
    }

    @Test
    void should_deleteReview_when_exists() {
        UUID reviewId = UUID.randomUUID();
        Review review = Review.builder()
                .id(reviewId)
                .tenantId(UUID.randomUUID())
                .taskId(UUID.randomUUID())
                .reviewerType("HUMAN")
                .status("PENDING")
                .build();

        when(reviewRepository.findById(reviewId)).thenReturn(Optional.of(review));

        reviewService.deleteReview(reviewId);

        verify(reviewRepository).delete(review);
    }

    @Test
    void should_throwNotFound_when_deleteReviewMissing() {
        UUID reviewId = UUID.randomUUID();
        when(reviewRepository.findById(reviewId)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> reviewService.deleteReview(reviewId));
    }
}
