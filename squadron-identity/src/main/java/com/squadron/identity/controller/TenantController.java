package com.squadron.identity.controller;

import com.squadron.common.dto.ApiResponse;
import com.squadron.common.dto.TenantDto;
import com.squadron.identity.service.TenantService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/tenants")
@RequiredArgsConstructor
public class TenantController {

    private final TenantService tenantService;

    @PostMapping
    public ResponseEntity<ApiResponse<TenantDto>> createTenant(@Valid @RequestBody TenantDto dto) {
        TenantDto created = tenantService.createTenant(dto);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(created));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<TenantDto>>> listTenants() {
        List<TenantDto> tenants = tenantService.listTenants();
        return ResponseEntity.ok(ApiResponse.success(tenants));
    }

    @GetMapping("/current")
    public ResponseEntity<ApiResponse<TenantDto>> getCurrentTenant() {
        UUID tenantId = extractTenantId();
        TenantDto tenant = tenantService.getTenant(tenantId);
        return ResponseEntity.ok(ApiResponse.success(tenant));
    }

    @PatchMapping("/current/settings")
    public ResponseEntity<ApiResponse<TenantDto>> updateCurrentTenantSettings(
            @RequestBody Map<String, Object> settings) {
        UUID tenantId = extractTenantId();
        TenantDto updated = tenantService.updateTenantSettings(tenantId, settings);
        return ResponseEntity.ok(ApiResponse.success(updated));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<TenantDto>> getTenant(@PathVariable UUID id) {
        TenantDto tenant = tenantService.getTenant(id);
        return ResponseEntity.ok(ApiResponse.success(tenant));
    }

    @GetMapping("/slug/{slug}")
    public ResponseEntity<ApiResponse<TenantDto>> getTenantBySlug(@PathVariable String slug) {
        TenantDto tenant = tenantService.getTenantBySlug(slug);
        return ResponseEntity.ok(ApiResponse.success(tenant));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<TenantDto>> updateTenant(@PathVariable UUID id,
                                                                @Valid @RequestBody TenantDto dto) {
        TenantDto updated = tenantService.updateTenant(id, dto);
        return ResponseEntity.ok(ApiResponse.success(updated));
    }

    private UUID extractTenantId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth instanceof JwtAuthenticationToken jwtAuth) {
            String tenantIdStr = jwtAuth.getToken().getClaimAsString("tenant_id");
            if (tenantIdStr != null) {
                return UUID.fromString(tenantIdStr);
            }
        }
        throw new IllegalArgumentException("No tenant_id claim found in JWT");
    }
}
