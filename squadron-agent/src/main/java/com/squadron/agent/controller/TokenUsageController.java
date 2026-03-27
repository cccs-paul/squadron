package com.squadron.agent.controller;

import com.squadron.agent.dto.UsageByAgentDto;
import com.squadron.agent.dto.UsageSummaryDto;
import com.squadron.agent.service.TokenUsageService;
import com.squadron.common.dto.ApiResponse;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/agent/usage")
public class TokenUsageController {

    private final TokenUsageService tokenUsageService;

    public TokenUsageController(TokenUsageService tokenUsageService) {
        this.tokenUsageService = tokenUsageService;
    }

    @GetMapping("/tenant/{tenantId}")
    public ResponseEntity<ApiResponse<UsageSummaryDto>> getTenantSummary(
            @PathVariable UUID tenantId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant start,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant end) {
        return ResponseEntity.ok(ApiResponse.success(tokenUsageService.getTenantSummary(tenantId, start, end)));
    }

    @GetMapping("/tenant/{tenantId}/user/{userId}")
    public ResponseEntity<ApiResponse<UsageSummaryDto>> getUserSummary(
            @PathVariable UUID tenantId,
            @PathVariable UUID userId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant start,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant end) {
        return ResponseEntity.ok(ApiResponse.success(tokenUsageService.getUserSummary(tenantId, userId, start, end)));
    }

    @GetMapping("/tenant/{tenantId}/team/{teamId}")
    public ResponseEntity<ApiResponse<UsageSummaryDto>> getTeamSummary(
            @PathVariable UUID tenantId,
            @PathVariable UUID teamId) {
        return ResponseEntity.ok(ApiResponse.success(tokenUsageService.getTeamSummary(tenantId, teamId)));
    }

    @GetMapping("/tenant/{tenantId}/by-agent")
    public ResponseEntity<ApiResponse<List<UsageByAgentDto>>> getByAgentType(
            @PathVariable UUID tenantId) {
        return ResponseEntity.ok(ApiResponse.success(tokenUsageService.getUsageByAgentType(tenantId)));
    }
}
