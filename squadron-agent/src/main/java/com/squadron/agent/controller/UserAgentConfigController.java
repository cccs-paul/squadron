package com.squadron.agent.controller;

import com.squadron.agent.dto.UserAgentConfigDto;
import com.squadron.agent.entity.UserAgentConfig;
import com.squadron.agent.service.UserAgentConfigService;
import com.squadron.common.dto.ApiResponse;
import com.squadron.common.security.TenantContext;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * REST controller for managing a user's personal AI agent squadron.
 * All endpoints operate on the currently authenticated user's agents.
 * Path: /api/agents/squadron (forwarded from gateway without stripPrefix).
 */
@RestController
@RequestMapping("/api/agents/squadron")
public class UserAgentConfigController {

    private final UserAgentConfigService service;

    public UserAgentConfigController(UserAgentConfigService service) {
        this.service = service;
    }

    /**
     * Returns the current user's agent squadron.
     * Seeds defaults if the user has no agents configured.
     */
    @GetMapping
    public ResponseEntity<ApiResponse<List<UserAgentConfig>>> getMySquadron() {
        UUID tenantId = TenantContext.getTenantId();
        UUID userId = TenantContext.getUserId();
        List<UserAgentConfig> agents = service.getUserSquadron(tenantId, userId);
        return ResponseEntity.ok(ApiResponse.success(agents));
    }

    /**
     * Returns the max agents per user configuration.
     */
    @GetMapping("/limits")
    public ResponseEntity<ApiResponse<Map<String, Integer>>> getLimits() {
        Map<String, Integer> limits = Map.of("maxAgentsPerUser", service.getMaxAgentsPerUser());
        return ResponseEntity.ok(ApiResponse.success(limits));
    }

    /**
     * Adds a new agent to the current user's squadron.
     */
    @PostMapping
    public ResponseEntity<ApiResponse<UserAgentConfig>> addAgent(
            @Valid @RequestBody UserAgentConfigDto dto) {
        UUID tenantId = TenantContext.getTenantId();
        UUID userId = TenantContext.getUserId();
        UserAgentConfig agent = service.addAgent(tenantId, userId, dto);
        return ResponseEntity.ok(ApiResponse.success(agent));
    }

    /**
     * Updates an existing agent in the current user's squadron.
     */
    @PutMapping("/{agentId}")
    public ResponseEntity<ApiResponse<UserAgentConfig>> updateAgent(
            @PathVariable UUID agentId,
            @Valid @RequestBody UserAgentConfigDto dto) {
        UUID tenantId = TenantContext.getTenantId();
        UUID userId = TenantContext.getUserId();
        UserAgentConfig agent = service.updateAgent(tenantId, userId, agentId, dto);
        return ResponseEntity.ok(ApiResponse.success(agent));
    }

    /**
     * Removes an agent from the current user's squadron.
     * At least 1 agent must remain.
     */
    @DeleteMapping("/{agentId}")
    public ResponseEntity<ApiResponse<Void>> removeAgent(@PathVariable UUID agentId) {
        UUID tenantId = TenantContext.getTenantId();
        UUID userId = TenantContext.getUserId();
        service.removeAgent(tenantId, userId, agentId);
        return ResponseEntity.ok(ApiResponse.success(null));
    }

    /**
     * Resets the user's squadron back to default agents.
     */
    @PostMapping("/reset")
    public ResponseEntity<ApiResponse<List<UserAgentConfig>>> resetToDefaults() {
        UUID tenantId = TenantContext.getTenantId();
        UUID userId = TenantContext.getUserId();
        List<UserAgentConfig> agents = service.resetToDefaults(tenantId, userId);
        return ResponseEntity.ok(ApiResponse.success(agents));
    }
}
