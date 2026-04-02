package com.squadron.platform.controller;

import com.squadron.common.dto.ApiResponse;
import com.squadron.platform.dto.CreateSshKeyRequest;
import com.squadron.platform.dto.SshKeyResponse;
import com.squadron.platform.entity.SshKey;
import com.squadron.platform.service.SshKeyService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/platforms/ssh-keys")
@Tag(name = "SSH Keys", description = "Manage SSH keys for Git platform connections")
public class SshKeyController {

    private final SshKeyService sshKeyService;

    public SshKeyController(SshKeyService sshKeyService) {
        this.sshKeyService = sshKeyService;
    }

    @PostMapping
    @Operation(summary = "Create a new SSH key")
    public ResponseEntity<ApiResponse<SshKeyResponse>> createSshKey(
            @Valid @RequestBody CreateSshKeyRequest request) {
        SshKey created = sshKeyService.createSshKey(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(SshKeyResponse.fromEntity(created)));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get an SSH key by ID")
    public ResponseEntity<ApiResponse<SshKeyResponse>> getSshKey(@PathVariable UUID id) {
        SshKey sshKey = sshKeyService.getSshKey(id);
        return ResponseEntity.ok(ApiResponse.success(SshKeyResponse.fromEntity(sshKey)));
    }

    @GetMapping("/tenant/{tenantId}")
    @Operation(summary = "List SSH keys for a tenant")
    public ResponseEntity<ApiResponse<List<SshKeyResponse>>> listSshKeysByTenant(
            @PathVariable UUID tenantId) {
        List<SshKeyResponse> keys = sshKeyService.listSshKeysByTenant(tenantId)
                .stream()
                .map(SshKeyResponse::fromEntity)
                .toList();
        return ResponseEntity.ok(ApiResponse.success(keys));
    }

    @GetMapping("/connection/{connectionId}")
    @Operation(summary = "List SSH keys for a connection")
    public ResponseEntity<ApiResponse<List<SshKeyResponse>>> listSshKeysByConnection(
            @PathVariable UUID connectionId) {
        List<SshKeyResponse> keys = sshKeyService.listSshKeysByConnection(connectionId)
                .stream()
                .map(SshKeyResponse::fromEntity)
                .toList();
        return ResponseEntity.ok(ApiResponse.success(keys));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete an SSH key")
    public ResponseEntity<Void> deleteSshKey(@PathVariable UUID id) {
        sshKeyService.deleteSshKey(id);
        return ResponseEntity.noContent().build();
    }

    /**
     * Internal endpoint for inter-service calls to retrieve decrypted private key.
     * This should be secured to only allow service-to-service communication.
     */
    @GetMapping("/{id}/private-key")
    @Operation(summary = "Get decrypted private key (internal use only)")
    public ResponseEntity<ApiResponse<String>> getDecryptedPrivateKey(@PathVariable UUID id) {
        String privateKey = sshKeyService.getDecryptedPrivateKey(id);
        return ResponseEntity.ok(ApiResponse.success(privateKey));
    }
}
