package com.squadron.agent.controller;

import com.squadron.agent.dto.AgentDashboardDto;
import com.squadron.agent.service.AgentDashboardService;
import com.squadron.common.dto.ApiResponse;
import com.squadron.common.security.TenantContext;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * Provides the aggregated agentic-work dashboard data.
 * Endpoint: GET /api/agents/dashboard
 */
@RestController
@RequestMapping("/api/agents/dashboard")
public class AgentDashboardController {

    private final AgentDashboardService dashboardService;

    public AgentDashboardController(AgentDashboardService dashboardService) {
        this.dashboardService = dashboardService;
    }

    /**
     * Returns the full agent dashboard payload for the current tenant.
     */
    @GetMapping
    @PreAuthorize("hasAnyRole('squadron-admin','team-lead','developer')")
    public ResponseEntity<ApiResponse<AgentDashboardDto>> getDashboard() {
        UUID tenantId = TenantContext.getTenantId();
        AgentDashboardDto dashboard = dashboardService.getDashboard(tenantId);
        return ResponseEntity.ok(ApiResponse.success(dashboard));
    }
}
