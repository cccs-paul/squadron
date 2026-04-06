package com.squadron.platform.controller;

import com.squadron.common.dto.ApiResponse;
import com.squadron.common.dto.CredentialResolutionResult;
import com.squadron.common.dto.ResolveCredentialRequest;
import com.squadron.platform.service.CredentialResolutionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/platforms/credentials")
@Tag(name = "Credentials", description = "Resolve credentials for platform connections")
public class CredentialController {

    private final CredentialResolutionService credentialResolutionService;

    public CredentialController(CredentialResolutionService credentialResolutionService) {
        this.credentialResolutionService = credentialResolutionService;
    }

    @PostMapping("/resolve")
    @PreAuthorize("hasAnyRole('squadron-admin', 'team-lead', 'developer')")
    @Operation(summary = "Resolve credentials for a platform connection")
    public ResponseEntity<ApiResponse<CredentialResolutionResult>> resolveCredentials(
            @Valid @RequestBody ResolveCredentialRequest request) {
        CredentialResolutionResult result = credentialResolutionService.resolveCredentials(
                request.getUserId(), request.getConnectionId(), request.getPurpose());
        return ResponseEntity.ok(ApiResponse.success(result));
    }
}
