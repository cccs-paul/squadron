package com.squadron.review.controller;

import com.squadron.common.dto.ApiResponse;
import com.squadron.review.service.ReviewOrchestrationService;
import com.squadron.review.service.ReviewOrchestrationService.ReviewOrchestrationResult;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/reviews/orchestration")
public class ReviewOrchestrationController {

    private final ReviewOrchestrationService reviewOrchestrationService;

    public ReviewOrchestrationController(ReviewOrchestrationService reviewOrchestrationService) {
        this.reviewOrchestrationService = reviewOrchestrationService;
    }

    @PostMapping("/task/{taskId}")
    @PreAuthorize("hasAnyRole('squadron-admin', 'team-lead', 'developer')")
    public ResponseEntity<ApiResponse<ReviewOrchestrationResult>> orchestrateReview(
            @PathVariable UUID taskId,
            @RequestParam UUID tenantId,
            @RequestParam(required = false) UUID teamId) {
        ReviewOrchestrationResult result = reviewOrchestrationService.orchestrateReview(taskId, tenantId, teamId);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(result));
    }

    @GetMapping("/task/{taskId}/check")
    @PreAuthorize("hasAnyRole('squadron-admin', 'team-lead', 'developer', 'qa')")
    public ResponseEntity<ApiResponse<Boolean>> checkAndTransition(
            @PathVariable UUID taskId,
            @RequestParam UUID tenantId,
            @RequestParam(required = false) UUID teamId) {
        boolean passed = reviewOrchestrationService.checkAndTransition(taskId, tenantId, teamId);
        return ResponseEntity.ok(ApiResponse.success(passed));
    }
}
