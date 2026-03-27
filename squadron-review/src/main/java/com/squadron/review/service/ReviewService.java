package com.squadron.review.service;

import com.squadron.common.config.NatsEventPublisher;
import com.squadron.common.event.ReviewUpdatedEvent;
import com.squadron.common.exception.ResourceNotFoundException;
import com.squadron.review.dto.CreateReviewRequest;
import com.squadron.review.dto.ReviewCommentDto;
import com.squadron.review.dto.ReviewDto;
import com.squadron.review.dto.SubmitReviewRequest;
import com.squadron.review.entity.Review;
import com.squadron.review.entity.ReviewComment;
import com.squadron.review.repository.ReviewCommentRepository;
import com.squadron.review.repository.ReviewRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
@Transactional
public class ReviewService {

    private static final Logger log = LoggerFactory.getLogger(ReviewService.class);

    private final ReviewRepository reviewRepository;
    private final ReviewCommentRepository reviewCommentRepository;
    private final NatsEventPublisher natsEventPublisher;

    public ReviewService(ReviewRepository reviewRepository,
                         ReviewCommentRepository reviewCommentRepository,
                         NatsEventPublisher natsEventPublisher) {
        this.reviewRepository = reviewRepository;
        this.reviewCommentRepository = reviewCommentRepository;
        this.natsEventPublisher = natsEventPublisher;
    }

    public Review createReview(CreateReviewRequest request) {
        log.info("Creating review for task {} by reviewer type {}", request.getTaskId(), request.getReviewerType());

        Review review = Review.builder()
                .tenantId(request.getTenantId())
                .taskId(request.getTaskId())
                .reviewerId(request.getReviewerId())
                .reviewerType(request.getReviewerType())
                .status("PENDING")
                .build();

        return reviewRepository.save(review);
    }

    public ReviewDto submitReview(SubmitReviewRequest request) {
        Review review = reviewRepository.findById(request.getReviewId())
                .orElseThrow(() -> new ResourceNotFoundException("Review", request.getReviewId()));

        log.info("Submitting review {} with status {}", review.getId(), request.getStatus());

        review.setStatus(request.getStatus());
        review.setSummary(request.getSummary());
        review = reviewRepository.save(review);

        List<ReviewComment> savedComments = new ArrayList<>();
        if (request.getComments() != null) {
            for (ReviewCommentDto commentDto : request.getComments()) {
                ReviewComment comment = ReviewComment.builder()
                        .reviewId(review.getId())
                        .filePath(commentDto.getFilePath())
                        .lineNumber(commentDto.getLineNumber())
                        .body(commentDto.getBody())
                        .severity(commentDto.getSeverity())
                        .category(commentDto.getCategory())
                        .build();
                savedComments.add(reviewCommentRepository.save(comment));
            }
        }

        publishReviewUpdatedEvent(review);

        return toDto(review, savedComments);
    }

    @Transactional(readOnly = true)
    public ReviewDto getReview(UUID id) {
        Review review = reviewRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Review", id));
        List<ReviewComment> comments = reviewCommentRepository.findByReviewId(id);
        return toDto(review, comments);
    }

    @Transactional(readOnly = true)
    public List<ReviewDto> listReviewsForTask(UUID taskId) {
        List<Review> reviews = reviewRepository.findByTaskId(taskId);
        List<ReviewDto> result = new ArrayList<>();
        for (Review review : reviews) {
            List<ReviewComment> comments = reviewCommentRepository.findByReviewId(review.getId());
            result.add(toDto(review, comments));
        }
        return result;
    }

    public void deleteReview(UUID id) {
        Review review = reviewRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Review", id));
        reviewRepository.delete(review);
        log.info("Deleted review {}", id);
    }

    private void publishReviewUpdatedEvent(Review review) {
        ReviewUpdatedEvent event = new ReviewUpdatedEvent();
        event.setReviewId(review.getId());
        event.setTaskId(review.getTaskId());
        event.setReviewerType(review.getReviewerType());
        event.setStatus(review.getStatus());
        event.setTenantId(review.getTenantId());
        event.setSource("squadron-review");

        natsEventPublisher.publish("squadron.reviews.updated", event);
    }

    private ReviewDto toDto(Review review, List<ReviewComment> comments) {
        List<ReviewCommentDto> commentDtos = comments.stream()
                .map(c -> ReviewCommentDto.builder()
                        .filePath(c.getFilePath())
                        .lineNumber(c.getLineNumber())
                        .body(c.getBody())
                        .severity(c.getSeverity())
                        .category(c.getCategory())
                        .build())
                .toList();

        return ReviewDto.builder()
                .id(review.getId())
                .tenantId(review.getTenantId())
                .taskId(review.getTaskId())
                .reviewerId(review.getReviewerId())
                .reviewerType(review.getReviewerType())
                .status(review.getStatus())
                .summary(review.getSummary())
                .createdAt(review.getCreatedAt())
                .updatedAt(review.getUpdatedAt())
                .comments(commentDtos)
                .build();
    }
}
