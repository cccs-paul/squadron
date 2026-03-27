package com.squadron.identity.controller;

import com.squadron.common.dto.ApiResponse;
import com.squadron.identity.entity.AuthProviderConfig;
import com.squadron.identity.service.AuthProviderConfigService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * REST controller for auth provider config management (admin only).
 */
@RestController
@RequestMapping("/api/auth-providers")
@RequiredArgsConstructor
public class AuthProviderConfigController {

    private final AuthProviderConfigService authProviderConfigService;

    @PostMapping
    public ResponseEntity<ApiResponse<AuthProviderConfig>> createConfig(@RequestBody Map<String, Object> body) {
        UUID tenantId = UUID.fromString(body.get("tenantId").toString());
        String providerType = body.get("providerType").toString();
        String name = body.get("name").toString();
        String config = body.containsKey("config") ? body.get("config").toString() : "{}";
        boolean enabled = body.containsKey("enabled") ? Boolean.parseBoolean(body.get("enabled").toString()) : true;
        int priority = body.containsKey("priority") ? Integer.parseInt(body.get("priority").toString()) : 0;

        AuthProviderConfig created = authProviderConfigService.createConfig(
                tenantId, providerType, name, config, enabled, priority);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(created));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<AuthProviderConfig>>> listConfigs(@RequestParam UUID tenantId) {
        List<AuthProviderConfig> configs = authProviderConfigService.listConfigs(tenantId);
        return ResponseEntity.ok(ApiResponse.success(configs));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<AuthProviderConfig>> getConfig(@PathVariable UUID id) {
        AuthProviderConfig config = authProviderConfigService.getConfig(id);
        return ResponseEntity.ok(ApiResponse.success(config));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<AuthProviderConfig>> updateConfig(
            @PathVariable UUID id,
            @RequestBody Map<String, Object> body) {
        String name = body.containsKey("name") ? body.get("name").toString() : null;
        String providerType = body.containsKey("providerType") ? body.get("providerType").toString() : null;
        String config = body.containsKey("config") ? body.get("config").toString() : null;
        Boolean enabled = body.containsKey("enabled") ? Boolean.parseBoolean(body.get("enabled").toString()) : null;
        Integer priority = body.containsKey("priority") ? Integer.parseInt(body.get("priority").toString()) : null;

        AuthProviderConfig updated = authProviderConfigService.updateConfig(id, name, providerType, config, enabled, priority);
        return ResponseEntity.ok(ApiResponse.success(updated));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> deleteConfig(@PathVariable UUID id) {
        authProviderConfigService.deleteConfig(id);
        return ResponseEntity.ok(ApiResponse.success(null));
    }
}
