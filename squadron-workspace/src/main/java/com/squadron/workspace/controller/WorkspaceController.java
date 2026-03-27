package com.squadron.workspace.controller;

import com.squadron.common.dto.ApiResponse;
import com.squadron.workspace.dto.CreateWorkspaceRequest;
import com.squadron.workspace.dto.ExecRequest;
import com.squadron.workspace.dto.ExecResult;
import com.squadron.workspace.dto.WorkspaceDto;
import com.squadron.workspace.service.WorkspaceGitService;
import com.squadron.workspace.service.WorkspaceService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
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
@RequestMapping("/api/workspaces")
public class WorkspaceController {

    private final WorkspaceService workspaceService;
    private final WorkspaceGitService workspaceGitService;

    public WorkspaceController(WorkspaceService workspaceService,
                                WorkspaceGitService workspaceGitService) {
        this.workspaceService = workspaceService;
        this.workspaceGitService = workspaceGitService;
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('squadron-admin','team-lead','developer')")
    public ResponseEntity<ApiResponse<WorkspaceDto>> createWorkspace(
            @Valid @RequestBody CreateWorkspaceRequest request) {
        WorkspaceDto workspace = workspaceService.createWorkspace(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(workspace));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('squadron-admin','team-lead','developer')")
    public ResponseEntity<ApiResponse<WorkspaceDto>> getWorkspace(@PathVariable UUID id) {
        WorkspaceDto workspace = workspaceService.getWorkspace(id);
        return ResponseEntity.ok(ApiResponse.success(workspace));
    }

    @GetMapping("/task/{taskId}")
    @PreAuthorize("hasAnyRole('squadron-admin','team-lead','developer')")
    public ResponseEntity<ApiResponse<List<WorkspaceDto>>> listByTask(@PathVariable UUID taskId) {
        List<WorkspaceDto> workspaces = workspaceService.listWorkspacesByTask(taskId);
        return ResponseEntity.ok(ApiResponse.success(workspaces));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('squadron-admin','team-lead','developer')")
    public ResponseEntity<ApiResponse<Void>> destroyWorkspace(@PathVariable UUID id) {
        workspaceService.destroyWorkspace(id);
        return ResponseEntity.ok(ApiResponse.success(null));
    }

    @PostMapping("/{id}/exec")
    @PreAuthorize("hasAnyRole('squadron-admin','team-lead','developer')")
    public ResponseEntity<ApiResponse<ExecResult>> execInWorkspace(
            @PathVariable UUID id,
            @Valid @RequestBody ExecRequest request) {
        request.setWorkspaceId(id);
        ExecResult result = workspaceService.execInWorkspace(request);
        return ResponseEntity.ok(ApiResponse.success(result));
    }

    @GetMapping("/active/tenant/{tenantId}")
    @PreAuthorize("hasAnyRole('squadron-admin','team-lead','developer')")
    public ResponseEntity<ApiResponse<List<WorkspaceDto>>> listActiveWorkspaces(
            @PathVariable UUID tenantId) {
        List<WorkspaceDto> workspaces = workspaceService.listActiveWorkspaces(tenantId);
        return ResponseEntity.ok(ApiResponse.success(workspaces));
    }

    @PostMapping(value = "/{id}/copy-to", consumes = MediaType.APPLICATION_OCTET_STREAM_VALUE)
    @PreAuthorize("hasAnyRole('squadron-admin','team-lead','developer')")
    public ResponseEntity<ApiResponse<Void>> copyToWorkspace(
            @PathVariable UUID id,
            @RequestParam String path,
            @RequestBody byte[] content) {
        workspaceService.copyToWorkspace(id, content, path);
        return ResponseEntity.ok(ApiResponse.success(null));
    }

    @GetMapping("/{id}/copy-from")
    @PreAuthorize("hasAnyRole('squadron-admin','team-lead','developer')")
    public ResponseEntity<byte[]> copyFromWorkspace(
            @PathVariable UUID id,
            @RequestParam String path) {
        byte[] content = workspaceService.copyFromWorkspace(id, path);
        return ResponseEntity.ok()
                .header("Content-Disposition", "attachment; filename=" + extractFilename(path))
                .body(content);
    }

    @PostMapping("/{id}/git/clone")
    @PreAuthorize("hasAnyRole('squadron-admin','team-lead','developer')")
    public ResponseEntity<ApiResponse<ExecResult>> cloneRepo(
            @PathVariable UUID id,
            @RequestParam(required = false) String accessToken) {
        ExecResult result = workspaceGitService.cloneRepository(id, accessToken);
        return ResponseEntity.ok(ApiResponse.success(result));
    }

    @PostMapping("/{id}/git/branch")
    @PreAuthorize("hasAnyRole('squadron-admin','team-lead','developer')")
    public ResponseEntity<ApiResponse<ExecResult>> createBranch(
            @PathVariable UUID id,
            @RequestParam String branchName,
            @RequestParam(required = false) String baseBranch) {
        ExecResult result = workspaceGitService.createBranch(id, branchName, baseBranch);
        return ResponseEntity.ok(ApiResponse.success(result));
    }

    @PostMapping("/{id}/git/commit")
    @PreAuthorize("hasAnyRole('squadron-admin','team-lead','developer')")
    public ResponseEntity<ApiResponse<ExecResult>> commitChanges(
            @PathVariable UUID id,
            @RequestParam String message,
            @RequestParam(required = false) String authorName,
            @RequestParam(required = false) String authorEmail) {
        ExecResult result = workspaceGitService.commitChanges(id, message, authorName, authorEmail);
        return ResponseEntity.ok(ApiResponse.success(result));
    }

    @PostMapping("/{id}/git/push")
    @PreAuthorize("hasAnyRole('squadron-admin','team-lead','developer')")
    public ResponseEntity<ApiResponse<ExecResult>> pushChanges(
            @PathVariable UUID id,
            @RequestParam(required = false) String branch,
            @RequestParam(required = false) String accessToken) {
        ExecResult result = workspaceGitService.pushChanges(id, branch, accessToken);
        return ResponseEntity.ok(ApiResponse.success(result));
    }

    @GetMapping("/{id}/git/diff")
    @PreAuthorize("hasAnyRole('squadron-admin','team-lead','developer','qa','viewer')")
    public ResponseEntity<ApiResponse<ExecResult>> getDiff(@PathVariable UUID id) {
        ExecResult result = workspaceGitService.getDiff(id);
        return ResponseEntity.ok(ApiResponse.success(result));
    }

    @GetMapping("/{id}/git/status")
    @PreAuthorize("hasAnyRole('squadron-admin','team-lead','developer','qa','viewer')")
    public ResponseEntity<ApiResponse<ExecResult>> getGitStatus(@PathVariable UUID id) {
        ExecResult result = workspaceGitService.getStatus(id);
        return ResponseEntity.ok(ApiResponse.success(result));
    }

    private String extractFilename(String path) {
        int lastSlash = path.lastIndexOf('/');
        return lastSlash >= 0 ? path.substring(lastSlash + 1) : path;
    }
}
