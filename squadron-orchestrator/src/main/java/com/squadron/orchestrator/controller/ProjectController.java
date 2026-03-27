package com.squadron.orchestrator.controller;

import com.squadron.common.dto.ApiResponse;
import com.squadron.orchestrator.dto.CreateProjectRequest;
import com.squadron.orchestrator.entity.Project;
import com.squadron.orchestrator.service.ProjectService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/projects")
@Tag(name = "Projects", description = "Project management for grouping tasks")
public class ProjectController {

    private final ProjectService projectService;

    public ProjectController(ProjectService projectService) {
        this.projectService = projectService;
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('squadron-admin','team-lead')")
    @Operation(summary = "Create a new project")
    public ResponseEntity<ApiResponse<Project>> createProject(@Valid @RequestBody CreateProjectRequest request) {
        Project project = projectService.createProject(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(project));
    }

    @GetMapping("/tenant/{tenantId}")
    @PreAuthorize("hasAnyRole('squadron-admin','team-lead','developer','qa','viewer')")
    public ResponseEntity<ApiResponse<List<Project>>> listByTenant(@PathVariable UUID tenantId) {
        List<Project> projects = projectService.listProjectsByTenant(tenantId);
        return ResponseEntity.ok(ApiResponse.success(projects));
    }

    @GetMapping("/team/{teamId}")
    @PreAuthorize("hasAnyRole('squadron-admin','team-lead','developer','qa','viewer')")
    public ResponseEntity<ApiResponse<List<Project>>> listByTeam(@PathVariable UUID teamId) {
        List<Project> projects = projectService.listProjectsByTeam(teamId);
        return ResponseEntity.ok(ApiResponse.success(projects));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('squadron-admin','team-lead','developer','qa','viewer')")
    @Operation(summary = "Get a project by ID")
    public ResponseEntity<ApiResponse<Project>> getProject(@PathVariable UUID id) {
        Project project = projectService.getProject(id);
        return ResponseEntity.ok(ApiResponse.success(project));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('squadron-admin','team-lead')")
    public ResponseEntity<ApiResponse<Project>> updateProject(@PathVariable UUID id,
                                                               @Valid @RequestBody CreateProjectRequest request) {
        Project project = projectService.updateProject(id, request);
        return ResponseEntity.ok(ApiResponse.success(project));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('squadron-admin','team-lead')")
    public ResponseEntity<Void> deleteProject(@PathVariable UUID id) {
        projectService.deleteProject(id);
        return ResponseEntity.noContent().build();
    }
}
