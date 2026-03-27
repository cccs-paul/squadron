package com.squadron.git.controller;

import com.squadron.common.dto.ApiResponse;
import com.squadron.git.dto.CreatePullRequestRequest;
import com.squadron.git.dto.DiffResult;
import com.squadron.git.dto.MergeRequest;
import com.squadron.git.dto.MergeabilityDto;
import com.squadron.git.dto.PullRequestDto;
import com.squadron.git.service.PullRequestService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
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
@RequestMapping("/api/git/pull-requests")
public class PullRequestController {

    private final PullRequestService pullRequestService;

    public PullRequestController(PullRequestService pullRequestService) {
        this.pullRequestService = pullRequestService;
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('squadron-admin','team-lead','developer')")
    public ResponseEntity<ApiResponse<PullRequestDto>> createPullRequest(
            @Valid @RequestBody CreatePullRequestRequest request) {
        PullRequestDto dto = pullRequestService.createPullRequest(request);
        return ResponseEntity.ok(ApiResponse.success(dto));
    }

    @PostMapping("/{id}/merge")
    @PreAuthorize("hasAnyRole('squadron-admin','team-lead','developer')")
    public ResponseEntity<ApiResponse<Void>> mergePullRequest(
            @PathVariable UUID id,
            @Valid @RequestBody MergeRequest request) {
        pullRequestService.mergePullRequest(request);
        return ResponseEntity.ok(ApiResponse.success(null));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('squadron-admin','team-lead','developer','qa','viewer')")
    public ResponseEntity<ApiResponse<PullRequestDto>> getPullRequest(@PathVariable UUID id) {
        PullRequestDto dto = pullRequestService.getPullRequest(id);
        return ResponseEntity.ok(ApiResponse.success(dto));
    }

    @GetMapping("/task/{taskId}")
    @PreAuthorize("hasAnyRole('squadron-admin','team-lead','developer','qa','viewer')")
    public ResponseEntity<ApiResponse<PullRequestDto>> getByTaskId(@PathVariable UUID taskId) {
        PullRequestDto dto = pullRequestService.getByTaskId(taskId);
        return ResponseEntity.ok(ApiResponse.success(dto));
    }

    @GetMapping("/{id}/mergeability")
    @PreAuthorize("hasAnyRole('squadron-admin','team-lead','developer','qa','viewer')")
    public ResponseEntity<ApiResponse<MergeabilityDto>> checkMergeability(@PathVariable UUID id) {
        MergeabilityDto dto = pullRequestService.checkMergeability(id);
        return ResponseEntity.ok(ApiResponse.success(dto));
    }

    @GetMapping("/tenant/{tenantId}")
    @PreAuthorize("hasAnyRole('squadron-admin','team-lead','developer','qa','viewer')")
    public ResponseEntity<ApiResponse<List<PullRequestDto>>> listPullRequests(
            @PathVariable UUID tenantId,
            @RequestParam(required = false) String status) {
        List<PullRequestDto> dtos = pullRequestService.listPullRequestsByTenant(tenantId, status);
        return ResponseEntity.ok(ApiResponse.success(dtos));
    }

    @PostMapping("/{id}/reviewers")
    @PreAuthorize("hasAnyRole('squadron-admin','team-lead','developer')")
    public ResponseEntity<ApiResponse<Void>> requestReviewers(
            @PathVariable UUID id,
            @RequestBody List<String> reviewers,
            @RequestParam String accessToken) {
        pullRequestService.requestReviewers(id, reviewers, accessToken);
        return ResponseEntity.ok(ApiResponse.success(null));
    }

    @GetMapping("/{id}/diff")
    @PreAuthorize("hasAnyRole('squadron-admin','team-lead','developer','qa','viewer')")
    public ResponseEntity<ApiResponse<DiffResult>> getDiff(
            @PathVariable UUID id,
            @RequestParam String accessToken) {
        DiffResult diff = pullRequestService.getDiff(id, accessToken);
        return ResponseEntity.ok(ApiResponse.success(diff));
    }
}
