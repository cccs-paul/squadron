package com.squadron.identity.controller;

import com.squadron.common.dto.ApiResponse;
import com.squadron.common.dto.ResourcePermissionDto;
import com.squadron.common.security.AccessLevel;
import com.squadron.common.security.TenantContext;
import com.squadron.identity.service.PermissionService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * REST controller for resource permission management.
 */
@RestController
@RequestMapping("/api/permissions")
@RequiredArgsConstructor
public class PermissionController {

    private final PermissionService permissionService;

    @PostMapping
    public ResponseEntity<ApiResponse<ResourcePermissionDto>> grantPermission(
            @Valid @RequestBody ResourcePermissionDto dto) {
        ResourcePermissionDto created = permissionService.grantPermission(
                dto.getTenantId(), dto.getResourceType(), dto.getResourceId(),
                dto.getGranteeType(), dto.getGranteeId(), dto.getAccessLevel());
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(created));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> revokePermission(@PathVariable UUID id) {
        permissionService.revokePermission(id);
        return ResponseEntity.ok(ApiResponse.success(null));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<ResourcePermissionDto>>> getPermissions(
            @RequestParam UUID tenantId,
            @RequestParam String resourceType,
            @RequestParam UUID resourceId) {
        List<ResourcePermissionDto> permissions = permissionService.getPermissions(tenantId, resourceType, resourceId);
        return ResponseEntity.ok(ApiResponse.success(permissions));
    }

    @GetMapping("/check")
    public ResponseEntity<ApiResponse<Map<String, Object>>> checkAccess(
            @RequestParam String resourceType,
            @RequestParam UUID resourceId,
            @RequestParam String accessLevel) {
        UUID userId = TenantContext.getUserId();
        UUID tenantId = TenantContext.getTenantId();

        if (userId == null || tenantId == null) {
            return ResponseEntity.ok(ApiResponse.success(Map.of("hasAccess", false)));
        }

        AccessLevel required = AccessLevel.valueOf(accessLevel);
        boolean hasAccess = permissionService.checkAccess(userId, tenantId, resourceType, resourceId, required);
        AccessLevel effective = permissionService.getEffectiveAccessLevel(userId, tenantId, resourceType, resourceId);

        return ResponseEntity.ok(ApiResponse.success(Map.of(
                "hasAccess", hasAccess,
                "effectiveAccessLevel", effective.name()
        )));
    }
}
