package com.squadron.git.controller;

import com.squadron.common.dto.ApiResponse;
import com.squadron.git.dto.BranchRequest;
import com.squadron.git.dto.CloneRequest;
import com.squadron.git.dto.CommitRequest;
import com.squadron.git.dto.GitCommandResult;
import com.squadron.git.dto.PushRequest;
import com.squadron.git.entity.GitOperation;
import com.squadron.git.service.GitOperationService;
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
@RequestMapping("/api/git/operations")
public class GitOperationController {

    private final GitOperationService gitOperationService;

    public GitOperationController(GitOperationService gitOperationService) {
        this.gitOperationService = gitOperationService;
    }

    @PostMapping("/clone")
    @PreAuthorize("hasAnyRole('squadron-admin','team-lead','developer')")
    public ResponseEntity<ApiResponse<GitCommandResult>> cloneRepo(
            @RequestParam UUID taskId,
            @Valid @RequestBody CloneRequest request) {
        GitCommandResult result = gitOperationService.cloneRepo(taskId, request);
        return ResponseEntity.ok(ApiResponse.success(result));
    }

    @PostMapping("/branch")
    @PreAuthorize("hasAnyRole('squadron-admin','team-lead','developer')")
    public ResponseEntity<ApiResponse<GitCommandResult>> createBranch(
            @RequestParam UUID taskId,
            @Valid @RequestBody BranchRequest request) {
        GitCommandResult result = gitOperationService.createBranch(taskId, request);
        return ResponseEntity.ok(ApiResponse.success(result));
    }

    @PostMapping("/commit")
    @PreAuthorize("hasAnyRole('squadron-admin','team-lead','developer')")
    public ResponseEntity<ApiResponse<GitCommandResult>> commit(
            @RequestParam UUID taskId,
            @Valid @RequestBody CommitRequest request) {
        GitCommandResult result = gitOperationService.commit(taskId, request);
        return ResponseEntity.ok(ApiResponse.success(result));
    }

    @PostMapping("/push")
    @PreAuthorize("hasAnyRole('squadron-admin','team-lead','developer')")
    public ResponseEntity<ApiResponse<GitCommandResult>> push(
            @RequestParam UUID taskId,
            @Valid @RequestBody PushRequest request) {
        GitCommandResult result = gitOperationService.push(taskId, request);
        return ResponseEntity.ok(ApiResponse.success(result));
    }

    @GetMapping("/task/{taskId}")
    @PreAuthorize("hasAnyRole('squadron-admin','team-lead','developer')")
    public ResponseEntity<ApiResponse<List<GitOperation>>> listOperationsForTask(
            @PathVariable UUID taskId) {
        List<GitOperation> operations = gitOperationService.listOperationsByTask(taskId);
        return ResponseEntity.ok(ApiResponse.success(operations));
    }
}
