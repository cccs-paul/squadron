package com.squadron.agent.controller;

import com.squadron.agent.dto.PlanApprovalRequest;
import com.squadron.agent.entity.TaskPlan;
import com.squadron.agent.service.PlanService;
import com.squadron.common.dto.ApiResponse;
import com.squadron.common.security.TenantContext;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/agents/plans")
public class PlanController {

    private final PlanService planService;

    public PlanController(PlanService planService) {
        this.planService = planService;
    }

    /**
     * Lists all plan versions for a task, ordered by version descending.
     */
    @GetMapping("/task/{taskId}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<List<TaskPlan>>> listPlans(@PathVariable UUID taskId) {
        List<TaskPlan> plans = planService.listPlans(taskId);
        return ResponseEntity.ok(ApiResponse.success(plans));
    }

    /**
     * Returns the latest plan for a task.
     */
    @GetMapping("/task/{taskId}/latest")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<TaskPlan>> getLatestPlan(@PathVariable UUID taskId) {
        TaskPlan plan = planService.getLatestPlan(taskId);
        return ResponseEntity.ok(ApiResponse.success(plan));
    }

    /**
     * Approves or rejects a plan.
     */
    @PostMapping("/approve")
    @PreAuthorize("hasAnyRole('squadron-admin','team-lead')")
    public ResponseEntity<ApiResponse<TaskPlan>> approvePlan(@Valid @RequestBody PlanApprovalRequest request) {
        UUID userId = TenantContext.getUserId();
        TaskPlan plan = planService.approvePlan(request.getPlanId(), userId);
        return ResponseEntity.ok(ApiResponse.success(plan));
    }
}
