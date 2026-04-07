package com.squadron.platform.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

/**
 * Request DTO for server-side deploy key generation.
 * Unlike {@link CreateSshKeyRequest}, this does not require the caller to supply
 * a public/private key pair -- the server generates an Ed25519 keypair automatically.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GenerateDeployKeyRequest {

    @NotNull(message = "Tenant ID is required")
    private UUID tenantId;

    @NotNull(message = "Connection ID is required")
    private UUID connectionId;

    @NotBlank(message = "Key name is required")
    private String name;

    /**
     * Key usage: DEPLOY_KEY or USER_KEY. Defaults to DEPLOY_KEY if not specified.
     */
    private String keyUsage;
}
