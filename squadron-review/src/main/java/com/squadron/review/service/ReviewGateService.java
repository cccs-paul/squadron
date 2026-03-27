package com.squadron.review.service;

import com.squadron.review.dto.ReviewCommentDto;
import com.squadron.review.dto.ReviewDto;
import com.squadron.review.dto.ReviewSummary;
import com.squadron.review.entity.Review;
import com.squadron.review.entity.ReviewComment;
import com.squadron.review.entity.ReviewPolicy;
import com.squadron.review.repository.ReviewCommentRepository;
import com.squadron.review.repository.ReviewRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
public class ReviewGateService {

    private static final Logger log = LoggerFactory.getLogger(ReviewGateService.class);

    private final ReviewRepository reviewRepository;
    private final ReviewCommentRepository reviewCommentRepository;
    private final ReviewPolicyService reviewPolicyService;

    public ReviewGateService(ReviewRepository reviewRepository,
                             ReviewCommentRepository reviewCommentRepository,
                             ReviewPolicyService reviewPolicyService) {
        this.reviewRepository = reviewRepository;
        this.reviewCommentRepository = reviewCommentRepository;
        this.reviewPolicyService = reviewPolicyService;
    }

    public ReviewSummary checkReviewGate(UUID taskId, UUID tenantId, UUID teamId) {
        log.info("Checking review gate for task {} tenant {} team {}", taskId, tenantId, teamId);

        ReviewPolicy policy = reviewPolicyService.resolvePolicy(tenantId, teamId);

        List<Review> allReviews = reviewRepository.findByTaskId(taskId);

        long humanApprovals = reviewRepository.countByTaskIdAndReviewerTypeAndStatus(taskId, "HUMAN", "APPROVED");

        long aiApprovals = reviewRepository.countByTaskIdAndReviewerTypeAndStatus(taskId, "AI", "APPROVED");
        boolean aiApproval = aiApprovals > 0;

        boolean humanRequirementMet = humanApprovals >= policy.getMinHumanApprovals();
        boolean aiRequirementMet = !policy.getRequireAiReview() || aiApproval;
        boolean policyMet = humanRequirementMet && aiRequirementMet;

        List<ReviewDto> reviewDtos = new ArrayList<>();
        for (Review review : allReviews) {
            List<ReviewComment> comments = reviewCommentRepository.findByReviewId(review.getId());
            reviewDtos.add(toDto(review, comments));
        }

        return ReviewSummary.builder()
                .taskId(taskId)
                .totalReviews(allReviews.size())
                .humanApprovals((int) humanApprovals)
                .aiApproval(aiApproval)
                .policyMet(policyMet)
                .reviews(reviewDtos)
                .build();
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
