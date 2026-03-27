package com.squadron.review.controller;

import com.squadron.common.dto.ApiResponse;
import com.squadron.review.dto.ReviewPolicyDto;
import com.squadron.review.entity.ReviewPolicy;
import com.squadron.review.service.ReviewPolicyService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/reviews/policies")
public class ReviewPolicyController {

    private final ReviewPolicyService reviewPolicyService;

    public ReviewPolicyController(ReviewPolicyService reviewPolicyService) {
        this.reviewPolicyService = reviewPolicyService;
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('squadron-admin','team-lead')")
    public ResponseEntity<ApiResponse<ReviewPolicy>> createOrUpdatePolicy(
            @Valid @RequestBody ReviewPolicyDto dto) {
        ReviewPolicy policy = reviewPolicyService.createOrUpdatePolicy(dto);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(policy));
    }

    @GetMapping("/resolve")
    @PreAuthorize("hasAnyRole('squadron-admin','team-lead')")
    public ResponseEntity<ApiResponse<ReviewPolicy>> resolvePolicy(
            @RequestParam UUID tenantId,
            @RequestParam(required = false) UUID teamId) {
        ReviewPolicy policy = reviewPolicyService.resolvePolicy(tenantId, teamId);
        return ResponseEntity.ok(ApiResponse.success(policy));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('squadron-admin','team-lead')")
    public ResponseEntity<ApiResponse<ReviewPolicy>> getPolicy(@PathVariable UUID id) {
        ReviewPolicy policy = reviewPolicyService.getPolicy(id);
        return ResponseEntity.ok(ApiResponse.success(policy));
    }
}
