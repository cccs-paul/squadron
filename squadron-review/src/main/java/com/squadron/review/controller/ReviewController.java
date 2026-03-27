package com.squadron.review.controller;

import com.squadron.common.dto.ApiResponse;
import com.squadron.review.dto.CreateReviewRequest;
import com.squadron.review.dto.ReviewDto;
import com.squadron.review.dto.ReviewSummary;
import com.squadron.review.dto.SubmitReviewRequest;
import com.squadron.review.entity.Review;
import com.squadron.review.service.ReviewGateService;
import com.squadron.review.service.ReviewService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/reviews")
public class ReviewController {

    private final ReviewService reviewService;
    private final ReviewGateService reviewGateService;

    public ReviewController(ReviewService reviewService, ReviewGateService reviewGateService) {
        this.reviewService = reviewService;
        this.reviewGateService = reviewGateService;
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('squadron-admin','team-lead','developer','qa')")
    public ResponseEntity<ApiResponse<Review>> createReview(@Valid @RequestBody CreateReviewRequest request) {
        Review review = reviewService.createReview(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(review));
    }

    @PostMapping("/submit")
    @PreAuthorize("hasAnyRole('squadron-admin','team-lead','developer','qa')")
    public ResponseEntity<ApiResponse<ReviewDto>> submitReview(@Valid @RequestBody SubmitReviewRequest request) {
        ReviewDto reviewDto = reviewService.submitReview(request);
        return ResponseEntity.ok(ApiResponse.success(reviewDto));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('squadron-admin','team-lead','developer','qa','viewer')")
    public ResponseEntity<ApiResponse<ReviewDto>> getReview(@PathVariable UUID id) {
        ReviewDto reviewDto = reviewService.getReview(id);
        return ResponseEntity.ok(ApiResponse.success(reviewDto));
    }

    @GetMapping("/task/{taskId}")
    @PreAuthorize("hasAnyRole('squadron-admin','team-lead','developer','qa','viewer')")
    public ResponseEntity<ApiResponse<List<ReviewDto>>> listReviewsForTask(@PathVariable UUID taskId) {
        List<ReviewDto> reviews = reviewService.listReviewsForTask(taskId);
        return ResponseEntity.ok(ApiResponse.success(reviews));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('squadron-admin','team-lead','developer','qa')")
    public ResponseEntity<Void> deleteReview(@PathVariable UUID id) {
        reviewService.deleteReview(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/task/{taskId}/gate")
    @PreAuthorize("hasAnyRole('squadron-admin','team-lead','developer','qa','viewer')")
    public ResponseEntity<ApiResponse<ReviewSummary>> checkReviewGate(
            @PathVariable UUID taskId,
            @RequestParam UUID tenantId,
            @RequestParam(required = false) UUID teamId) {
        ReviewSummary summary = reviewGateService.checkReviewGate(taskId, tenantId, teamId);
        return ResponseEntity.ok(ApiResponse.success(summary));
    }
}
