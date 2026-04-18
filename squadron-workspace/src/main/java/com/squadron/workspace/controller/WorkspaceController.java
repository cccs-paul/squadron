package com.squadron.workspace.controller;

import com.squadron.common.dto.ApiResponse;
import com.squadron.workspace.client.ResilientPlatformServiceClient;
import com.squadron.workspace.dto.CreateWorkspaceRequest;
import com.squadron.workspace.dto.ExecRequest;
import com.squadron.workspace.dto.ExecResult;
import com.squadron.workspace.dto.TestGitAccessRequest;
import com.squadron.workspace.dto.TestGitAccessResult;
import com.squadron.workspace.dto.WorkspaceDto;
import com.squadron.workspace.service.WorkspaceGitService;
import com.squadron.workspace.service.WorkspaceService;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;
import java.util.regex.Pattern;

@RestController
@RequestMapping("/api/workspaces")
public class WorkspaceController {

    private static final Logger log = LoggerFactory.getLogger(WorkspaceController.class);
    private static final int MAX_COMMAND_LENGTH = 4096;
    private static final Pattern SHELL_META_PATTERN = Pattern.compile("['\";$`|&(){}<>\\n\\r]");
    private static final List<String> ALLOWED_PATH_PREFIXES = List.of("/workspace/", "/home/", "/tmp/");

    private final WorkspaceService workspaceService;
    private final WorkspaceGitService workspaceGitService;
    private final ResilientPlatformServiceClient platformServiceClient;

    public WorkspaceController(WorkspaceService workspaceService,
                                WorkspaceGitService workspaceGitService,
                                ResilientPlatformServiceClient platformServiceClient) {
        this.workspaceService = workspaceService;
        this.workspaceGitService = workspaceGitService;
        this.platformServiceClient = platformServiceClient;
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
        // Validate total command length
        int totalLength = request.getCommand().stream().mapToInt(String::length).sum();
        if (totalLength > MAX_COMMAND_LENGTH) {
            return ResponseEntity.badRequest().body(
                    ApiResponse.error("Command exceeds maximum allowed length of " + MAX_COMMAND_LENGTH + " characters"));
        }
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
        String pathError = validatePath(path);
        if (pathError != null) {
            return ResponseEntity.badRequest().body(ApiResponse.error(pathError));
        }
        workspaceService.copyToWorkspace(id, content, path);
        return ResponseEntity.ok(ApiResponse.success(null));
    }

    @GetMapping("/{id}/copy-from")
    @PreAuthorize("hasAnyRole('squadron-admin','team-lead','developer')")
    public ResponseEntity<byte[]> copyFromWorkspace(
            @PathVariable UUID id,
            @RequestParam String path) {
        String pathError = validatePath(path);
        if (pathError != null) {
            return ResponseEntity.badRequest().build();
        }
        byte[] content = workspaceService.copyFromWorkspace(id, path);
        return ResponseEntity.ok()
                .header("Content-Disposition", "attachment; filename=" + extractFilename(path))
                .body(content);
    }

    @PostMapping("/test-git-access")
    @PreAuthorize("hasAnyRole('squadron-admin','team-lead','developer')")
    public ResponseEntity<ApiResponse<TestGitAccessResult>> testGitAccess(
            @Valid @RequestBody TestGitAccessRequest request) {
        String sshPrivateKey = resolveSshPrivateKey(request.getSshKeyId());
        TestGitAccessResult result = workspaceGitService.testGitAccess(
                request.getCloneUrl(), request.getAccessToken(), sshPrivateKey, request.getBranch());
        return ResponseEntity.ok(ApiResponse.success(result));
    }

    @PostMapping("/{id}/git/clone")
    @PreAuthorize("hasAnyRole('squadron-admin','team-lead','developer')")
    public ResponseEntity<ApiResponse<ExecResult>> cloneRepo(
            @PathVariable UUID id,
            @RequestHeader(value = "X-Access-Token", required = false) String accessToken,
            @RequestParam(required = false) UUID sshKeyId) {
        String sshPrivateKey = resolveSshPrivateKey(sshKeyId);
        ExecResult result = workspaceGitService.cloneRepository(id, accessToken, sshPrivateKey);
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
            @RequestHeader(value = "X-Access-Token", required = false) String accessToken,
            @RequestParam(required = false) UUID sshKeyId) {
        String sshPrivateKey = resolveSshPrivateKey(sshKeyId);
        ExecResult result = workspaceGitService.pushChanges(id, branch, accessToken, sshPrivateKey);
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

    /**
     * Resolves the SSH private key from the platform service by SSH key ID.
     * Returns null if sshKeyId is null.
     */
    private String resolveSshPrivateKey(UUID sshKeyId) {
        if (sshKeyId == null) {
            return null;
        }
        try {
            log.info("Resolving SSH private key for sshKeyId: {}", sshKeyId);
            return platformServiceClient.getDecryptedPrivateKey(sshKeyId);
        } catch (Exception e) {
            log.error("Failed to resolve SSH private key for sshKeyId: {}", sshKeyId, e);
            throw new RuntimeException("Failed to resolve SSH key: " + e.getMessage(), e);
        }
    }

    private String extractFilename(String path) {
        int lastSlash = path.lastIndexOf('/');
        return lastSlash >= 0 ? path.substring(lastSlash + 1) : path;
    }

    /**
     * Validates a file path for copy operations. Returns an error message if invalid, null if valid.
     */
    private String validatePath(String path) {
        if (path == null || path.isBlank()) {
            return "Path must not be null or blank";
        }
        if (path.contains("..")) {
            return "Path must not contain '..' (path traversal)";
        }
        if (SHELL_META_PATTERN.matcher(path).find()) {
            return "Path contains illegal characters";
        }
        if (ALLOWED_PATH_PREFIXES.stream().noneMatch(path::startsWith)) {
            return "Path must start with /workspace/, /home/, or /tmp/";
        }
        return null;
    }
}
