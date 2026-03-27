package com.squadron.config.controller;

import com.squadron.common.dto.ApiResponse;
import com.squadron.config.dto.ConfigEntryDto;
import com.squadron.config.dto.ConfigUpdateRequest;
import com.squadron.config.dto.ResolvedConfigDto;
import com.squadron.config.entity.ConfigAuditLog;
import com.squadron.config.entity.ConfigEntry;
import com.squadron.config.service.ConfigService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
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
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/config")
@RequiredArgsConstructor
public class ConfigController {

    private final ConfigService configService;

    @GetMapping("/resolve")
    @PreAuthorize("hasAnyRole('squadron-admin','team-lead','developer','qa','viewer')")
    public ResponseEntity<ApiResponse<ResolvedConfigDto>> resolveConfig(
            @RequestParam UUID tenantId,
            @RequestParam(required = false) UUID teamId,
            @RequestParam(required = false) UUID userId,
            @RequestParam String key) {
        ResolvedConfigDto resolved = configService.resolveConfig(tenantId, teamId, userId, key);
        return ResponseEntity.ok(ApiResponse.success(resolved));
    }

    @GetMapping("/tenant/{tenantId}")
    @PreAuthorize("hasAnyRole('squadron-admin','team-lead','developer','qa','viewer')")
    public ResponseEntity<ApiResponse<List<ConfigEntryDto>>> listTenantConfigs(
            @PathVariable UUID tenantId) {
        List<ConfigEntryDto> configs = configService.listConfigs(tenantId, null, null)
                .stream()
                .map(this::toDto)
                .collect(Collectors.toList());
        return ResponseEntity.ok(ApiResponse.success(configs));
    }

    @GetMapping("/tenant/{tenantId}/team/{teamId}")
    @PreAuthorize("hasAnyRole('squadron-admin','team-lead','developer','qa','viewer')")
    public ResponseEntity<ApiResponse<List<ConfigEntryDto>>> listTeamConfigs(
            @PathVariable UUID tenantId,
            @PathVariable UUID teamId) {
        List<ConfigEntryDto> configs = configService.listConfigs(tenantId, teamId, null)
                .stream()
                .map(this::toDto)
                .collect(Collectors.toList());
        return ResponseEntity.ok(ApiResponse.success(configs));
    }

    @GetMapping("/tenant/{tenantId}/user/{userId}")
    @PreAuthorize("hasAnyRole('squadron-admin','team-lead','developer','qa','viewer')")
    public ResponseEntity<ApiResponse<List<ConfigEntryDto>>> listUserConfigs(
            @PathVariable UUID tenantId,
            @PathVariable UUID userId) {
        List<ConfigEntryDto> configs = configService.listConfigs(tenantId, null, userId)
                .stream()
                .map(this::toDto)
                .collect(Collectors.toList());
        return ResponseEntity.ok(ApiResponse.success(configs));
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('squadron-admin','team-lead')")
    public ResponseEntity<ApiResponse<ConfigEntryDto>> setConfig(
            @RequestParam UUID tenantId,
            @RequestParam(required = false) UUID teamId,
            @RequestParam(required = false) UUID userId,
            @Valid @RequestBody ConfigUpdateRequest request) {
        ConfigEntry saved = configService.setConfig(tenantId, teamId, userId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(toDto(saved)));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('squadron-admin','team-lead')")
    public ResponseEntity<ApiResponse<Void>> deleteConfig(@PathVariable UUID id) {
        configService.deleteConfig(id);
        return ResponseEntity.ok(ApiResponse.success(null));
    }

    @GetMapping("/audit/{configEntryId}")
    @PreAuthorize("hasAnyRole('squadron-admin','team-lead','developer','qa','viewer')")
    public ResponseEntity<ApiResponse<List<ConfigAuditLog>>> getAuditLog(
            @PathVariable UUID configEntryId) {
        List<ConfigAuditLog> auditLogs = configService.getAuditLog(configEntryId);
        return ResponseEntity.ok(ApiResponse.success(auditLogs));
    }

    private ConfigEntryDto toDto(ConfigEntry entry) {
        return ConfigEntryDto.builder()
                .id(entry.getId())
                .tenantId(entry.getTenantId())
                .teamId(entry.getTeamId())
                .userId(entry.getUserId())
                .configKey(entry.getConfigKey())
                .configValue(entry.getConfigValue())
                .description(entry.getDescription())
                .createdAt(entry.getCreatedAt())
                .updatedAt(entry.getUpdatedAt())
                .createdBy(entry.getCreatedBy())
                .build();
    }
}
