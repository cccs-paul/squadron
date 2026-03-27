package com.squadron.orchestrator.controller;

import com.squadron.common.dto.ApiResponse;
import com.squadron.orchestrator.dto.CreateWorkflowDefinitionRequest;
import com.squadron.orchestrator.entity.WorkflowDefinition;
import com.squadron.orchestrator.service.WorkflowDefinitionService;
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
@RequestMapping("/api/workflow-definitions")
public class WorkflowDefinitionController {

    private final WorkflowDefinitionService workflowDefinitionService;

    public WorkflowDefinitionController(WorkflowDefinitionService workflowDefinitionService) {
        this.workflowDefinitionService = workflowDefinitionService;
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('squadron-admin','team-lead')")
    public ResponseEntity<ApiResponse<WorkflowDefinition>> create(
            @Valid @RequestBody CreateWorkflowDefinitionRequest request) {
        WorkflowDefinition definition = workflowDefinitionService.create(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(definition));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('squadron-admin','team-lead','developer','qa','viewer')")
    public ResponseEntity<ApiResponse<WorkflowDefinition>> getById(@PathVariable UUID id) {
        WorkflowDefinition definition = workflowDefinitionService.getById(id);
        return ResponseEntity.ok(ApiResponse.success(definition));
    }

    @GetMapping("/tenant/{tenantId}")
    @PreAuthorize("hasAnyRole('squadron-admin','team-lead','developer','qa','viewer')")
    public ResponseEntity<ApiResponse<List<WorkflowDefinition>>> listByTenant(@PathVariable UUID tenantId) {
        List<WorkflowDefinition> definitions = workflowDefinitionService.listByTenant(tenantId);
        return ResponseEntity.ok(ApiResponse.success(definitions));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('squadron-admin','team-lead')")
    public ResponseEntity<ApiResponse<WorkflowDefinition>> update(
            @PathVariable UUID id,
            @Valid @RequestBody CreateWorkflowDefinitionRequest request) {
        WorkflowDefinition definition = workflowDefinitionService.update(id, request);
        return ResponseEntity.ok(ApiResponse.success(definition));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('squadron-admin')")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        workflowDefinitionService.delete(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/activate")
    @PreAuthorize("hasAnyRole('squadron-admin','team-lead')")
    public ResponseEntity<Void> activate(@PathVariable UUID id) {
        workflowDefinitionService.activate(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/deactivate")
    @PreAuthorize("hasAnyRole('squadron-admin','team-lead')")
    public ResponseEntity<Void> deactivate(@PathVariable UUID id) {
        workflowDefinitionService.deactivate(id);
        return ResponseEntity.noContent().build();
    }
}
