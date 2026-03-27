package com.squadron.agent.controller;

import com.squadron.agent.dto.SquadronConfigDto;
import com.squadron.agent.entity.SquadronConfig;
import com.squadron.agent.service.SquadronConfigService;
import com.squadron.common.dto.ApiResponse;
import jakarta.validation.Valid;
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
@RequestMapping("/api/agents/config")
public class SquadronConfigController {

    private final SquadronConfigService configService;

    public SquadronConfigController(SquadronConfigService configService) {
        this.configService = configService;
    }

    /**
     * Creates or updates a squadron configuration.
     */
    @PostMapping
    @PreAuthorize("hasRole('squadron-admin')")
    public ResponseEntity<ApiResponse<SquadronConfig>> createOrUpdateConfig(
            @Valid @RequestBody SquadronConfigDto dto) {
        SquadronConfig config = configService.createOrUpdateConfig(dto);
        return ResponseEntity.ok(ApiResponse.success(config));
    }

    /**
     * Lists all configurations for a tenant.
     */
    @GetMapping("/tenant/{tenantId}")
    @PreAuthorize("hasRole('squadron-admin')")
    public ResponseEntity<ApiResponse<List<SquadronConfig>>> listConfigs(@PathVariable UUID tenantId) {
        List<SquadronConfig> configs = configService.listConfigsByTenant(tenantId);
        return ResponseEntity.ok(ApiResponse.success(configs));
    }

    /**
     * Resolves the effective configuration using the hierarchy:
     * user-specific -> team-level -> tenant-level.
     */
    @GetMapping("/resolve")
    @PreAuthorize("hasRole('squadron-admin')")
    public ResponseEntity<ApiResponse<SquadronConfigDto>> resolveConfig(
            @RequestParam UUID tenantId,
            @RequestParam(required = false) UUID teamId,
            @RequestParam(required = false) UUID userId) {
        SquadronConfigDto config = configService.resolveConfig(tenantId, teamId, userId);
        return ResponseEntity.ok(ApiResponse.success(config));
    }

    /**
     * Returns a specific configuration by ID.
     */
    @GetMapping("/{id}")
    @PreAuthorize("hasRole('squadron-admin')")
    public ResponseEntity<ApiResponse<SquadronConfig>> getConfig(@PathVariable UUID id) {
        SquadronConfig config = configService.getConfig(id);
        return ResponseEntity.ok(ApiResponse.success(config));
    }

    /**
     * Deletes a configuration.
     */
    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('squadron-admin')")
    public ResponseEntity<ApiResponse<Void>> deleteConfig(@PathVariable UUID id) {
        configService.deleteConfig(id);
        return ResponseEntity.ok(ApiResponse.success(null));
    }
}
